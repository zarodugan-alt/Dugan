package com.dugan.agent.ui.call

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dugan.agent.domain.model.AgentState
import com.dugan.agent.domain.model.CallState
import com.dugan.agent.domain.orchestrator.VoiceAgentOrchestrator
import com.dugan.agent.domain.telecom.CallController
import com.dugan.agent.domain.telecom.CallManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CallUiState(
    val call: CallState = CallState(),
    val agent: AgentState = AgentState(),
    val elapsedSeconds: Long = 0,
)

@HiltViewModel
class CallViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val callManager: CallManager,
    controller: CallController,
    orchestrator: VoiceAgentOrchestrator,
) : ViewModel() {

    val callId: String? = savedState.get<String>(CallActivity.EXTRA_CALL_ID)

    val ui: StateFlow<CallUiState> = combine(
        controller.state,
        orchestrator.state,
    ) { call, agent -> CallUiState(call = call, agent = agent) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallUiState())

    fun toggleMute() {
        val current = ui.value.call.isMuted
        callManager.mute(callId, !current)
    }

    fun toggleSpeaker() {
        callManager.setSpeakerphone(!ui.value.call.isSpeakerphoneOn)
    }

    fun toggleHold() = callManager.hold(callId)

    /** The "Let Agent Handle" switch. For SIM calls the call must be active first. */
    fun setAgentHandling(enabled: Boolean) = callManager.setAgentHandling(enabled)

    fun endCall() {
        callManager.endCall(callId)
        callManager.setAgentHandling(false)
    }
}
