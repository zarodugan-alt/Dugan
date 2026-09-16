package com.dugan.agent.domain.telecom

import com.dugan.agent.util.AgentLog
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.telecom.TelecomManager
import com.dugan.agent.data.repository.ContactRepository
import com.dugan.agent.domain.command.VoiceCommandHandler
import com.dugan.agent.domain.command.VoiceCommandParser
import com.dugan.agent.domain.model.AgentCommand
import com.dugan.agent.domain.model.CallState
import com.dugan.agent.domain.model.CallTransport
import com.dugan.agent.domain.orchestrator.VoiceAgentOrchestrator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges Telecom, the dialer UI and the agent loop.
 *
 * Also the [VoiceCommandHandler] for "call John": it resolves the name, speaks a
 * confirmation, and only dials on an explicit yes. A dial is a side effect, so
 * it is never speculated on and never routed to a small model.
 */
@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val controller: CallController,
    private val registrar: PhoneAccountRegistrar,
    private val contacts: ContactRepository,
    private val parser: VoiceCommandParser,
    private val orchestrator: VoiceAgentOrchestrator,
) : VoiceCommandHandler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Awaiting a yes/no on this dial. Cleared on answer, refusal or timeout. */
    @Volatile
    private var pendingDial: String? = null

    init {
        orchestrator.setVoiceCommandHandler(this)
        scope.launch {
            controller.state.collect { orchestrator.setCallState(it) }
        }
    }

    /** Called from [VoiceInCallService] whenever the framework call set changes. */
    fun onCallSetChanged() {
        val snapshot = controller.state.value
        controller.updateLocal { it.copy(isDefaultDialer = registrar.isDefaultDialer()) }
        // A real SIM call always wins: stop the agent rather than talk over it.
        if (snapshot.transport == CallTransport.Sim && snapshot.hasCall) {
            orchestrator.dispatch(AgentCommand.Stop)
        }
    }

    // -- Dialling ------------------------------------------------------------

    fun placeCall(number: String) {
        val normalized = normalize(number)
        if (normalized.isEmpty()) return
        val uri = Uri.fromParts("tel", normalized, null)
        runCatching {
            val intent = Intent(Intent.ACTION_CALL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            AgentLog.w(TAG, "placeCall failed: ${it.message}")
            // Fall back to the system dialer, which never needs CALL_PHONE.
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** Self-managed VoIP call through our own ConnectionService. */
    fun placeVoipCall(number: String, callId: String) {
        val uri = Uri.fromParts("tel", normalize(number), null)
        val extras = android.os.Bundle().apply {
            putString(EXTRA_CALL_ID, callId)
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, registrar.accountHandle)
        }
        runCatching {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            // addNewOutgoingCall is @SystemApi; placeCall is the public equivalent.
            telecom.placeCall(uri, extras)
        }.onFailure { AgentLog.w(TAG, "placeVoipCall failed: ${it.message}") }
    }

    fun answer(callId: String?) {
        controller.callById(callId)?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
    }

    fun endCall(callId: String?) {
        controller.callById(callId)?.disconnect()
    }

    fun hold(callId: String?) {
        controller.callById(callId)?.hold()
    }

    fun mute(callId: String?, muted: Boolean) {
        controller.callById(callId)?.let {
            if (muted) it.playDtmfTone(' ') else it.playDtmfTone(' ')
        }
        controller.updateLocal { it.copy(isMuted = muted) }
        audioManager.isMicrophoneMute = muted
    }

    /** SIM capture only works on the loudspeaker, so the toggle also gates the agent. */
    fun setSpeakerphone(on: Boolean) {
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = on
        }
        controller.updateLocal { it.copy(isSpeakerphoneOn = on) }
    }

    /** The "Let Agent Handle" switch on the call screen. */
    fun setAgentHandling(enabled: Boolean) {
        controller.updateLocal { it.copy(agentHandling = enabled) }
        if (enabled) {
            orchestrator.dispatch(AgentCommand.Start)
        } else {
            orchestrator.dispatch(AgentCommand.Stop)
        }
    }

    // -- Voice-command dialling ---------------------------------------------

    override suspend fun handle(transcript: String): Boolean {
        // Confirmation of an already-proposed dial takes priority over parsing.
        pendingDial?.let { proposed ->
            when {
                parser.isAffirmative(transcript) -> {
                    pendingDial = null
                    orchestrator.speak("Dialling now.")
                    placeCall(proposed)
                    return true
                }
                parser.isNegative(transcript) -> {
                    pendingDial = null
                    orchestrator.speak("Okay, cancelled.")
                    return true
                }
            }
        }

        val intent = parser.parse(transcript) ?: return false

        // A spoken number dials directly -- no contact to mis-resolve.
        parser.parseNumber(intent.contactQuery)?.let { digits ->
            pendingDial = digits
            orchestrator.speak("Dialling $digits. Is that right?")
            return true
        }

        val contact = contacts.resolveByName(intent.contactQuery)
            ?: contacts.contacts().let { all ->
                parser.fuzzyMatch(intent.contactQuery, all.map { it.displayName })
                    ?.let { name -> all.firstOrNull { it.displayName == name } }
            }

        if (contact == null) {
            AgentLog.i(TAG, "no contact matched '${intent.contactQuery}'")
            orchestrator.speak("I could not find ${intent.contactQuery} in your contacts.")
            // Return true so the LLM does not also answer; the prompt was the reply.
            return true
        }

        pendingDial = contact.number
        // Confirm out loud before dialling. A dial is a side effect, so it is never
        // fired on the first pass -- only after an explicit affirmative.
        orchestrator.speak("Calling ${contact.displayName}. Is that right?")
        return true
    }

    /** Number the UI should read back to the user before dialling. */
    fun consumePendingDial(): String? = pendingDial.also { pendingDial = null }

    fun hasPendingDial(): Boolean = pendingDial != null

    private fun normalize(number: String): String =
        number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    companion object {
        const val EXTRA_CALL_ID = "com.dugan.agent.CALL_ID"
        private const val TAG = "CallManager"
    }
}
