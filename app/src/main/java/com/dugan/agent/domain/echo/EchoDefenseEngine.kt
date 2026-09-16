package com.dugan.agent.domain.echo

import android.util.Log
import com.dugan.agent.data.local.TtsCache
import com.dugan.agent.domain.audio.AudioFrame
import com.dugan.agent.domain.audio.AudioPlaybackEngine
import com.dugan.agent.domain.model.DuganSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Layers 3, 4 and 5.
 *
 * Layer 1 (hardware AEC) and layer 2 (software AEC) live in
 * [com.dugan.agent.domain.audio] because they operate on samples before anything
 * else sees them. This engine handles everything downstream: gating the mic,
 * deciding whether speech during playback is a real barge-in, and rejecting
 * transcripts that turn out to be the agent's own words.
 */
@Singleton
class EchoDefenseEngine @Inject constructor(
    private val playback: AudioPlaybackEngine,
    private val fuzzy: FuzzyMatcher,
    private val embeddings: HashingEmbeddingMatcher,
    private val verifier: LlmEchoVerifier,
    ttsCache: TtsCache,
) {

    /** Last [HISTORY] things the agent said, newest last. */
    private val recentTTS = ArrayDeque<TtsRecord>()

    @Volatile
    private var settings: DuganSettings = DuganSettings()

    /** Counters surfaced in the debug readout so tuning is not guesswork. */
    data class Counters(
        val framesGated: Long = 0,
        val framesPassedDuringSpeech: Long = 0,
        val echoesRejectedByText: Long = 0,
        val escalationsToLlm: Long = 0,
    )

    @Volatile
    private var counters = Counters()

    init {
        // Silence the unused-parameter warning without dropping the dependency:
        // the cache is warmed with the same phrases we defend against.
        Log.d(TAG, "echo defence ready, warm phrases=${TtsCache.WarmPhrases.size}")
    }

    fun configure(newSettings: DuganSettings) {
        settings = newSettings
    }

    fun snapshotCounters(): Counters = counters

    /** Records what the agent just said, so later turns can be compared against it. */
    fun recordAgentSpeech(text: String) {
        if (text.isBlank()) return
        val record = TtsRecord(
            text = text,
            timestampMs = System.currentTimeMillis(),
            embedding = embeddings.embed(text),
        )
        synchronized(recentTTS) {
            recentTTS.addLast(record)
            while (recentTTS.size > HISTORY) recentTTS.removeFirst()
        }
    }

    fun recentAgentSpeech(): List<String> = synchronized(recentTTS) { recentTTS.map { it.text } }

    fun clearHistory() = synchronized(recentTTS) { recentTTS.clear() }

    // -- Layer 3: mic gating -------------------------------------------------

    /**
     * The nuclear option: while the agent is audibly speaking, throw the mic
     * data away. 100% effective against self-echo, at the cost of not hearing a
     * genuine interruption -- which is what layer 4 exists to buy back.
     *
     * @return true when the frame should be discarded.
     */
    fun shouldGate(frame: AudioFrame): Boolean {
        if (!settings.micGatingEnabled) return false
        if (!playback.isPlaying) return false
        // Barge-in enabled means we let speech through and arbitrate in layer 4.
        if (settings.bargeInEnabled && frame.rms > BARGE_IN_LEVEL) return false
        val gated = playback.wasSpeakingAt(frame.timestampMs)
        if (gated) counters = counters.copy(framesGated = counters.framesGated + 1)
        return gated
    }

    // -- Layer 4: echo-aware VAD / barge-in ----------------------------------

    /**
     * True when speech arriving during playback is a real human rather than the
     * agent's own voice leaking back through the loudspeaker.
     *
     * Normalised cross-correlation against the far-end reference: high
     * correlation means the mic is hearing the speaker, low means something else
     * is making noise in the room.
     */
    fun isBargeIn(frame: AudioFrame): Boolean {
        if (!settings.bargeInEnabled) return false
        if (!playback.wasSpeakingAt(frame.timestampMs)) return true

        val reference = playback.referenceAround(frame.timestampMs, windowMs = 250)
        if (reference.isEmpty()) return true

        val correlation = correlate(frame.samples, reference)
        counters = counters.copy(framesPassedDuringSpeech = counters.framesPassedDuringSpeech + 1)
        return correlation < ECHO_CORRELATION_THRESHOLD
    }

    /**
     * Zero-lag normalised cross-correlation over the overlapping tail.
     * Deliberately cheap: this runs on the audio thread.
     */
    fun correlate(nearEnd: ShortArray, farEnd: ShortArray): Float {
        if (nearEnd.isEmpty() || farEnd.isEmpty()) return 0f
        val n = minOf(nearEnd.size, farEnd.size)
        val nearOffset = nearEnd.size - n
        val farOffset = farEnd.size - n

        var dot = 0.0
        var energyNear = 0.0
        var energyFar = 0.0
        for (i in 0 until n) {
            val a = nearEnd[nearOffset + i].toDouble()
            val b = farEnd[farOffset + i].toDouble()
            dot += a * b
            energyNear += a * a
            energyFar += b * b
        }
        if (energyNear == 0.0 || energyFar == 0.0) return 0f
        return (dot / (sqrt(energyNear) * sqrt(energyFar))).toFloat().coerceIn(-1f, 1f)
    }

    // -- Layer 5: text-level defence -----------------------------------------

    /**
     * Runs 5a then 5b, and only escalates to 5c when they disagree.
     */
    suspend fun classifyTranscript(candidate: String): EchoDecision {
        if (!settings.textEchoDefenseEnabled || candidate.isBlank()) {
            return EchoDecision(EchoVerdict.New, "disabled", 1f)
        }
        val records = synchronized(recentTTS) { recentTTS.toList() }
        if (records.isEmpty()) return EchoDecision(EchoVerdict.New, "no-history", 1f)

        // 5a: lexical.
        val lexical = records.maxOf { max(fuzzy.bestSuffixSimilarity(candidate, it), fuzzy.tokenOverlap(candidate, it.normalized)) }
        if (lexical >= LEXICAL_ECHO) {
            counters = counters.copy(echoesRejectedByText = counters.echoesRejectedByText + 1)
            return EchoDecision(EchoVerdict.Echo, "5a-lexical", lexical)
        }

        // 5b: semantic.
        val candidateEmbedding = embeddings.embed(candidate)
        val semantic = if (candidateEmbedding == null) {
            0f
        } else {
            records.mapNotNull { it.embedding }
                .maxOfOrNull { HashingEmbeddingMatcher.cosine(candidateEmbedding, it) } ?: 0f
        }
        if (semantic >= SEMANTIC_ECHO) {
            counters = counters.copy(echoesRejectedByText = counters.echoesRejectedByText + 1)
            return EchoDecision(EchoVerdict.Echo, "5b-semantic", semantic)
        }

        // Confident on both counts and they agree it is new -> done.
        if (lexical < LEXICAL_NEW && semantic < SEMANTIC_NEW) {
            return EchoDecision(EchoVerdict.New, "5ab-agree", 1f - max(lexical, semantic))
        }

        // 5c: escalate.
        counters = counters.copy(escalationsToLlm = counters.escalationsToLlm + 1)
        val isEcho = verifier.isEcho(candidate, records.map { it.text })
        return EchoDecision(
            verdict = if (isEcho) EchoVerdict.Echo else EchoVerdict.New,
            layer = "5c-llm",
            confidence = 0.9f,
        )
    }

    private companion object {
        const val TAG = "EchoDefense"
        const val HISTORY = 5
        const val ECHO_CORRELATION_THRESHOLD = 0.55f
        const val BARGE_IN_LEVEL = 0.12f
        const val LEXICAL_ECHO = 0.85f
        const val SEMANTIC_ECHO = 0.9f
        const val LEXICAL_NEW = 0.45f
        const val SEMANTIC_NEW = 0.6f
    }
}
