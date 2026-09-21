package com.dugan.agent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dugan.agent.data.api.KeyVerifier
import com.dugan.agent.data.local.KeyVault
import com.dugan.agent.data.local.TtsCache
import com.dugan.agent.data.repository.KeyRepository
import com.dugan.agent.data.repository.SettingsRepository
import com.dugan.agent.domain.audio.AecStatus
import com.dugan.agent.domain.model.AgentModel
import com.dugan.agent.domain.model.ApiProvider
import com.dugan.agent.domain.model.DuganSettings
import com.dugan.agent.domain.model.KeyTestResult
import com.dugan.agent.domain.model.KeyVerifyScheduler
import com.dugan.agent.domain.model.ModelCatalog
import com.dugan.agent.domain.model.keyAdvisory
import com.dugan.agent.domain.model.maskKey
import com.dugan.agent.domain.model.sanitizeKey
import com.dugan.agent.domain.orchestrator.VoiceAgentOrchestrator
import com.dugan.agent.domain.telecom.PhoneAccountRegistrar
import com.dugan.agent.util.CrashLog
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
    /** Shape observation about [draft]. Advisory only — the probe decides. */
    val advisory: String? = null,
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
    private val keyVerifier: KeyVerifier,
    private val orchestrator: VoiceAgentOrchestrator,
    private val registrar: PhoneAccountRegistrar,
    private val vault: KeyVault,
    private val ttsCache: TtsCache,
) : ViewModel() {

    val settings: StateFlow<DuganSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuganSettings())

    private val _rows = MutableStateFlow(KeyRow.emptyRows())
    val rows: StateFlow<Map<String, KeyRow>> = _rows.asStateFlow()

    /**
     * Verify-on-paste. A pasted or typed key is sent to its provider once the
     * field has been quiet for a moment; the response — not the shape of the
     * key — is what the badge shows.
     */
    private val verifyScheduler = KeyVerifyScheduler(
        scope = viewModelScope,
        onState = { provider, _, result ->
            _rows.update { it + (provider.id to row(provider).copy(result = result)) }
            refreshAecStatus()
        },
        verify = { provider, key -> keyVerifier.verify(provider, key) },
    )

    private val _aecStatus = MutableStateFlow(AecStatus())
    val aecStatus: StateFlow<AecStatus> = _aecStatus.asStateFlow()

    /** Live count so the Agent screen banner can react without polling. */
    val configuredCount: StateFlow<Int> = keyRepository.keys
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val isDefaultDialer: Boolean get() = registrar.isDefaultDialer()

    /**
     * The most recent uncaught exception, captured by [CrashLog] in the
     * Application. Null when the app has never crashed.
     *
     * A crash normally takes its logcat with it; this is what makes one
     * diagnosable from the device itself, with no `adb` attached.
     */
    private val _lastCrash = MutableStateFlow(CrashLog.last())
    val lastCrash: StateFlow<String?> = _lastCrash.asStateFlow()

    /** Drops the stored crash record once it has been read. */
    fun clearCrashLog() {
        CrashLog.clear()
        _lastCrash.value = null
    }

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

    /**
     * Called on every keystroke and on paste. The key is cleaned, any shape
     * observation is shown next to the field, and a live check is queued.
     */
    fun onDraftChange(provider: ApiProvider, value: String) {
        val clean = sanitizeKey(value)
        _rows.update {
            it + (provider.id to row(provider).copy(draft = clean, advisory = keyAdvisory(provider, clean)))
        }
        verifyScheduler.submit(provider, clean)
    }

    /**
     * Local-only write to the encrypted vault. Works offline, spends no quota, and
     * is the only thing that has to succeed for the agent to run.
     */
    fun saveKey(provider: ApiProvider) {
        val draft = sanitizeKey(row(provider).draft)
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
        verifyScheduler.forget(provider)
        _rows.update {
            it + (provider.id to KeyRow(storedMask = null, result = KeyTestResult.Untested))
        }
    }

    /**
     * Explicit re-check, for when the user wants the answer now rather than
     * after the paste settles.
     *
     * Unlike the old implementation this does *not* write the vault first: the
     * probe takes the candidate key directly, so testing a bad key can never
     * displace a working one. Saving stays a separate, deliberate act.
     */
    fun testKey(provider: ApiProvider) {
        val draft = sanitizeKey(row(provider).draft).ifBlank { vault.read(provider).orEmpty() }
        if (draft.isBlank()) {
            _rows.update {
                it + (provider.id to row(provider).copy(result = KeyTestResult.Invalid("No key to test")))
            }
            return
        }
        verifyScheduler.submitNow(provider, draft)
    }

    fun update(transform: (DuganSettings) -> DuganSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
        refreshAecStatus()
    }

    fun resetAllKeys() {
        keyRepository.clearAll()
        ttsCache.clear()
        verifyScheduler.cancelAll()
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
