package com.dugan.agent.domain.command

import javax.inject.Inject
import javax.inject.Singleton

/** A dial instruction extracted from a transcript. */
data class DialIntent(
    val contactQuery: String,
    /** Exact words the user said, so the confirmation prompt can quote them. */
    val spoken: String,
)

/**
 * Voice-command dialling: "call John", "ring mum", "phone 555 1234".
 *
 * Runs before the LLM, because a dial instruction is a write with side effects
 * and must never be routed through speculation or a small model.
 */
@Singleton
class VoiceCommandParser @Inject constructor() {

    fun parse(transcript: String): DialIntent? {
        val text = transcript.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.isEmpty()) return null

        for (verb in VERBS) {
            val index = text.indexOf(verb)
            if (index < 0) continue
            val rest = text.substring(index + verb.length).trim()
                .removePrefix("the ").removePrefix("a ")
                .trim()
            if (rest.isEmpty()) continue
            // Reject sentences where "call" is a noun ("that was a close call
            // yesterday"). The word immediately before the verb has to be one that
            // can legitimately introduce a command.
            val prefix = text.substring(0, index).trim()
            if (prefix.isNotEmpty() && prefix.split(' ').last() !in LEADING_OK) continue
            return DialIntent(contactQuery = rest, spoken = transcript.trim())
        }
        return null
    }

    /** Digits spoken as a number are dialled directly, no contact lookup. */
    fun parseNumber(transcript: String): String? {
        val digits = transcript.filter { it.isDigit() }
        return if (digits.length in 7..15) digits else null
    }

    /** "yes" / "yeah" / "go ahead" -- used to confirm a dial. */
    fun isAffirmative(transcript: String): Boolean {
        val t = transcript.trim().lowercase().replace(Regex("[^a-z ]"), "").trim()
        return t in AFFIRMATIVE
    }

    fun isNegative(transcript: String): Boolean {
        val t = transcript.trim().lowercase().replace(Regex("[^a-z ]"), "").trim()
        return t in NEGATIVE
    }

    /**
     * Levenshtein distance for mispronounced names. Small standalone copy so this
     * class stays dependency-free and unit-testable.
     */
    fun fuzzyMatch(spoken: String, candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        val target = spoken.lowercase()
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        for (candidate in candidates) {
            val d = distance(target, candidate.lowercase())
            if (d < bestDistance) {
                bestDistance = d
                best = candidate
            }
        }
        // Only accept a fuzzy hit when it is close relative to the name's length.
        return if (best != null && bestDistance <= (target.length / 3).coerceAtLeast(1)) best else null
    }

    private fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            for (j in 0..b.length) previous[j] = current[j]
        }
        return previous[b.length]
    }

    private companion object {
        val VERBS = listOf("call ", "ring ", "phone ", "dial ")

        /**
         * Words that can legitimately sit immediately before a dial verb.
         *
         * Deliberately does NOT contain the empty string: `endsWith("")` is true
         * for every string, which silently disabled this guard entirely.
         */
        val LEADING_OK = setOf("please", "ok", "okay", "now", "then", "hey", "and", "you", "dugan")
        val AFFIRMATIVE = setOf("yes", "yeah", "yep", "yup", "sure", "go ahead", "correct", "right", "do it", "please do")
        val NEGATIVE = setOf("no", "nope", "cancel", "stop", "never mind", "forget it")
    }
}
