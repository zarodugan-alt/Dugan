package com.dugan.agent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dugan.agent.data.api.AgentLlm
import com.dugan.agent.data.api.AgentTts
import com.dugan.agent.data.api.GroqSttClient
import com.dugan.agent.data.local.KeyVault
import com.dugan.agent.data.local.TtsCache
import com.dugan.agent.data.repository.KeyRepository
import com.dugan.agent.data.repository.SettingsRepository
import com.dugan.agent.domain.audio.AecStatus
import com.dugan.agent.domain.model.AgentModel
import com.dugan.agent.domain.model.ApiProvider
import com.dugan.agent.domain.model.DuganSettings
import com.dugan.agent.domain.model.KeyTestResult
import com.dugan.agent.domain.model.ModelCatalog
import com.dugan.agent.domain.orchestrator.VoiceAgentOrchestrator
import com.dugan.agent.domain.telecom.PhoneAccountRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val keyRepository: KeyRepository,
    private val settingsRepository: SettingsRepository,
    private val stt: GroqSttClient,
    private val llm: AgentLlm,
    private val tts: AgentTts,
    private val orchestrator: VoiceAgentOrchestrator,
    private val registrar: PhoneAccountRegistrar,
    private val vault: KeyVault,
    private val ttsCache: TtsCache,
) : ViewModel() {

    val settings: StateFlow<DuganSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuganSettings())

    /** Draft key text per provider, kept out of the vault until saved. */
    private val _drafts = MutableStateFlow(Map<String, String>())
    val drafts: StateFlow<Map<String, String>> = _drafts.asStateFlow()

    private val _testResults = MutableStateFlow(Map<String, KeyTestResult>())
    val testResults: StateFlow<Map<String, KeyTestResult>> = _testResults.asStateFlow()

    private val _aecStatus = MutableStateFlow(AecStatus())
    val aecStatus: StateFlow<AecStatus> = _aecStatus.asStateFlow()

    val isDefaultDialer: Boolean get() = registrar.isDefaultDialer()

    init {
        refreshAecStatus()
        // Seed drafts with masked placeholders so the fields are not blank for a
        // user who has already configured keys.
        _drafts.value = ApiProvider.entries.associate { it.id to "" }
    }

    fun draftFor(provider: ApiProvider): String = _drafts.value[provider.id].orEmpty()

    fun storedMask(provider: ApiProvider): String = keyRepository.displayKey(provider)

    fun isConfigured(provider: ApiProvider): Boolean = keyRepository.isConfigured(provider)

    fun onDraftChange(provider: ApiProvider, value: String) {
        _drafts.update { it + (provider.id to value) }
        _testResults.update { it + (provider.id to KeyTestResult.Untested) }
    }

    /**
     * Smallest valid request per provider. A valid key returns 200; an invalid one
     * returns 401, which is what the badge shows.
     */
    fun testKey(provider: ApiProvider) {
        val key = _drafts.value[provider.id].orEmpty().ifBlank { vault.read(provider).orEmpty() }
        if (key.isBlank()) {
            _testResults.update { it + (provider.id to KeyTestResult.Invalid("No key to test")) }
            return
        }
        _testResults.update { it + (provider.id to KeyTestResult.Testing) }

        // Save first so the client picks the key up from the vault.
        val saved = keyRepository.save(provider, key)
        if (!saved) {
            val reason = keyRepository.validate(provider, key) ?: "Key rejected"
            _testResults.update { it + (provider.id to KeyTestResult.Invalid(reason)) }
            return
        }

        viewModelScope.launch {
            val result = when (provider) {
                ApiProvider.Groq -> stt.ping(ModelCatalog.DefaultStt)
                ApiProvider.Gemini -> llm.ping(ModelCatalog.DefaultThinking)
                ApiProvider.UnrealSpeech -> tts.ping(ModelCatalog.DefaultTts)
            }
            _testResults.update {
                it + (
                    provider.id to result.fold(
                        onSuccess = { KeyTestResult.Valid("Reachable") },
                        onFailure = { t -> KeyTestResult.Invalid(t.message?.take(160) ?: "Request failed") },
                    )
                    )
            }
            refreshAecStatus()
        }
    }

    fun update(transform: (DuganSettings) -> DuganSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
        refreshAecStatus()
    }

    fun resetAllKeys() {
        keyRepository.clearAll()
        _drafts.value = ApiProvider.entries.associate { it.id to "" }
        _testResults.value = emptyMap()
        ttsCache.clear()
    }

    fun clearCache() {
        ttsCache.clear()
    }

    fun refreshAecStatus() {
        _aecStatus.value = orchestrator.aecStatus()
    }

    val sttModels: List<AgentModel> = ModelCatalog.SttModels
    val ttsModels: List<AgentModel> = ModelCatalog.TtsModels
    val thinkingModels: List<AgentModel> = ModelCatalog.ThinkingModels
}
