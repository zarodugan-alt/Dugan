package com.dugan.agent.domain.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmTest {

    @Test
    fun `bytes and shorts round trip`() {
        val samples = shortArrayOf(0, 1, -1, 32767, -32768, 1234, -4321)
        assertArrayEquals(samples, Pcm.toShorts(Pcm.toBytes(samples)))
    }

    @Test
    fun `silence has zero rms`() {
        assertEquals(0f, Pcm.rms(ShortArray(1024)), 0.0001f)
    }

    @Test
    fun `full scale square wave has rms near one`() {
        val signal = ShortArray(1024) { if (it % 2 == 0) 32767 else -32768 }
        assertTrue(Pcm.rms(signal) > 0.99f)
    }

    @Test
    fun `peak tracks the largest absolute sample`() {
        assertEquals(1f, Pcm.peak(shortArrayOf(0, 32767, -100)), 0.001f)
        assertEquals(0f, Pcm.peak(ShortArray(8)), 0.001f)
    }

    @Test
    fun `resampling halves the sample count going 16k to 8k`() {
        val input = ShortArray(1600) { (it % 100).toShort() }
        assertEquals(800, Pcm.resample(input, 16_000, 8_000).size)
    }

    @Test
    fun `resampling to the same rate is a no-op`() {
        val input = ShortArray(320) { it.toShort() }
        assertArrayEquals(input, Pcm.resample(input, 16_000, 16_000))
    }

    @Test
    fun `wav header is 44 bytes and declares the right rate`() {
        val wav = Pcm.toWav(ShortArray(1600), 16_000)
        assertEquals(44 + 3200, wav.size)
        assertEquals('R'.code.toByte(), wav[0])
        assertEquals('I'.code.toByte(), wav[1])
        assertEquals('F'.code.toByte(), wav[2])
        assertEquals('F'.code.toByte(), wav[3])
        // Sample rate is a little-endian int32 at offset 24.
        val rate = (wav[24].toInt() and 0xFF) or
            ((wav[25].toInt() and 0xFF) shl 8) or
            ((wav[26].toInt() and 0xFF) shl 16) or
            ((wav[27].toInt() and 0xFF) shl 24)
        assertEquals(16_000, rate)
    }

    @Test
    fun `concat preserves order and length`() {
        val merged = Pcm.concat(shortArrayOf(1, 2), shortArrayOf(3), shortArrayOf(4, 5, 6))
        assertArrayEquals(shortArrayOf(1, 2, 3, 4, 5, 6), merged)
    }
}
