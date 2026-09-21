package com.dugan.agent.domain.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The paste → probe → verdict pipeline, with the network replaced by a counter.
 *
 * The interesting property is not that a request happens but that exactly the
 * right number happen: one per settled paste, never one per keystroke, and
 * never two for a key that already came back reachable. The verdict itself must
 * come from the probe, which is why the fake returns the provider's own error
 * text and the assertions look for it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeyVerifySchedulerTest {

    private class Recorder(private val outcome: (String) -> Result<Unit>) {
        val verifiedKeys = mutableListOf<String>()
        val states = mutableListOf<KeyTestResult>()

        /** Key each verdict was reported against, so a late answer is visible. */
        val stateKeys = mutableListOf<String>()

        fun verify(provider: ApiProvider, key: String): Result<Unit> {
            verifiedKeys += key
            return outcome(key)
        }

        fun onState(provider: ApiProvider, key: String, result: KeyTestResult) {
            states += result
            stateKeys += key
        }
    }

    private fun scheduler(scope: CoroutineScope, rec: Recorder) = KeyVerifyScheduler(
        scope = scope,
        onState = rec::onState,
        verify = rec::verify,
    )

    private val key = "AQ.Ab8SAMPLEKEY00000000000000000000000000000000000000"

    @Test
    fun `a burst of edits produces one request for the final text`() = runTest {
        val rec = Recorder { Result.success(Unit) }
        val scheduler = scheduler(this, rec)

        key.indices.forEach { i -> scheduler.submit(ApiProvider.Gemini, key.take(i + 1)) }
        advanceUntilIdle()

        assertEquals("one request per settled paste", 1, rec.verifiedKeys.size)
        assertEquals(key, rec.verifiedKeys.single())
    }

    @Test
    fun `nothing is sent while the field is still being edited`() = runTest {
        val rec = Recorder { Result.success(Unit) }
        val scheduler = scheduler(this, rec)

        scheduler.submit(ApiProvider.Gemini, key)
        advanceTimeBy(KeyVerifyScheduler.DEFAULT_QUIET_PERIOD_MS - 1)
        assertEquals(0, rec.verifiedKeys.size)

        advanceUntilIdle()
        assertEquals(1, rec.verifiedKeys.size)
    }

    @Test
    fun `a reachable key reports valid against that key`() = runTest {
        val rec = Recorder { Result.success(Unit) }
        val scheduler = scheduler(this, rec)

        scheduler.submit(ApiProvider.Gemini, key)
        advanceUntilIdle()

        assertTrue(rec.states.first() is KeyTestResult.Testing)
        assertTrue(rec.states.last() is KeyTestResult.Valid)
        assertEquals(key, rec.stateKeys.last())
    }

    @Test
    fun `a rejected key reports the provider's own reason`() = runTest {
        val rec = Recorder { Result.failure(RuntimeException("[gemini/401] API key not valid")) }
        val scheduler = scheduler(this, rec)

        scheduler.submit(ApiProvider.Gemini, key)
        advanceUntilIdle()

        val verdict = rec.states.last()
        assertTrue("expected Invalid, got $verdict", verdict is KeyTestResult.Invalid)
        assertTrue((verdict as KeyTestResult.Invalid).detail.contains("API key not valid"))
    }

    @Test
    fun `an empty field never spends a request`() = runTest {
        val rec = Recorder { Result.success(Unit) }
        val scheduler = scheduler(this, rec)

        scheduler.submit(ApiProvider.Groq, "   ")
        advanceUntilIdle()

        assertEquals(0, rec.verifiedKeys.size)
        assertTrue(rec.states.last() is KeyTestResult.Untested)
    }

    @Test
    fun `the clipboard's trailing newline still verifies the real key`() = runTest {
        val rec = Recorder { Result.success(Unit) }
        val scheduler = scheduler(this, rec)

        scheduler.submit(ApiProvider.Gemini, " $key\r\n")
        advanceUntilIdle()

        assertEquals(key, rec.verifiedKeys.single())
        assertTrue(rec.states.last() is KeyTestResult.Valid)
    }

    @Test
    fun `a key already proven reachable is not checked twice`() = runTest {
        val rec = Recorder { Result.success(Unit) }
        val scheduler = scheduler(this, rec)

        scheduler.submit(ApiProvider.Gemini, key)
        advanceUntilIdle()
        scheduler.submit(ApiProvider.Gemini, key)
        advanceUntilIdle()

        assertEquals(1, rec.verifiedKeys.size)
        // The verdict is restated rather than left stale, so the badge stays
        // right without spending quota.
        assertTrue(rec.states.last() is KeyTestResult.Valid)
    }

    @Test
    fun `a key that failed is checked again on the next paste`() = runTest {
        var reachable = false
        val rec = Recorder { if (reachable) Result.success(Unit) else Result.failure(RuntimeException("nope")) }
        val scheduler = scheduler(this, rec)

        scheduler.submit(ApiProvider.Gemini, key)
        advanceUntilIdle()
        reachable = true
        scheduler.submit(ApiProvider.Gemini, key)
        advanceUntilIdle()

        assertEquals(2, rec.verifiedKeys.size)
        assertTrue(rec.states.last() is KeyTestResult.Valid)
    }

    @Test
    fun `submitNow skips the quiet period`() = runTest {
        val rec = Recorder { Result.success(Unit) }
        val scheduler = scheduler(this, rec)

        scheduler.submitNow(ApiProvider.Groq, key)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(1, rec.verifiedKeys.size)
    }

    @Test
    fun `forgetting a key makes the next paste cost a request again`() = runTest {
        val rec = Recorder { Result.success(Unit) }
        val scheduler = scheduler(this, rec)

        scheduler.submit(ApiProvider.Gemini, key)
        advanceUntilIdle()
        scheduler.forget(ApiProvider.Gemini)
        scheduler.submit(ApiProvider.Gemini, key)
        advanceUntilIdle()

        assertEquals(2, rec.verifiedKeys.size)
    }

    @Test
    fun `an edit in flight is cancelled rather than answered late`() = runTest {
        val rec = Recorder { Result.success(Unit) }
        val scheduler = scheduler(this, rec)

        scheduler.submit(ApiProvider.Gemini, "gsk_superseded000000")
        advanceTimeBy(KeyVerifyScheduler.DEFAULT_QUIET_PERIOD_MS - 1)
        scheduler.submit(ApiProvider.Gemini, key)
        advanceUntilIdle()

        // Only the final text is ever sent, so no verdict can arrive for a key
        // that is no longer in the field.
        assertEquals(listOf(key), rec.verifiedKeys)
    }
}
