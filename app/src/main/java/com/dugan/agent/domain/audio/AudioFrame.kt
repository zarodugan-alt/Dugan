package com.dugan.agent.domain.audio

/**
 * One block of captured audio.
 *
 * @property samples interleaved-free mono PCM16
 * @property rms 0f..1f energy, pre-computed so the visualiser and VAD share work
 * @property timestampMs capture time; barge-in correlation needs this to line
 *   the mic signal up against the TTS playback log
 */
data class AudioFrame(
    val samples: ShortArray,
    val rms: Float,
    val timestampMs: Long,
    val sampleRate: Int,
) {
    val durationMs: Int get() = if (sampleRate == 0) 0 else samples.size * 1000 / sampleRate

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return samples.contentEquals(other.samples) &&
            rms == other.rms &&
            timestampMs == other.timestampMs &&
            sampleRate == other.sampleRate
    }

    override fun hashCode(): Int =
        ((samples.contentHashCode() * 31) + rms.hashCode()) * 31 + timestampMs.hashCode()
}
