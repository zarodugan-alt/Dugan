package com.dugan.agent.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dugan.agent.data.api.AgentLlm
import com.dugan.agent.data.api.AgentTts
import com.dugan.agent.data.api.GroqSttClient
import com.dugan.agent.data.repository.KeyRepository
import com.dugan.agent.data.repository.SettingsRepository
import com.dugan.agent.domain.model.ApiProvider
import com.dugan.agent.domain.model.KeyTestResult
import com.dugan.agent.domain.model.ModelCatalog
import com.dugan.agent.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val keyRepository: KeyRepository,
    private val settingsRepository: SettingsRepository,
    private val stt: GroqSttClient,
    private val llm: AgentLlm,
    private val tts: AgentTts,
) : ViewModel() {

    /** 0 = keys, 1 = permissions, 2 = theme. */
    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _keys = MutableStateFlow(ApiProvider.entries.associate { it.id to "" })
    val keys: StateFlow<Map<String, String>> = _keys.asStateFlow()

    // Explicit type argument: seeded with Untested, so without it the flow infers
    Map<String, KeyTestResult.Untested> and rejects Invalid/Testing/Valid.
    private val _results: MutableStateFlow<Map<String, KeyTestResult>> =
        MutableStateFlow(ApiProvider.entries.associate { it.id to KeyTestResult.Untested })
    val results: StateFlow<Map<String, KeyTestResult>> = _results.asStateFlow()

    private val _themeId = MutableStateFlow(AppTheme.CrimsonNoir.id)
    val themeId: StateFlow<String> = _themeId.asStateFlow()

    /** Permissions the user has already skipped; non-critical ones do not block. */
    private val _skippedPermissions = MutableStateFlow<Set<String>>(emptySet())
    val skippedPermissions: StateFlow<Set<String>> = _skippedPermissions.asStateFlow()

    val allKeysValid: Boolean
        get() = ApiProvider.entries.all { results.value[it.id] is KeyTestResult.Valid }

    fun onKeyChange(provider: ApiProvider, value: String) {
        _keys.update { it + (provider.id to value) }
        _results.update { r -> r + (provider.id to KeyTestResult.Untested) }
    }

    fun test(provider: ApiProvider) {
        val value = keys.value[provider.id].orEmpty()
        val invalid = keyRepository.validate(provider, value)
        if (invalid != null) {
            _results.update { it + (provider.id to KeyTestResult.Invalid(invalid)) }
            return
        }
        _results.update { it + (provider.id to KeyTestResult.Testing) }
        keyRepository.save(provider, value)

        viewModelScope.launch {
            val outcome = when (provider) {
                ApiProvider.Groq -> stt.ping(ModelCatalog.DefaultStt)
                ApiProvider.Gemini -> llm.ping(ModelCatalog.DefaultThinking)
                ApiProvider.UnrealSpeech -> tts.ping(ModelCatalog.DefaultTts)
            }
            _results.update {
                it + (
                    provider.id to outcome.fold(
                        onSuccess = { KeyTestResult.Valid("Reachable") },
                        onFailure = { t -> KeyTestResult.Invalid(t.message?.take(160) ?: "Request failed") },
                    )
                    )
            }
        }
    }

    fun skipPermission(permission: String) {
        _skippedPermissions.update { it + permission }
    }

    fun selectTheme(theme: AppTheme) {
        _themeId.value = theme.id
    }

    fun next() {
        _step.update { (it + 1).coerceAtMost(2) }
    }

    fun back() {
        _step.update { (it - 1).coerceAtLeast(0) }
    }

    fun finish() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(themeId = _themeId.value, onboardingCompleted = true) }
        }
    }
}
