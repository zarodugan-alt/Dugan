package com.dugan.agent.data.api

import com.dugan.agent.domain.model.AgentModel

/**
 * Speech-to-text. One method, streaming-friendly: implementations emit partial
 * transcripts as chunks land and a final transcript on [SttResult.isFinal].
 */
interface SttClient {

    val provider: String

    suspend fun transcribe(
        model: AgentModel,
        pcm16: ShortArray,
        sampleRate: Int,
        language: String? = "en",
    ): SttResult

    /** Probe used by "Test Connection": a near-silent clip must come back 200. */
    suspend fun ping(model: AgentModel): Result<Unit>
}

data class SttResult(
    val text: String,
    val isFinal: Boolean = true,
    val language: String? = null,
    val durationMs: Long = 0,
)
