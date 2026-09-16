package com.dugan.agent.data.local

import android.util.LruCache
import com.dugan.agent.domain.model.DuganSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layer of the latency stack that matters most for short replies: a repeated
 * "One moment" costs ~5ms from here instead of ~300ms over the network.
 *
 * Bounded by decoded PCM byte count, not entry count, so a single long phrase
 * cannot evict the whole cache and a phone with 2GB RAM is not punished.
 */
@Singleton
class TtsCache @Inject constructor() {

    private val cache = object : LruCache<String, ByteArray>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    @Volatile
    var enabled: Boolean = true

    /** Key includes voice + speed: the same text at a different rate is different audio. */
    fun keyFor(text: String, settings: DuganSettings): String =
        "${settings.ttsVoiceId}|${settings.playbackSpeed}|${settings.ttsModelId}|${text.trim().lowercase()}"

    fun get(key: String): ByteArray? = if (enabled) cache.get(key) else null

    fun put(key: String, pcm: ByteArray) {
        if (!enabled || pcm.isEmpty()) return
        // Don't let one huge utterance dominate the budget.
        if (pcm.size > MAX_BYTES / 2) return
        cache.put(key, pcm)
    }

    fun size(): Int = cache.size()

    fun clear() = cache.evictAll()

    /** Seed the phrases the agent says constantly, so the first hit is never cold. */
    fun prewarm(warm: List<String>, settings: DuganSettings, synthesize: suspend (String) -> ByteArray?) {
        warm.forEach { text ->
            val key = keyFor(text, settings)
            if (cache.get(key) != null) return@forEach
            synthesize(text)?.let { put(key, it) }
        }
    }

    companion object {
        /** ~2MB of 24kHz mono 16-bit PCM ~= 44 seconds of speech. */
        private const val MAX_BYTES = 2 * 1024 * 1024

        val WarmPhrases = listOf(
            "Let me check that.",
            "One moment.",
            "Sorry, could you repeat that?",
            "I'm not sure.",
            "Go ahead.",
            "Calling now.",
        )
    }
}
