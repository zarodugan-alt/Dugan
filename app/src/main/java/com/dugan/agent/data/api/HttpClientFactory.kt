package com.dugan.agent.data.api

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One shared OkHttp client for every provider.
 *
 * The whole point is connection warm-up: HTTP/2 keeps a single multiplexed
 * connection per origin alive, so a turn does not pay a 50-200ms TCP+TLS
 * handshake to Groq/Gemini every time.
 */
@Singleton
class HttpClientFactory @Inject constructor() {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Pings keep the HTTP/2 connection out of the idle pool during a call.
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            // No read timeout on streaming calls: silence between SSE frames is normal.
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
    }

    /** Short-timeout client for the "Test Connection" probes and the echo verifier. */
    val probeClient: OkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(15, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(2, 5, TimeUnit.MINUTES))
            .build()
    }

    /** Opens a connection now so the first real turn does not pay for it. */
    fun warmUp(urls: List<String>) {
        urls.forEach { url ->
            runCatching {
                client.newCall(
                    okhttp3.Request.Builder().url(url).head().build(),
                ).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = Unit
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        response.close()
                    }
                })
            }
        }
    }
}
