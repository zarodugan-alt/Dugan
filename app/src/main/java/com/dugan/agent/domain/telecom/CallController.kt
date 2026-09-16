package com.dugan.agent.domain.telecom

import android.telecom.Call
import com.dugan.agent.domain.model.CallState
import com.dugan.agent.domain.model.CallTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "what call is on screen".
 *
 * [VoiceInCallService] pushes framework [Call] objects in here; the Compose UI
 * observes the resulting [CallState]. Keeping the framework type out of the UI
 * means the screens render identically for SIM and VoIP calls and in previews.
 */
@Singleton
class CallController @Inject constructor() {

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state.asStateFlow()

    /** Live framework calls, keyed by the telecom call id. */
    private val calls = LinkedHashMap<String, Call>()

    val activeCallCount: Int get() = calls.size

    fun onCallAdded(id: String, call: Call) {
        calls[id] = call
        syncFromFramework()
    }

    fun onCallRemoved(id: String) {
        calls.remove(id)
        syncFromFramework()
    }

    /** Merges non-framework state the UI owns (agent toggle, default-dialer flag). */
    fun updateLocal(transform: (CallState) -> CallState) {
        _state.update(transform)
    }

    private fun syncFromFramework() {
        val primary = calls.values.firstOrNull { it.state == Call.STATE_ACTIVE }
            ?: calls.values.firstOrNull()

        if (primary == null) {
            _state.update {
                it.copy(
                    transport = CallTransport.None,
                    handle = null,
                    displayName = null,
                    connectedAtMs = null,
                    isRinging = false,
                    agentHandling = false,
                    isOnHold = false,
                )
            }
            return
        }

        val details = primary.details
        _state.update { previous ->
            previous.copy(
                transport = if (primary.isSelfManaged()) CallTransport.Voip else CallTransport.Sim,
                handle = details?.handle?.schemeSpecificPart,
                displayName = details?.contactDisplayName ?: details?.handle?.schemeSpecificPart,
                connectedAtMs = if (primary.state == Call.STATE_ACTIVE) {
                    previous.connectedAtMs ?: System.currentTimeMillis()
                } else {
                    null
                },
                isRinging = primary.state == Call.STATE_RINGING,
                isMuted = primary.isMuted,
                isOnHold = primary.state == Call.STATE_HOLDING,
            )
        }
    }

    fun callById(id: String?): Call? = id?.let { calls[it] }

    fun primaryCall(): Call? = calls.values.firstOrNull { it.state == Call.STATE_ACTIVE }
        ?: calls.values.firstOrNull()

    fun clear() {
        calls.clear()
        _state.value = CallState()
    }
}

/**
 * Self-managed connections are the app's own VoIP calls; everything else came
 * through the carrier and is subject to the SIM restrictions.
 */
internal fun Call.isSelfManaged(): Boolean = runCatching {
    details.hasProperty(android.telecom.Connection.PROPERTY_SELF_MANAGED)
}.getOrDefault(false)
