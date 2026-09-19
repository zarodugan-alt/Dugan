package com.dugan.agent.data.local

import com.dugan.agent.domain.model.ApiProvider
import com.dugan.agent.domain.model.KeyTestResult
import com.dugan.agent.domain.model.looksLikeKey
import com.dugan.agent.domain.model.maskKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vault semantics via the in-memory implementation.
 *
 * The EncryptedSharedPreferences-backed vault cannot run on the JVM, but the
 * rules that actually protect the keys -- trimming, blank rejection, complete
 * redaction -- are the ones worth pinning, and they are identical in both.
 */
class BYOKVaultTest {

    @Test
    fun `keys are trimmed on write`() {
        val vault = InMemoryKeyVault()
        vault.write(ApiProvider.Groq, "  gsk_abcdef0123456789  ")
        assertEquals("gsk_abcdef0123456789", vault.read(ApiProvider.Groq))
    }

    @Test
    fun `a blank key reads back as absent`() {
        val vault = InMemoryKeyVault()
        vault.write(ApiProvider.Gemini, "   ")
        assertNull(vault.read(ApiProvider.Gemini))
    }

    @Test
    fun `hasAllRequired needs all three providers`() {
        val vault = InMemoryKeyVault()
        assertFalse(vault.hasAllRequired())

        vault.write(ApiProvider.Groq, "gsk_0123456789abcdef")
        vault.write(ApiProvider.Gemini, "AIza0123456789abcdef")
        assertFalse(vault.hasAllRequired())

        vault.write(ApiProvider.UnrealSpeech, "us_0123456789abcdef")
        assertTrue(vault.hasAllRequired())
    }

    @Test
    fun `clear removes one key and leaves the others`() {
        val vault = InMemoryKeyVault(
            mapOf(
                ApiProvider.Groq.id to "gsk_0123456789abcdef",
                ApiProvider.Gemini.id to "AIza0123456789abcdef",
            ),
        )
        vault.clear(ApiProvider.Groq)
        assertNull(vault.read(ApiProvider.Groq))
        assertEquals("AIza0123456789abcdef", vault.read(ApiProvider.Gemini))
    }

    @Test
    fun `clearAll empties the vault`() {
        val vault = InMemoryKeyVault(mapOf(ApiProvider.Groq.id to "gsk_0123456789abcdef"))
        vault.clearAll()
        assertTrue(vault.keys.value.isEmpty())
    }

    // -- Redaction -----------------------------------------------------------

    @Test
    fun `masking never reveals the middle of a key`() {
        val masked = maskKey("gsk_abcdefghijklmnopqrstuvwxyz")
        assertFalse("masked value leaked the body: $masked", masked.contains("mnop"))
        assertTrue(masked.startsWith("gsk_"))
        assertTrue(masked.contains("•"))
    }

    @Test
    fun `short keys are fully masked`() {
        assertEquals("********", maskKey("12345678"))
        assertEquals("*******", maskKey("1234567"))
    }

    @Test
    fun `absent key renders as empty marker`() {
        assertEquals("(empty)", maskKey(null))
        assertEquals("(empty)", maskKey(""))
    }

    // -- Structural validation ----------------------------------------------

    @Test
    fun `groq keys must start with the gsk_ or groq_ prefix`() {
        assertTrue(looksLikeKey(ApiProvider.Groq, "gsk_0123456789abcdef"))
        assertTrue(looksLikeKey(ApiProvider.Groq, "groq_0123456789abcdef"))
        assertFalse(looksLikeKey(ApiProvider.Groq, "sk-0123456789abcdef"))
    }

    @Test
    fun `gemini accepts both current key shapes`() {
        assertTrue(looksLikeKey(ApiProvider.Gemini, "AIzaSyD-0123456789abcdef"))
        assertTrue(looksLikeKey(ApiProvider.Gemini, "ya29.a0AfH6SM0123456789"))
        assertFalse(looksLikeKey(ApiProvider.Gemini, "gsk_0123456789abcdef"))
    }

    @Test
    fun `unreal speech has no fixed prefix so only length and shape are checked`() {
        assertTrue(looksLikeKey(ApiProvider.UnrealSpeech, "anything-long-enough-here"))
        assertFalse(looksLikeKey(ApiProvider.UnrealSpeech, "short"))
    }

    @Test
    fun `keys containing whitespace are rejected`() {
        assertFalse(looksLikeKey(ApiProvider.Groq, "gsk_0123 456789abcdef"))
        assertFalse(looksLikeKey(ApiProvider.Groq, "gsk_0123\n456789abcdef"))
    }

    @Test
    fun `untested and testing are not ok`() {
        assertFalse(KeyTestResult.Untested.isOk)
        assertFalse(KeyTestResult.Testing.isOk)
        assertTrue(KeyTestResult.Valid("ok").isOk)
        assertFalse(KeyTestResult.Invalid("nope").isOk)
    }
}
