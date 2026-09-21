package com.dugan.agent.domain.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal RIFF/WAVE reader for 16-bit PCM.
 *
 * Groq's Orpheus TTS returns WAV and nothing else, and a WAV body is already
 * PCM: walking the header here is cheaper than routing it through MediaCodec
 * and, unlike [AudioDecoder], it has no Android dependency, so the decode step
 * is covered by a JVM unit test rather than by hope. [AudioDecoder] stays for
 * the genuinely compressed formats (Edge TTS hands back MP3).
 */
object Wav {

    /** Not called `Decoded`: AudioDecoder already owns that name in this package. */
    data class Audio(val sampleRate: Int, val channels: Int, val pcm: ShortArray)

    /** @return the decoded PCM, or null when [bytes] is not a 16-bit PCM WAV. */
    fun decode(bytes: ByteArray): Audio? {
        if (bytes.size < 44) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bb.ascii(0, 4) != "RIFF" || bb.ascii(8, 4) != "WAVE") return null

        var sampleRate = 0
        var channels = 1
        var bitsPerSample = 0

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = bb.ascii(offset, 4)
            val size = bb.getInt(offset + 4)
            if (size < 0) return null
            val body = offset + 8
            when (id) {
                "fmt " -> {
                    if (body + 16 > bytes.size) return null
                    channels = bb.getShort(body + 2).toInt().coerceAtLeast(1)
                    sampleRate = bb.getInt(body + 4)
                    bitsPerSample = bb.getShort(body + 14).toInt()
                }

                "data" -> {
                    if (sampleRate <= 0 || bitsPerSample != 16) return null
                    val available = minOf(size, bytes.size - body)
                    val pcm = ShortArray(available / 2)
                    for (i in pcm.indices) pcm[i] = bb.getShort(body + i * 2)
                    return Audio(sampleRate, channels, pcm)
                }
            }
            // Chunks are word aligned: an odd size carries one pad byte.
            offset = body + size + (size and 1)
        }
        return null
    }

    /**
     * Interleaved multi-channel samples down to mono. Groq returns mono, but
     * AudioTrack is configured for a single channel, so a stereo body would
     * otherwise play at double speed.
     */
    fun toMono(pcm: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return pcm
        val frames = pcm.size / channels
        val out = ShortArray(frames)
        for (frame in 0 until frames) {
            var acc = 0
            for (c in 0 until channels) acc += pcm[frame * channels + c]
            out[frame] = (acc / channels).toShort()
        }
        return out
    }

    private fun ByteBuffer.ascii(offset: Int, length: Int): String =
        String(ByteArray(length) { get(offset + it) }, Charsets.US_ASCII)
}
