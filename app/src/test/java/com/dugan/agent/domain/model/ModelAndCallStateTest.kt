package com.dugan.agent.domain.model

import com.dugan.agent.data.api.AgentLlm
import com.dugan.agent.data.api.GeminiLlmClient
import com.dugan.agent.data.api.GroqLlmClient
import com.dugan.agent.data.api.HttpClientFactory
import com.dugan.agent.data.local.InMemoryKeyVault
import com.dugan.agent.domain.latency.ModelRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRouterHeuristicTest {

    private val vault = InMemoryKeyVault()
    private val http = HttpClientFactory()
    private val router = ModelRouter(
        AgentLlm(GeminiLlmClient(vault, http), GroqLlmClient(vault, http)),
    )

    @Test
    fun `greetings and one word answers are simple`() {
        assertEquals(ModelRouter.Complexity.Simple, router.heuristic("yes"))
        assertEquals(ModelRouter.Complexity.Simple, router.heuristic("thanks"))
        assertEquals(ModelRouter.Complexity.Simple, router.heuristic("hi there"))
    }

    @Test
    fun `planning and comparison requests are complex`() {
        assertEquals(ModelRouter.Complexity.Complex, router.heuristic("compare these two options"))
        assertEquals(ModelRouter.Complexity.Complex, router.heuristic("explain why that happens"))
        assertEquals(ModelRouter.Complexity.Complex, router.heuristic("plan my week step by step"))
    }

    @Test
    fun `an empty transcript is undecided`() {
        assertNull(router.heuristic(""))
    }

    @Test
    fun `a mid length neutral request is left to the classifier`() {
        assertNull(router.heuristic("what are the opening hours tomorrow"))
    }

    @Test
    fun `routing is disabled returns the preferred model untouched`() = kotlinx.coroutines.test.runTest {
        val preferred = ModelCatalog.ThinkingModels.last()
        assertEquals(preferred, router.route("yes", preferred, enabled = false))
    }

    @Test
    fun `the catalogue ships at least one small router model`() {
        assertTrue(ModelCatalog.ThinkingModels.any { it.isRouterCandidate })
        assertEquals(ModelCatalog.DefaultRouter, ModelCatalog.ThinkingModels[1])
    }
}

class CallStateTest {

    @Test
    fun `no call allows capture in standalone mode`() {
        assertTrue(CallState.NoCall.canCapture)
        assertFalse(CallState.NoCall.hasCall)
    }

    @Test
    fun `voip captures as soon as a call exists`() {
        assertTrue(CallState(transport = CallTransport.Voip).canCapture)
    }

    @Test
    fun `sim capture needs the loudspeaker on`() {
        assertFalse(CallState(transport = CallTransport.Sim, isSpeakerphoneOn = false).canCapture)
        assertTrue(CallState(transport = CallTransport.Sim, isSpeakerphoneOn = true).canCapture)
    }

    @Test
    fun `transport defaults to none`() {
        assertEquals(CallTransport.None, CallState().transport)
    }
}

class ModelCatalogTest {

    @Test
    fun `every thinking model supports thinking`() {
        assertTrue(ModelCatalog.ThinkingModels.all { it.supportsThinking })
    }

    @Test
    fun `stt and tts models are not thinking models`() {
        assertTrue(ModelCatalog.SttModels.none { it.supportsThinking })
        assertTrue(ModelCatalog.TtsModels.none { it.supportsThinking })
    }

    @Test
    fun `all ids are unique across the catalogue`() {
        val all = ModelCatalog.ThinkingModels + ModelCatalog.SttModels + ModelCatalog.TtsModels
        assertEquals(all.size, all.map { it.id }.distinct().size)
    }

    @Test
    fun `byId resolves every catalogue entry and null otherwise`() {
        (ModelCatalog.ThinkingModels + ModelCatalog.SttModels + ModelCatalog.TtsModels).forEach {
            assertEquals(it.id, ModelCatalog.byId(it.id)?.id)
        }
        assertNull(ModelCatalog.byId("does-not-exist"))
    }

    @Test
    fun `settings fall back to defaults for unknown model ids`() {
        val settings = DuganSettings(sttModelId = "nope", ttsModelId = "nope", thinkingModelId = "nope")
        assertEquals(ModelCatalog.DefaultStt, settings.sttModel)
        assertEquals(ModelCatalog.DefaultTts, settings.ttsModel)
        assertEquals(ModelCatalog.DefaultThinking, settings.thinkingModel)
    }

    @Test
    fun `answer delay converts seconds to millis`() {
        assertEquals(3_000L, DuganSettings(answerDelaySeconds = 3).answerDelayMs)
    }

    @Test
    fun `theme id defaults to crimson noir`() {
        assertEquals("crimson_noir", DuganSettings().themeId)
    }
}
