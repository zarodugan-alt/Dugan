package com.dugan.agent.data.repository

import com.dugan.agent.data.local.KeyVault
import com.dugan.agent.domain.model.ApiProvider
import com.dugan.agent.domain.model.looksLikeKey
import com.dugan.agent.domain.model.maskKey
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

    /** @return a human-readable reason when the key is structurally wrong, else null. */
    fun validate(provider: ApiProvider, key: String): String? = when {
        key.isBlank() -> "Key is empty"
        !looksLikeKey(provider, key) ->
            if (provider.expectedKeyPrefixes.isEmpty()) {
                "Key looks too short"
            } else {
                "Key should start with ${provider.expectedKeyPrefixes.joinToString(" or ")}"
            }
        else -> null
    }

    fun save(provider: ApiProvider, key: String): Boolean {
        if (validate(provider, key) != null) return false
        vault.write(provider, key)
        return true
    }

    fun clear(provider: ApiProvider) = vault.clear(provider)

    fun clearAll() = vault.clearAll()

    fun displayKey(provider: ApiProvider): String = maskKey(vault.read(provider))
}
