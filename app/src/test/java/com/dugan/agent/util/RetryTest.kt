package com.dugan.agent.util

import com.dugan.agent.data.api.ApiException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RetryTest {

    @Test
    fun `succeeds first time without retrying`() = runTest {
        var calls = 0
        val result = withRetry("test", attempts = 3) { calls++; "ok" }
        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `retries a 500 and then succeeds`() = runTest {
        var calls = 0
        val result = withRetry("test", attempts = 3, initialDelayMs = 1) {
            calls++
            if (calls < 3) throw ApiException(500, "test", "server error")
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(3, calls)
    }

    @Test
    fun `retries a transport failure`() = runTest {
        var calls = 0
        val result = withRetry("test", attempts = 2, initialDelayMs = 1) {
            calls++
            if (calls == 1) throw IOException("connection reset")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun `gives up after the configured number of attempts`() = runTest {
        var calls = 0
        assertThrows(ApiException::class.java) {
            kotlinx.coroutines.runBlocking {
                withRetry("test", attempts = 3, initialDelayMs = 1) {
                    calls++
                    throw ApiException(503, "test", "unavailable")
                }
            }
        }
        assertEquals(3, calls)
    }

    @Test
    fun `never retries an auth failure -- that would just burn quota`() = runTest {
        var calls = 0
        assertThrows(ApiException::class.java) {
            kotlinx.coroutines.runBlocking {
                withRetry("test", attempts = 5, initialDelayMs = 1) {
                    calls++
                    throw ApiException(401, "test", "bad key")
                }
            }
        }
        assertEquals(1, calls)
    }

    @Test
    fun `never retries a client error`() = runTest {
        var calls = 0
        assertThrows(ApiException::class.java) {
            kotlinx.coroutines.runBlocking {
                withRetry("test", attempts = 5, initialDelayMs = 1) {
                    calls++
                    throw ApiException(400, "test", "malformed request")
                }
            }
        }
        assertEquals(1, calls)
    }

    @Test
    fun `retries a 429 because quota windows reset`() = runTest {
        var calls = 0
        withRetry("test", attempts = 2, initialDelayMs = 1) {
            calls++
            if (calls == 1) throw ApiException(429, "test", "rate limited")
            "ok"
        }
        assertEquals(2, calls)
    }

    @Test
    fun `does not retry an arbitrary programming error`() = runTest {
        var calls = 0
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                withRetry("test", attempts = 4, initialDelayMs = 1) {
                    calls++
                    throw IllegalStateException("bug")
                }
            }
        }
        assertEquals(1, calls)
    }
}

class AgentLogRedactTest {

    @Test
    fun `redacts a groq key`() {
        val out = AgentLog.redact("calling with gsk_ABCDEFGHIJKLMNOP1234567890 now")
        assertTrue("key leaked: $out", !out.contains("ABCDEFGHIJKLMNOP"))
        assertTrue(out.contains("<redacted>"))
    }

    @Test
    fun `redacts a gemini key`() {
        val out = AgentLog.redact("key=AIzaSyD-abcdefghijklmnopqrstuvwx")
        assertTrue("key leaked: $out", !out.contains("abcdefghijklmnop"))
    }

    @Test
    fun `redacts the AQ auth key format gemini issues now`() {
        val out = AgentLog.redact("probe failed with AQ.Ab8SAMPLEKEY00000000000000000000000000000000000000")
        assertTrue("key leaked: $out", !out.contains("SAMPLEKEY0000000000"))
        assertTrue(out.contains("<redacted>"))
    }

    @Test
    fun `leaves ordinary text alone`() {
        val message = "turn 4 completed in 312ms with 2 sentences"
        assertEquals(message, AgentLog.redact(message))
    }

    @Test
    fun `short prefixes that are not keys are untouched`() {
        assertEquals("gsk_ short", AgentLog.redact("gsk_ short"))
    }
}
