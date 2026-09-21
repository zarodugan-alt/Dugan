package com.dugan.agent.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Orpheus rejects any request whose `input` exceeds 200 characters, while the
 * pipeline hands over whole sentences. These pin the splitting rule: nothing
 * over the limit reaches the wire, and no word is broken in half unless a single
 * word is longer than the limit.
 */
class GroqTtsSplitTest {

    private val words = List(60) { "word$it" }
    private val sentence = words.joinToString(" ")

    @Test
    fun `a short sentence is sent as one piece, untouched`() {
        assertEquals(listOf("Hello there."), splitForTts("Hello there."))
    }

    @Test
    fun `exactly the limit stays a single piece`() {
        val exact = "a".repeat(GROQ_TTS_MAX_INPUT)
        assertEquals(listOf(exact), splitForTts(exact))
    }

    @Test
    fun `nothing sent to the api exceeds the limit`() {
        val pieces = splitForTts(sentence)

        assertTrue("expected the sentence to need splitting", pieces.size > 1)
        pieces.forEach {
            assertTrue("piece over the limit (${it.length}): $it", it.length <= GROQ_TTS_MAX_INPUT)
        }
    }

    @Test
    fun `splits on a space so no word is broken`() {
        splitForTts(sentence).forEach { piece ->
            assertTrue("piece starts mid-word: $piece", piece.startsWith("word"))
            assertTrue("piece ends mid-word: $piece", piece.last().isDigit())
        }
    }

    @Test
    fun `no words are lost or duplicated`() {
        val rejoined = splitForTts(sentence).joinToString(" ")
        assertEquals(sentence, rejoined)
    }

    @Test
    fun `a single over-long token is hard-cut rather than dropped`() {
        val blob = "x".repeat(GROQ_TTS_MAX_INPUT * 2 + 37)
        val pieces = splitForTts(blob)

        pieces.forEach { assertTrue(it.length <= GROQ_TTS_MAX_INPUT) }
        assertEquals(blob.length, pieces.sumOf { it.length })
    }

    @Test
    fun `blank input produces no requests`() {
        assertEquals(emptyList<String>(), splitForTts(""))
        assertEquals(emptyList<String>(), splitForTts("   \n "))
    }

    @Test
    fun `surrounding whitespace is trimmed before measuring`() {
        assertEquals(listOf("Hi."), splitForTts("  Hi.  "))
    }

    @Test
    fun `the limit is configurable so the rule stays testable at small sizes`() {
        val pieces = splitForTts("one two three four five six", maxChars = 9)

        pieces.forEach { assertTrue("piece over limit: $it", it.length <= 9) }
        assertEquals("one two three four five six", pieces.joinToString(" "))
    }
}
