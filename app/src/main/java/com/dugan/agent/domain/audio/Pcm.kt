package com.dugan.agent.domain.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** PCM helpers shared by capture, playback, AEC and the API clients. */
object Pcm {

    /** 16-bit little-endian PCM samples -> raw bytes. */
    fun toBytes(samples: ShortArray, offset: Int = 0, length: Int = samples.size): ByteArray {
        val out = ByteArray(length * 2)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).let { bb ->
            for (i in offset until offset + length) bb.putShort(samples[i])
        }
        return out
    }

    fun toShorts(bytes: ByteArray): ShortArray {
        val out = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).let { bb ->
            for (i in out.indices) out[i] = bb.short
        }
        return out
    }

    /** Root-mean-square in 0f..1f. Drives the orb and the energy VAD. */
    fun rms(samples: ShortArray, offset: Int = 0, length: Int = samples.size): Float {
        if (length <= 0) return 0f
        var acc = 0.0
        for (i in offset until offset + length) {
            val v = samples[i].toDouble() / Short.MAX_VALUE
            acc += v * v
        }
        return sqrt(acc / length).toFloat().coerceIn(0f, 1f)
    }

    /** Peak absolute amplitude in 0f..1f. Cheaper than RMS, used for gating. */
    fun peak(samples: ShortArray): Float {
        var peak = 0
        for (s in samples) {
            val a = if (s < 0) -s.toInt() else s.toInt()
            if (a > peak) peak = a
        }
        return (peak.toFloat() / Short.MAX_VALUE).coerceIn(0f, 1f)
    }

    fun concat(vararg chunks: ShortArray): ShortArray {
        val total = chunks.sumOf { it.size }
        val out = ShortArray(total)
        var pos = 0
        for (c in chunks) {
            System.arraycopy(c, 0, out, pos, c.size)
            pos += c.size
        }
        return out
    }

    /** Clip-safe gain in dB. */
    fun applyGain(samples: ShortArray, gainDb: Float) {
        if (gainDb == 0f) return
        val g = Math.pow(10.0, gainDb / 20.0).toFloat()
        for (i in samples.indices) {
            samples[i] = (samples[i] * g).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /**
     * Linear resample. Not audiophile quality, but the AEC reference only needs
     * to be time-aligned with the mic signal, not pristine.
     */
    fun resample(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate || input.isEmpty()) return input
        val ratio = fromRate.toDouble() / toRate
        val outLen = (input.size / ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val i0 = srcPos.toInt().coerceIn(0, input.size - 1)
            val i1 = (i0 + 1).coerceAtMost(input.size - 1)
            val frac = (srcPos - i0).toFloat()
            out[i] = (input[i0] * (1f - frac) + input[i1] * frac).toInt()
                .coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** RIFF/WAVE header + payload, as Groq's transcription endpoint expects. */
    fun toWav(pcm: ShortArray, sampleRate: Int, channels: Int = 1): ByteArray {
        val data = toBytes(pcm)
        val byteRate = sampleRate * channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + data.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * 2).toShort())
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(data.size)
        return header.array() + data
    }
}
