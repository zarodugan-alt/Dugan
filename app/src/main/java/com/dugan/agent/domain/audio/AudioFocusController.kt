package com.dugan.agent.domain.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.dugan.agent.util.AgentLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio focus bookkeeping.
 *
 * Without this the agent will happily talk over a navigation prompt or an
 * incoming ring, and -- worse -- will keep talking after the user has answered a
 * real call. On transient loss we pause; on regain we resume.
 */
@Singleton
class AudioFocusController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    var onTransientLoss: () -> Unit = {}

    @Volatile
    var onRegain: () -> Unit = {}

    @Volatile
    private var held = false

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                AgentLog.i(TAG, "audio focus lost permanently")
                held = false
                onTransientLoss()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                AgentLog.i(TAG, "audio focus lost transiently")
                onTransientLoss()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                AgentLog.i(TAG, "audio focus regained")
                onRegain()
            }
        }
    }

    private val request by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(listener, handler)
            .build()
    }

    fun request(): Boolean {
        val result = runCatching { audioManager.requestAudioFocus(request) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!held) AgentLog.w(TAG, "audio focus denied; speaking anyway at the risk of talking over the user")
        return held
    }

    fun abandon() {
        if (!held) return
        runCatching { audioManager.abandonAudioFocusRequest(request) }
        held = false
    }

    val isHeld: Boolean get() = held

    private companion object {
        const val TAG = "AudioFocus"
    }
}
