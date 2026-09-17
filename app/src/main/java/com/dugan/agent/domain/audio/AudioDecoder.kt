package com.dugan.agent.domain.audio

import com.dugan.agent.util.AgentLog
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Decodes whatever a TTS provider returns (MPEG/AAC/Opus) into 16-bit PCM.
 *
 * We buffer one sentence before decoding rather than feeding MediaCodec a raw
 * elementary stream: MediaExtractor sniffs the container and sample rate for us,
 * which removes an entire class of csd-0 guessing bugs, and the pipeline already
 * dispatches TTS sentence-by-sentence so the added latency is one sentence of
 * synthesis that was happening in parallel anyway.
 */
object AudioDecoder {

    private const val TAG = "AudioDecoder"
    private const val TIMEOUT_US = 10_000L

    data class Decoded(val sampleRate: Int, val channels: Int, val pcm: ShortArray)

    fun decode(container: ByteArray): Decoded? {
        if (container.isEmpty()) return null
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(ByteArraySource(container))
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it)
                    .getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                1
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val out = ArrayList<Short>(container.size)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val read = extractor.readSampleData(buffer, 0)
                        if (read < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, read, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> if (inputDone) continue else continue
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    else -> {
                        if (outIndex >= 0) {
                            val buffer = codec.getOutputBuffer(outIndex)!!
                            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            val shorts = ShortArray(info.size / 2)
                            // ByteBuffer has get(ByteArray) but not get(ShortArray).
                            // asShortBuffer() inherits the LITTLE_ENDIAN order set above.
                            buffer.asShortBuffer().get(shorts)
                            for (s in shorts) out.add(s)
                            codec.releaseOutputBuffer(outIndex, false)
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val pcm = ShortArray(out.size) { out[it] }
            val stereo = if (channels >= 2) downmix(pcm) else pcm
            Decoded(sampleRate, 1, stereo)
        } catch (t: Throwable) {
            AgentLog.w(TAG, "decode failed: ${t.javaClass.simpleName}: ${t.message}")
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun downmix(stereo: ShortArray): ShortArray {
        val frames = stereo.size / 2
        val mono = ShortArray(frames)
        for (i in 0 until frames) {
            mono[i] = ((stereo[i * 2].toInt() + stereo[i * 2 + 1].toInt()) / 2)
                .coerceIn(-32768, 32767).toShort()
        }
        return mono
    }

    /** MediaDataSource over an in-memory buffer -- no temp files, no disk I/O. */
    private class ByteArraySource(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val available = (data.size - position).toInt().coerceAtMost(size)
            System.arraycopy(data, position.toInt(), buffer, offset, available)
            return available
        }

        override fun getSize(): Long = data.size.toLong()
        override fun close() = Unit
    }
}
