package com.dugan.agent.domain.model

/** The two BYOK endpoints the device talks to directly. No proxy, no backend. */
enum class ApiProvider(
    val id: String,
    val displayName: String,
    val emoji: String,
    val role: String,
    val signupUrl: String,
    /**
     * Prefixes this provider has been seen to issue, newest first.
     *
     * Advisory only — [looksLikeKey] never rejects a key for failing to match.
     * A provider can re-brand its tokens whenever it likes: Google AI Studio
     * switched from `AIza` Standard keys to `AQ.` Auth keys in mid-2026, and
     * every tool that had welded `AIza` into a validator started rejecting
     * perfectly good keys overnight. The Test probe is the only authority on
     * whether a key is live.
     *
     * What the list *is* good for: recognising a key that belongs to a
     * different provider, which is the one paste mistake worth catching
     * locally, and telling the user what shape to expect.
     */
    val knownKeyPrefixes: List<String>,
    /** Shown in the key field so the expected shape is obvious before you paste. */
    val keyHint: String,
) {
    Groq(
        id = "groq",
        displayName = "Groq",
        emoji = "🎤",
        role = "Hearing + Speaking",
        signupUrl = "https://console.groq.com/keys",
        knownKeyPrefixes = listOf("gsk_"),
        keyHint = "gsk_…  (one key covers both hearing and speaking)",
    ),
    Gemini(
        id = "gemini",
        displayName = "Google Gemini",
        emoji = "🧠",
        role = "Thinking",
        signupUrl = "https://aistudio.google.com/apikey",
        // `AQ.` Auth keys are what AI Studio issues now. The older `AIza` /
        // `ya29.` Standard keys stay listed so keys still in circulation keep
        // working until Google retires them, and so a pasted Standard key is
        // still recognisable as a Gemini key.
        knownKeyPrefixes = listOf("AQ.", "AIza", "ya29."),
        keyHint = "AQ.Ab…  (older AIza… keys still work)",
    ),
    ;

    companion object {
        fun fromId(id: String): ApiProvider? = entries.firstOrNull { it.id == id }
    }
}

/** Result of a "Test Connection" probe, surfaced verbatim in the UI. */
sealed interface KeyTestResult {
    data object Untested : KeyTestResult
    data object Testing : KeyTestResult
    data class Valid(val detail: String) : KeyTestResult
    data class Invalid(val detail: String) : KeyTestResult

    val isOk: Boolean get() = this is Valid
}

/**
 * Redaction helper: keys are never logged, never rendered in full, and never
 * placed in an exception message.
 */
fun maskKey(key: String?): String {
    if (key.isNullOrBlank()) return "(empty)"
    val trimmed = key.trim()
    if (trimmed.length <= 8) return "*".repeat(trimmed.length)
    return trimmed.take(4) + "•".repeat(8) + trimmed.takeLast(4)
}

/** Shortest string that could plausibly be an API key from either provider. */
private const val MIN_KEY_LENGTH = 16

/**
 * Zero-width characters clipboard managers and chat apps like to smuggle in.
 * They are invisible, so a field that rejects them looks broken to the user.
 */
private val INVISIBLE_CHARS = setOf('\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF')

/**
 * Normalises whatever the clipboard handed us.
 *
 * No provider issues a key containing whitespace, but pastes routinely carry a
 * trailing newline, some IMEs substitute a space for the line break when text
 * lands in a single-line field, and a copied `curl` line can bring a quote or
 * two along. Stripping the noise at the point of entry means a paste always
 * lands exactly as the provider issued it, and [looksLikeKey] never has to
 * reject a key over formatting.
 */
fun sanitizeKey(raw: String): String =
    raw.filterNot { it.isWhitespace() || it in INVISIBLE_CHARS }

/**
 * Cheap structural check before burning a network round-trip.
 *
 * This is *not* a verdict and never decides whether a key works — the provider
 * does, via [com.dugan.agent.domain.model.KeyVerifyScheduler]. It only rejects
 * input that cannot possibly be a credential at all: blank, or carrying a space
 * or line break, which no provider issues.
 *
 * Shape is deliberately not checked beyond that. Google re-issued Gemini keys
 * from `AIza` Standard to `AQ.` Auth format in mid-2026, and every tool that
 * had welded the old prefix into a validator started rejecting valid keys.
 */
fun looksLikeKey(provider: ApiProvider, key: String?): Boolean =
    keyProblem(provider, key) == null

/**
 * @return a human-readable reason [key] cannot be a credential at all, or null
 *   when it is worth sending to the provider to find out.
 */
fun keyProblem(provider: ApiProvider, key: String?): String? {
    val trimmed = key?.trim().orEmpty()
    if (trimmed.isBlank()) return "Key is empty"
    if (trimmed.any { it.isWhitespace() }) return "Key contains a space or line break"
    if (trimmed.any { it in INVISIBLE_CHARS }) return "Key contains invisible characters — copy it again"
    return null
}

/**
 * Observations about a key's shape: worth showing next to the field, never
 * worth blocking on. The live probe still runs and still decides.
 *
 * The useful case is a key pasted into the wrong field — a Groq key in the
 * Gemini box otherwise fails with a confusing 401 from the wrong service.
 */
fun keyAdvisory(provider: ApiProvider, key: String?): String? {
    val trimmed = key?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    val owner = ApiProvider.entries
        .firstOrNull { it != provider && it.knownKeyPrefixes.any { prefix -> trimmed.startsWith(prefix) } }
    if (owner != null) return "That looks like a ${owner.displayName} key — is this the right field?"
    if (trimmed.length < MIN_KEY_LENGTH) return "That is unusually short for a ${provider.displayName} key"
    return null
}
