package com.dugan.agent.domain.latency

import com.dugan.agent.util.AgentLog
import com.dugan.agent.data.api.AgentLlm
import com.dugan.agent.data.api.ChatMessage
import com.dugan.agent.domain.model.AgentModel
import com.dugan.agent.domain.model.ModelCatalog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Model routing.
 *
 * Most turns ("what time is it?", "yes", "thanks") do not need a thinking model.
 * Sending them to Flash-Lite instead of Flash cuts TTFT substantially; the goal
 * is to route ~70% of turns to the small model.
 */
@Singleton
class ModelRouter @Inject constructor(
    private val llm: AgentLlm,
) {

    enum class Complexity { Simple, Complex }

    /**
     * Heuristic first, model second.
     *
     * The heuristic is free and catches the obvious cases; the classifier only
     * runs when the heuristic is unsure, so we do not pay a model call to decide
     * whether to make a model call.
     */
    suspend fun route(
        transcript: String,
        preferred: AgentModel,
        enabled: Boolean = true,
    ): AgentModel {
        if (!enabled) return preferred
        val small = ModelCatalog.DefaultRouter

        when (heuristic(transcript)) {
            Complexity.Simple -> return small
            Complexity.Complex -> return preferred
            null -> Unit // undecided -> ask
        }

        return runCatching {
            val verdict = llm.complete(
                model = small,
                messages = listOf(
                    ChatMessage(
                        "user",
                        "Classify the request as SIMPLE (facts, greetings, short answers, " +
                            "single-step lookups) or COMPLEX (multi-step reasoning, planning, " +
                            "comparisons, arithmetic, code).\n\nRequest: \"$transcript\"\n\n" +
                            "Answer with one word.",
                    ),
                ),
                systemPrompt = "Reply with exactly SIMPLE or COMPLEX.",
                maxOutputTokens = 2,
            ).trim().uppercase()
            if (verdict.startsWith("SIMPLE")) small else preferred
        }.onFailure {
            AgentLog.w(TAG, "router fell back to preferred model: ${it.javaClass.simpleName}")
        }.getOrDefault(preferred)
    }

    /** @return null when the heuristic cannot decide. */
    fun heuristic(transcript: String): Complexity? {
        val text = transcript.trim().lowercase()
        if (text.isEmpty()) return null
        if (text in TRIVIAL) return Complexity.Simple
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= 3 && COMPLEX_MARKERS.none { text.contains(it) }) return Complexity.Simple
        if (COMPLEX_MARKERS.any { text.contains(it) }) return Complexity.Complex
        if (words.size >= 18) return Complexity.Complex
        return null
    }

    private companion object {
        const val TAG = "ModelRouter"
        val TRIVIAL = setOf(
            "yes", "no", "yeah", "nope", "ok", "okay", "thanks", "thank you", "sure",
            "hello", "hi", "hey", "goodbye", "bye", "stop", "cancel", "repeat",
        )
        val COMPLEX_MARKERS = listOf(
            "compare", "explain why", "step by step", "plan", "calculate", "estimate",
            "pros and cons", "should i", "write", "summarise", "summarize", "debug",
            "how does", "why does", "difference between",
        )
    }
}
