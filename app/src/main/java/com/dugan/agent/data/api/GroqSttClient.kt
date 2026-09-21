package com.dugan.agent.data.api

import com.dugan.agent.data.local.KeyVault
import com.dugan.agent.domain.audio.Pcm
import com.dugan.agent.domain.model.ApiProvider
import com.dugan.agent.domain.model.AgentModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GroqTranscription(val text: String = "", val language: String? = null)

/**
 * Groq Whisper transcription.
 *
 * Groq's REST surface has no streaming transcription, so "streaming STT" here
 * means what the spec describes: the caller slices the utterance into 3s chunks
 * with 500ms overlap and calls [transcribe] per chunk. Each call returns in
 * ~150ms on the turbo model, which is fast enough to feel incremental.
 */
@Singleton
class GroqSttClient @Inject constructor(
    private val vault: KeyVault,
    http: HttpClientFactory,
) : SttClient {

    override val provider: String = ApiProvider.Groq.id
    private val client = http.client
    private val probe = http.probeClient
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun transcribe(
        model: AgentModel,
        pcm16: ShortArray,
        sampleRate: Int,
        language: String?,
    ): SttResult = transcribeWith(model, pcm16, sampleRate, language, keyOverride = null)

    /**
     * @param keyOverride credential to use instead of the stored one. Only the
     *   probe passes one, so a pasted key can be verified without being written
     *   to the vault first.
     */
    private suspend fun transcribeWith(
        model: AgentModel,
        pcm16: ShortArray,
        sampleRate: Int,
        language: String?,
        keyOverride: String?,
    ): SttResult = withContext(Dispatchers.IO) {
        val key = keyOverride ?: vault.read(ApiProvider.Groq)
            ?: throw ApiException(401, provider, "Groq API key not configured")
        if (pcm16.isEmpty()) return@withContext SttResult("", isFinal = true)

        val wav = Pcm.toWav(pcm16, sampleRate)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "chunk.wav",
                wav.toRequestBody("audio/wav".toMediaType()),
            )
            .addFormDataPart("model", model.wireId)
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature", "0")
            .apply { language?.let { addFormDataPart("language", it) } }
            .build()

        val request = Request.Builder()
            .url(ApiEndpoints.GROQ_TRANSCRIPTIONS)
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(response.code, provider, redact(text, key))
            }
            val parsed = runCatching { json.decodeFromString<GroqTranscription>(text) }
                .getOrElse { GroqTranscription() }
            SttResult(
                text = parsed.text.trim(),
                isFinal = true,
                language = parsed.language,
                durationMs = pcm16.size * 1000L / sampleRate,
            )
        }
    }

    override suspend fun ping(model: AgentModel, keyOverride: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            // 100ms of silence: a valid key returns 200 with an empty transcript.
            runCatching { transcribeWith(model, ShortArray(1600), 16_000, null, keyOverride) }.map { }
        }

    /** Never let a key or an echoed Authorization header reach a log line. */
    private fun redact(body: String, key: String): String =
        body.replace(key, "<redacted>").take(300)
}
