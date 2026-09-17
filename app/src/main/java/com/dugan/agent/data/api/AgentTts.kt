package com.dugan.agent.data.api

import com.dugan.agent.domain.model.AgentModel
import com.dugan.agent.util.AgentLog
import com.dugan.agent.util.withRetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TTS with two levels of degradation.
 *
 * 1. **Unreal Speech** — the configured voice.
 * 2. **Edge TTS** — keyless, engaged when Unreal reports 429/402.
 * 3. **Platform TTS** — no network at all, engaged when both above fail.
 *
 * Each switch is remembered for the life of the process so a failing provider
 * does not cost a round-trip on every sentence of the call.
 */
@Singleton
class AgentTts @Inject constructor(
    private val unreal: UnrealSpeechTtsClient,
    private val edge: EdgeTtsClient,
    private val device: DeviceTtsClient,
) {
    @Volatile
    private var unrealExhausted = false

    @Volatile
    private var networkTtsDown = false

    /** Reset when the user edits a key or the hour rolls over. */
    fun resetFallback() {
        unrealExhausted = false
        networkTtsDown = false
    }

    fun synthesize(model: AgentModel, text: String, voiceId: String, speed: Float): Flow<ShortArray> = flow {
        if (text.isBlank()) return@flow

        // Tier 1: Unreal Speech.
        if (!unrealExhausted && !networkTtsDown && model.id != "edge-tts") {
            val audio = runCatching {
                withRetry("unreal-tts", attempts = 2) {
                    unreal.synthesize(model, text, voiceId, speed).toList()
                }
            }
            if (audio.isSuccess) {
                audio.getOrThrow().forEach { emit(it) }
                return@flow
            }
            val failure = audio.exceptionOrNull()
            if (failure is ApiException && failure.isQuota) {
                AgentLog.w(TAG, "Unreal Speech quota exhausted; moving to Edge TTS")
                unrealExhausted = true
            } else {
                AgentLog.w(TAG, "Unreal Speech failed (${failure?.javaClass?.simpleName}); moving to Edge TTS")
            }
        }

        // Tier 2: keyless Edge TTS.
        if (!networkTtsDown) {
            val audio = runCatching { edge.synthesize(model, text, voiceId, speed).toList() }
            if (audio.isSuccess) {
                audio.getOrThrow().forEach { emit(it) }
                return@flow
            }
            AgentLog.w(TAG, "Edge TTS failed (${audio.exceptionOrNull()?.javaClass?.simpleName}); using platform TTS")
            networkTtsDown = true
        }

        // Tier 3: on-device. Always available, so this is the end of the chain.
        device.synthesize(model, text, voiceId, speed).collect { emit(it) }
    }.catch { t ->
        AgentLog.e(TAG, "TTS chain exhausted", t)
        throw t
    }

    suspend fun ping(model: AgentModel): Result<Unit> = when (model.id) {
        "edge-tts" -> edge.ping(model)
        else -> unreal.ping(model)
    }

    private companion object {
        const val TAG = "AgentTts"
    }
}
