package com.dugan.agent.data.api

import com.dugan.agent.domain.model.AgentModel
import com.dugan.agent.domain.model.ThinkingLevel
import kotlinx.coroutines.flow.Flow

data class ChatMessage(val role: String, val content: String)

/**
 * Streaming text generation.
 *
 * [stream] emits token deltas in arrival order and completes on finish. Callers
 * cancel the returned Flow's job to abort mid-response (the Stop button).
 */
interface LlmClient {

    val provider: String

    fun stream(
        model: AgentModel,
        messages: List<ChatMessage>,
        thinkingLevel: ThinkingLevel,
        /** Stable across turns so the provider can reuse its KV prefix cache. */
        systemPrompt: String,
        maxOutputTokens: Int = 512,
    ): Flow<String>

    /** Single non-streamed completion; used by the echo verifier and router. */
    suspend fun complete(
        model: AgentModel,
        messages: List<ChatMessage>,
        systemPrompt: String,
        maxOutputTokens: Int = 32,
    ): String

    suspend fun ping(model: AgentModel): Result<Unit>
}
