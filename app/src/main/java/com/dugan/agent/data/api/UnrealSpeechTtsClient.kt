package com.dugan.agent.data.api

import com.dugan.agent.data.local.KeyVault
import com.dugan.agent.domain.audio.AudioDecoder
import com.dugan.agent.domain.audio.Pcm
import com.dugan.agent.domain.model.AgentModel
import com.dugan.agent.domain.model.ApiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class UnrealTtsRequest(
    val text: String,
    val voiceId: String,
    val speed: Float = 0f,
    val pitch: Float = 1f,
    /** Documented as a suffixed string ("320k", "192k", "128k", ...), not a number. */
    val bitrate: String = "128k",
    /** `libmp3lame` is the documented default and what [AudioDecoder] expects back. */
    @SerialName("codec") val codec: String = "libmp3lame",
)

/**
 * Unreal Speech `/stream`.
 *
 * The endpoint returns a compressed stream. We buffer the response for a single
 * sentence and decode once -- see [AudioDecoder] for why buffering one sentence
 * is the right trade-off here.
 */
@Singleton
class UnrealSpeechTtsClient @Inject constructor(
    private val vault: KeyVault,
    http: HttpClientFactory,
) : TtsClient {

    override val provider: String = ApiProvider.UnrealSpeech.id
    override val outputSampleRate: Int = 24_000
    private val client = http.client
    private val probe = http.probeClient
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun synthesize(
        model: AgentModel,
        text: String,
        voiceId: String,
        speed: Float,
    ): Flow<ShortArray> = callbackFlow {
        val key = vault.read(ApiProvider.UnrealSpeech)
        if (key == null) {
            close(ApiException(401, provider, "Unreal Speech key not configured"))
            return@callbackFlow
        }
        if (text.isBlank()) {
            close()
            return@callbackFlow
        }

        val body = json.encodeToString(
            UnrealTtsRequest.serializer(),
            UnrealTtsRequest(text = text, voiceId = voiceFor(voiceId), speed = speed),
        ).toRequestBody(JSON_MEDIA)

        val request = Request.Builder()
            .url(ApiEndpoints.UNREAL_STREAM)
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()

        val call = client.newCall(request)
        val response = runCatching { call.execute() }.getOrElse { close(it); return@callbackFlow }

        response.use { resp ->
            if (!resp.isSuccessful) {
                close(ApiException(resp.code, provider, resp.body?.string()?.take(300).orEmpty()))
                return@callbackFlow
            }
            val bytes = runCatching { resp.body?.bytes() }.getOrNull()
            // ByteArray? has no isNullOrEmpty() in the stdlib -- check explicitly
            // so `bytes` smart-casts to non-null for the decoder below.
            if (bytes == null || bytes.isEmpty()) {
                close(ApiException(500, provider, "empty audio body"))
                return@callbackFlow
            }
            val decoded = AudioDecoder.decode(bytes)
            if (decoded == null) {
                close(ApiException(500, provider, "could not decode TTS audio"))
                return@callbackFlow
            }
            // Emit in 20ms frames so AudioTrack's low-latency buffer stays drained
            // instead of being handed one giant write().
            val frame = decoded.sampleRate / 50
            var offset = 0
            while (offset < decoded.pcm.size) {
                val len = minOf(frame, decoded.pcm.size - offset)
                val chunk = ShortArray(len)
                System.arraycopy(decoded.pcm, offset, chunk, 0, len)
                trySend(chunk)
                offset += len
            }
            close()
        }

        awaitClose { runCatching { call.cancel() } }
    }.flowOn(Dispatchers.IO)

    override suspend fun ping(model: AgentModel, keyOverride: String?): Result<Unit> =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching {
            val key = keyOverride ?: vault.read(ApiProvider.UnrealSpeech)
                ?: throw ApiException(401, provider, "Unreal Speech key not configured")
            // Shortest accepted request. Uses a voice the endpoint documents so
            // the probe can only fail on the credential, not on the parameters.
            val body = json.encodeToString(
                UnrealTtsRequest.serializer(),
                UnrealTtsRequest(text = "Hi.", voiceId = DEFAULT_VOICE),
            ).toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url(ApiEndpoints.UNREAL_STREAM)
                .header("Authorization", "Bearer $key")
                .post(body)
                .build()
            probe.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw ApiException(resp.code, provider, resp.body?.string()?.take(200).orEmpty())
                }
            }
        }
    }

    /**
     * Unreal Speech only knows its own five voices, while the rest of the app
     * defaults to an Edge TTS voice name ("Aria"). An unrecognised id comes back
     * as a 400 on every request, which the user experiences as "the key does not
     * work". Map anything unknown onto the documented default instead.
     */
    private fun voiceFor(voiceId: String): String =
        VOICES.firstOrNull { it.equals(voiceId, ignoreCase = true) } ?: DEFAULT_VOICE

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** The only voices `/stream` documents. */
        val VOICES = listOf("Scarlett", "Dan", "Liv", "Will", "Amy")
        const val DEFAULT_VOICE = "Scarlett"
    }
}
