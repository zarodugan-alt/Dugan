package com.dugan.agent.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dugan.agent.data.api.KeyVerifier
import com.dugan.agent.data.repository.KeyRepository
import com.dugan.agent.data.repository.SettingsRepository
import com.dugan.agent.domain.model.ApiProvider
import com.dugan.agent.domain.model.KeyTestResult
import com.dugan.agent.domain.model.KeyVerifyScheduler
import com.dugan.agent.domain.model.keyAdvisory
import com.dugan.agent.domain.model.sanitizeKey
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
    private val keyVerifier: KeyVerifier,
) : ViewModel() {

    /** 0 = keys, 1 = permissions, 2 = theme. */
    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _keys = MutableStateFlow(ApiProvider.entries.associate { it.id to "" })
    val keys: StateFlow<Map<String, String>> = _keys.asStateFlow()

    // Explicit type argument: seeded with Untested, so without it the flow
    // infers Map<String, KeyTestResult.Untested> and rejects the other subtypes.
    private val _results: MutableStateFlow<Map<String, KeyTestResult>> =
        MutableStateFlow(ApiProvider.entries.associate { it.id to KeyTestResult.Untested })
    val results: StateFlow<Map<String, KeyTestResult>> = _results.asStateFlow()

    /** Shape observations, one per provider. Advisory only — the probe decides. */
    private val _advisories: MutableStateFlow<Map<String, String?>> =
        MutableStateFlow(ApiProvider.entries.associate { it.id to (null as String?) })
    val advisories: StateFlow<Map<String, String?>> = _advisories.asStateFlow()

    private val _themeId = MutableStateFlow(AppTheme.CrimsonNoir.id)
    val themeId: StateFlow<String> = _themeId.asStateFlow()

    /** Permissions the user has already skipped; non-critical ones do not block. */
    private val _skippedPermissions = MutableStateFlow<Set<String>>(emptySet())
    val skippedPermissions: StateFlow<Set<String>> = _skippedPermissions.asStateFlow()

    val allKeysValid: Boolean
        get() = ApiProvider.entries.all { results.value[it.id] is KeyTestResult.Valid }

    /**
     * Verify-on-paste.
     *
     * Paste a key and it is sent to its provider as soon as the field goes
     * quiet; the answer comes back from the provider itself, not from a guess
     * about what a key should look like. A key that comes back reachable is
     * stored straight away, so the wizard advances on proof rather than on the
     * user finding the right button — and a key that fails is never written to
     * the vault at all.
     */
    private val verifyScheduler = KeyVerifyScheduler(
        scope = viewModelScope,
        onState = { provider, key, result ->
            _results.update { it + (provider.id to result) }
            if (result.isOk) keyRepository.save(provider, key)
        },
        verify = { provider, key -> keyVerifier.verify(provider, key) },
    )

    fun onKeyChange(provider: ApiProvider, value: String) {
        val clean = sanitizeKey(value)
        _keys.update { it + (provider.id to clean) }
        _advisories.update { it + (provider.id to keyAdvisory(provider, clean)) }
        verifyScheduler.submit(provider, clean)
    }

    /** Explicit re-check, for when the user wants the answer immediately. */
    fun test(provider: ApiProvider) {
        val value = sanitizeKey(keys.value[provider.id].orEmpty())
        if (value.isBlank()) {
            _results.update { it + (provider.id to KeyTestResult.Invalid("No key to test")) }
            return
        }
        verifyScheduler.submitNow(provider, value)
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
