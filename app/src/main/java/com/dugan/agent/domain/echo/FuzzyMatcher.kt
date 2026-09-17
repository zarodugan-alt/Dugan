package com.dugan.agent.domain.echo

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Sub-layer 5a: Levenshtein distance and token overlap.
 *
 * ~1ms and no model, so it runs on every candidate transcript. It catches the
 * common case -- STT returns the agent's sentence back almost verbatim.
 */
@Singleton
class FuzzyMatcher @Inject constructor() {

    /** Normalised 0f..1f, where 1f means identical. */
    fun similarity(a: String, b: String): Float {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() && y.isEmpty()) return 1f
        if (x.isEmpty() || y.isEmpty()) return 0f
        val distance = levenshtein(x, y)
        return 1f - distance.toFloat() / max(x.length, y.length)
    }

    /** Jaccard overlap of word sets; order-independent, so it survives re-ordering. */
    fun tokenOverlap(a: String, b: String): Float {
        val sa = normalize(a).split(' ').filter { it.isNotBlank() }.toSet()
        val sb = normalize(b).split(' ').filter { it.isNotBlank() }.toSet()
        if (sa.isEmpty() || sb.isEmpty()) return 0f
        val intersection = sa.intersect(sb).size
        val union = sa.union(sb).size
        return intersection.toFloat() / union
    }

    /**
     * Sliding check: the mic often captures only the tail of what the agent said,
     * so compare against every suffix window of the record rather than requiring
     * a full-string match.
     */
    fun bestSuffixSimilarity(candidate: String, record: TtsRecord): Float {
        val c = normalize(candidate)
        if (c.isEmpty()) return 0f
        val words = record.normalized.split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return 0f
        var best = 0f
        // Compare the candidate against trailing windows of similar word count.
        val candidateWords = c.split(' ').filter { it.isNotBlank() }.size
        for (window in candidateWords - 2..candidateWords + 2) {
            if (window <= 0 || window > words.size) continue
            val suffix = words.takeLast(window).joinToString(" ")
            best = max(best, max(similarity(c, suffix), tokenOverlap(c, suffix)))
        }
        return best
    }

    /**
     * Banded Levenshtein: full DP is O(n*m), which is fine for transcripts but
     * wasteful when we only care whether the distance is small.
     */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            val ca = a[i - 1]
            for (j in 1..b.length) {
                val cost = if (ca == b[j - 1]) 0 else 1
                current[j] = min(
                    min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + cost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    fun normalize(text: String): String =
        text.lowercase()
            // Drop apostrophes rather than blanking them: "it's" and "its" are the
            // same word, and STT picks between them arbitrarily. Blanking to a space
            // would split "it's" into two tokens and cost a permanent edit distance.
            .replace("'", "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
