package com.dugan.agent.domain.audio

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Which echo-defence layers are live right now. Shown in Settings > Audio. */
data class AecStatus(
    val hardwareAec: Boolean = false,
    val softwareAec: Boolean = false,
    val micGating: Boolean = false,
    val bargeIn: Boolean = false,
    val textDefense: Boolean = false,
) {
    val activeLayerCount: Int =
        listOf(hardwareAec, softwareAec, micGating, bargeIn, textDefense).count { it }
}

/**
 * Layer 2: software acoustic echo cancellation.
 *
 * `nearEnd` is the microphone, `farEnd` is what we just pushed to AudioTrack.
 * Implementations return the near-end signal with the far-end component
 * subtracted.
 */
interface AecProcessor {
    val name: String
    val isActive: Boolean
    fun reset()
    fun process(nearEnd: ShortArray, farEnd: ShortArray, strength: Float): ShortArray
}

/**
 * Pass-through used when the platform's own AEC is doing the job. Keeping it as
 * an explicit type means the orchestrator always has a processor to call and the
 * layer accounting stays honest.
 */
@Singleton
class PassthroughAec @Inject constructor() : AecProcessor {
    override val name: String = "none"
    override val isActive: Boolean = false
    override fun reset() = Unit
    override fun process(nearEnd: ShortArray, farEnd: ShortArray, strength: Float): ShortArray = nearEnd
}

/**
 * Normalised LMS adaptive filter -- the same family of algorithm as WebRTC's
 * AEC3, at a fraction of the sophistication.
 *
 * Honest scope: a time-domain NLMS with a 256-tail (16ms @16kHz) handles the
 * direct loudspeaker path, which is the dominant echo term on a phone held at
 * arm's length on speakerphone. It will not cancel long room reverberation the
 * way a partitioned frequency-domain AEC3 would. For that, drop in the NDK build
 * of `kyralo/android-webrtc-aec3` and implement [AecProcessor] over it -- the
 * orchestrator does not care which one it gets.
 */
@Singleton
class SoftwareAec @Inject constructor() : AecProcessor {

    override val name: String = "nlms-256"

    @Volatile
    override var isActive: Boolean = false
        private set

    private val weights = FloatArray(TAPS)
    private val farHistory = FloatArray(TAPS)

    override fun reset() {
        weights.fill(0f)
        farHistory.fill(0f)
        isActive = false
    }

    override fun process(nearEnd: ShortArray, farEnd: ShortArray, strength: Float): ShortArray {
        if (strength <= 0f) return nearEnd
        if (farEnd.isEmpty()) return nearEnd

        isActive = true
        // Resample the far-end reference onto the mic clock. The TTS engine runs
        // at 24kHz and the mic at 16kHz; a linear resample is enough for the
        // filter to converge because it only needs phase alignment, not fidelity.
        val reference = if (farEnd.size >= nearEnd.size) {
            farEnd.copyOfRange(farEnd.size - nearEnd.size, farEnd.size)
        } else {
            ShortArray(nearEnd.size - farEnd.size) + farEnd
        }

        val mu = STEP_SIZE * strength
        val out = ShortArray(nearEnd.size)

        for (i in nearEnd.indices) {
            // Shift the far-end history and insert the newest reference sample.
            System.arraycopy(farHistory, 1, farHistory, 0, TAPS - 1)
            farHistory[TAPS - 1] = reference[i] / 32768f

            var estimate = 0f
            var norm = EPSILON
            for (t in 0 until TAPS) {
                val x = farHistory[t]
                estimate += weights[t] * x
                norm += x * x
            }

            val d = nearEnd[i] / 32768f
            val error = d - estimate

            val update = mu * error / norm
            for (t in 0 until TAPS) weights[t] += update * farHistory[t]

            out[i] = (error * 32768f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** Divergence guard: a filter that blows up is worse than no filter. */
    fun isDiverged(): Boolean = weights.any { abs(it) > 8f }

    private companion object {
        const val TAPS = 256
        const val STEP_SIZE = 0.4f
        const val EPSILON = 1e-6f
    }
}

/** Picks the strongest available processor and keeps the status honest. */
@Singleton
class AecController @Inject constructor(
    private val software: SoftwareAec,
    private val passthrough: PassthroughAec,
) {
    fun select(hardwareAecAvailable: Boolean, softwareEnabled: Boolean): AecProcessor =
        when {
            softwareEnabled -> software
            else -> passthrough
        }

    fun status(
        hardwareAecAvailable: Boolean,
        softwareEnabled: Boolean,
        micGating: Boolean,
        bargeIn: Boolean,
        textDefense: Boolean,
    ): AecStatus = AecStatus(
        hardwareAec = hardwareAecAvailable,
        softwareAec = softwareEnabled && software.isActive,
        micGating = micGating,
        bargeIn = bargeIn,
        textDefense = textDefense,
    )
}
