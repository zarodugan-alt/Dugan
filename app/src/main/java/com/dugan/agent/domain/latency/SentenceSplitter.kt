package com.dugan.agent.domain.latency

/**
 * Incremental sentence splitter for streaming TTS.
 *
 * The single biggest perceived-latency win in the pipeline: as soon as the LLM
 * produces "It's 72 degrees and sunny." we synthesise and start playing it while
 * the rest of the response is still being generated.
 */
class SentenceSplitter(
    /**
     * Do not emit fragments shorter than this on their own; they merge into the
     * following sentence instead. Six characters still lets a bare "Really?" or
     * "Sure." through, which are things worth saying.
     */
    private val minLength: Int = 6,
) {
    private val buffer = StringBuilder()
    private val emitted = mutableListOf<String>()

    /** Feeds new LLM tokens; returns any sentences that became complete. */
    fun accept(delta: String): List<String> {
        buffer.append(delta)
        val ready = mutableListOf<String>()
        var searchFrom = 0
        while (true) {
            val index = findBoundary(searchFrom)
            if (index < 0) break
            val sentence = buffer.substring(0, index + 1).trim()
            if (sentence.length < minLength) {
                // Too short to speak on its own. Advance past this boundary
                // instead of consuming it, so the fragment merges into the next
                // sentence. Re-inserting it at position 0 would make the next
                // findBoundary(0) return the same index and spin forever.
                searchFrom = index + 1
                continue
            }
            buffer.delete(0, index + 1)
            searchFrom = 0
            ready += sentence
            emitted += sentence
        }
        return ready
    }

    /** Everything left over when the stream ends. */
    fun flush(): String? {
        val tail = buffer.toString().trim()
        buffer.setLength(0)
        return if (tail.isEmpty()) null else tail.also { emitted += it }
    }

    fun sentencesSoFar(): List<String> = emitted.toList()

    private fun findBoundary(from: Int): Int {
        var i = from
        while (i < buffer.length) {
            val c = buffer[i]
            if (c == '?' || c == '!') return i
            if (c == '.' && isSentenceEnd(i)) return i
            // A comma in a very long clause is still worth starting speech on.
            if (c == ',' && i >= COMMA_FLUSH_LENGTH) return i
            i++
        }
        return -1
    }

    /**
     * Distinguishes a sentence-ending period from an abbreviation or a decimal
     * point ("3.5", "Dr. Smith", "v1.0").
     */
    private fun isSentenceEnd(dotIndex: Int): Boolean {
        val previous = buffer.getOrNull(dotIndex - 1) ?: return false
        val next = buffer.getOrNull(dotIndex + 1)
        // Decimal: digit on both sides.
        if (previous.isDigit() && next?.isDigit() == true) return false
        // Abbreviation: a following capital or lowercase letter with no space.
        if (next != null && next != ' ' && next != '\n' && next != '"' && next != '\'') return false
        // Known abbreviations.
        val wordStart = (dotIndex - 1 downTo 0).takeWhile { buffer[it].isLetter() }.lastOrNull() ?: dotIndex
        val word = buffer.substring(wordStart, dotIndex).lowercase()
        return word !in ABBREVIATIONS
    }

    private companion object {
        const val COMMA_FLUSH_LENGTH = 140
        val ABBREVIATIONS = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc", "e.g", "i.e",
            "inc", "ltd", "co", "no", "approx", "dept", "est", "vol", "fig",
        )
    }
}
