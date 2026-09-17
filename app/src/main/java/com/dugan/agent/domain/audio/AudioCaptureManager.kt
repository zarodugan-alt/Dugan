package com.dugan.agent.domain.audio

import com.dugan.agent.util.AgentLog
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.dugan.agent.domain.model.CallTransport
import com.dugan.agent.domain.model.InputSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the AudioRecord lifecycle.
 *
 * Source selection is the whole ballgame on Android:
 *  - VOICE_COMMUNICATION gives the far end cleanly and routes through the
 *    platform's hardware AEC/NS/AGC, but is only reachable during a VoIP call.
 *  - VOICE_CALL / VOICE_UPLINK / VOICE_DOWNLINK are denied to third-party apps
 *    from Android 9 on, so a SIM call can only be heard by putting the call on
 *    the loudspeaker and recording with MIC. Both voices then arrive mixed.
 */
@Singleton
class AudioCaptureManager @Inject constructor() {

    data class CaptureConfig(
        val inputSource: InputSource = InputSource.Auto,
        val transport: CallTransport = CallTransport.None,
        val sampleRate: Int = 16_000,
        /** 20ms at 16kHz. Matches the AudioTrack write frame. */
        val frameMs: Int = 20,
    )

    /** Which layers the platform actually gave us -- surfaced in Settings > Audio. */
    data class CaptureDiagnostics(
        val resolvedSource: Int,
        val hardwareAec: Boolean = false,
        val noiseSuppressor: Boolean = false,
        val automaticGainControl: Boolean = false,
        val bufferSizeBytes: Int = 0,
    )

    @Volatile
    var diagnostics: CaptureDiagnostics? = null
        private set

    @Volatile
    var isCapturing: Boolean = false
        private set

    /**
     * Hot loop: reads [config.frameMs] of audio at a time and emits frames until
     * the collecting coroutine is cancelled.
     */
    @SuppressLint("MissingPermission") // RECORD_AUDIO is gated in the UI layer
    fun capture(config: CaptureConfig): Flow<AudioFrame> = callbackFlow {
        val source = resolveSource(config)
        val frameSamples = config.sampleRate * config.frameMs / 1000
        val minBuffer = AudioRecord.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            close(IllegalStateException("AudioRecord.getMinBufferSize returned $minBuffer"))
            return@callbackFlow
        }
        // 4x the minimum: enough headroom that a GC pause or an STT upload does
        // not overrun the HAL ring buffer and drop samples.
        val bufferSize = (minBuffer * 4).coerceAtLeast(frameSamples * 2 * 4)

        val recorder = runCatching {
            AudioRecord(
                source,
                config.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        }.getOrElse {
            close(it)
            return@callbackFlow
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            close(IllegalStateException("AudioRecord failed to initialise for source $source"))
            return@callbackFlow
        }

        val sessionId = recorder.audioSessionId
        // Layer 1 of echo defence: whatever the platform offers, ask for it.
        val aec = enableIfAvailable(sessionId) { AcousticEchoCanceler.create(it) }
        val ns = enableIfAvailable(sessionId) { NoiseSuppressor.create(it) }
        val agc = enableIfAvailable(sessionId) { AutomaticGainControl.create(it) }

        diagnostics = CaptureDiagnostics(
            resolvedSource = source,
            hardwareAec = aec?.enabled == true,
            noiseSuppressor = ns?.enabled == true,
            automaticGainControl = agc?.enabled == true,
            bufferSizeBytes = bufferSize,
        )
        AgentLog.i(TAG, "capture started: source=$source aec=${aec?.enabled} ns=${ns?.enabled}")

        isCapturing = true
        recorder.startRecording()

        val buffer = ShortArray(frameSamples)
        withContext(Dispatchers.IO) {
            while (isActive) {
                val read = recorder.read(buffer, 0, frameSamples)
                if (read <= 0) {
                    // A negative read is a HAL error; give the loop a beat rather
                    // than spinning hot and burning the CPU.
                    if (read < 0) Thread.sleep(5)
                    continue
                }
                val frame = if (read == frameSamples) buffer.copyOf() else buffer.copyOf(read)
                trySend(
                    AudioFrame(
                        samples = frame,
                        rms = Pcm.rms(frame),
                        timestampMs = System.currentTimeMillis(),
                        sampleRate = config.sampleRate,
                    ),
                )
            }
        }

        awaitClose {
            isCapturing = false
            runCatching { recorder.stop() }
            recorder.release()
            runCatching { aec?.release() }
            runCatching { ns?.release() }
            runCatching { agc?.release() }
            diagnostics = null
            AgentLog.i(TAG, "capture stopped")
        }
    }

    /**
     * AUTO_SOURCE hands the platform the choice, which on many devices already
     * gives AEC. We only force MIC when we know we are on a loudspeaker SIM call.
     */
    private fun resolveSource(config: CaptureConfig): Int = when (config.inputSource) {
        InputSource.VoipCommunication -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
        InputSource.SimSpeakerphone -> MediaRecorder.AudioSource.MIC
        InputSource.Auto -> when (config.transport) {
            CallTransport.Voip -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            // SIM: the far end is in the room, so MIC is the only legal source.
            CallTransport.Sim -> MediaRecorder.AudioSource.MIC
            CallTransport.None -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }
    }

    private fun <T : android.media.audiofx.AudioEffect> enableIfAvailable(
        sessionId: Int,
        factory: (Int) -> T?,
    ): T? = runCatching {
        factory(sessionId)?.also { it.enabled = true }
    }.getOrNull()

    private companion object {
        const val TAG = "AudioCapture"
    }
}
