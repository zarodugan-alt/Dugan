package com.dugan.agent.domain.audio

import com.dugan.agent.util.AgentLog
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-latency playback plus the far-end reference the echo layers need.
 *
 * Two responsibilities, and the second is the one that is easy to forget:
 *  1. Write decoded TTS PCM to AudioTrack in 20ms frames so the small
 *     PERFORMANCE_MODE_LOW_LATENCY buffer never starves.
 *  2. Record *when* each chunk hit the speaker. Layer 3 (mic gating) and layer 4
 *     (barge-in correlation) both key off that log, and layer 2 needs the
 *     samples themselves as the AEC reference signal.
 *
 * Chunks are queued rather than played one call at a time: the pipeline
 * synthesises sentence N+1 while sentence N is still audible, and a per-call
 * AudioTrack would truncate the first at the second's arrival.
 */
@Singleton
class AudioPlaybackEngine @Inject constructor() {

    data class PlaybackChunk(
        val startedAtMs: Long,
        val endedAtMs: Long,
        val samples: ShortArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PlaybackChunk) return false
            return startedAtMs == other.startedAtMs &&
                endedAtMs == other.endedAtMs &&
                samples.contentEquals(other.samples)
        }

        override fun hashCode(): Int = startedAtMs.hashCode() * 31 + samples.contentHashCode()
    }

    @Volatile
    var sampleRate: Int = DEFAULT_RATE
        private set

    @Volatile
    var isPlaying: Boolean = false
        private set

    private val running = AtomicBoolean(false)
    private var track: AudioTrack? = null
    private var writer: Job? = null
    private var queue: Channel<ShortArray>? = null

    /** Ring of recent output chunks, newest last. Bounded so it cannot grow unboundedly. */
    private val recentOutput = ArrayDeque<PlaybackChunk>()

    @Volatile
    var onLevel: (Float) -> Unit = {}

    /**
     * Opens the output path. Idempotent: calling it again at the same rate is a
     * no-op, so the orchestrator can call it on every sentence safely.
     *
     * @return false if AudioTrack could not be created (no output device).
     */
    fun start(scope: CoroutineScope, rate: Int = DEFAULT_RATE): Boolean {
        if (running.get() && sampleRate == rate) return true
        stop()

        val minBuffer = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            AgentLog.w(TAG, "AudioTrack unavailable for rate=$rate (min=$minBuffer)")
            return false
        }

        val newTrack = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // USAGE_VOICE_COMMUNICATION routes into the call path and, on
                        // most devices, engages the same AEC the mic is using.
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                // 2x min keeps the low-latency path; a larger buffer re-arms the
                // deep-buffer mixer and adds roughly 100ms.
                .setBufferSizeInBytes(minBuffer * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        }.getOrElse {
            AgentLog.w(TAG, "AudioTrack init failed: ${it.javaClass.simpleName}: ${it.message}")
            return false
        }

        sampleRate = rate
        track = newTrack
        queue = Channel(Channel.UNLIMITED)
        running.set(true)
        isPlaying = true
        newTrack.play()

        writer = scope.launch(Dispatchers.IO) { drain(newTrack) }
        return true
    }

    /** Queues PCM for playback. Returns false if the engine is not running. */
    fun enqueue(samples: ShortArray): Boolean {
        if (!running.get() || samples.isEmpty()) return false
        onLevel(Pcm.rms(samples))
        return queue?.trySend(samples)?.isSuccess == true
    }

    /** Blocks until everything queued has reached the speaker. */
    suspend fun drainQuietly(timeoutMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (running.get() && System.currentTimeMillis() < deadline) {
            val pending = queue?.isEmpty ?: true
            val inFlight = track?.playbackHeadPosition ?: 0
            if (pending && writtenFrames <= inFlight.toLong()) return
            kotlinx.coroutines.delay(20)
        }
    }

    @Volatile
    private var writtenFrames: Long = 0

    private suspend fun drain(track: AudioTrack) {
        val channel = queue ?: return
        val frameSamples = (sampleRate / 50).coerceAtLeast(1) // 20ms
        val pending = ShortArray(frameSamples)
        var pendingCount = 0

        try {
            for (chunk in channel) {
                if (!running.get()) break
                var offset = 0
                while (offset < chunk.size && running.get()) {
                    val take = minOf(frameSamples - pendingCount, chunk.size - offset)
                    System.arraycopy(chunk, offset, pending, pendingCount, take)
                    pendingCount += take
                    offset += take
                    if (pendingCount == frameSamples) {
                        write(track, pending, frameSamples)
                        pendingCount = 0
                    }
                }
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Barge-in / Stop: leave immediately, the caller flushes.
        } finally {
            if (pendingCount > 0 && running.get()) write(track, pending, pendingCount)
            onLevel(0f)
        }
    }

    private fun write(track: AudioTrack, buffer: ShortArray, length: Int) {
        val startedAtMs = System.currentTimeMillis()
        var written = 0
        while (written < length && running.get()) {
            val n = track.write(buffer, written, length - written)
            if (n <= 0) break
            written += n
        }
        writtenFrames += written
        recordOutput(startedAtMs, buffer.copyOf(length))
    }

    private fun recordOutput(startedAtMs: Long, samples: ShortArray) {
        val endedAtMs = startedAtMs + (samples.size * 1000L / sampleRate.coerceAtLeast(1))
        synchronized(recentOutput) {
            recentOutput.addLast(PlaybackChunk(startedAtMs, endedAtMs, samples))
            while (recentOutput.size > MAX_RECENT_CHUNKS) recentOutput.removeFirst()
        }
    }

    /** Output active within [windowMs] of [atMs]; used by mic gating. */
    fun wasSpeakingAt(atMs: Long, windowMs: Long = 250): Boolean = synchronized(recentOutput) {
        recentOutput.any { atMs >= it.startedAtMs - windowMs && atMs <= it.endedAtMs + windowMs }
    }

    /** Far-end reference samples overlapping [atMs], for AEC and barge-in correlation. */
    fun referenceAround(atMs: Long, windowMs: Long = 500): ShortArray = synchronized(recentOutput) {
        val matching = recentOutput.filter {
            it.endedAtMs >= atMs - windowMs && it.startedAtMs <= atMs + windowMs
        }
        if (matching.isEmpty()) ShortArray(0) else Pcm.concat(*matching.map { it.samples }.toTypedArray())
    }

    fun recentOutputSnapshot(): List<PlaybackChunk> = synchronized(recentOutput) { recentOutput.toList() }

    fun clearOutputLog() {
        synchronized(recentOutput) { recentOutput.clear() }
        writtenFrames = 0
    }

    /** Hard stop: flush the buffer so barge-in silence is immediate. */
    fun stop() {
        running.set(false)
        isPlaying = false
        runCatching { writer?.cancel() }
        writer = null
        runCatching { queue?.close() }
        queue = null
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
        writtenFrames = 0
        onLevel(0f)
    }

    /** Route the loudspeaker on/off. Required for SIM capture. */
    fun setSpeakerphone(audioManager: AudioManager, on: Boolean) {
        runCatching {
            audioManager.mode = if (on) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = on
        }
    }

    private companion object {
        const val TAG = "AudioPlayback"
        const val DEFAULT_RATE = 24_000

        /** ~10s of 20ms frames. */
        const val MAX_RECENT_CHUNKS = 500
    }
}

/** Convenience for callers that just want to fire a whole buffer at the engine. */
suspend fun AudioPlaybackEngine.playAll(scope: CoroutineScope, pcm: ShortArray, rate: Int) {
    if (!start(scope, rate)) return
    enqueue(pcm)
    drainQuietly()
}
