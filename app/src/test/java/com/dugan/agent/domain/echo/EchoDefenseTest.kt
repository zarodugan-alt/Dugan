package com.dugan.agent.domain.echo

import com.dugan.agent.data.api.AgentLlm
import com.dugan.agent.data.api.GeminiLlmClient
import com.dugan.agent.data.api.GroqLlmClient
import com.dugan.agent.data.api.HttpClientFactory
import com.dugan.agent.data.local.InMemoryKeyVault
import com.dugan.agent.data.local.TtsCache
import com.dugan.agent.domain.audio.AudioPlaybackEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    private val matcher = FuzzyMatcher()

    @Test
    fun `identical strings score one`() {
        assertEquals(1f, matcher.similarity("hello world", "hello world"), 0.001f)
    }

    @Test
    fun `similarity ignores case and punctuation`() {
        assertTrue(matcher.similarity("It's fine.", "its fine") > 0.99f)
    }

    @Test
    fun `unrelated strings score low`() {
        assertTrue(matcher.similarity("the weather is sunny", "call john right now") < 0.4f)
    }

    @Test
    fun `levenshtein counts single edits`() {
        assertEquals(0, matcher.levenshtein("kitten", "kitten"))
        assertEquals(3, matcher.levenshtein("kitten", "sitting"))
        assertEquals(5, matcher.levenshtein("", "hello"))
    }

    @Test
    fun `token overlap is order independent`() {
        val overlap = matcher.tokenOverlap(
            "it is seventy two degrees and sunny",
            "sunny and seventy two degrees it is",
        )
        assertEquals(1f, overlap, 0.001f)
    }

    @Test
    fun `suffix matching catches a partial echo of a long reply`() {
        val record = TtsRecord(
            text = "It's 72 degrees and sunny in your area today.",
            timestampMs = 0,
        )
        val partial = "sunny in your area today"
        assertTrue(matcher.bestSuffixSimilarity(partial, record) > 0.8f)
    }

    @Test
    fun `empty inputs do not divide by zero`() {
        assertEquals(0f, matcher.similarity("", "something"), 0.001f)
        assertEquals(0f, matcher.tokenOverlap("", ""), 0.001f)
        assertEquals(1f, matcher.similarity("", ""), 0.001f)
    }
}

class EmbeddingMatcherTest {

    private val matcher = HashingEmbeddingMatcher()

    @Test
    fun `embeddings are unit normalised`() {
        val vector = matcher.embed("the quick brown fox")!!
        val magnitude = Math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1f, magnitude, 0.01f)
    }

    @Test
    fun `identical text is perfectly similar`() {
        val a = matcher.embed("what time is it")!!
        val b = matcher.embed("what time is it")!!
        assertEquals(1f, HashingEmbeddingMatcher.cosine(a, b), 0.001f)
    }

    @Test
    fun `reordered words stay similar because trigrams are order free`() {
        val a = matcher.embed("it is sunny and warm today")!!
        val b = matcher.embed("today it is warm and sunny")!!
        assertTrue(HashingEmbeddingMatcher.cosine(a, b) > 0.7f)
    }

    @Test
    fun `too short to embed returns null`() {
        assertEquals(null, matcher.embed("hi"))
    }
}

class EchoDefenseEngineTest {

    private fun engine(): EchoDefenseEngine {
        val vault = InMemoryKeyVault()
        val http = HttpClientFactory()
        // The verifier is only reached when 5a and 5b disagree; these tests never
        // get there, so the clients are constructed but never make a request.
        val llm = AgentLlm(GeminiLlmClient(vault, http), GroqLlmClient(vault, http))
        return EchoDefenseEngine(
            playback = AudioPlaybackEngine(),
            fuzzy = FuzzyMatcher(),
            embeddings = HashingEmbeddingMatcher(),
            verifier = LlmEchoVerifier(llm),
            ttsCache = TtsCache(),
        )
    }

    @Test
    fun `a verbatim echo is rejected by the lexical layer`() = runTest {
        val engine = engine()
        engine.recordAgentSpeech("It's 72 degrees and sunny in your area.")

        val decision = engine.classifyTranscript("It's 72 degrees and sunny in your area.")
        assertEquals(EchoVerdict.Echo, decision.verdict)
        assertEquals("5a-lexical", decision.layer)
    }

    @Test
    fun `new human speech is accepted`() = runTest {
        val engine = engine()
        engine.recordAgentSpeech("It's 72 degrees and sunny in your area.")

        val decision = engine.classifyTranscript("Please set an alarm for seven tomorrow morning.")
        assertEquals(EchoVerdict.New, decision.verdict)
    }

    @Test
    fun `with no history everything is new`() = runTest {
        val engine = engine()
        val decision = engine.classifyTranscript("It's 72 degrees and sunny in your area.")
        assertEquals(EchoVerdict.New, decision.verdict)
        assertEquals("no-history", decision.layer)
    }

    @Test
    fun `blank transcripts short circuit`() = runTest {
        val engine = engine()
        engine.recordAgentSpeech("Hello there.")
        val decision = engine.classifyTranscript("   ")
        assertEquals(EchoVerdict.New, decision.verdict)
    }

    @Test
    fun `history is capped at five records`() {
        val engine = engine()
        repeat(8) { engine.recordAgentSpeech("sentence number $it is here") }
        assertEquals(5, engine.recentAgentSpeech().size)
        // The oldest three should have been dropped.
        assertTrue(engine.recentAgentSpeech().none { it.contains("number 0") })
        assertTrue(engine.recentAgentSpeech().any { it.contains("number 7") })
    }

    @Test
    fun `correlation of a signal with itself is one`() {
        val engine = engine()
        val signal = ShortArray(256) { (it * 37 % 1000).toShort() }
        assertEquals(1f, engine.correlate(signal, signal), 0.01f)
    }

    @Test
    fun `correlation of unrelated signals is low`() {
        val engine = engine()
        val a = ShortArray(256) { (it * 37 % 1000).toShort() }
        val b = ShortArray(256) { ((255 - it) * 91 % 1000).toShort() }
        assertTrue(engine.correlate(a, b) < 0.9f)
    }

    @Test
    fun `correlation with an empty reference is zero`() {
        val engine = engine()
        assertEquals(0f, engine.correlate(ShortArray(64) { 100 }, ShortArray(0)), 0.001f)
    }
}
