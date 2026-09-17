package com.dugan.agent.domain.model

enum class Speaker { User, Agent, System }

/**
 * Wire role for the LLM.
 *
 * A separate extension rather than a property on the enum so the wire vocabulary
 * stays next to the API clients that own it, and so [Speaker.System] -- which is a
 * UI concept for notices -- maps onto the `system` role providers expect.
 */
fun Speaker.chatRole(): String = when (this) {
    Speaker.User -> "user"
    Speaker.Agent -> "assistant"
    Speaker.System -> "system"
}

/**
 * One bubble in the transcript.
 *
 * @property isFinal false while the text is still streaming in; the UI renders
 *   those with a trailing caret and does not persist them to Room.
 * @property latencyMs time-to-first-audio for agent turns, from the turn clock.
 */
data class TranscriptEntry(
    val id: Long,
    val speaker: Speaker,
    val text: String,
    val timestampMs: Long,
    val isFinal: Boolean = true,
    val latencyMs: Long? = null,
    /** Set when text-level echo defence discarded this as the agent hearing itself. */
    val suppressedAsEcho: Boolean = false,
)

/**
 * Wire role for the provider APIs.
 *
 * Gemini and Groq disagree on the assistant role name ("model" vs "assistant");
 * both accept "assistant", and Gemini's REST surface tolerates it too, so one
 * mapping keeps [com.dugan.agent.data.api.AgentLlm] provider-agnostic.
 */
fun Speaker.role(): String = when (this) {
    Speaker.User -> "user"
    Speaker.Agent -> "assistant"
    Speaker.System -> "system"
}
