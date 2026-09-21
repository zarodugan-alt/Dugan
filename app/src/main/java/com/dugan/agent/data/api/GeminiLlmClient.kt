package com.dugan.agent.data.api

import com.dugan.agent.data.local.KeyVault
import com.dugan.agent.domain.model.AgentModel
import com.dugan.agent.domain.model.ApiProvider
import com.dugan.agent.domain.model.ModelProvider
import com.dugan.agent.domain.model.ThinkingLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class Part(val text: String? = null)

@Serializable
private data class Content(
    val role: String? = null,
    val parts: List<Part> = emptyList(),
)

@Serializable
private data class ThinkingConfig(
    @SerialName("thinkingBudget") val thinkingBudget: Int,
)

@Serializable
private data class GenerationConfig(
    val temperature: Double = 0.0,
    val topP: Double = 1.0,
    val maxOutputTokens: Int? = null,
    val thinkingConfig: ThinkingConfig? = null,
)

@Serializable
private data class GeminiRequestBody(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null,
)

@Serializable
private data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)

@Serializable
private data class GeminiChunk(
    val candidates: List<Candidate> = emptyList(),
)

/**
 * Gemini over the `streamGenerateContent?alt=sse` endpoint.
 *
 * temperature 0 / topP 1 gives greedy decoding: the same prompt yields the same
 * tokens, which is what makes speculative execution and prefix caching worth
 * anything at all.
 */
@Singleton
class GeminiLlmClient @Inject constructor(
    private val vault: KeyVault,
    http: HttpClientFactory,
) : LlmClient {

    override val provider: String = ApiProvider.Gemini.id
    private val client = http.client
    private val probe = http.probeClient
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun key(): String = vault.read(ApiProvider.Gemini)
        ?: throw ApiException(401, provider, "Gemini API key not configured")

    /**
     * Sends the key in `x-goog-api-key` rather than as a `?key=` query param.
     *
     * Both are documented, but the header keeps the secret out of URLs — which
     * means out of proxy logs, crash reporters and any `okhttp` logging
     * interceptor — and it needs no percent-encoding, so a key containing
     * characters that are awkward in a query string still arrives intact.
     */
    private fun Request.Builder.withApiKey(keyOverride: String? = null): Request.Builder =
        header(GEMINI_KEY_HEADER, keyOverride ?: key())

    private fun buildBody(
        messages: List<ChatMessage>,
        systemPrompt: String,
        thinkingLevel: ThinkingLevel,
        maxOutputTokens: Int,
    ): GeminiRequestBody = GeminiRequestBody(
        contents = messages.map { Content(role = it.role, parts = listOf(Part(text = it.content))) },
        systemInstruction = if (systemPrompt.isBlank()) null else Content(parts = listOf(Part(text = systemPrompt))),
        generationConfig = GenerationConfig(
            maxOutputTokens = maxOutputTokens,
            thinkingConfig = ThinkingConfig(thinkingBudget = thinkingLevel.thinkingBudget),
        ),
    )

    override fun stream(
        model: AgentModel,
        messages: List<ChatMessage>,
        thinkingLevel: ThinkingLevel,
        systemPrompt: String,
        maxOutputTokens: Int,
    ): Flow<String> = callbackFlow {
        val url = ApiEndpoints.geminiStream(model.wireId) + "?alt=sse"
        val request = Request.Builder()
            .url(url)
            .withApiKey()
            .header("Content-Type", "application/json")
            .post(json.encodeToString(GeminiRequestBody.serializer(), buildBody(messages, systemPrompt, thinkingLevel, maxOutputTokens)).toRequestBody(JSON_MEDIA))
            .build()

        val call = client.newCall(request)
        val response: Response = runCatching { call.execute() }
            .getOrElse { close(it); return@callbackFlow }

        response.use { resp ->
            if (!resp.isSuccessful) {
                close(ApiException(resp.code, provider, resp.body?.string()?.take(300).orEmpty()))
                return@callbackFlow
            }
            val source: BufferedSource = resp.body?.source()
                ?: run { close(ApiException(500, provider, "empty SSE body")); return@callbackFlow }

            runCatching {
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    val chunk = runCatching { json.decodeFromString<GeminiChunk>(payload) }.getOrNull()
                        ?: continue
                    chunk.candidates.forEach { candidate ->
                        candidate.content?.parts?.forEach { part ->
                            part.text?.takeIf { it.isNotEmpty() }?.let { trySend(it) }
                        }
                    }
                }
                close()
            }.onFailure { close(it) }
        }

        awaitClose { runCatching { call.cancel() } }
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(
        model: AgentModel,
        messages: List<ChatMessage>,
        systemPrompt: String,
        maxOutputTokens: Int,
    ): String = withContext(Dispatchers.IO) {
        val url = ApiEndpoints.geminiGenerate(model.wireId)
        val request = Request.Builder()
            .url(url)
            .withApiKey()
            .header("Content-Type", "application/json")
            .post(
                json.encodeToString(
                    GeminiRequestBody.serializer(),
                    buildBody(messages, systemPrompt, ThinkingLevel.Quick, maxOutputTokens),
                ).toRequestBody(JSON_MEDIA),
            )
            .build()

        probe.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, provider, body.take(300))
            runCatching { json.decodeFromString<GeminiChunk>(body) }
                .getOrNull()
                ?.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.mapNotNull { it.text }
                ?.joinToString("")
                .orEmpty()
        }
    }

    override suspend fun ping(model: AgentModel, keyOverride: String?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(ApiEndpoints.GEMINI_MODELS_PROBE + "?pageSize=1")
                .withApiKey(keyOverride)
                .build()
            probe.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw ApiException(resp.code, provider, resp.body?.string()?.take(200).orEmpty())
                }
            }
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** Documented header for a Gemini API key, `AIza` or `AQ.` alike. */
        const val GEMINI_KEY_HEADER = "x-goog-api-key"
    }
}

/** True for models the main-screen selector should route to Gemini rather than Groq. */
internal fun AgentModel.isGemini(): Boolean = this.provider == ModelProvider.Gemini
