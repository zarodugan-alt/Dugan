package com.dugan.agent.domain.latency

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeculativeExecutorTest {

    private val executor = SpeculativeExecutor()

    @Test
    fun `speculates on a short idempotent partial with enough words`() {
        assertTrue(
            executor.shouldSpeculate(
                speechDurationMs = 1_200,
                partial = "what is the weather today",
                retries = 0,
                isIdempotent = true,
            ),
        )
    }

    @Test
    fun `never speculates on a non-idempotent turn`() {
        assertFalse(
            executor.shouldSpeculate(
                speechDurationMs = 1_000,
                partial = "send a message to mum now",
                retries = 0,
                isIdempotent = false,
            ),
        )
    }

    @Test
    fun `refuses when the utterance is already long`() {
        assertFalse(
            executor.shouldSpeculate(
                speechDurationMs = 9_000,
                partial = "a very long rambling request that is not finished yet at all",
                retries = 0,
                isIdempotent = true,
            ),
        )
    }

    @Test
    fun `caps wasted compute at two retries`() {
        assertFalse(
            executor.shouldSpeculate(
                speechDurationMs = 1_000,
                partial = "what is the weather today",
                retries = 2,
                isIdempotent = true,
            ),
        )
    }

    @Test
    fun `needs at least three words to predict anything`() {
        assertFalse(
            executor.shouldSpeculate(
                speechDurationMs = 500,
                partial = "what is",
                retries = 0,
                isIdempotent = true,
            ),
        )
    }

    @Test
    fun `disabled flag wins over everything`() {
        assertFalse(
            executor.shouldSpeculate(
                speechDurationMs = 1_000,
                partial = "what is the weather today",
                retries = 0,
                isIdempotent = true,
                enabled = false,
            ),
        )
    }

    @Test
    fun `attempt counter resets on reset`() = runTest {
        executor.speculate(backgroundScope) { /* completes immediately */ }
        assertEquals(1, executor.attemptCount())
        executor.reset()
        assertEquals(0, executor.attemptCount())
    }
}
