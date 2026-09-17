package com.dugan.agent.domain.audio

import javax.inject.Inject
import javax.inject.Singleton

/**
 * End-of-turn decision.
 *
 * A fixed silence threshold costs ~700ms of dead air on every turn because it
 * cannot tell "the user is thinking" from "the user is finished". A semantic
 * model reads the last few seconds and answers the question directly, which is
 * where most of the perceived latency win comes from.
 */
interface EotDetector {
    val name: String

    /**
     * @param transcript best STT so far for this turn
     * @param trailingAudio last N seconds of capture
     * @return probability the speaker has finished, 0f..1f
     */
    fun turnEndProbability(transcript: String, trailingAudio: ShortArray): Float
}

/**
 * Silence-threshold fallback, always available.
 *
 * Cheap grammar heuristics let it close a turn early on an obviously complete
 * utterance ("what time is it?") and hold longer on an obviously incomplete one
 * ("I'd like to..."), which recovers a lot of the semantic model's advantage
 * with none of its cost.
 */
@Singleton
class SilenceEotDetector @Inject constructor() : EotDetector {

    override val name: String = "silence"

    override fun turnEndProbability(transcript: String, trailingAudio: ShortArray): Float {
        val text = transcript.trim()
        if (text.isEmpty()) return 0f

        val endsSentence = text.last() in charArrayOf('?', '!', '.')
        val endsWithOpenConnector = text.lowercase().endsWithAnyOf(DANGLING)
        val hasMainVerb = text.split(' ').size >= 2

        return when {
            endsWithOpenConnector -> 0.1f
            endsSentence && hasMainVerb -> 0.95f
            hasMainVerb -> 0.6f
            else -> 0.3f
        }
    }

    private fun String.endsWithAnyOf(suffixes: List<String>): Boolean =
        suffixes.any { endsWith(it) }

    private companion object {
        val DANGLING = listOf(
            " the", " a", " an", " and", " or", " but", " to", " of", " for",
            " with", " that", " because", " if", " when", " my", " i",
        )
    }
}

/**
 * Smart Turn v3.2 via ONNX Runtime.
 *
 * Like Silero, the ~11MB int8 model is not vendored. Drop
 * `app/src/main/assets/models/smart_turn_v3.2.onnx` in place and this activates;
 * otherwise [EotController] falls back to [SilenceEotDetector].
 *
 * Model contract: 16kHz audio, last 8 seconds, log-mel front end, single sigmoid
 * output = P(turn complete).
 */
class SmartTurnEotDetector(
    private val session: Any?,
) : EotDetector {

    override val name: String = "smart-turn-v3.2"

    override fun turnEndProbability(transcript: String, trailingAudio: ShortArray): Float {
        if (session == null || trailingAudio.isEmpty()) return 0f
        val window = if (trailingAudio.size > WINDOW) {
            trailingAudio.copyOfRange(trailingAudio.size - WINDOW, trailingAudio.size)
        } else {
            trailingAudio
        }
        val input = FloatArray(window.size) { window[it] / 32768f }
        return runCatching {
            val onnxTensor = Class.forName("ai.onnxruntime.OnnxTensor")
            val env = Class.forName("ai.onnxruntime.OrtEnvironment")
                .getMethod("getEnvironment").invoke(null)
            val tensor = onnxTensor.getMethod(
                "createTensor",
                env.javaClass,
                java.nio.FloatBuffer::class.java,
                LongArray::class.java,
            ).invoke(null, env, java.nio.FloatBuffer.wrap(input), longArrayOf(1, input.size.toLong()))
            val result = session!!.javaClass
                .getMethod("run", Map::class.java)
                .invoke(session, mapOf(INPUT_NAME to tensor))
            @Suppress("UNCHECKED_CAST")
            val first = (result as List<Any>).first()
            val value = first.javaClass.getMethod("getValue").invoke(first)
            ((value as Array<FloatArray>)[0][0]).coerceIn(0f, 1f)
        }.getOrDefault(0f)
    }

    companion object {
        const val INPUT_NAME = "audio"
        /** 8 seconds @ 16kHz, matching the published model's context. */
        const val WINDOW = 8 * 16_000
        const val ASSET_PATH = "models/smart_turn_v3.2.onnx"

        fun loadOrNull(assets: android.content.res.AssetManager): SmartTurnEotDetector? = runCatching {
            val bytes = assets.open(ASSET_PATH).use { it.readBytes() }
            val env = Class.forName("ai.onnxruntime.OrtEnvironment")
                .getMethod("getEnvironment").invoke(null)
            val session = env.javaClass
                .getMethod("createSession", ByteArray::class.java)
                .invoke(env, bytes)
            SmartTurnEotDetector(session)
        }.getOrNull()
    }
}

/** Chooses the best detector that is actually loaded. */
@Singleton
class EotController @Inject constructor(
    private val silence: SilenceEotDetector,
) {
    private var smartTurn: SmartTurnEotDetector? = null

    fun attach(detector: SmartTurnEotDetector?) {
        smartTurn = detector
    }

    val active: EotDetector get() = smartTurn ?: silence

    /**
     * Blend semantic confidence with the silence timer: whichever is more
     * certain wins, and a very confident model can close the turn before the
     * silence threshold elapses at all.
     */
    fun endOfTurn(
        transcript: String,
        trailingAudio: ShortArray,
        silenceMs: Int,
        silenceThresholdMs: Int,
    ): Boolean {
        val semantic = active.turnEndProbability(transcript, trailingAudio)
        if (semantic >= HIGH_CONFIDENCE) return true
        if (semantic <= LOW_CONFIDENCE && silenceMs < silenceThresholdMs) return false
        return silenceMs >= silenceThresholdMs
    }

    private companion object {
        const val HIGH_CONFIDENCE = 0.8f
        const val LOW_CONFIDENCE = 0.2f
    }
}
