package com.dugan.agent.domain.echo

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Sub-layer 5b: semantic similarity.
 *
 * Needed because STT rarely returns the agent's words verbatim -- "It's 72
 * degrees and sunny" comes back as "it is seventy two degrees and sunny". A
 * lexical matcher misses that; cosine similarity over sentence embeddings does
 * not.
 */
interface EmbeddingMatcher {
    val name: String
    fun embed(text: String): FloatArray?
}

/**
 * Hashing-trigram embedder. Not a real sentence model, but it is deterministic,
 * dependency-free, and genuinely order-insensitive, which is exactly the failure
 * mode sub-layer 5a misses. Replace with all-MiniLM-L6-v2 (ONNX, ~23MB) by
 * implementing [EmbeddingMatcher] over an OrtSession -- the engine will pick it
 * up with no other change.
 */
@Singleton
class HashingEmbeddingMatcher @Inject constructor() : EmbeddingMatcher {

    override val name: String = "hashing-trigram"

    override fun embed(text: String): FloatArray? {
        val cleaned = text.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim()
        if (cleaned.length < 3) return null
        val vector = FloatArray(DIMENSIONS)
        // Character trigrams so "72" and "seventy two" still share mass with the
        // surrounding sentence.
        for (i in 0..cleaned.length - 3) {
            val trigram = cleaned.substring(i, i + 3)
            val bucket = (trigram.hashCode().rem(DIMENSIONS) + DIMENSIONS) % DIMENSIONS
            vector[bucket] += 1f
        }
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm == 0f) return null
        for (i in vector.indices) vector[i] /= norm
        return vector
    }

    companion object {
        const val DIMENSIONS = 512

        fun cosine(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size || a.isEmpty()) return 0f
            var dot = 0f
            var na = 0f
            var nb = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                na += a[i] * a[i]
                nb += b[i] * b[i]
            }
            if (na == 0f || nb == 0f) return 0f
            return (dot / (sqrt(na) * sqrt(nb))).coerceIn(-1f, 1f)
        }
    }
}
