package com.dugan.agent.domain.latency

import com.dugan.agent.data.api.ApiEndpoints
import com.dugan.agent.data.api.HttpClientFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens TLS + HTTP/2 connections to every provider at session start.
 *
 * A cold connection costs 50-200ms of handshake on the first turn of a call,
 * which is a large slice of a 400ms budget. Doing it while the user is still
 * saying hello makes it free.
 */
@Singleton
class ConnectionWarmup @Inject constructor(
    private val http: HttpClientFactory,
) {
    fun warmAll() {
        http.warmUp(
            listOf(
                ApiEndpoints.GROQ_TRANSCRIPTIONS,
                ApiEndpoints.GROQ_CHAT,
                ApiEndpoints.GROQ_SPEECH,
                ApiEndpoints.geminiGenerate("gemini-2.5-flash"),
            ),
        )
    }
}
