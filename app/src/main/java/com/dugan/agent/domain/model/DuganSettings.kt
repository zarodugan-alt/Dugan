package com.dugan.agent.domain.model

/** Audio input path. See [com.dugan.agent.domain.audio.AudioCaptureManager]. */
enum class InputSource(val label: String) {
    /** Loudspeaker + MIC. The only option for SIM calls on Android 9+. */
    SimSpeakerphone("SIM Speakerphone"),

    /** Hardware AEC/NS/AGC. Clean, but only reachable during a VoIP call. */
    VoipCommunication("VoIP Communication"),

    /** Let the orchestrator pick per call transport. */
    Auto("Auto"),
}

/**
 * Everything the Settings screen edits, in one immutable snapshot.
 *
 * Persisted by [com.dugan.agent.data.local.SettingsStore] as DataStore
 * preferences; models/keys live elsewhere (Room-less prefs and the vault).
 */
data class DuganSettings(
    // Models
    val sttModelId: String = ModelCatalog.DefaultStt.id,
    val ttsModelId: String = ModelCatalog.DefaultTts.id,
    val thinkingModelId: String = ModelCatalog.DefaultThinking.id,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.Default,

    // Agent behaviour
    val listeningMode: ListeningMode = ListeningMode.PushToTalk,
    val autoAnswerVoip: Boolean = false,
    /** 0..10 seconds the agent waits before auto-answering a VoIP call. */
    val answerDelaySeconds: Int = 3,
    val greeting: String = "Hi, this is Dugan. How can I help?",

    // Audio
    val inputSource: InputSource = InputSource.Auto,
    /** 0f..1f; higher = needs louder audio to count as speech. */
    val vadSensitivity: Float = 0.5f,
    /** Trailing silence before a turn is closed when semantic EOT is unavailable. */
    val silenceThresholdMs: Int = 700,
    /** 0f..1f strength of the software AEC. 0 disables layer 2. */
    val echoSuppression: Float = 1.0f,
    /** -1f..1f, passed straight to the TTS provider. */
    val playbackSpeed: Float = 0f,
    /**
     * A Groq Orpheus voice: hannah, autumn, diana, austin, daniel, troy. The
     * keyless Edge TTS tier cannot use these and substitutes its own default.
     */
    val ttsVoiceId: String = "hannah",

    // Latency toggles
    val streamingStt: Boolean = true,
    val speculativeLlm: Boolean = true,
    val ttsCaching: Boolean = true,
    val prefixCaching: Boolean = true,
    val modelRouting: Boolean = true,

    // Echo defence layer switches (layer 1 hardware is always attempted)
    val softwareAecEnabled: Boolean = true,
    val micGatingEnabled: Boolean = true,
    val bargeInEnabled: Boolean = true,
    val textEchoDefenseEnabled: Boolean = true,

    // Presentation
    val themeId: String = "crimson_noir",
    val dynamicColor: Boolean = false,
    val onboardingCompleted: Boolean = false,
) {
    val answerDelayMs: Long get() = answerDelaySeconds * 1000L

    val sttModel: AgentModel get() = ModelCatalog.byId(sttModelId) ?: ModelCatalog.DefaultStt
    val ttsModel: AgentModel get() = ModelCatalog.byId(ttsModelId) ?: ModelCatalog.DefaultTts
    val thinkingModel: AgentModel get() = ModelCatalog.byId(thinkingModelId) ?: ModelCatalog.DefaultThinking
}
