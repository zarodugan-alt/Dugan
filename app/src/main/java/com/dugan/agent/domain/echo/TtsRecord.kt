package com.dugan.agent.domain.echo

/**
 * One thing the agent has said, kept for text-level echo defence.
 *
 * @property embedding optional sentence vector for the semantic sub-layer; null
 *   when the embedding model is not bundled.
 */
data class TtsRecord(
    val text: String,
    val timestampMs: Long,
    val embedding: FloatArray? = null,
) {
    val normalized: String by lazy { text.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim() }

    val tokens: Set<String> by lazy { normalized.split(' ').filter { it.isNotBlank() }.toSet() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TtsRecord) return false
        return text == other.text && timestampMs == other.timestampMs &&
            ((embedding == null && other.embedding == null) ||
                (embedding != null && other.embedding != null && embedding.contentEquals(other.embedding)))
    }

    override fun hashCode(): Int = text.hashCode() * 31 + timestampMs.hashCode()
}

/** Outcome of the text-level check. */
enum class EchoVerdict {
    /** Clearly the agent's own words coming back through the mic. Drop it. */
    Echo,

    /** Clearly new human input. Process it. */
    New,

    /** Sub-layers 5a/5b disagreed; escalate to the LLM verifier. */
    Uncertain,
}

data class EchoDecision(
    val verdict: EchoVerdict,
    /** Which layer produced the verdict, for logging and the latency readout. */
    val layer: String,
    val confidence: Float,
)
