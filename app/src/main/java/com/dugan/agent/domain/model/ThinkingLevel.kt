package com.dugan.agent.domain.model

/**
 * Maps onto Gemini's `thinkingConfig.thinkingLevel`.
 *
 * The API takes an integer budget on current models; the enum keeps the wire
 * format in one place so a Gemini schema change is a one-line edit.
 */
enum class ThinkingLevel(
    val label: String,
    /** thinkingBudget sent to Gemini. -1 = provider default, 0 = thinking off. */
    val thinkingBudget: Int,
) {
    Quick("Quick", 128),
    Balanced("Balanced", 1024),
    Deep("Deep", 4096),
    ;

    companion object {
        val Default = Balanced
    }
}
