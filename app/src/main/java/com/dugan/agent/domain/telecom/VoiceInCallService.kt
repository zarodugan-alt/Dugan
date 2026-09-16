package com.dugan.agent.domain.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.dugan.agent.service.AgentForegroundService
import com.dugan.agent.ui.call.CallActivity
import dagger.hilt.android.AndroidEntryPoint
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

    /**
     * `Call` exposes no public stable id, so identity-hash it. Stable for the
     * lifetime of the object, which is exactly as long as we need it.
     */
    private fun idOf(call: Call): String = System.identityHashCode(call).toString()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val id = idOf(call)
        Log.i(TAG, "onCallAdded $id ${call.details?.handle}")

        controller.onCallAdded(id, call)
        call.registerCallback(stateCallback)
        callManager.onCallSetChanged()

        AgentForegroundService.start(this)
        launchCallScreen(id, call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        val id = idOf(call)
        Log.i(TAG, "onCallRemoved $id")

        call.unregisterCallback(stateCallback)
        controller.onCallRemoved(id)
        callManager.onCallSetChanged()

        if (controller.activeCallCount == 0) {
            AgentForegroundService.stop(this)
        }
    }

    override fun onCallAudioStateChanged(call: Call, audioState: android.telecom.CallAudioState) {
        super.onCallAudioStateChanged(call, audioState)
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

        override fun onDisconnected(call: Call, disconnectCause: android.telecom.DisconnectCause) {
            controller.onCallRemoved(idOf(call))
            callManager.onCallSetChanged()
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
            .onFailure { Log.w(TAG, "could not launch call screen: ${it.message}") }
    }

    override fun onDestroy() {
        controller.clear()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "InCallService"
    }
}
