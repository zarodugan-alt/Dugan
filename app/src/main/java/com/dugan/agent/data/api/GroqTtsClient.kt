package com.dugan.agent.data.api

import com.dugan.agent.data.local.KeyVault
import com.dugan.agent.domain.audio.Wav
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
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GroqSpeechRequest(
    val model: String,
    val input: String,
    val voice: String,
    /** The only format Orpheus serves. */
    @SerialName("response_format") val responseFormat: String = "wav",
)

/**
 * Groq Orpheus text-to-speech.
 *
 * It runs on the same `gsk_` key the agent already uses for Whisper, which is
 * the point: two providers and two keys instead of three, one less signup and
 * one less thing to expire mid-call.
 *
 * Three constraints from Groq's own docs shape this client:
 *
 * - `input` is capped at 200 characters while the pipeline hands over whole
 *   sentences, so text is cut on a word boundary and the pieces are played back
 *   to back. At sentence level the seams are inaudible.
 * - `response_format` only supports `wav`, so the body goes through [Wav]
 *   rather than MediaCodec.
 * - There is no speed parameter. [speed] is part of the [TtsClient] contract and
 *   is ignored here; the keyless Edge TTS tier and the on-device tier both
 *   honour it, so the setting still does something whenever this tier is
 *   skipped.
 */
@Singleton
class GroqTtsClient @Inject constructor(
    private val vault: KeyVault,
    http: HttpClientFactory,
) : TtsClient {

    override val provider: String = ApiProvider.Groq.id
    override val outputSampleRate: Int = SAMPLE_RATE
    private val client = http.client
    private val probe = http.probeClient
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun synthesize(
        model: AgentModel,
        text: String,
        voiceId: String,
        speed: Float,
    ): Flow<ShortArray> = callbackFlow {
        val key = vault.read(ApiProvider.Groq)
        if (key == null) {
            close(ApiException(401, provider, "Groq API key not configured"))
            return@callbackFlow
        }
        if (text.isBlank()) {
            close()
            return@callbackFlow
        }

        val voice = voiceFor(voiceId)
        // Held so awaitClose can cancel whichever piece is in flight.
        var active: Call? = null

        val failure = runCatching {
            for (piece in splitForTts(text)) {
                val bytes = fetch(model.wireId, piece, voice, key, client) { active = it }
                val decoded = Wav.decode(bytes)
                    ?: throw ApiException(500, provider, "could not decode Groq TTS audio")
                // 20ms frames keep AudioTrack's low-latency buffer drained
                // instead of being handed one giant write().
                val pcm = Wav.toMono(decoded.pcm, decoded.channels)
                val frame = (decoded.sampleRate / 50).coerceAtLeast(1)
                var offset = 0
                while (offset < pcm.size) {
                    val len = minOf(frame, pcm.size - offset)
                    trySend(pcm.copyOfRange(offset, offset + len))
                    offset += len
                }
            }
        }.exceptionOrNull()

        if (failure != null) close(failure) else close()
        awaitClose { runCatching { active?.cancel() } }
    }.flowOn(Dispatchers.IO)

    /**
     * @param keyOverride credential to test instead of the stored one, so a
     *   freshly pasted key can be verified before it reaches the vault.
     */
    override suspend fun ping(model: AgentModel, keyOverride: String?): Result<Unit> =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                val key = keyOverride ?: vault.read(ApiProvider.Groq)
                    ?: throw ApiException(401, provider, "Groq API key not configured")
                // Shortest useful request: one word, a voice the model documents.
                fetch(model.wireId, "Hi.", DEFAULT_VOICE, key, probe)
            }.map { }
        }

    private fun fetch(
        wireId: String,
        text: String,
        voice: String,
        key: String,
        httpClient: OkHttpClient,
        onCall: (Call) -> Unit = {},
    ): ByteArray {
        val body = json.encodeToString(
            GroqSpeechRequest.serializer(),
            GroqSpeechRequest(model = wireId, input = text, voice = voice),
        ).toRequestBody(JSON_MEDIA)

        val request = Request.Builder()
            .url(ApiEndpoints.GROQ_SPEECH)
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()

        val call = httpClient.newCall(request)
        onCall(call)
        call.execute().use { resp ->
            if (!resp.isSuccessful) {
                // Never interpolate the key: the body is echoed by some gateways.
                throw ApiException(resp.code, provider, resp.body?.string()?.take(300).orEmpty())
            }
            return resp.body?.bytes() ?: throw ApiException(500, provider, "empty audio body")
        }
    }

    /**
     * Orpheus knows six English voices. The rest of the app used to default to
     * an Edge TTS name, which this endpoint would reject outright, so anything
     * unrecognised maps onto the documented default rather than failing every
     * sentence of the call.
     */
    private fun voiceFor(voiceId: String): String =
        VOICES.firstOrNull { it.equals(voiceId, ignoreCase = true) } ?: DEFAULT_VOICE

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val VOICES = listOf("hannah", "autumn", "diana", "austin", "daniel", "troy")
        const val DEFAULT_VOICE = "hannah"
        const val SAMPLE_RATE = 24_000
    }
}

/** Orpheus rejects a request whose `input` exceeds this many characters. */
const val GROQ_TTS_MAX_INPUT = 200

/**
 * Cuts [text] into pieces the endpoint accepts, preferring a space so no word is
 * broken in half. A single token longer than the limit is hard-cut rather than
 * dropped: losing the end of a sentence is worse than an ugly break.
 */
fun splitForTts(text: String, maxChars: Int = GROQ_TTS_MAX_INPUT): List<String> {
    require(maxChars > 0) { "maxChars must be positive" }
    val trimmed = text.trim()
    if (trimmed.length <= maxChars) return if (trimmed.isEmpty()) emptyList() else listOf(trimmed)

    val pieces = mutableListOf<String>()
    var start = 0
    while (start < trimmed.length) {
        val end = minOf(start + maxChars, trimmed.length)
        if (end == trimmed.length) {
            pieces += trimmed.substring(start).trim()
            break
        }
        // Walk back to the last space inside the window.
        var cut = trimmed.lastIndexOf(' ', end)
        if (cut <= start) cut = end
        pieces += trimmed.substring(start, cut).trim()
        start = if (cut == end) end else cut + 1
    }
    return pieces.filter { it.isNotEmpty() }
}
