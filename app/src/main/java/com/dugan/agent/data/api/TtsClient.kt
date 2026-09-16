package com.dugan.agent.data.api

import com.dugan.agent.domain.model.AgentModel
import kotlinx.coroutines.flow.Flow

/**
 * Text-to-speech, emitting 16-bit PCM ready for AudioTrack.
 *
 * Providers return compressed audio; implementations decode before emitting so
 * the orchestrator never has to care what codec came back.
 */
interface TtsClient {

    val provider: String

    /** Decoded sample rate of the PCM this client emits. */
    val outputSampleRate: Int

    fun synthesize(
        model: AgentModel,
        text: String,
        voiceId: String,
        speed: Float,
    ): Flow<ShortArray>

    suspend fun ping(model: AgentModel): Result<Unit>
}
