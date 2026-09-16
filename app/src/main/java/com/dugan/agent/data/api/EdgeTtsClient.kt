package com.dugan.agent.data.api

import com.dugan.agent.domain.audio.AudioDecoder
import com.dugan.agent.domain.model.AgentModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keyless fallback TTS: Microsoft Edge's read-aloud endpoint.
 *
 * Used only when Unreal Speech returns 429/402, so a mid-conversation quota
 * exhaustion degrades to a different voice instead of silencing the agent.
 * No API key exists for this service; it authenticates with the public
 * `TrustedClientToken` plus a time-bucketed `Sec-MS-GEC` challenge.
 */
@Singleton
class EdgeTtsClient @Inject constructor(
    http: HttpClientFactory,
) : TtsClient {

    override val provider: String = "edge_tts"
    override val outputSampleRate: Int = 24_000

    private val client: OkHttpClient = http.client

    override fun synthesize(
        model: AgentModel,
        text: String,
        voiceId: String,
        speed: Float,
    ): Flow<ShortArray> = callbackFlow {
        if (text.isBlank()) {
            close()
            return@callbackFlow
        }

        val collected = ByteArrayOutputStream()
        val voice = if (voiceId.contains('-')) voiceId else "en-US-$voiceId"
        // Unreal's -1f..1f maps onto Edge's -50%..+50%.
        val rate = ((speed * 50).toInt()).let { if (it >= 0) "+$it%" else "$it%" }

        val request = Request.Builder()
            .url(ApiEndpoints.edgeTtsWebSocket(secMsGec()))
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("User-Agent", USER_AGENT)
            .build()

        val ws = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(CONFIG_MESSAGE)
                    webSocket.send(ssml(text, voice, rate))
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    // Binary frames are "Path:audio\r\n" + a header, then MP3 payload.
                    val raw = bytes.toByteArray()
                    val separator = findHeaderEnd(raw)
                    if (separator > 0 && separator < raw.size) {
                        collected.write(raw, separator, raw.size - separator)
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        emitAudio()
                        webSocket.close(1000, "done")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    close(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    emitAudio()
                    close()
                }

                private fun emitAudio() {
                    val bytes = collected.toByteArray()
                    if (bytes.isEmpty()) return
                    collected.reset()
                    val decoded = AudioDecoder.decode(bytes) ?: return
                    val frame = decoded.sampleRate / 50
                    var offset = 0
                    while (offset < decoded.pcm.size) {
                        val len = minOf(frame, decoded.pcm.size - offset)
                        val chunk = ShortArray(len)
                        System.arraycopy(decoded.pcm, offset, chunk, 0, len)
                        trySend(chunk)
                        offset += len
                    }
                }
            },
        )

        awaitClose { runCatching { ws.cancel() } }
    }.flowOn(Dispatchers.IO)

    override suspend fun ping(model: AgentModel): Result<Unit> = Result.success(Unit)

    /**
     * `Sec-MS-GEC` is SHA-256 over the Windows file-time tick count (100ns since
     * 1601-01-01) rounded down to the current 5-minute bucket, concatenated with
     * the trusted client token, rendered as uppercase hex.
     */
    internal fun secMsGec(nowMs: Long = System.currentTimeMillis()): String {
        val windowsTicks = (nowMs + EPOCH_DIFF_MS) * TICKS_PER_MS
        val bucket = windowsTicks - (windowsTicks % FIVE_MINUTE_TICKS)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$bucket$TRUSTED_CLIENT_TOKEN".toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun findHeaderEnd(raw: ByteArray): Int {
        // Look for the blank line that terminates the text header.
        for (i in 0 until raw.size - 3) {
            if (raw[i] == '\r'.code.toByte() && raw[i + 1] == '\n'.code.toByte() &&
                raw[i + 2] == '\r'.code.toByte() && raw[i + 3] == '\n'.code.toByte()
            ) {
                return i + 4
            }
        }
        return -1
    }

    private fun ssml(text: String, voice: String, rate: String): String {
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return """
            Path:speech.context
            X-RequestId:${java.util.UUID.randomUUID()}
            Content-Type:application/ssml+xml
            X-Timestamp:${System.currentTimeMillis()}Z

            <speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>
              <voice name='$voice'>
                <prosody pitch='+0Hz' rate='$rate' volume='+0%'>$escaped</prosody>
              </voice>
            </speak>
        """.trimIndent()
    }

    private companion object {
        const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"
        const val EPOCH_DIFF_MS = 11_644_473_600_000L
        const val TICKS_PER_MS = 10_000L
        const val FIVE_MINUTE_TICKS = 5L * 60 * 1000 * 10_000

        const val CONFIG_MESSAGE =
            "Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":" +
                "{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"}," +
                "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
    }
}
