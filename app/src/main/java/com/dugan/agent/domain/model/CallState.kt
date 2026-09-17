package com.dugan.agent.domain.model

/** Transport a call arrived on. Determines which audio source is viable. */
enum class CallTransport {
    /** None -- the agent is being used as a standalone assistant. */
    None,

    /**
     * Cellular/SIM. Android 9+ denies VOICE_CALL to third-party apps, so we can
     * only hear it via speakerphone + MIC, and we cannot answer it ourselves.
     */
    Sim,

    /** Self-managed VoIP: VOICE_COMMUNICATION gives clean audio and we can answer. */
    Voip,
}

/**
 * @property transport how the call arrived
 * @property handle E.164 or tel: URI of the far end, if known
 * @property displayName contact name if resolved, else the raw handle
 * @property connectedAtMs epoch millis the call became active, for the timer
 * @property isMuted local mute
 * @property isSpeakerphoneOn required true for SIM capture to work at all
 * @property isOnHold remote hold
 * @property agentHandling the "Let Agent Handle" toggle
 */
data class CallState(
    val transport: CallTransport = CallTransport.None,
    val handle: String? = null,
    val displayName: String? = null,
    val connectedAtMs: Long? = null,
    val isRinging: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeakerphoneOn: Boolean = false,
    val isOnHold: Boolean = false,
    val agentHandling: Boolean = false,
    val isDefaultDialer: Boolean = false,
) {
    val hasCall: Boolean get() = transport != CallTransport.None

    /** True when we are allowed to capture audio for this call. */
    val canCapture: Boolean
        get() = when (transport) {
            CallTransport.None -> true // standalone assistant mode
            CallTransport.Voip -> hasCall
            // SIM audio only reaches the mic once the call is on the loudspeaker.
            CallTransport.Sim -> hasCall && isSpeakerphoneOn
        }

    companion object {
        val NoCall = CallState()
    }
}
