package com.dugan.agent.data.api

import com.dugan.agent.domain.model.ApiProvider
import com.dugan.agent.domain.model.ModelCatalog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that answers "does this key actually work?".
 *
 * Each provider is probed with the smallest request it accepts, using the
 * candidate key directly. Nothing is written to the vault first, so a key can
 * be verified while it is still only text in a field — which is what makes
 * verify-on-paste safe: a typo never overwrites a working key.
 *
 * The probe is the verdict. Nothing here inspects the shape of the key, because
 * a provider can re-prefix its tokens at any time (Gemini went from `AIza` to
 * `AQ.` in 2026) and the endpoint is the only thing that knows the truth.
 */
/**
 * One Groq key covers both Whisper and Orpheus TTS, so a single probe settles
 * it: the cheapest one is the transcription request, and a credential that
 * authenticates there authenticates on `/audio/speech` too.
 */
@Singleton
class KeyVerifier @Inject constructor(
    private val stt: GroqSttClient,
    private val llm: AgentLlm,
) {
    /** @param key the credential to test, exactly as pasted. */
    suspend fun verify(provider: ApiProvider, key: String): Result<Unit> = when (provider) {
        ApiProvider.Groq -> stt.ping(ModelCatalog.DefaultStt, key)
        ApiProvider.Gemini -> llm.ping(ModelCatalog.DefaultThinking, key)
    }
}
