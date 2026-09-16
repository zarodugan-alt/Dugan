package com.dugan.agent.domain.model

/** Which provider's API and key a model is reached through. */
enum class ModelProvider(
    val id: String,
    val displayName: String,
    /** Single-letter badge shown on the model chips. */
    val badge: String,
) {
    Gemini("gemini", "Gemini", "G"),
    Groq("groq", "Groq", "Q"),
    ;

    companion object {
        fun fromId(id: String): ModelProvider? = entries.firstOrNull { it.id == id }
    }
}

/**
 * @property supportsThinking whether the model honours a thinking budget; only
 *   these are shown in the main-screen model selector.
 * @property isRouterCandidate cheap enough to run the intent classifier on.
 */
data class AgentModel(
    val id: String,
    val wireId: String,
    val displayName: String,
    val provider: ModelProvider,
    val supportsThinking: Boolean = true,
    val isRouterCandidate: Boolean = false,
)

/** The fixed catalogue. No network calls, no runtime model discovery. */
object ModelCatalog {

    val ThinkingModels: List<AgentModel> = listOf(
        AgentModel(
            id = "gemini-flash",
            wireId = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            provider = ModelProvider.Gemini,
        ),
        AgentModel(
            id = "gemini-flash-lite",
            wireId = "gemini-2.5-flash-lite",
            displayName = "Gemini 2.5 Flash-Lite",
            provider = ModelProvider.Gemini,
            isRouterCandidate = true,
        ),
        AgentModel(
            id = "gemini-pro",
            wireId = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro",
            provider = ModelProvider.Gemini,
        ),
        AgentModel(
            id = "llama-4-scout",
            wireId = "meta-llama/llama-4-scout-17b-16e-instruct",
            displayName = "Llama 4 Scout",
            provider = ModelProvider.Groq,
        ),
        AgentModel(
            id = "llama-3.3-70b",
            wireId = "llama-3.3-70b-versatile",
            displayName = "Llama 3.3 70B",
            provider = ModelProvider.Groq,
        ),
        AgentModel(
            id = "gpt-oss-120b",
            wireId = "openai/gpt-oss-120b",
            displayName = "GPT-OSS 120B",
            provider = ModelProvider.Groq,
        ),
    )

    val SttModels: List<AgentModel> = listOf(
        AgentModel(
            id = "whisper-large-v3-turbo",
            wireId = "whisper-large-v3-turbo",
            displayName = "Whisper Large v3 Turbo",
            provider = ModelProvider.Groq,
            supportsThinking = false,
        ),
        AgentModel(
            id = "whisper-large-v3",
            wireId = "whisper-large-v3",
            displayName = "Whisper Large v3",
            provider = ModelProvider.Groq,
            supportsThinking = false,
        ),
        AgentModel(
            id = "distil-whisper-large-v3-en",
            wireId = "distil-whisper-large-v3-en",
            displayName = "Distil Whisper Large v3 (EN)",
            provider = ModelProvider.Groq,
            supportsThinking = false,
        ),
    )

    /**
     * TTS "models" are endpoints rather than weights. `unreal-stream` is primary;
     * `edge-tts` is the keyless fallback used when Unreal quota is exhausted.
     */
    val TtsModels: List<AgentModel> = listOf(
        AgentModel(
            id = "unreal-stream",
            wireId = "stream",
            displayName = "Unreal Speech /stream",
            provider = ModelProvider.Gemini,
            supportsThinking = false,
        ),
        AgentModel(
            id = "edge-tts",
            wireId = "edge-tts",
            displayName = "Microsoft Edge TTS (no key)",
            provider = ModelProvider.Gemini,
            supportsThinking = false,
        ),
    )

    val DefaultThinking: AgentModel = ThinkingModels[0]
    val DefaultRouter: AgentModel = ThinkingModels[1]
    val DefaultStt: AgentModel = SttModels[0]
    val DefaultTts: AgentModel = TtsModels[0]

    fun byId(id: String): AgentModel? =
        (ThinkingModels + SttModels + TtsModels).firstOrNull { it.id == id }
}
