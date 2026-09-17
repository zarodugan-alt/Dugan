package com.dugan.agent.domain.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {

    private val parser = VoiceCommandParser()

    @Test
    fun `detects call instructions`() {
        assertEquals("john", parser.parse("Call John")?.contactQuery)
        assertEquals("mum", parser.parse("please ring mum")?.contactQuery)
        assertEquals("office", parser.parse("phone the office")?.contactQuery)
        // Digit groups keep their spacing; parseNumber() normalises them later.
        assertEquals("555 1234", parser.parse("dial 555 1234")?.contactQuery)
    }

    @Test
    fun `strips a leading article from the contact name`() {
        assertEquals("doctor", parser.parse("call the doctor")?.contactQuery)
    }

    @Test
    fun `does not treat call as a noun`() {
        assertNull(parser.parse("that was a close call yesterday"))
        assertNull(parser.parse("I need to make a phone call"))
    }

    @Test
    fun `ignores unrelated speech`() {
        assertNull(parser.parse("what is the weather today"))
        assertNull(parser.parse(""))
    }

    @Test
    fun `spoken numbers between seven and fifteen digits dial directly`() {
        assertEquals("5551234", parser.parseNumber("five five five one two three four 5551234"))
        assertNull(parseDigits("123"))
    }

    private fun parseDigits(text: String) = parser.parseNumber(text)

    @Test
    fun `affirmatives and negatives are recognised for confirmation`() {
        assertTrue(parser.isAffirmative("Yes"))
        assertTrue(parser.isAffirmative("yeah, go ahead"))
        assertTrue(parser.isNegative("no"))
        assertTrue(parser.isNegative("Never mind."))
        assertFalse(parser.isAffirmative("no"))
        assertFalse(parser.isNegative("yes"))
    }

    @Test
    fun `fuzzy matching tolerates a mispronounced name`() {
        val candidates = listOf("Jonathan", "Amanda", "Priya")
        assertEquals("Jonathan", parser.fuzzyMatch("Jonatan", candidates))
    }

    @Test
    fun `fuzzy matching refuses a distant match`() {
        val candidates = listOf("Jonathan", "Amanda", "Priya")
        assertNull(parser.fuzzyMatch("xyz", candidates))
    }

    @Test
    fun `fuzzy matching on an empty candidate list is null`() {
        assertNull(parser.fuzzyMatch("Jonatan", emptyList()))
    }
}
