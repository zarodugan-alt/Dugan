package com.dugan.agent.domain.model

/**
 * The five states of the agent loop, plus the two non-loop states.
 *
 * Idle -> Listening -> Thinking -> Speaking -> Idle is the happy path.
 * Paused is reachable from any of Listening/Thinking/Speaking and resumes back
 * into the phase it left. Error is sticky until [AgentCommand.Reset].
 */
enum class AgentPhase {
    Idle,
    Listening,
    Thinking,
    Speaking,
    Paused,
    Error,
    ;

    val isActive: Boolean
        get() = this == Listening || this == Thinking || this == Speaking

    val isTerminal: Boolean
        get() = this == Idle || this == Error
}

/** Commands the UI (or a call event) can issue to the orchestrator. */
enum class AgentCommand {
    Start,
    Stop,
    Pause,
    Continue,
    Reset,
    BargeIn,
}

/**
 * Why the agent is listening. Drives audio-source selection in
 * [com.dugan.agent.domain.audio.AudioCaptureManager].
 */
enum class ListeningMode {
    /** Hold the mic button; release submits. Battery friendly, deterministic. */
    PushToTalk,

    /** Always-on; VAD + semantic end-of-turn decide when the turn is over. */
    ContinuousListening,
}

/** Immutable snapshot handed to the UI on every transition. */
data class AgentState(
    val phase: AgentPhase = AgentPhase.Idle,
    val phaseBeforePause: AgentPhase = AgentPhase.Idle,
    val listeningMode: ListeningMode = ListeningMode.PushToTalk,
    val callState: CallState = CallState.NoCall,
    /** Live STT partial for the in-progress user turn; empty between turns. */
    val partialTranscript: String = "",
    /** Streaming agent text for the in-progress turn; empty between turns. */
    val partialResponse: String = "",
    /** 0f..1f RMS of the capture buffer, drives the orb visualiser. */
    val inputLevel: Float = 0f,
    /** 0f..1f RMS of the playback buffer. */
    val outputLevel: Float = 0f,
    /** True while the agent's own voice is on the speaker (mic gating / barge-in). */
    val agentSpeaking: Boolean = false,
    val turnId: Long = 0L,
    val errorMessage: String? = null,
    /** End-to-end timing of the last completed turn, for the latency readout. */
    val lastTurnLatencyMs: Long? = null,
    /** Main-screen segmented control. Null means "use the Settings default". */
    val thinkingLevelOverride: ThinkingLevel? = null,
    /** Main-screen model chip. Null means "use the Settings default". */
    val modelOverride: AgentModel? = null,
) {
    val canStart: Boolean get() = phase == AgentPhase.Idle || phase == AgentPhase.Error
    val canPause: Boolean get() = phase.isActive
    val canContinue: Boolean get() = phase == AgentPhase.Paused
    val canStop: Boolean get() = phase.isActive || phase == AgentPhase.Paused
}
