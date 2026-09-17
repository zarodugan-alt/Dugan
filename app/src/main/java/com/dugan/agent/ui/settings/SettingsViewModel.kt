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
import com.dugan.agent.domain.model.maskKey
import com.dugan.agent.domain.orchestrator.VoiceAgentOrchestrator
import com.dugan.agent.domain.telecom.PhoneAccountRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Per-provider key state for one [com.dugan.agent.ui.components.KeyField]. */
data class KeyRow(
    val draft: String = "",
    val storedMask: String? = null,
    val result: KeyTestResult = KeyTestResult.Untested,
) {
    /** Save is enabled only when the draft is a real change from what is stored. */
    val isDirty: Boolean get() = draft.isNotBlank() && draft.trim() != storedMask

    companion object {
        fun emptyRows(): Map<String, KeyRow> =
            ApiProvider.entries.associate { it.id to KeyRow() }
    }
}

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

    private val _rows = MutableStateFlow(KeyRow.emptyRows())
    val rows: StateFlow<Map<String, KeyRow>> = _rows.asStateFlow()

    private val _aecStatus = MutableStateFlow(AecStatus())
    val aecStatus: StateFlow<AecStatus> = _aecStatus.asStateFlow()

    /** Live count so the Agent screen banner can react without polling. */
    val configuredCount: StateFlow<Int> = keyRepository.keys
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val isDefaultDialer: Boolean get() = registrar.isDefaultDialer()

    /** Intent for the system role prompt, or null when the role is already held. */
    fun requestDialerRoleIntent(): android.content.Intent? = registrar.requestDefaultDialerIntent()

    init {
        refreshAecStatus()
        syncStoredMasks()
    }

    private fun syncStoredMasks() {
        _rows.update { current ->
            current.mapValues { (id, row) ->
                val provider = ApiProvider.fromId(id) ?: return@mapValues row
                row.copy(storedMask = vault.read(provider)?.let(::maskKey))
            }
        }
    }

    fun row(provider: ApiProvider): KeyRow =
        _rows.value[provider.id] ?: KeyRow()

    fun onDraftChange(provider: ApiProvider, value: String) {
        _rows.update { it + (provider.id to row(provider).copy(draft = value, result = KeyTestResult.Untested)) }
    }

    /**
     * Local-only write to the encrypted vault. Works offline, spends no quota, and
     * is the only thing that has to succeed for the agent to run.
     */
    fun saveKey(provider: ApiProvider) {
        val draft = row(provider).draft.trim()
        val problem = keyRepository.validate(provider, draft)
        if (problem != null) {
            _rows.update {
                it + (provider.id to row(provider).copy(result = KeyTestResult.Invalid(problem)))
            }
            return
        }
        if (keyRepository.save(provider, draft)) {
            _rows.update {
                it + (
                    provider.id to row(provider).copy(
                        // Clearing the draft puts the field back to its masked state
                        // and stops it looking like there is an unsaved change.
                        draft = "",
                        storedMask = maskKey(draft),
                        result = KeyTestResult.Untested,
                    )
                    )
            }
        }
    }

    fun clearKey(provider: ApiProvider) {
        keyRepository.clear(provider)
        ttsCache.clear()
        _rows.update {
            it + (provider.id to KeyRow(storedMask = null, result = KeyTestResult.Untested))
        }
    }

    /**
     * Smallest valid request per provider. Saves first so the client under test
     * reads the candidate key from the vault rather than the previous one.
     */
    fun testKey(provider: ApiProvider) {
        val draft = row(provider).draft.trim().ifBlank { vault.read(provider).orEmpty() }
        if (draft.isBlank()) {
            _rows.update {
                it + (provider.id to row(provider).copy(result = KeyTestResult.Invalid("No key to test")))
            }
            return
        }
        val problem = keyRepository.validate(provider, draft)
        if (problem != null) {
            _rows.update {
                it + (provider.id to row(provider).copy(result = KeyTestResult.Invalid(problem)))
            }
            return
        }

        _rows.update { it + (provider.id to row(provider).copy(result = KeyTestResult.Testing)) }
        keyRepository.save(provider, draft)

        viewModelScope.launch {
            val outcome = when (provider) {
                ApiProvider.Groq -> stt.ping(ModelCatalog.DefaultStt)
                ApiProvider.Gemini -> llm.ping(ModelCatalog.DefaultThinking)
                ApiProvider.UnrealSpeech -> tts.ping(ModelCatalog.DefaultTts)
            }
            _rows.update {
                it + (
                    provider.id to row(provider).copy(
                        draft = "",
                        storedMask = maskKey(draft),
                        result = outcome.fold(
                            onSuccess = { KeyTestResult.Valid("Reachable") },
                            onFailure = { t ->
                                KeyTestResult.Invalid(t.message?.take(160) ?: "Request failed")
                            },
                        ),
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
        ttsCache.clear()
        _rows.value = KeyRow.emptyRows()
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
