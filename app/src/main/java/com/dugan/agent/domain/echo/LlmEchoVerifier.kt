package com.dugan.agent.domain.echo

import com.dugan.agent.util.AgentLog
import com.dugan.agent.data.api.AgentLlm
import com.dugan.agent.data.api.ChatMessage
import com.dugan.agent.domain.model.ModelCatalog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sub-layer 5c: ask the cheapest model whether a transcript is an echo.
 *
 * Costs ~250ms and ~50 tokens, so it only ever runs when 5a and 5b disagree.
 * In practice that is a small fraction of turns.
 */
@Singleton
class LlmEchoVerifier @Inject constructor(
    private val llm: AgentLlm,
) {
    /** @return true when the model believes the transcript is the agent's own voice. */
    suspend fun isEcho(candidate: String, recentAgentSpeech: List<String>): Boolean {
        if (candidate.isBlank() || recentAgentSpeech.isEmpty()) return false
        return runCatching {
            val reply = llm.complete(
                model = ModelCatalog.DefaultRouter,
                messages = listOf(
                    ChatMessage(
                        "user",
                        "RECENT AGENT SPEECH:\n" +
                            recentAgentSpeech.joinToString("\n") { "- $it" } +
                            "\n\nNEW AUDIO TRANSCRIPT:\n\"$candidate\"\n\n" +
                            "Is the new transcript the same statement as any recent agent " +
                            "speech (possibly re-worded by a speech recogniser)? " +
                            "Answer with exactly one word: ECHO or NEW.",
                    ),
                ),
                systemPrompt = "You are a strict classifier. Reply with one word only.",
                maxOutputTokens = 2,
            )
            reply.trim().uppercase().startsWith("ECHO")
        }.onFailure {
            AgentLog.w(TAG, "echo verifier failed: ${it.javaClass.simpleName}")
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "EchoVerifier"
    }
}
