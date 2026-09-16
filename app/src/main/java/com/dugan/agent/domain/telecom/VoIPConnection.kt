package com.dugan.agent.domain.telecom

import android.content.Context
import android.net.Uri
import android.telecom.Connection
import android.telecom.TelecomManager
import android.util.Log

/**
 * A self-managed VoIP connection.
 *
 * `PROPERTY_SELF_MANAGED` is what tells Telecom that *we* own the media: the
 * framework will not try to route audio, and `VOICE_COMMUNICATION` capture works
 * for the duration of the call. This is the path that gives clean audio; the SIM
 * path never gets here.
 *
 * @param onStateChange invoked so [CallManager] can drive the agent lifecycle.
 */
class VoIPConnection(
    context: Context,
    private val address: Uri?,
    private val isIncoming: Boolean,
    private val onStateChange: (VoIPConnection, State) -> Unit = { _, _ -> },
) : Connection(context, null) {

    enum class State { Ringing, Dialing, Active, Holding, Disconnected }

    /** Signalling handle, so the service can push remote SDP into the right call. */
    @Volatile
    var callId: String? = null

    init {
        setConnectionProperties(PROPERTY_SELF_MANAGED)
        // Routes the audio path into the VoIP mixer: hardware AEC engages here.
        setAudioModeIsVoip(true)
        connectionCapabilities = CAPABILITY_MUTE or CAPABILITY_HOLD
        address?.let {
            setAddress(it, TelecomManager.PRESENTATION_ALLOWED)
        }
    }

    override fun onAnswer() {
        Log.i(TAG, "onAnswer")
        setAudioModeIsVoip(true)
        setActive()
        onStateChange(this, State.Active)
    }

    override fun onReject() {
        Log.i(TAG, "onReject")
        setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.REJECTED))
        onStateChange(this, State.Disconnected)
        destroy()
    }

    override fun onDisconnect() {
        Log.i(TAG, "onDisconnect")
        setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
        onStateChange(this, State.Disconnected)
        destroy()
    }

    override fun onHold() {
        setOnHold()
        onStateChange(this, State.Holding)
    }

    override fun onUnhold() {
        setActive()
        onStateChange(this, State.Active)
    }

    override fun onPlayDtmfTone(c: Char) = Unit
    override fun onStopDtmfTone() = Unit

    fun markDialing() {
        setDialing()
        onStateChange(this, State.Dialing)
    }

    fun markRinging() {
        setRinging()
        onStateChange(this, State.Ringing)
    }

    fun markActive() {
        setActive()
        onStateChange(this, State.Active)
    }

    fun markDisconnected(cause: Int = android.telecom.DisconnectCause.REMOTE) {
        setDisconnected(android.telecom.DisconnectCause(cause))
        onStateChange(this, State.Disconnected)
        destroy()
    }

    private companion object {
        const val TAG = "VoIPConnection"
    }
}
