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

    /**
     * Probe used by "Test Connection" and by auto-verify on paste: a
     * near-silent clip must come back 200.
     *
     * @param keyOverride credential to test instead of the stored one, so a
     *   freshly pasted key can be verified before anything is written to the
     *   vault. Null means "use the stored key".
     */
    suspend fun ping(model: AgentModel, keyOverride: String? = null): Result<Unit>
}

data class SttResult(
    val text: String,
    val isFinal: Boolean = true,
    val language: String? = null,
    val durationMs: Long = 0,
)
