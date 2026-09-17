package com.dugan.agent.domain.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.dugan.agent.data.repository.SettingsRepository
import com.dugan.agent.domain.model.CallTransport
import com.dugan.agent.domain.orchestrator.VoiceAgentOrchestrator
import com.dugan.agent.service.AgentForegroundService
import com.dugan.agent.ui.call.CallActivity
import com.dugan.agent.util.AgentLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What makes Dugan able to act as the phone's dialer UI.
 *
 * Bound by the system only while the app holds ROLE_DIALER. Every framework call
 * -- carrier or self-managed -- is reported here, which is why this class, not
 * [VoiceConnectionService], is the authoritative source for [CallController].
 */
@AndroidEntryPoint
class VoiceInCallService : InCallService() {

    @Inject
    lateinit var controller: CallController

    @Inject
    lateinit var callManager: CallManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var orchestrator: VoiceAgentOrchestrator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * `Call` exposes no public stable id, so identity-hash it. Stable for the
     * lifetime of the object, which is exactly as long as we need it.
     */
    private fun idOf(call: Call): String = System.identityHashCode(call).toString()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val id = idOf(call)
        AgentLog.i(TAG, "onCallAdded $id ${call.details?.handle}")

        controller.onCallAdded(id, call)
        call.registerCallback(stateCallback)
        callManager.onCallSetChanged()

        AgentForegroundService.start(this)
        launchCallScreen(id, call)
        maybeAutoAnswer(call)
    }

    /**
     * Auto-answers an incoming **VoIP** call after the configured delay, then hands
     * it to the agent with the configured greeting.
     *
     * Deliberately never fires for a SIM call: Android 9+ does not let a
     * third-party app answer a cellular call, so attempting it would either throw
     * or silently do nothing while the caller keeps ringing.
     */
    private fun maybeAutoAnswer(call: Call) {
        if (!call.isSelfManaged()) return
        if (call.state != Call.STATE_RINGING) return

        scope.launch {
            val settings = runCatching { settingsRepository.settings.first() }.getOrNull() ?: return@launch
            if (!settings.autoAnswerVoip) return@launch

            AgentLog.i(TAG, "auto-answering VoIP call in ${settings.answerDelaySeconds}s")
            delay(settings.answerDelayMs)

            // The call may have been answered or hung up while we waited.
            if (call.state != Call.STATE_RINGING) return@launch

            runCatching { call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY) }
                .onFailure { AgentLog.w(TAG, "auto-answer failed: ${it.message}") }
                .onSuccess {
                    controller.updateLocal { it.copy(transport = CallTransport.Voip, agentHandling = true) }
                    callManager.setAgentHandling(true)
                    orchestrator.speak(settings.greeting)
                }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        val id = idOf(call)
        AgentLog.i(TAG, "onCallRemoved $id")

        call.unregisterCallback(stateCallback)
        controller.onCallRemoved(id)
        callManager.onCallSetChanged()

        if (controller.activeCallCount == 0) {
            AgentForegroundService.stop(this)
        }
    }

    // InCallService's callback takes only the audio state, not the call.
    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        controller.updateLocal {
            it.copy(
                isMuted = audioState.isMuted,
                isSpeakerphoneOn = audioState.route == android.telecom.CallAudioState.ROUTE_SPEAKER,
            )
        }
    }

    private val stateCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            controller.onCallAdded(idOf(call), call)
            callManager.onCallSetChanged()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            controller.onCallAdded(idOf(call), call)
        }

    }

    /**
     * The in-call UI must come up on its own task and over the lockscreen: an
     * incoming call arrives with the screen off more often than not.
     */
    private fun launchCallScreen(id: String, call: Call) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra(CallActivity.EXTRA_CALL_ID, id)
            putExtra(CallActivity.EXTRA_HANDLE, call.details?.handle?.schemeSpecificPart)
            putExtra(CallActivity.EXTRA_DISPLAY_NAME, call.details?.contactDisplayName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(intent) }
            .onFailure { AgentLog.w(TAG, "could not launch call screen: ${it.message}") }
    }

    override fun onDestroy() {
        scope.cancel()
        controller.clear()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "InCallService"
    }
}
