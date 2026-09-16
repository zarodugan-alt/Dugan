package com.dugan.agent.domain.telecom

import com.dugan.agent.util.AgentLog
import android.net.Uri
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject

/**
 * Self-managed ConnectionService: the VoIP half of the Telecom integration.
 *
 * The framework calls in here when something places or receives a call on our
 * PhoneAccount. We hand back a [VoIPConnection] marked PROPERTY_SELF_MANAGED,
 * which is what keeps the media path under our control and makes
 * VOICE_COMMUNICATION capture legal for the call's duration.
 *
 * Note on identity: Telecom gives real `Call` objects to InCallService, not to
 * ConnectionService -- and `Call` is final, so we cannot synthesise one here.
 * This service therefore tracks its own [VoIPConnection]s by id, and
 * [VoiceInCallService] populates [CallController] with the framework's view.
 */
@AndroidEntryPoint
class VoiceConnectionService : ConnectionService() {

    @Inject
    lateinit var controller: CallController

    /** Our own connections, keyed by the signalling call id. */
    private val connections = LinkedHashMap<String, VoIPConnection>()

    fun connectionFor(callId: String?): VoIPConnection? = callId?.let { connections[it] }

    override fun onCreateIncomingConnection(
        phoneAccountHandle: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val address = request?.address
        val callId = request.extras.callId() ?: UUID.randomUUID().toString()
        AgentLog.i(TAG, "onCreateIncomingConnection id=$callId address=$address")

        return newConnection(callId, address).apply { markRinging() }
    }

    override fun onCreateOutgoingConnection(
        phoneAccountHandle: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val address = request?.address
        val callId = request.extras.callId() ?: UUID.randomUUID().toString()
        AgentLog.i(TAG, "onCreateOutgoingConnection id=$callId address=$address")

        return newConnection(callId, address).apply { markDialing() }
    }

    override fun onCreateIncomingConnectionFailed(
        phoneAccountHandle: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ) {
        AgentLog.w(TAG, "incoming connection failed")
        super.onCreateIncomingConnectionFailed(phoneAccountHandle, request)
    }

    override fun onCreateOutgoingConnectionFailed(
        phoneAccountHandle: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ) {
        AgentLog.w(TAG, "outgoing connection failed")
        super.onCreateOutgoingConnectionFailed(phoneAccountHandle, request)
    }

    private fun newConnection(callId: String, address: Uri?): VoIPConnection {
        val connection = VoIPConnection(
            context = this,
            address = address,
            isIncoming = true,
            onStateChange = { conn, state -> onConnectionStateChanged(callId, conn, state) },
        )
        connection.callId = callId
        connections[callId] = connection
        return connection
    }

    private fun onConnectionStateChanged(
        callId: String,
        connection: VoIPConnection,
        state: VoIPConnection.State,
    ) {
        AgentLog.i(TAG, "connection $callId -> $state")
        if (state == VoIPConnection.State.Disconnected) {
            connections.remove(callId)
        }
        // Mirror the transport/hold flags the in-call UI needs. The framework
        // callbacks in VoiceInCallService remain authoritative for everything
        // else, so this only fills the gaps self-managed calls do not report.
        controller.updateLocal { current ->
            if (current.handle == null && connection.callId == callId) {
                current.copy(
                    transport = com.dugan.agent.domain.model.CallTransport.Voip,
                    isOnHold = state == VoIPConnection.State.Holding,
                )
            } else {
                current.copy(isOnHold = state == VoIPConnection.State.Holding)
            }
        }
    }

    /** Called by the signalling layer when the remote end hangs up. */
    fun disconnect(callId: String) {
        connections.remove(callId)?.markDisconnected(DisconnectCause.REMOTE)
    }

    fun disconnectAll() {
        connections.values.toList().forEach { it.markDisconnected(DisconnectCause.LOCAL) }
        connections.clear()
    }

    private fun Bundle?.callId(): String? = this?.getString(CallManager.EXTRA_CALL_ID)

    private companion object {
        const val TAG = "ConnectionService"
    }
}
