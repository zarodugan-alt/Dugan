package com.dugan.agent.domain.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkedSttTest {

    private val stt = ChunkedStt()

    @Test
    fun `stitch drops words the overlap transcribed twice`() {
        val previous = listOf("it", "is", "seventy", "two", "degrees")
        val next = listOf("degrees", "and", "sunny")
        assertEquals("and sunny", stt.stitch(previous, next))
    }

    @Test
    fun `stitch keeps everything when there is no overlap`() {
        val previous = listOf("hello", "there")
        val next = listOf("how", "are", "you")
        assertEquals("how are you", stt.stitch(previous, next))
    }

    @Test
    fun `stitch is case and punctuation insensitive`() {
        val previous = listOf("It", "is", "fine.")
        val next = listOf("fine", "thanks")
        assertEquals("thanks", stt.stitch(previous, next))
    }

    @Test
    fun `stitch only looks back as far as the configured window`() {
        val previous = listOf("a", "b", "c", "d", "e", "f", "g")
        // "d e f g" repeats, but only the last 5 words are compared, so a 4-word
        // overlap is still inside the window and gets removed.
        val next = listOf("d", "e", "f", "g", "h")
        assertEquals("h", stt.stitch(previous, next, maxWords = 5))
    }

    @Test
    fun `stitch handles empty sides`() {
        assertEquals("one two", stt.stitch(emptyList(), listOf("one", "two")))
        assertEquals("one two", stt.stitch(listOf("x"), listOf("one", "two")))
    }

    @Test
    fun `utterance buffer reports duration and emptiness`() {
        val buffer = ChunkedStt.UtteranceBuffer()
        assertTrue(buffer.isEmpty)
        assertEquals(0L, buffer.durationMs)

        buffer.add(ShortArray(1600), 16_000)
        assertTrue(!buffer.isEmpty)
        assertEquals(100L, buffer.durationMs)
        assertEquals(1600, buffer.snapshot().size)

        buffer.clear()
        assertTrue(buffer.isEmpty)
    }

    @Test
    fun `utterance buffer keeps only the trailing window`() {
        val buffer = ChunkedStt.UtteranceBuffer(maxSeconds = 1)
        repeat(5) { buffer.add(ShortArray(16_000), 16_000) }
        // Capped at one second of 16kHz audio.
        assertTrue(buffer.snapshot().size <= 16_000)
    }
}
