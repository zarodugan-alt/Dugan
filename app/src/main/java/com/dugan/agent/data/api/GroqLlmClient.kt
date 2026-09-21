package com.dugan.agent.data.api

import com.dugan.agent.data.local.KeyVault
import com.dugan.agent.domain.model.AgentModel
import com.dugan.agent.domain.model.ApiProvider
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
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GroqMessage(val role: String, val content: String)

@Serializable
private data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val stream: Boolean = true,
    val temperature: Double = 0.0,
    @SerialName("top_p") val topP: Double = 1.0,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    val reasoningEffort: String? = null,
)

@Serializable
private data class Delta(val content: String? = null, val role: String? = null)

@Serializable
private data class Choice(
    val delta: Delta? = null,
    val message: GroqMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
private data class GroqChatChunk(val choices: List<Choice> = emptyList())

/**
 * Groq chat-completions, used for the Llama / GPT-OSS entries in the model
 * selector. Same streaming contract as Gemini so the orchestrator is
 * provider-agnostic.
 *
 * Groq exposes `reasoning_effort` instead of a thinking budget; the mapping
 * keeps the UI's three-way Quick/Balanced/Deep control meaningful on both.
 */
@Singleton
class GroqLlmClient @Inject constructor(
    private val vault: KeyVault,
    http: HttpClientFactory,
) : LlmClient {

    override val provider: String = ApiProvider.Groq.id
    private val client = http.client
    private val probe = http.probeClient
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun key(override: String? = null): String = override
        ?: vault.read(ApiProvider.Groq)
        ?: throw ApiException(401, provider, "Groq API key not configured")

    private fun request(
        model: AgentModel,
        messages: List<ChatMessage>,
        systemPrompt: String,
        thinkingLevel: ThinkingLevel,
        maxOutputTokens: Int,
        stream: Boolean,
        keyOverride: String? = null,
    ): Request {
        val all = buildList {
            if (systemPrompt.isNotBlank()) add(GroqMessage("system", systemPrompt))
            messages.forEach { add(GroqMessage(it.role, it.content)) }
        }
        val body = GroqChatRequest(
            model = model.wireId,
            messages = all,
            stream = stream,
            maxCompletionTokens = maxOutputTokens,
            // Only reasoning-capable models accept this; others 400 on it.
            reasoningEffort = if (model.id == "gpt-oss-120b") {
                when (thinkingLevel) {
                    ThinkingLevel.Quick -> "low"
                    ThinkingLevel.Balanced -> "medium"
                    ThinkingLevel.Deep -> "high"
                }
            } else {
                null
            },
        )
        return Request.Builder()
            .url(ApiEndpoints.GROQ_CHAT)
            .header("Authorization", "Bearer ${key(keyOverride)}")
            .header("Content-Type", "application/json")
            .post(json.encodeToString(GroqChatRequest.serializer(), body).toRequestBody(JSON_MEDIA))
            .build()
    }

    override fun stream(
        model: AgentModel,
        messages: List<ChatMessage>,
        thinkingLevel: ThinkingLevel,
        systemPrompt: String,
        maxOutputTokens: Int,
    ): Flow<String> = callbackFlow {
        val call = client.newCall(request(model, messages, systemPrompt, thinkingLevel, maxOutputTokens, true))
        val response = runCatching { call.execute() }.getOrElse { close(it); return@callbackFlow }

        response.use { resp ->
            if (!resp.isSuccessful) {
                close(ApiException(resp.code, provider, resp.body?.string()?.take(300).orEmpty()))
                return@callbackFlow
            }
            val source = resp.body?.source()
                ?: run { close(ApiException(500, provider, "empty SSE body")); return@callbackFlow }
            runCatching {
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    if (payload.isEmpty()) continue
                    val chunk = runCatching { json.decodeFromString<GroqChatChunk>(payload) }.getOrNull()
                        ?: continue
                    chunk.choices.forEach { choice ->
                        choice.delta?.content?.takeIf { it.isNotEmpty() }?.let { trySend(it) }
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
    ): String = completeWith(model, messages, systemPrompt, maxOutputTokens, keyOverride = null)

    private suspend fun completeWith(
        model: AgentModel,
        messages: List<ChatMessage>,
        systemPrompt: String,
        maxOutputTokens: Int,
        keyOverride: String?,
    ): String = withContext(Dispatchers.IO) {
        probe.newCall(
            request(model, messages, systemPrompt, ThinkingLevel.Quick, maxOutputTokens, false, keyOverride),
        )
            .execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException(resp.code, provider, body.take(300))
                runCatching { json.decodeFromString<GroqChatChunk>(body) }
                    .getOrNull()
                    ?.choices
                    ?.firstOrNull()
                    ?.message
                    ?.content
                    .orEmpty()
            }
    }

    override suspend fun ping(model: AgentModel, keyOverride: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                completeWith(model, listOf(ChatMessage("user", "ping")), "", 1, keyOverride)
            }.map { }
        }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
