package com.dugan.agent.domain.audio

import javax.inject.Inject
import javax.inject.Singleton

/** Voice activity decision for a single frame. */
data class VadDecision(
    val isSpeech: Boolean,
    /** 0f..1f confidence. Energy VAD reports a normalised level; Silero a probability. */
    val probability: Float,
)

/**
 * Frame-level VAD.
 *
 * Implementations must be cheap enough to run on every 20ms frame on the audio
 * thread. Silero v5 costs ~0.03ms/frame on a mid-range SoC; the energy detector
 * here is free and keeps the app functional without any bundled model.
 */
interface VadEngine {
    val name: String
    fun reset()
    fun process(frame: AudioFrame, sensitivity: Float): VadDecision
}

/**
 * Adaptive-threshold energy VAD.
 *
 * Tracks a running noise floor so it copes with a moving car or a fan without
 * manual recalibration, then requires a few consecutive voiced frames before it
 * commits -- a single cough should not start a turn.
 */
@Singleton
class EnergyVadEngine @Inject constructor() : VadEngine {

    override val name: String = "energy"

    private var noiseFloor: Float = 0f
    private var voicedRun: Int = 0
    private var silentRun: Int = 0
    private var inSpeech: Boolean = false

    override fun reset() {
        noiseFloor = 0f
        voicedRun = 0
        silentRun = 0
        inSpeech = false
    }

    override fun process(frame: AudioFrame, sensitivity: Float): VadDecision {
        val level = frame.rms

        // Two floors, take the stricter. The adaptive one tracks the room; the
        // absolute one stops a dead-silent room from making the detector fire on
        // nothing. sensitivity 0 -> permissive, 1 -> needs a loud, clear voice.
        val adaptive = noiseFloor * (FLOOR_MULTIPLIER + sensitivity * FLOOR_SENSITIVITY_GAIN) + MIN_DELTA
        val absolute = ABSOLUTE_FLOOR * (1f + sensitivity * ABSOLUTE_SENSITIVITY_GAIN)
        val threshold = maxOf(adaptive, absolute)
        val voiced = level > threshold

        // Only adapt the floor from frames we did NOT classify as speech. Updating
        // it during speech makes the detector learn the caller's own voice as
        // "noise" and then stop firing for the rest of the call.
        if (!voiced) {
            noiseFloor = if (noiseFloor == 0f) level else noiseFloor * 0.95f + level * 0.05f
        }

        if (voiced) {
            voicedRun++
            silentRun = 0
        } else {
            silentRun++
            voicedRun = 0
        }

        // 3 frames (60ms) to open so a cough does not start a turn; 15 frames
        // (300ms) to close so a mid-sentence breath does not end one.
        if (!inSpeech && voicedRun >= VOICE_ON_FRAMES) inSpeech = true
        if (inSpeech && silentRun >= VOICE_OFF_FRAMES) inSpeech = false

        return VadDecision(isSpeech = inSpeech, probability = (level / (threshold + 1e-6f)).coerceIn(0f, 1f))
    }

    private companion object {
        /** ~-46 dBFS: below this is room tone on any reasonable phone mic. */
        const val ABSOLUTE_FLOOR = 0.005f
        const val MIN_DELTA = 0.01f
        const val FLOOR_MULTIPLIER = 2f
        const val FLOOR_SENSITIVITY_GAIN = 6f
        const val ABSOLUTE_SENSITIVITY_GAIN = 8f
        /** 3 frames = 60ms of voice to open, 15 frames = 300ms to close. */
        const val VOICE_ON_FRAMES = 3
        const val VOICE_OFF_FRAMES = 15
    }
}

/**
 * Silero VAD v5 via LiteRT.
 *
 * The 1.26MB FP32 model is NOT vendored in this repository -- drop it at
 * `app/src/main/assets/models/silero_vad.tflite` and this engine activates
 * itself. Until then [isAvailable] is false and the orchestrator stays on
 * [EnergyVadEngine], so nothing in the build depends on the asset.
 *
 * Model contract: input `[1, 512]` float32 @16kHz plus `[2, 1, 128]` state,
 * output `[1, 1]` speech probability and the updated state.
 */
class SileroVadEngine(
    private val interpreter: Any?,
) : VadEngine {

    override val name: String = "silero-v5"

    private val state = Array(2) { Array(1) { FloatArray(128) } }
    private val window = FloatArray(CHUNK)
    private var windowPos = 0

    override fun reset() {
        state.forEach { plane -> plane.forEach { it.fill(0f) } }
        windowPos = 0
    }

    override fun process(frame: AudioFrame, sensitivity: Float): VadDecision {
        if (interpreter == null) return VadDecision(isSpeech = false, probability = 0f)
        // Silero wants exactly 512 samples at 16kHz; frames arrive in 320-sample
        // blocks, so buffer across calls.
        var decision = VadDecision(isSpeech = false, probability = 0f)
        for (s in frame.samples) {
            window[windowPos++] = s / 32768f
            if (windowPos == CHUNK) {
                decision = run(window, sensitivity)
                windowPos = 0
            }
        }
        return decision
    }

    /** Reflection keeps the LiteRT dependency optional. */
    private fun run(chunk: FloatArray, sensitivity: Float): VadDecision = runCatching {
        val input = arrayOf<Array<FloatArray>>(arrayOf(chunk))
        val outputs = HashMap<Int, Any>()
        val invoke = interpreter!!.javaClass.getMethod("runForMultipleInputsOutputs", Array<Any>::class.java, Map::class.java)
        invoke.invoke(interpreter, arrayOf(input, state), outputs)
        @Suppress("UNCHECKED_CAST")
        val prob = ((outputs[0] as? Array<Array<FloatArray>>)?.get(0)?.get(0))?.get(0) ?: 0f
        VadDecision(isSpeech = prob > (0.3f + sensitivity * 0.4f), probability = prob)
    }.getOrDefault(VadDecision(isSpeech = false, probability = 0f))

    companion object {
        const val CHUNK = 512
        const val ASSET_PATH = "models/silero_vad.tflite"
        const val SAMPLE_RATE = 16_000

        /** Loads the model if it is present; returns null otherwise. */
        fun loadOrNull(assets: android.content.res.AssetManager): SileroVadEngine? = runCatching {
            assets.open(ASSET_PATH).use { it.readBytes() }
            val clazz = Class.forName("org.tensorflow.lite.Interpreter")
            val optionsClass = Class.forName("org.tensorflow.lite.Interpreter\$Options")
            val options = optionsClass.getConstructor().newInstance()
            optionsClass.getMethod("setNumThreads", Int::class.java).invoke(options, 1)
            val interpreter = clazz.getConstructor(ByteArray::class.java, optionsClass)
                .newInstance(assets.open(ASSET_PATH).use { it.readBytes() }, options)
            SileroVadEngine(interpreter)
        }.getOrNull()
    }
}
