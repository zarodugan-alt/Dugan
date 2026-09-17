package com.dugan.agent.data.api

import com.dugan.agent.domain.model.AgentModel
import com.dugan.agent.domain.model.ModelProvider
import com.dugan.agent.domain.model.ThinkingLevel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider-agnostic front door to the LLMs.
 *
 * The orchestrator holds an [AgentModel] and does not care whether it resolves to
 * Gemini or Groq; the badge in the UI is the same bit of information.
 */
@Singleton
class AgentLlm @Inject constructor(
    private val gemini: GeminiLlmClient,
    private val groq: GroqLlmClient,
) {
    private fun clientFor(model: AgentModel): LlmClient =
        when (model.provider) {
            ModelProvider.Gemini -> gemini
            ModelProvider.Groq -> groq
        }

    fun stream(
        model: AgentModel,
        messages: List<ChatMessage>,
        thinkingLevel: ThinkingLevel,
        systemPrompt: String,
        maxOutputTokens: Int = 512,
    ): Flow<String> = clientFor(model).stream(model, messages, thinkingLevel, systemPrompt, maxOutputTokens)

    suspend fun complete(
        model: AgentModel,
        messages: List<ChatMessage>,
        systemPrompt: String,
        maxOutputTokens: Int = 32,
    ): String = clientFor(model).complete(model, messages, systemPrompt, maxOutputTokens)

    suspend fun ping(model: AgentModel): Result<Unit> = clientFor(model).ping(model)
}
