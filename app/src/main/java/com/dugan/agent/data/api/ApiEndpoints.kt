package com.dugan.agent.data.api

/** Every remote endpoint the app touches. All are called directly from the device. */
object ApiEndpoints {
    // Groq
    const val GROQ_TRANSCRIPTIONS = "https://api.groq.com/openai/v1/audio/transcriptions"
    const val GROQ_CHAT = "https://api.groq.com/openai/v1/chat/completions"

    // Gemini. The model id is substituted per call.
    fun geminiStream(model: String) =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent"

    fun geminiGenerate(model: String) =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    // Groq text-to-speech (Orpheus). Same key as the Groq endpoints above.
    const val GROQ_SPEECH = "https://api.groq.com/openai/v1/audio/speech"

    // Keyless TTS fallback, used when Groq quota is exhausted.
    const val EDGE_TTS_LIST_URL =
        "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list?trustedclienttoken=6A5AA1D4EAFF4E9FB37E23D68491D6F4"

    fun edgeTtsWebSocket(secMsGec: String) =
        "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/websocket/v1" +
            "?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4&Sec-MS-GEC=$secMsGec&Sec-MS-GEC-Version=1-130.0.2849.68"

    /** Minimal round-trip used by "Test Connection". */
    const val GEMINI_MODELS_PROBE = "https://generativelanguage.googleapis.com/v1beta/models"
}

/**
 * Failures are mapped into these so callers can branch on recovery strategy
 * instead of string-matching an HTTP body.
 */
class ApiException(
    val statusCode: Int,
    val provider: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException("[$provider/$statusCode] $message", cause) {

    /** 429 and 402 both mean "stop asking, switch provider". */
    val isQuota: Boolean get() = statusCode == 429 || statusCode == 402 || statusCode == 403
    val isAuth: Boolean get() = statusCode == 401
    val isRetryable: Boolean get() = statusCode == 408 || statusCode == 429 || statusCode in 500..599
}
