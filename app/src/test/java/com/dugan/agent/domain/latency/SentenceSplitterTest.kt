package com.dugan.agent.domain.latency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {

    @Test
    fun `emits on the first full stop so speech can start early`() {
        val splitter = SentenceSplitter()
        // One delta completed two sentences, so both come back; the caller starts
        // speaking the first while the second is still being synthesised.
        val ready = splitter.accept("It's 72 degrees. More text is coming.")
        assertEquals(listOf("It's 72 degrees.", "More text is coming."), ready)
    }

    @Test
    fun `emits nothing until the first boundary arrives`() {
        val splitter = SentenceSplitter()
        assertEquals(emptyList<String>(), splitter.accept("It's 72 degrees"))
        assertEquals(listOf("It's 72 degrees."), splitter.accept("."))
    }

    @Test
    fun `does not emit mid-sentence`() {
        val splitter = SentenceSplitter()
        assertTrue(splitter.accept("It is currently ").isEmpty())
        assertTrue(splitter.accept("seventy two degrees").isEmpty())
    }

    @Test
    fun `does not split on a decimal point`() {
        val splitter = SentenceSplitter()
        val ready = splitter.accept("Pi is 3.14159 and that is final.")
        assertEquals(listOf("Pi is 3.14159 and that is final."), ready)
    }

    @Test
    fun `does not split on a known abbreviation`() {
        val splitter = SentenceSplitter()
        val ready = splitter.accept("Dr. Smith will see you now.")
        assertEquals(listOf("Dr. Smith will see you now."), ready)
    }

    @Test
    fun `flush returns the trailing fragment`() {
        val splitter = SentenceSplitter()
        splitter.accept("No terminator here")
        assertEquals("No terminator here", splitter.flush())
        assertEquals(null, splitter.flush())
    }

    @Test
    fun `question and exclamation marks terminate`() {
        val splitter = SentenceSplitter()
        assertEquals(listOf("Really?", "Absolutely!"), splitter.accept("Really? Absolutely!"))
    }

    @Test
    fun `tokens arriving one character at a time still produce whole sentences`() {
        val splitter = SentenceSplitter()
        val out = mutableListOf<String>()
        "The answer is forty two. Thank you.".forEach { out += splitter.accept(it.toString()) }
        out += splitter.flush()?.let { listOf(it) }.orEmpty()
        assertEquals(listOf("The answer is forty two.", "Thank you."), out)
    }

    @Test
    fun `very short fragments are merged rather than spoken alone`() {
        val splitter = SentenceSplitter(minLength = 20)
        val ready = splitter.accept("Yes. That is the correct answer indeed.")
        // "Yes." is below minLength, so it is held and merged into the next sentence.
        assertEquals(listOf("Yes. That is the correct answer indeed."), ready)
    }
}
