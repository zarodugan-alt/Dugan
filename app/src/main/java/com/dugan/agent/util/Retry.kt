package com.dugan.agent.util

import com.dugan.agent.data.api.ApiException
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Retry with exponential backoff.
 *
 * Only retryable failures are retried: transport errors and 408/429/5xx. A 401 or
 * a 400 is a permanent answer and retrying it just wastes the user's quota.
 */
suspend fun <T> withRetry(
    what: String,
    attempts: Int = 3,
    initialDelayMs: Long = 250,
    maxDelayMs: Long = 4_000,
    block: suspend () -> T,
): T {
    var lastError: Throwable? = null
    var delayMs = initialDelayMs

    repeat(attempts) { attempt ->
        try {
            return block()
        } catch (t: Throwable) {
            val retryable = when (t) {
                is ApiException -> t.isRetryable
                is IOException -> true
                else -> false
            }
            lastError = t
            if (!retryable || attempt == attempts - 1) throw t
            AgentLog.w("Retry", "$what failed (attempt ${attempt + 1}/$attempts), backing off ${delayMs}ms")
            delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
        }
    }
    throw lastError ?: IllegalStateException("$what failed")
}
