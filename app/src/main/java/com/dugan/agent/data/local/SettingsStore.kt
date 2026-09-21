package com.dugan.agent.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dugan.agent.domain.model.DuganSettings
import com.dugan.agent.domain.model.InputSource
import com.dugan.agent.domain.model.ListeningMode
import com.dugan.agent.domain.model.ThinkingLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "dugan_settings")

/**
 * Plain (non-sensitive) settings. Anything secret goes in [EncryptedKeyVault].
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<DuganSettings> = context.settingsDataStore.data.map { p ->
        DuganSettings(
            sttModelId = p[K.sttModel] ?: DuganSettings().sttModelId,
            ttsModelId = p[K.ttsModel] ?: DuganSettings().ttsModelId,
            thinkingModelId = p[K.thinkingModel] ?: DuganSettings().thinkingModelId,
            thinkingLevel = p[K.thinkingLevel]?.let { runCatching { ThinkingLevel.valueOf(it) }.getOrNull() }
                ?: ThinkingLevel.Default,
            listeningMode = p[K.listeningMode]?.let { runCatching { ListeningMode.valueOf(it) }.getOrNull() }
                ?: ListeningMode.PushToTalk,
            autoAnswerVoip = p[K.autoAnswerVoip] ?: false,
            answerDelaySeconds = p[K.answerDelay] ?: 3,
            greeting = p[K.greeting] ?: DuganSettings().greeting,
            inputSource = p[K.inputSource]?.let { runCatching { InputSource.valueOf(it) }.getOrNull() }
                ?: InputSource.Auto,
            vadSensitivity = p[K.vadSensitivity] ?: 0.5f,
            silenceThresholdMs = p[K.silenceThreshold] ?: 700,
            echoSuppression = p[K.echoSuppression] ?: 1.0f,
            playbackSpeed = p[K.playbackSpeed] ?: 0f,
            ttsVoiceId = p[K.ttsVoice] ?: DuganSettings().ttsVoiceId,
            streamingStt = p[K.streamingStt] ?: true,
            speculativeLlm = p[K.speculativeLlm] ?: true,
            ttsCaching = p[K.ttsCaching] ?: true,
            prefixCaching = p[K.prefixCaching] ?: true,
            modelRouting = p[K.modelRouting] ?: true,
            softwareAecEnabled = p[K.softwareAec] ?: true,
            micGatingEnabled = p[K.micGating] ?: true,
            bargeInEnabled = p[K.bargeIn] ?: true,
            textEchoDefenseEnabled = p[K.textEchoDefense] ?: true,
            themeId = p[K.theme] ?: "crimson_noir",
            dynamicColor = p[K.dynamicColor] ?: false,
            onboardingCompleted = p[K.onboardingDone] ?: false,
        )
    }

    suspend fun update(transform: (DuganSettings) -> DuganSettings) {
        val current = settings.first()
        val next = transform(current)
        context.settingsDataStore.edit { p ->
            p[K.sttModel] = next.sttModelId
            p[K.ttsModel] = next.ttsModelId
            p[K.thinkingModel] = next.thinkingModelId
            p[K.thinkingLevel] = next.thinkingLevel.name
            p[K.listeningMode] = next.listeningMode.name
            p[K.autoAnswerVoip] = next.autoAnswerVoip
            p[K.answerDelay] = next.answerDelaySeconds
            p[K.greeting] = next.greeting
            p[K.inputSource] = next.inputSource.name
            p[K.vadSensitivity] = next.vadSensitivity
            p[K.silenceThreshold] = next.silenceThresholdMs
            p[K.echoSuppression] = next.echoSuppression
            p[K.playbackSpeed] = next.playbackSpeed
            p[K.ttsVoice] = next.ttsVoiceId
            p[K.streamingStt] = next.streamingStt
            p[K.speculativeLlm] = next.speculativeLlm
            p[K.ttsCaching] = next.ttsCaching
            p[K.prefixCaching] = next.prefixCaching
            p[K.modelRouting] = next.modelRouting
            p[K.softwareAec] = next.softwareAecEnabled
            p[K.micGating] = next.micGatingEnabled
            p[K.bargeIn] = next.bargeInEnabled
            p[K.textEchoDefense] = next.textEchoDefenseEnabled
            p[K.theme] = next.themeId
            p[K.dynamicColor] = next.dynamicColor
            p[K.onboardingDone] = next.onboardingCompleted
        }
    }

    private object K {
        val sttModel = stringPreferencesKey("stt_model")
        val ttsModel = stringPreferencesKey("tts_model")
        val thinkingModel = stringPreferencesKey("thinking_model")
        val thinkingLevel = stringPreferencesKey("thinking_level")
        val listeningMode = stringPreferencesKey("listening_mode")
        val autoAnswerVoip = booleanPreferencesKey("auto_answer_voip")
        val answerDelay = intPreferencesKey("answer_delay_seconds")
        val greeting = stringPreferencesKey("greeting")
        val inputSource = stringPreferencesKey("input_source")
        val vadSensitivity = floatPreferencesKey("vad_sensitivity")
        val silenceThreshold = intPreferencesKey("silence_threshold_ms")
        val echoSuppression = floatPreferencesKey("echo_suppression")
        val playbackSpeed = floatPreferencesKey("playback_speed")
        val ttsVoice = stringPreferencesKey("tts_voice")
        val streamingStt = booleanPreferencesKey("streaming_stt")
        val speculativeLlm = booleanPreferencesKey("speculative_llm")
        val ttsCaching = booleanPreferencesKey("tts_caching")
        val prefixCaching = booleanPreferencesKey("prefix_caching")
        val modelRouting = booleanPreferencesKey("model_routing")
        val softwareAec = booleanPreferencesKey("software_aec")
        val micGating = booleanPreferencesKey("mic_gating")
        val bargeIn = booleanPreferencesKey("barge_in")
        val textEchoDefense = booleanPreferencesKey("text_echo_defense")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val onboardingDone = booleanPreferencesKey("onboarding_completed")
    }
}
