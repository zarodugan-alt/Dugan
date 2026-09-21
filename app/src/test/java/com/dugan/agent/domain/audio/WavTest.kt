package com.dugan.agent.domain.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Groq's Orpheus TTS returns WAV and nothing else, and this is the only decoder
 * on that path -- so its behaviour is pinned here rather than discovered on a
 * call, at 3am, mid-conversation.
 */
class WavTest {

    /** Builds a RIFF/WAVE with an optional junk chunk before `data`. */
    private fun wav(
        samples: ShortArray,
        sampleRate: Int = 24_000,
        channels: Int = 1,
        junkChunk: Pair<String, ByteArray>? = null,
    ): ByteArray {
        val data = ByteArray(samples.size * 2)
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).let { bb ->
            samples.forEach { bb.putShort(it) }
        }
        val junk = junkChunk?.let { (id, body) -> 8 + body.size + (body.size and 1) } ?: 0
        val out = ByteBuffer.allocate(12 + 24 + junk + 8 + data.size).order(ByteOrder.LITTLE_ENDIAN)

        out.put("RIFF".toByteArray()).putInt(out.capacity() - 8).put("WAVE".toByteArray())
        // fmt chunk: PCM, channels, rate, byte rate, block align, bits
        out.put("fmt ".toByteArray()).putInt(16)
        out.putShort(1).putShort(channels.toShort()).putInt(sampleRate)
        out.putInt(sampleRate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        junkChunk?.let { (id, body) ->
            out.put(id.toByteArray()).putInt(body.size).put(body)
            if (body.size and 1 == 1) out.put(0) // word alignment pad
        }
        out.put("data".toByteArray()).putInt(data.size).put(data)
        return out.array()
    }

    @Test
    fun `decodes a 16-bit mono wav`() {
        val samples = shortArrayOf(0, 1, -1, 32_767, -32_768, 1234)
        val decoded = Wav.decode(wav(samples, sampleRate = 24_000))

        assertEquals(24_000, decoded!!.sampleRate)
        assertEquals(1, decoded.channels)
        assertArrayEquals(samples, decoded.pcm)
    }

    @Test
    fun `reads the sample rate from the header rather than assuming one`() {
        assertEquals(48_000, Wav.decode(wav(shortArrayOf(1, 2, 3), sampleRate = 48_000))!!.sampleRate)
    }

    @Test
    fun `skips an unknown chunk and its alignment pad before the data`() {
        // "LIST" + 3 bytes of body => one pad byte. A parser that assumed
        // data starts at offset 44 would read the wrong samples.
        val junk = "LIST" to byteArrayOf(1, 2, 3)
        val decoded = Wav.decode(wav(shortArrayOf(9, 8, 7), junkChunk = junk))

        assertArrayEquals(shortArrayOf(9, 8, 7), decoded!!.pcm)
    }

    @Test
    fun `interleaved stereo is averaged down to mono`() {
        val stereo = shortArrayOf(100, 200, -100, -200)
        val mono = Wav.toMono(stereo, channels = 2)

        assertArrayEquals(shortArrayOf(150, -150), mono)
    }

    @Test
    fun `mono passes through untouched`() {
        val mono = shortArrayOf(5, 6, 7)
        assertTrue(mono === Wav.toMono(mono, channels = 1))
    }

    @Test
    fun `anything that is not a wav comes back null instead of throwing`() {
        assertNull(Wav.decode(ByteArray(0)))
        assertNull(Wav.decode(ByteArray(10)))
        // An MP3-ish body: long enough, wrong magic.
        assertNull(Wav.decode(ByteArray(200) { 0xFF.toByte() }))
    }

    @Test
    fun `a truncated body yields the samples that are actually there`() {
        val full = wav(shortArrayOf(1, 2, 3, 4), sampleRate = 24_000)
        val truncated = full.copyOf(full.size - 4) // drop the last two samples

        assertArrayEquals(shortArrayOf(1, 2), Wav.decode(truncated)!!.pcm)
    }
}
