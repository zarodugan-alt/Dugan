package com.dugan.agent.domain.latency

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

/**
 * Speculative execution.
 *
 * Fires the LLM on the *first* STT partial instead of waiting for the final
 * transcript. Greedy decoding makes this useful: if the partial and the final
 * agree, the response is already streaming and we saved a whole STT->LLM
 * round-trip. If they disagree, we cancel and restart -- wasted tokens, not
 * wrong answers.
 *
 * The rules in [shouldSpeculate] are the safety valve.
 */
@Singleton
class SpeculativeExecutor @Inject constructor() {

    private val attempts = AtomicInteger(0)
    private var current: Job? = null

    /**
     * @param speechDurationMs how long the user has been talking so far
     * @param partial the transcript fragment we would speculate on
     * @param retries how many speculations have already been discarded this turn
     * @param isIdempotent false for anything with side effects
     */
    fun shouldSpeculate(
        speechDurationMs: Long,
        partial: String,
        retries: Int,
        isIdempotent: Boolean,
        enabled: Boolean = true,
    ): Boolean {
        if (!enabled) return false
        // Never speculate on a write. A discarded speculation that already sent a
        // text message is not recoverable.
        if (!isIdempotent) return false
        // Long utterances are unlikely to be finished; the discard rate explodes.
        if (speechDurationMs > MAX_SPEECH_MS) return false
        if (retries >= MAX_RETRIES) return false
        // Too little signal to predict anything from.
        return partial.trim().split(' ').filter { it.isNotBlank() }.size >= MIN_WORDS
    }

    /**
     * Launches [work] as the current speculation, cancelling any previous one.
     * Returns the job so the caller can await or discard it.
     */
    fun speculate(scope: CoroutineScope, work: suspend () -> Unit): Job {
        current?.cancel()
        attempts.incrementAndGet()
        return scope.launch { runCatching { work() }.onFailure { Log.d(TAG, "speculation cancelled") } }
            .also { current = it }
    }

    /** Called when the final transcript invalidates the speculation. */
    fun discard(): Int {
        current?.cancel()
        current = null
        return attempts.get()
    }

    fun reset() {
        current?.cancel()
        current = null
        attempts.set(0)
    }

    fun attemptCount(): Int = attempts.get()

    private companion object {
        const val TAG = "Speculative"
        const val MAX_SPEECH_MS = 8_000L
        const val MAX_RETRIES = 2
        const val MIN_WORDS = 3
    }
}
