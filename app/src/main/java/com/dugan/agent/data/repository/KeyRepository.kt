package com.dugan.agent.data.repository

import com.dugan.agent.data.local.KeyVault
import com.dugan.agent.domain.model.ApiProvider
import com.dugan.agent.domain.model.keyProblem
import com.dugan.agent.domain.model.maskKey
import com.dugan.agent.domain.model.sanitizeKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing wrapper that never lets a raw key escape into a log or a message. */
@Singleton
class KeyRepository @Inject constructor(
    private val vault: KeyVault,
) {
    val keys: Flow<Map<String, String>> get() = vault.keys

    fun isConfigured(provider: ApiProvider): Boolean = vault.read(provider) != null

    fun allConfigured(): Boolean = vault.hasAllRequired()

    /**
     * @return a human-readable reason when [key] cannot be a [provider] key,
     *   else null.
     *
     * An unrecognised prefix is deliberately *not* a failure. The Test probe is
     * what decides whether a key is live, so a provider re-branding its keys
     * never locks a user out of their own account.
     */
    fun validate(provider: ApiProvider, key: String): String? = keyProblem(provider, sanitizeKey(key))

    fun save(provider: ApiProvider, key: String): Boolean {
        val cleaned = sanitizeKey(key)
        if (keyProblem(provider, cleaned) != null) return false
        vault.write(provider, cleaned)
        return true
    }

    fun clear(provider: ApiProvider) = vault.clear(provider)

    fun clearAll() = vault.clearAll()

    fun displayKey(provider: ApiProvider): String = maskKey(vault.read(provider))
}
