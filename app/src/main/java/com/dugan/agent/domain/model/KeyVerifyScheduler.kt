package com.dugan.agent.domain.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Turns a burst of edits into one live verification per provider.
 *
 * Pasting fires a single change callback, but typing fires one per character,
 * and either can be followed by more edits a moment later. Probing on every
 * edit would burn quota and race itself — two responses landing out of order
 * would leave the field showing the verdict for a key that is no longer there.
 * So each submission restarts a short quiet period, and only once the field has
 * settled is the key actually sent to its provider.
 *
 * The verdict comes from that request alone. Shape is never consulted: Google
 * re-issued Gemini keys from `AIza` to `AQ.` in 2026 and every tool that had
 * welded the old prefix into a validator started rejecting valid keys.
 *
 * Deliberately framework-free so the timing can be tested with
 * `kotlinx-coroutines-test` instead of on a device.
 */
class KeyVerifyScheduler(
    private val scope: CoroutineScope,
    private val quietPeriodMs: Long = DEFAULT_QUIET_PERIOD_MS,
    /** Called with the key the verdict belongs to, so callers can act on exactly that key. */
    private val onState: (ApiProvider, String, KeyTestResult) -> Unit,
    private val verify: suspend (ApiProvider, String) -> Result<Unit>,
) {
    private val pending = mutableMapOf<String, Job>()

    /** Last key that came back reachable, per provider id, with its verdict. */
    private val reached = mutableMapOf<String, Pair<String, KeyTestResult>>()

    /**
     * Queue a check for [rawKey]. Any check already queued for this provider is
     * cancelled, so only the final text is ever sent.
     */
    fun submit(provider: ApiProvider, rawKey: String) = enqueue(provider, rawKey, waitQuietPeriod = true)

    /** Check immediately, for an explicit Test button rather than a keystroke. */
    fun submitNow(provider: ApiProvider, rawKey: String) = enqueue(provider, rawKey, waitQuietPeriod = false)

    private fun enqueue(provider: ApiProvider, rawKey: String, waitQuietPeriod: Boolean) {
        val key = sanitizeKey(rawKey)
        pending.remove(provider.id)?.cancel()

        if (key.isBlank()) {
            reached.remove(provider.id)
            onState(provider, key, KeyTestResult.Untested)
            return
        }

        // Already proven reachable and unchanged: restate the verdict instead of
        // spending another request on the same credential.
        reached[provider.id]?.takeIf { it.first == key }?.let { (_, verdict) ->
            onState(provider, key, verdict)
            return
        }

        pending[provider.id] = scope.launch {
            if (waitQuietPeriod) delay(quietPeriodMs)
            onState(provider, key, KeyTestResult.Testing)
            val outcome = verify(provider, key)
            val verdict = outcome.fold(
                onSuccess = { KeyTestResult.Valid("Reachable") },
                onFailure = { t -> KeyTestResult.Invalid(t.message?.take(160) ?: "Request failed") },
            )
            if (outcome.isSuccess) reached[provider.id] = key to verdict
            onState(provider, key, verdict)
        }
    }

    /** Drop a cached verdict, e.g. after the key is removed from the vault. */
    fun forget(provider: ApiProvider) {
        pending.remove(provider.id)?.cancel()
        reached.remove(provider.id)
    }

    fun cancelAll() {
        pending.values.forEach { it.cancel() }
        pending.clear()
        reached.clear()
    }

    companion object {
        /** Long enough to swallow a paste plus a stray keystroke, short enough to feel instant. */
        const val DEFAULT_QUIET_PERIOD_MS = 700L
    }
}
