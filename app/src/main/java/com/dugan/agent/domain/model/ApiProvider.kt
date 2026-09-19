package com.dugan.agent.domain.model

/** The three BYOK endpoints the device talks to directly. No proxy, no backend. */
enum class ApiProvider(
    val id: String,
    val displayName: String,
    val emoji: String,
    val role: String,
    val signupUrl: String,
    /** Prefix used to sanity-check a pasted key before we spend a request on it. */
    val expectedKeyPrefixes: List<String>,
) {
    Groq(
        id = "groq",
        displayName = "Groq",
        emoji = "🎤",
        role = "Speech-to-Text",
        signupUrl = "https://console.groq.com/keys",
        expectedKeyPrefixes = listOf("gsk_", "groq_"),
    ),
    Gemini(
        id = "gemini",
        displayName = "Google Gemini",
        emoji = "🧠",
        role = "Thinking",
        signupUrl = "https://aistudio.google.com/apikey",
        expectedKeyPrefixes = listOf("AIza", "ya29."),
    ),
    UnrealSpeech(
        id = "unreal_speech",
        displayName = "Unreal Speech",
        emoji = "🗣️",
        role = "Text-to-Speech",
        signupUrl = "https://unrealspeech.com",
        expectedKeyPrefixes = emptyList(), // no fixed prefix documented
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

/** Cheap structural check before burning a network round-trip. */
fun looksLikeKey(provider: ApiProvider, key: String?): Boolean {
    val trimmed = key?.trim().orEmpty()
    if (trimmed.length < 16) return false
    if (trimmed.contains(' ') || trimmed.contains('\n')) return false
    if (provider.expectedKeyPrefixes.isEmpty()) return true
    return provider.expectedKeyPrefixes.any { trimmed.startsWith(it) }
}
