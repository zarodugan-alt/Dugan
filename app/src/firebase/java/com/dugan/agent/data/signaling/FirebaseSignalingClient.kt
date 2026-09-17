package com.dugan.agent.data.signaling

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Realtime Database as a signalling layer -- and nothing else.
 *
 * Wired in only when the app is built with `-Pdugan.firebase=true`, which adds
 * this source set and the `google-services` plugin. Without that flag the
 * project builds and runs on [LocalOnlySignalingClient].
 *
 * Layout:
 * ```
 * /calls/{callId}/
 *   caller: { uid, displayName }
 *   callee: { uid, displayName }
 *   offer:  { sdp, type }
 *   answer: { sdp, type }
 *   iceCandidates/{pushId}: { candidate, sdpMid, sdpMLineIndex }
 *   status: "ringing" | "active" | "ended"
 *   timestamp: Long
 * ```
 *
 * Security rules must scope reads/writes to the two participants and expire
 * unanswered calls after 5 minutes; the database is public-facing even though it
 * carries no media.
 */
class FirebaseSignalingClient(
    private val database: FirebaseDatabase,
) : SignalingClient {

    override val name: String = "firebase-rtdb"
    override val isAvailable: Boolean = true

    private fun calls() = database.getReference("calls")

    override fun observeCall(callId: String): Flow<CallSignal> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.toSignal(callId)?.let { trySend(it) }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        calls().child(callId).addValueEventListener(listener)
        awaitClose { calls().child(callId).removeEventListener(listener) }
    }

    override fun observeIncoming(): Flow<CallSignal> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { child ->
                    val signal = child.toSignal(child.key.orEmpty()) ?: return@forEach
                    if (signal.status == SignalingStatus.Ringing) trySend(signal)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        calls().addValueEventListener(listener)
        awaitClose { calls().removeEventListener(listener) }
    }

    override suspend fun startCall(callId: String, calleeUid: String, displayName: String) {
        calls().child(callId).updateChildren(
            mapOf(
                "caller/displayName" to displayName,
                "callee/uid" to calleeUid,
                "status" to SignalingStatus.Ringing.name.lowercase(),
                "timestamp" to System.currentTimeMillis(),
            ),
        ).await()
    }

    override suspend fun sendOffer(callId: String, offer: SessionDescription) {
        calls().child(callId).child("offer")
            .setValue(mapOf("sdp" to offer.sdp, "type" to offer.type)).await()
    }

    override suspend fun sendAnswer(callId: String, answer: SessionDescription) {
        calls().child(callId).child("answer")
            .setValue(mapOf("sdp" to answer.sdp, "type" to answer.type)).await()
        calls().child(callId).child("status").setValue("active").await()
    }

    override suspend fun sendCandidate(callId: String, candidate: IceCandidate) {
        calls().child(callId).child("iceCandidates").push().setValue(
            mapOf(
                "candidate" to candidate.candidate,
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
            ),
        ).await()
    }

    override suspend fun endCall(callId: String) {
        calls().child(callId).child("status").setValue("ended").await()
    }

    private fun DataSnapshot.toSignal(callId: String): CallSignal? {
        if (!exists()) return null
        val children = children.associate { (it.key ?: "") to it }

        @Suppress("UNCHECKED_CAST")
        fun sdp(key: String): SessionDescription? {
            val node = children[key] ?: return null
            val map = node.value as? Map<String, Any?> ?: return null
            val sdp = map["sdp"] as? String ?: return null
            return SessionDescription(type = map["type"] as? String ?: "offer", sdp = sdp)
        }

        val candidates = children["iceCandidates"]?.children?.mapNotNull { node ->
            val map = node.value as? Map<*, *> ?: return@mapNotNull null
            IceCandidate(
                candidate = map["candidate"] as? String ?: return@mapNotNull null,
                sdpMid = map["sdpMid"] as? String,
                sdpMLineIndex = (map["sdpMLineIndex"] as? Number)?.toInt() ?: 0,
            )
        }.orEmpty()

        return CallSignal(
            callId = callId,
            callerUid = (children["caller"]?.value as? Map<*, *>)?.get("uid") as? String,
            callerName = (children["caller"]?.value as? Map<*, *>)?.get("displayName") as? String,
            calleeUid = (children["callee"]?.value as? Map<*, *>)?.get("uid") as? String,
            offer = sdp("offer"),
            answer = sdp("answer"),
            candidates = candidates,
            status = when (children["status"]?.value as? String) {
                "active" -> SignalingStatus.Active
                "ended" -> SignalingStatus.Ended
                "failed" -> SignalingStatus.Failed
                else -> SignalingStatus.Ringing
            },
            timestampMs = (children["timestamp"]?.value as? Number)?.toLong() ?: 0L,
        )
    }
}
