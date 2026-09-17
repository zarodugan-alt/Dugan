package com.dugan.agent.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyVadEngineTest {

    private val vad = EnergyVadEngine()

    private fun frame(level: Short, samples: Int = 320) = AudioFrame(
        samples = ShortArray(samples) { level },
        rms = Pcm.rms(ShortArray(samples) { level }),
        timestampMs = 0,
        sampleRate = 16_000,
    )

    @Test
    fun `silence never opens speech`() {
        repeat(50) {
            assertFalse(vad.process(frame(0), sensitivity = 0.5f).isSpeech)
        }
    }

    @Test
    fun `sustained loud audio opens speech after a few frames`() {
        var opened = false
        repeat(20) {
            if (vad.process(frame(9000), sensitivity = 0.5f).isSpeech) opened = true
        }
        assertTrue("VAD never opened on sustained loud audio", opened)
    }

    @Test
    fun `a single frame does not open speech`() {
        vad.reset()
        repeat(10) { vad.process(frame(0), 0.5f) } // establish a noise floor
        assertFalse(vad.process(frame(9000), 0.5f).isSpeech)
    }

    @Test
    fun `reset clears the tracked state`() {
        repeat(20) { vad.process(frame(9000), 0.5f) }
        vad.reset()
        assertFalse(vad.process(frame(0), 0.5f).isSpeech)
    }

    @Test
    fun `higher sensitivity needs a louder signal`() {
        vad.reset()
        val strict = EnergyVadEngine()
        repeat(10) {
            vad.process(frame(1200), sensitivity = 0f)
            strict.process(frame(1200), sensitivity = 1f)
        }
        assertTrue(vad.process(frame(1200), 0f).isSpeech)
        assertFalse(strict.process(frame(1200), 1f).isSpeech)
    }
}

class SilenceEotDetectorTest {

    private val eot = SilenceEotDetector()

    @Test
    fun `a complete question ends the turn`() {
        assertTrue(eot.turnEndProbability("what time is it?", ShortArray(0)) > 0.8f)
    }

    @Test
    fun `a dangling connector holds the turn open`() {
        assertTrue(eot.turnEndProbability("I would like to", ShortArray(0)) < 0.3f)
    }

    @Test
    fun `an empty transcript never ends the turn`() {
        assertEquals(0f, eot.turnEndProbability("", ShortArray(0)), 0.001f)
    }

    @Test
    fun `a bare statement is ambiguous rather than decisive`() {
        val p = eot.turnEndProbability("the weather", ShortArray(0))
        assertTrue("expected an uncertain probability, got $p", p in 0.2f..0.8f)
    }
}

class EotControllerTest {

    private val controller = EotController(SilenceEotDetector())

    @Test
    fun `falls back to the silence detector when no model is attached`() {
        assertEquals("silence", controller.active.name)
    }

    @Test
    fun `closes the turn on the silence threshold`() {
        assertTrue(
            controller.endOfTurn(
                transcript = "tell me about the weather",
                trailingAudio = ShortArray(0),
                silenceMs = 900,
                silenceThresholdMs = 700,
            ),
        )
    }

    @Test
    fun `holds the turn while silence is below the threshold`() {
        assertFalse(
            controller.endOfTurn(
                transcript = "tell me about",
                trailingAudio = ShortArray(0),
                silenceMs = 200,
                silenceThresholdMs = 700,
            ),
        )
    }

    @Test
    fun `a confidently complete utterance can close early`() {
        assertTrue(
            controller.endOfTurn(
                transcript = "what time is it?",
                trailingAudio = ShortArray(0),
                silenceMs = 0,
                silenceThresholdMs = 700,
            ),
        )
    }
}
