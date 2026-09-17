package com.dugan.agent.data.local

import com.dugan.agent.util.AgentLog
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dugan.agent.domain.model.ApiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where BYOK keys live. Abstracted so unit tests can exercise the vault logic
 * without Robolectric.
 */
interface KeyVault {
    /** Snapshot of every stored key, keyed by [ApiProvider.id]. Never logged. */
    val keys: StateFlow<Map<String, String>>

    fun read(provider: ApiProvider): String?
    fun write(provider: ApiProvider, key: String)
    fun clear(provider: ApiProvider)
    fun clearAll()
    fun hasAllRequired(): Boolean
}

/**
 * EncryptedSharedPreferences-backed vault.
 *
 * The master key is an AES256-GCM key held by the Android Keystore and never
 * leaves the secure element, so `byok_vault.xml` on disk is useless without this
 * device. That is also exactly why the file is excluded from auto-backup in
 * `res/xml/data_extraction_rules.xml`: restoring it elsewhere produces
 * undecryptable ciphertext and a hard crash on read.
 */
@Singleton
class EncryptedKeyVault @Inject constructor(
    @ApplicationContext private val context: Context,
) : KeyVault {

    private val prefs by lazy { createPrefs() }

    private val _keys = MutableStateFlow<Map<String, String>>(emptyMap())
    override val keys: StateFlow<Map<String, String>> = _keys.asStateFlow()

    init {
        reload()
    }

    @Suppress("DEPRECATION")
    private fun createPrefs() = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.recoverCatching { failure ->
        // Keystore corruption (common after a restore or an OTA) leaves the vault
        // unreadable. Drop and recreate rather than crash-looping the app.
        AgentLog.w(TAG, "vault unreadable, recreating: ${failure.javaClass.simpleName}")
        context.deleteSharedPreferences(FILE_NAME)
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrThrow()

    private fun reload() {
        // Only the provider ids are enumerated; values stay out of any log line.
        _keys.value = ApiProvider.entries
            .mapNotNull { p -> prefs.getString(p.id, null)?.let { p.id to it } }
            .toMap()
    }

    override fun read(provider: ApiProvider): String? =
        prefs.getString(provider.id, null)?.takeIf { it.isNotBlank() }

    override fun write(provider: ApiProvider, key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(provider.id, trimmed).apply()
        reload()
    }

    override fun clear(provider: ApiProvider) {
        prefs.edit().remove(provider.id).apply()
        reload()
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
        reload()
    }

    override fun hasAllRequired(): Boolean =
        ApiProvider.entries.all { read(it) != null }

    companion object {
        private const val TAG = "BYOKVault"
        /** Must match the exclude path in res/xml/data_extraction_rules.xml. */
        const val FILE_NAME = "byok_vault"
    }
}

/** Test double with identical semantics and no Android dependency. */
class InMemoryKeyVault(
    initial: Map<String, String> = emptyMap(),
) : KeyVault {
    private val _keys = MutableStateFlow(initial)
    override val keys: StateFlow<Map<String, String>> = _keys.asStateFlow()

    override fun read(provider: ApiProvider): String? =
        _keys.value[provider.id]?.takeIf { it.isNotBlank() }

    override fun write(provider: ApiProvider, key: String) {
        _keys.value = _keys.value + (provider.id to key.trim())
    }

    override fun clear(provider: ApiProvider) {
        _keys.value = _keys.value - provider.id
    }

    override fun clearAll() {
        _keys.value = emptyMap()
    }

    override fun hasAllRequired(): Boolean =
        ApiProvider.entries.all { read(it) != null }
}
