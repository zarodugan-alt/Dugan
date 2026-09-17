package com.dugan.agent.data.api

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.dugan.agent.domain.audio.AudioDecoder
import com.dugan.agent.domain.model.AgentModel
import com.dugan.agent.util.AgentLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Last-resort TTS: the platform's own speech engine.
 *
 * No key, no quota, no network. Voice quality is a clear step down from Unreal
 * Speech and the first call pays engine initialisation, but it means a quota
 * exhaustion mid-call degrades to a robotic voice rather than to silence -- and
 * on a phone call, silence is the failure the other person cannot recover from.
 *
 * Reached only after Unreal Speech and Edge TTS have both failed.
 */
@Singleton
class DeviceTtsClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : TtsClient {

    override val provider: String = "device_tts"
    override val outputSampleRate: Int = 22_050

    @Volatile
    private var engine: TextToSpeech? = null

    private suspend fun engine(): TextToSpeech = engine ?: suspendCancellableCoroutine { cont ->
        var ready: TextToSpeech? = null
        ready = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS && ready != null) {
                ready?.language = Locale.getDefault()
                AgentLog.i(TAG, "platform TTS engine ready")
                if (cont.isActive) cont.resume(ready!!)
            } else {
                AgentLog.w(TAG, "platform TTS init failed (status=$status)")
                if (cont.isActive) cont.resume(ready!!)
            }
        }
        engine = ready
    }

    override fun synthesize(
        model: AgentModel,
        text: String,
        voiceId: String,
        speed: Float,
    ): Flow<ShortArray> = flow {
        if (text.isBlank()) return@flow
        val tts = engine()
        val out = File(context.cacheDir, "dugan_tts_${UUID.randomUUID()}.wav")

        val ok = suspendCancellableCoroutine { cont ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (cont.isActive) cont.resume(true)
                }
                @Deprecated("Required override")
                override fun onError(utteranceId: String?) {
                    if (cont.isActive) cont.resume(false)
                }
            })
            val params = android.os.Bundle()
            // Unreal's -1f..1f maps onto the platform's 0.5x..2.0x rate.
            tts.setSpeechRate(1f + speed.coerceIn(-1f, 1f) * 0.5f)
            val result = tts.synthesizeToFile(text, params, out, "dugan-${System.currentTimeMillis()}")
            if (result != TextToSpeech.SUCCESS && cont.isActive) cont.resume(false)
        }

        try {
            if (!ok || !out.exists()) {
                AgentLog.w(TAG, "platform TTS produced no audio")
                return@flow
            }
            val decoded = AudioDecoder.decode(out.readBytes()) ?: return@flow
            val frame = (decoded.sampleRate / 50).coerceAtLeast(1)
            var offset = 0
            while (offset < decoded.pcm.size) {
                val len = minOf(frame, decoded.pcm.size - offset)
                val chunk = ShortArray(len)
                System.arraycopy(decoded.pcm, offset, chunk, 0, len)
                emit(chunk)
                offset += len
            }
        } finally {
            runCatching { out.delete() }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun ping(model: AgentModel): Result<Unit> = runCatching {
        engine()
        Unit
    }

    fun shutdown() {
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
    }

    private companion object {
        const val TAG = "DeviceTts"
    }
}
