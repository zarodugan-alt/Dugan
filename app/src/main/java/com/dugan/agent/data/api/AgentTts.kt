package com.dugan.agent.data.api

import android.util.Log
import com.dugan.agent.domain.model.AgentModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TTS with automatic degradation.
 *
 * Unreal Speech is primary; when it reports quota exhaustion we retry the same
 * sentence on Edge TTS so the conversation keeps moving. The switch is
 * remembered for the rest of the process so we do not pay a failing round-trip
 * on every sentence.
 */
@Singleton
class AgentTts @Inject constructor(
    private val unreal: UnrealSpeechTtsClient,
    private val edge: EdgeTtsClient,
) {
    @Volatile
    private var unrealExhausted = false

    /** Reset when the user edits the key or the hour rolls over. */
    fun resetFallback() {
        unrealExhausted = false
    }

    fun synthesize(model: AgentModel, text: String, voiceId: String, speed: Float): Flow<ShortArray> = flow {
        if (!unrealExhausted && model.id != "edge-tts") {
            var failedWithQuota: ApiException? = null
            try {
                unreal.synthesize(model, text, voiceId, speed).collect { emit(it) }
                return@flow
            } catch (e: ApiException) {
                if (!e.isQuota) throw e
                failedWithQuota = e
            }
            failedWithQuota?.let {
                Log.w(TAG, "Unreal Speech quota exhausted; falling back to Edge TTS")
                unrealExhausted = true
            }
        }
        edge.synthesize(model, text, voiceId, speed).collect { emit(it) }
    }.catch { t ->
        Log.w(TAG, "TTS failed: ${t.javaClass.simpleName}")
        throw t
    }

    suspend fun ping(model: AgentModel): Result<Unit> =
        if (model.id == "edge-tts") edge.ping(model) else unreal.ping(model)

    private companion object {
        const val TAG = "AgentTts"
    }
}
