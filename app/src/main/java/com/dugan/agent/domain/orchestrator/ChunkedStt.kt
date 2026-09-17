package com.dugan.agent.domain.orchestrator

import com.dugan.agent.data.api.SttClient
import com.dugan.agent.data.api.SttResult
import com.dugan.agent.domain.audio.Pcm
import com.dugan.agent.domain.model.AgentModel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 3-second chunked STT with 500ms overlap.
 *
 * Groq has no streaming transcription endpoint, so incremental STT means cutting
 * the utterance into overlapping windows and transcribing each. The overlap
 * stops words being severed at a boundary; [deduplicate] then removes the
 * duplicated words the overlap re-produces.
 */
@Singleton
class ChunkedStt @Inject constructor() {

    data class Config(
        val chunkMs: Int = 3_000,
        val overlapMs: Int = 500,
        /** Compare this many trailing words when stitching chunks. */
        val dedupeWords: Int = 5,
    )

    /**
     * Transcribes [pcm] as a sequence of overlapping chunks.
     * [onPartial] fires after each chunk lands, so the UI can update live and the
     * speculative executor can act before the utterance is finished.
     */
    suspend fun transcribe(
        client: SttClient,
        model: AgentModel,
        pcm: ShortArray,
        sampleRate: Int,
        config: Config = Config(),
        onPartial: (String) -> Unit = {},
    ): SttResult {
        if (pcm.isEmpty()) return SttResult("")

        val chunkSamples = sampleRate * config.chunkMs / 1000
        val overlapSamples = sampleRate * config.overlapMs / 1000

        // Short utterance: one request is both faster and more accurate.
        if (pcm.size <= chunkSamples) {
            val single = client.transcribe(model, pcm, sampleRate)
            onPartial(single.text)
            return single
        }

        val merged = StringBuilder()
        var offset = 0
        var lastChunkWords: List<String> = emptyList()

        while (offset < pcm.size) {
            val end = minOf(offset + chunkSamples, pcm.size)
            val slice = pcm.copyOfRange(offset, end)
            val result = client.transcribe(model, slice, sampleRate)
            val words = result.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

            if (merged.isEmpty()) {
                merged.append(result.text.trim())
            } else if (words.isNotEmpty()) {
                merged.append(' ').append(stitch(lastChunkWords, words, config.dedupeWords))
            }
            lastChunkWords = words

            onPartial(merged.toString().trim())
            if (end >= pcm.size) break
            // Advance by the non-overlapping portion.
            offset = end - overlapSamples
        }

        val text = merged.toString().trim()
        return SttResult(
            text = text,
            isFinal = true,
            durationMs = pcm.size * 1000L / sampleRate,
        )
    }

    /**
     * Removes words the overlap transcribed twice.
     *
     * Finds the longest suffix of the previous chunk that matches a prefix of
     * this one, case- and punctuation-insensitively, capped at [maxWords].
     */
    fun stitch(previous: List<String>, next: List<String>, maxWords: Int = 5): String {
        if (previous.isEmpty() || next.isEmpty()) return next.joinToString(" ")
        val limit = minOf(maxWords, previous.size, next.size)
        var best = 0
        for (len in limit downTo 1) {
            val suffix = previous.takeLast(len).map { normalize(it) }
            val prefix = next.take(len).map { normalize(it) }
            if (suffix == prefix) {
                best = len
                break
            }
        }
        return next.drop(best).joinToString(" ")
    }

    private fun normalize(word: String): String = word.lowercase().replace(Regex("[^a-z0-9']"), "")

    /** Buffer helper the orchestrator uses to hold the current utterance. */
    class UtteranceBuffer(private val maxSeconds: Int = 30) {
        private val chunks = ArrayList<ShortArray>()
        private var samples = 0
        private var rate = 16_000

        val durationMs: Long get() = samples * 1000L / rate

        fun add(frame: ShortArray, sampleRate: Int) {
            rate = sampleRate
            chunks += frame
            samples += frame.size
            trim()
        }

        /** Keep only the trailing window, so a long monologue cannot OOM us. */
        private fun trim() {
            val maxSamples = rate * maxSeconds
            while (samples > maxSamples && chunks.size > 1) {
                samples -= chunks.removeAt(0).size
            }
        }

        fun snapshot(): ShortArray {
            if (chunks.isEmpty()) return ShortArray(0)
            return Pcm.concat(*chunks.toTypedArray())
        }

        fun clear() {
            chunks.clear()
            samples = 0
        }

        val isEmpty: Boolean get() = chunks.isEmpty()
    }
}
