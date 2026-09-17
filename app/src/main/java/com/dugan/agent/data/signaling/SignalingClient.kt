package com.dugan.agent.data.signaling

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** One leg of a WebRTC session description exchange. */
data class SessionDescription(val type: String, val sdp: String)

data class IceCandidate(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int)

enum class SignalingStatus { Ringing, Active, Ended, Failed }

data class CallSignal(
    val callId: String,
    val callerUid: String? = null,
    val callerName: String? = null,
    val calleeUid: String? = null,
    val offer: SessionDescription? = null,
    val answer: SessionDescription? = null,
    val candidates: List<IceCandidate> = emptyList(),
    val status: SignalingStatus = SignalingStatus.Ringing,
    val timestampMs: Long = 0L,
)

/**
 * Call setup only.
 *
 * Explicit non-goal: audio never flows through the signalling channel. Once the
 * offer/answer/ICE exchange completes, media is peer-to-peer WebRTC and this
 * interface is out of the data path entirely.
 */
interface SignalingClient {

    val name: String

    /** True when this implementation can actually reach a signalling server. */
    val isAvailable: Boolean

    fun observeCall(callId: String): Flow<CallSignal>

    fun observeIncoming(): Flow<CallSignal>

    suspend fun startCall(callId: String, calleeUid: String, displayName: String)

    suspend fun sendOffer(callId: String, offer: SessionDescription)

    suspend fun sendAnswer(callId: String, answer: SessionDescription)

    suspend fun sendCandidate(callId: String, candidate: IceCandidate)

    suspend fun endCall(callId: String)
}

/** Contributed via Hilt multibinding; see [com.dugan.agent.di.SignalingModule]. */
interface SignalingContributor {
    fun create(context: Context): SignalingClient
}

/**
 * Default: no external signalling.
 *
 * Lets the whole app -- dialer, agent, SIM speakerphone capture -- work with zero
 * configuration. VoIP call setup is the only thing that reports unavailable.
 */
object LocalOnlySignalingClient : SignalingClient {
    override val name: String = "local-only"
    override val isAvailable: Boolean = false

    override fun observeCall(callId: String): Flow<CallSignal> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeIncoming(): Flow<CallSignal> = kotlinx.coroutines.flow.emptyFlow()

    override suspend fun startCall(callId: String, calleeUid: String, displayName: String) = Unit
    override suspend fun sendOffer(callId: String, offer: SessionDescription) = Unit
    override suspend fun sendAnswer(callId: String, answer: SessionDescription) = Unit
    override suspend fun sendCandidate(callId: String, candidate: IceCandidate) = Unit
    override suspend fun endCall(callId: String) = Unit
}
