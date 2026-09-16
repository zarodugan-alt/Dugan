package com.dugan.agent.domain.orchestrator

import com.dugan.agent.util.AgentLog
import com.dugan.agent.data.api.AgentLlm
import com.dugan.agent.data.api.AgentTts
import com.dugan.agent.data.api.ApiException
import com.dugan.agent.data.api.ChatMessage
import com.dugan.agent.data.api.GroqSttClient
import com.dugan.agent.data.local.TtsCache
import com.dugan.agent.data.repository.HistoryRepository
import com.dugan.agent.data.repository.KeyRepository
import com.dugan.agent.data.repository.SettingsRepository
import com.dugan.agent.domain.audio.AecController
import com.dugan.agent.domain.audio.AudioCaptureManager
import com.dugan.agent.domain.audio.AudioFrame
import com.dugan.agent.domain.audio.AudioPlaybackEngine
import com.dugan.agent.domain.audio.AudioFocusController
import com.dugan.agent.domain.audio.VadController
import com.dugan.agent.domain.audio.EotController
import com.dugan.agent.domain.audio.Pcm
import com.dugan.agent.domain.audio.SoftwareAec
import com.dugan.agent.domain.command.NoOpVoiceCommandHandler
import com.dugan.agent.domain.command.VoiceCommandHandler
import com.dugan.agent.domain.echo.EchoDefenseEngine
import com.dugan.agent.domain.echo.EchoVerdict
import com.dugan.agent.domain.echo.FuzzyMatcher
import com.dugan.agent.domain.latency.ConnectionWarmup
import com.dugan.agent.domain.latency.ModelRouter
import com.dugan.agent.domain.latency.SentenceSplitter
import com.dugan.agent.domain.latency.SpeculativeExecutor
import com.dugan.agent.domain.model.AgentCommand
import com.dugan.agent.domain.model.AgentModel
import com.dugan.agent.domain.model.AgentPhase
import com.dugan.agent.domain.model.AgentState
import com.dugan.agent.domain.model.CallState
import com.dugan.agent.domain.model.DuganSettings
import com.dugan.agent.domain.model.ListeningMode
import com.dugan.agent.domain.model.Speaker
import com.dugan.agent.domain.model.ThinkingLevel
import com.dugan.agent.domain.model.TranscriptEntry
import com.dugan.agent.util.withRetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** One-shot signals the UI reacts to. */
sealed interface AgentEvent {
    data object MissingKeys : AgentEvent
    data object BargeInDetected : AgentEvent
    data class EchoSuppressed(val text: String) : AgentEvent
    data class AudioUnavailable(val reason: String) : AgentEvent
    data class Failure(val message: String) : AgentEvent
}

/** A completed speculative completion, reusable if the final transcript agrees. */
data class SpeculativeTurn(val partial: String, val response: String)

/**
 * Central coordinator: capture -> AEC -> VAD -> end-of-turn -> STT -> LLM -> TTS.
 *
 * Everything else in the app is a peripheral to this loop. It owns the agent
 * phase, the current utterance buffer, and the single place where echo defence
 * and latency optimisation are applied in the right order.
 */
@Singleton
class VoiceAgentOrchestrator @Inject constructor(
    private val capture: AudioCaptureManager,
    private val playback: AudioPlaybackEngine,
    private val vadController: VadController,
    private val eot: EotController,
    private val aecController: AecController,
    private val softwareAec: SoftwareAec,
    private val echo: EchoDefenseEngine,
    private val sttClient: GroqSttClient,
    private val chunkedStt: ChunkedStt,
    private val llm: AgentLlm,
    private val tts: AgentTts,
    private val ttsCache: TtsCache,
    private val history: HistoryRepository,
    private val keys: KeyRepository,
    private val settingsRepository: SettingsRepository,
    private val router: ModelRouter,
    private val speculation: SpeculativeExecutor,
    private val fuzzy: FuzzyMatcher,
    private val warmup: ConnectionWarmup,
    private val audioFocus: AudioFocusController,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val transcript: StateFlow<List<TranscriptEntry>> = _transcript.asStateFlow()

    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    @Volatile
    private var settings: DuganSettings = DuganSettings()

    @Volatile
    private var commandHandler: VoiceCommandHandler = NoOpVoiceCommandHandler

    @Volatile
    private var speculativeResult: SpeculativeTurn? = null

    private val utterance = ChunkedStt.UtteranceBuffer()
    private var captureJob: Job? = null
    private var turnJob: Job? = null
    private var turnId: Long = 0
    private var inSpeech = false
    private var silenceMs = 0

    init {
        scope.launch {
            runCatching { settingsRepository.settings.collect { applySettings(it) } }
        }
        scope.launch {
            runCatching { history.observeSession().collect { _transcript.value = it } }
        }
        playback.onLevel = { level -> _state.update { it.copy(outputLevel = level) } }
        audioFocus.onTransientLoss = { dispatch(AgentCommand.Pause) }
        audioFocus.onRegain = {
            // Only resume if the user paused us by losing focus, not deliberately.
            if (_state.value.phase == AgentPhase.Paused) dispatch(AgentCommand.Continue)
        }
        warmup.warmAll()
    }

    // -- Public control surface ---------------------------------------------

    fun setVoiceCommandHandler(handler: VoiceCommandHandler) {
        commandHandler = handler
    }

    fun setCallState(callState: CallState) {
        _state.update { it.copy(callState = callState) }
    }

    fun setThinkingLevel(level: ThinkingLevel) {
        _state.update { it.copy(thinkingLevelOverride = level) }
    }

    fun setModel(model: AgentModel) {
        _state.update { it.copy(modelOverride = model) }
    }

    fun dispatch(command: AgentCommand) {
        val current = _state.value
        if (command == AgentCommand.Start && !keys.allConfigured()) {
            _state.update {
                it.copy(phase = AgentPhase.Error, errorMessage = "Configure your API keys in Settings")
            }
            _events.tryEmit(AgentEvent.MissingKeys)
            return
        }

        val next = AgentStateMachine.transition(current.phase, command)
        _state.update {
            it.copy(
                phase = next,
                phaseBeforePause = AgentStateMachine.rememberPauseTarget(next, it.phaseBeforePause),
                errorMessage = if (next == AgentPhase.Error) it.errorMessage else null,
            )
        }

        when (command) {
            AgentCommand.Start, AgentCommand.Continue -> beginListening()
            AgentCommand.Stop -> {
                playback.stop()
                audioFocus.abandon()
                turnJob?.cancel()
                speculation.reset()
                utterance.clear()
                resetTurnTracking()
            }
            AgentCommand.Pause -> {
                captureJob?.cancel()
                playback.stop()
                audioFocus.abandon()
            }
            AgentCommand.Reset -> {
                playback.stop()
                turnJob?.cancel()
                speculation.reset()
                utterance.clear()
                echo.clearHistory()
                resetTurnTracking()
            }
            AgentCommand.BargeIn -> {
                playback.stop()
                turnJob?.cancel()
                speculation.reset()
                utterance.clear()
                resetTurnTracking()
                beginListening()
            }
        }
    }

    /**
     * Speaks a fixed string through the normal TTS chain.
     *
     * Used by the Telecom layer for prompts that must not go through the LLM --
     * a dial confirmation is a side-effecting action and is never speculated on.
     */
    fun speak(text: String) {
        if (text.isBlank()) return
        scope.launch {
            runCatching {
                echo.recordAgentSpeech(text)
                speakSentence(text)
                playback.drainQuietly()
                audioFocus.abandon()
                _state.update { it.copy(phase = AgentPhase.Listening, agentSpeaking = false) }
            }.onFailure { onTurnFailure(it) }
        }
    }

    /** Text input bypasses STT entirely and goes straight to the LLM. */
    fun submitText(text: String) {
        if (text.isBlank()) return
        turnJob?.cancel()
        turnJob = scope.launch {
            runCatching { runTurn(typedText = text.trim()) }.onFailure { onTurnFailure(it) }
        }
    }

    /** Push-to-talk: button pressed. */
    fun beginUtterance() {
        if (_state.value.phase != AgentPhase.Listening) dispatch(AgentCommand.Start)
        if (_state.value.phase != AgentPhase.Listening) return
        inSpeech = true
        silenceMs = 0
        utterance.clear()
    }

    /** Push-to-talk: button released. Closes the turn immediately, no VAD wait. */
    fun endUtterance() {
        if (_state.value.listeningMode != ListeningMode.PushToTalk) return
        if (utterance.isEmpty) {
            inSpeech = false
            return
        }
        val pcm = utterance.snapshot()
        utterance.clear()
        inSpeech = false
        closeTurn(pcm)
    }

    // -- Capture loop --------------------------------------------------------

    private fun beginListening() {
        captureJob?.cancel()
        vadController.active.reset()
        softwareAec.reset()
        playback.clearOutputLog()
        speculation.reset()
        speculativeResult = null
        resetTurnTracking()
        utterance.clear()

        val callState = _state.value.callState
        if (!callState.canCapture) {
            _events.tryEmit(AgentEvent.AudioUnavailable("Call audio is not reachable. Put the call on speaker."))
            _state.update { it.copy(phase = AgentPhase.Idle) }
            return
        }

        val config = AudioCaptureManager.CaptureConfig(
            inputSource = settings.inputSource,
            transport = callState.transport,
        )

        captureJob = scope.launch(Dispatchers.IO) {
            runCatching {
                capture.capture(config).collect { frame -> onFrame(frame) }
            }.onFailure {
                AgentLog.w(TAG, "capture failed: ${it.javaClass.simpleName}: ${it.message}")
                _events.tryEmit(AgentEvent.AudioUnavailable(it.message ?: "Microphone unavailable"))
                _state.update { s -> s.copy(phase = AgentPhase.Idle) }
            }
        }
    }

    private suspend fun onFrame(frame: AudioFrame) {
        if (_state.value.phase != AgentPhase.Listening) return

        // Layer 2: subtract the loudspeaker signal from the mic signal.
        val samples = if (settings.softwareAecEnabled && playback.isPlaying) {
            val reference = playback.referenceAround(frame.timestampMs)
            softwareAec.process(frame.samples, reference, settings.echoSuppression)
        } else {
            frame.samples
        }
        val working = AudioFrame(samples, Pcm.rms(samples), frame.timestampMs, frame.sampleRate)
        _state.update { it.copy(inputLevel = working.rms) }

        // Layers 3 + 4: gate the mic while speaking, unless a human is interrupting.
        if (playback.wasSpeakingAt(working.timestampMs)) {
            val interrupt = settings.bargeInEnabled &&
                working.rms > BARGE_IN_LEVEL &&
                echo.isBargeIn(working)
            if (!interrupt) return
            _events.tryEmit(AgentEvent.BargeInDetected)
            dispatch(AgentCommand.BargeIn)
            return
        }

        val decision = vadController.active.process(working, settings.vadSensitivity)

        when (_state.value.listeningMode) {
            ListeningMode.PushToTalk ->
                if (inSpeech) utterance.add(working.samples, working.sampleRate)

            ListeningMode.ContinuousListening -> {
                if (decision.isSpeech) {
                    if (!inSpeech) {
                        inSpeech = true
                        silenceMs = 0
                        utterance.clear()
                    }
                    utterance.add(working.samples, working.sampleRate)
                    silenceMs = 0
                    maybeSpeculate()
                } else if (inSpeech) {
                    // Keep buffering trailing silence: the EOT detector reads it.
                    utterance.add(working.samples, working.sampleRate)
                    silenceMs += working.durationMs
                    val pcm = utterance.snapshot()
                    val ended = eot.endOfTurn(
                        transcript = _state.value.partialTranscript,
                        trailingAudio = pcm,
                        silenceMs = silenceMs,
                        silenceThresholdMs = settings.silenceThresholdMs,
                    )
                    if (ended) {
                        inSpeech = false
                        utterance.clear()
                        closeTurn(pcm)
                    }
                }
            }
        }
    }

    // -- Turn pipeline -------------------------------------------------------

    private fun closeTurn(pcm: ShortArray) {
        if (pcm.isEmpty()) return
        turnJob?.cancel()
        turnJob = scope.launch {
            runCatching { runTurn(capturedAudio = pcm) }.onFailure { onTurnFailure(it) }
        }
    }

    /**
     * One full turn. Either [capturedAudio] (voice) or [typedText] (keyboard) is
     * non-null; the rest of the pipeline is identical.
     */
    private suspend fun runTurn(capturedAudio: ShortArray? = null, typedText: String? = null) {
        val myTurn = ++turnId
        val startedAt = System.currentTimeMillis()
        val fromText = typedText != null

        _state.update { it.copy(turnId = myTurn, partialResponse = "", phase = AgentPhase.Thinking) }

        // 1. Speech-to-text.
        val spoken: String = if (fromText) {
            typedText.trim()
        } else {
            val pcm = capturedAudio ?: ShortArray(0)
            if (pcm.isEmpty()) {
                _state.update { it.copy(phase = AgentPhase.Listening) }
                return
            }
            withRetry("stt", attempts = 3) {
                if (settings.streamingStt) {
                    chunkedStt.transcribe(
                        client = sttClient,
                        model = settings.sttModel,
                        pcm = pcm,
                        sampleRate = CAPTURE_RATE,
                    ) { partial ->
                        _state.update { s -> s.copy(partialTranscript = partial) }
                    }
                } else {
                    // Streaming off: one request for the whole utterance. Slower to
                    // first partial but avoids any chance of an overlap seam.
                    sttClient.transcribe(settings.sttModel, pcm, CAPTURE_RATE)
                }
            }.text.trim()
        }

        if (spoken.isBlank()) {
            _state.update { it.copy(phase = AgentPhase.Listening, partialTranscript = "") }
            return
        }

        // 2. Voice commands first: a dial is a side effect and must not be
        //    speculated on or routed to a small model.
        if (!fromText && commandHandler.handle(spoken)) {
            _state.update { it.copy(phase = AgentPhase.Listening, partialTranscript = "") }
            return
        }

        // 3. Layer 5: reject the agent's own words coming back through the mic.
        if (!fromText) {
            val verdict = echo.classifyTranscript(spoken)
            if (verdict.verdict == EchoVerdict.Echo) {
                AgentLog.i(TAG, "dropped echo via ${verdict.layer} (confidence ${verdict.confidence})")
                _events.tryEmit(AgentEvent.EchoSuppressed(spoken))
                _state.update { it.copy(phase = AgentPhase.Listening, partialTranscript = "") }
                return
            }
        }

        appendEntry(Speaker.User, spoken)
        _state.update { it.copy(partialTranscript = spoken) }

        // 4. Route to the cheapest model that can handle the turn.
        val preferred = _state.value.modelOverride ?: settings.thinkingModel
        val model = router.route(spoken, preferred, settings.modelRouting)
        val thinking = _state.value.thinkingLevelOverride ?: settings.thinkingLevel
        val messages = buildMessages(spoken)

        // 5. Reuse a speculative completion when the final transcript agrees.
        val reused = takeSpeculationIfValid(spoken)

        var firstAudioAt = 0L
        val response = StringBuilder()

        if (reused != null) {
            response.append(reused)
            _state.update { it.copy(partialResponse = reused) }
            SentenceSplitter().let { splitter ->
                splitter.accept(reused).forEach { sentence ->
                    if (myTurn != turnId) return
                    if (firstAudioAt == 0L) firstAudioAt = System.currentTimeMillis()
                    speakSentence(sentence)
                }
                splitter.flush()?.let { tail ->
                    if (myTurn == turnId) {
                        if (firstAudioAt == 0L) firstAudioAt = System.currentTimeMillis()
                        speakSentence(tail)
                    }
                }
            }
        } else {
            val splitter = SentenceSplitter()
            try {
                llm.stream(
                    model = model,
                    messages = messages,
                    thinkingLevel = thinking,
                    systemPrompt = SYSTEM_PROMPT,
                ).collect { delta ->
                    if (myTurn != turnId) return@collect
                    response.append(delta)
                    _state.update { it.copy(partialResponse = response.toString()) }
                    splitter.accept(delta).forEach { sentence ->
                        if (myTurn != turnId) return@forEach
                        if (firstAudioAt == 0L) firstAudioAt = System.currentTimeMillis()
                        speakSentence(sentence)
                    }
                }
            } catch (e: ApiException) {
                onTurnFailure(e)
                return
            }
            splitter.flush()?.let { tail ->
                if (myTurn == turnId) {
                    if (firstAudioAt == 0L) firstAudioAt = System.currentTimeMillis()
                    speakSentence(tail)
                }
            }
        }

        if (myTurn != turnId) return

        val text = response.toString().trim()
        if (text.isNotEmpty()) {
            echo.recordAgentSpeech(text)
            appendEntry(Speaker.Agent, text, latencyMs = if (firstAudioAt > 0) firstAudioAt - startedAt else null)
        }

        playback.drainQuietly()
        audioFocus.abandon()
        _state.update {
            it.copy(
                phase = AgentPhase.Listening,
                partialResponse = "",
                partialTranscript = "",
                lastTurnLatencyMs = if (firstAudioAt > 0) firstAudioAt - startedAt else null,
            )
        }
        resetTurnTracking()
    }

    /**
     * Message list for a turn.
     *
     * With prefix caching on, prior turns are sent as a stable leading block: the
     * provider can reuse its KV cache across turns, which is most of the TTFT win.
     * With it off, each request is stateless -- lower latency on the first turn of
     * a call, no memory of earlier ones.
     */
    private suspend fun buildMessages(userText: String): List<ChatMessage> {
        val history = if (settings.prefixCaching) history.contextWindow() else emptyList()
        return history.map { ChatMessage(it.speaker.chatRole(), it.text) } + ChatMessage("user", userText)
    }

    /**
     * Fires a throwaway LLM call on the current STT partial.
     *
     * Greedy decoding means that if the final transcript matches, the answer we
     * already generated is still the answer -- so we keep it and skip a whole
     * round-trip. If it does not match, [takeSpeculationIfValid] discards it.
     */
    private suspend fun maybeSpeculate() {
        if (!settings.speculativeLlm) return
        val partial = _state.value.partialTranscript
        val preferred = _state.value.modelOverride ?: settings.thinkingModel
        if (!speculation.shouldSpeculate(
                speechDurationMs = utterance.durationMs,
                partial = partial,
                retries = speculation.attemptCount(),
                isIdempotent = true,
            )
        ) {
            return
        }
        val messages = buildMessages(partial)
        speculation.speculate(scope) {
            val builder = StringBuilder()
            llm.stream(
                model = preferred,
                messages = messages,
                thinkingLevel = settings.thinkingLevel,
                systemPrompt = SYSTEM_PROMPT,
            ).collect { builder.append(it) }
            speculativeResult = SpeculativeTurn(partial, builder.toString().trim())
        }
    }

    /**
     * @return the speculative response, but only if the final transcript is close
     *   enough to the partial it was generated from.
     */
    private fun takeSpeculationIfValid(finalTranscript: String): String? {
        val candidate = speculativeResult ?: return null
        speculativeResult = null
        speculation.reset()
        val agreement = fuzzy.similarity(candidate.partial, finalTranscript)
        return if (agreement >= SPECULATION_AGREEMENT) {
            AgentLog.i(TAG, "reused speculative completion (agreement $agreement)")
            candidate.response.ifBlank { null }
        } else {
            speculation.discard()
            null
        }
    }

    /** One sentence of TTS -> cache -> AudioTrack queue. */
    private suspend fun speakSentence(sentence: String) {
        val trimmed = sentence.trim()
        if (trimmed.isEmpty()) return
        if (!playback.start(scope, PLAYBACK_RATE)) {
            _events.tryEmit(AgentEvent.AudioUnavailable("No audio output available"))
            return
        }
        audioFocus.request()

        val cacheKey = ttsCache.keyFor(trimmed, settings)
        val cached = ttsCache.get(cacheKey)

        _state.update { it.copy(phase = AgentPhase.Speaking, agentSpeaking = true) }

        if (cached != null) {
            chunkPcm(cached, PLAYBACK_RATE).forEach { playback.enqueue(it) }
            return
        }

        val collected = ArrayList<ShortArray>()
        tts.synthesize(settings.ttsModel, trimmed, settings.ttsVoiceId, settings.playbackSpeed)
            .collect { chunk ->
                collected += chunk
                playback.enqueue(chunk)
            }

        if (settings.ttsCaching && collected.isNotEmpty()) {
            // concat gives ShortArray; the cache stores raw PCM bytes.
            ttsCache.put(cacheKey, Pcm.toBytes(Pcm.concat(*collected.toTypedArray())))
        }
    }

    private fun chunkPcm(pcm: ByteArray, rate: Int): Array<ShortArray> {
        val shorts = Pcm.toShorts(pcm)
        val frame = (rate / 50).coerceAtLeast(1)
        val out = ArrayList<ShortArray>()
        var offset = 0
        while (offset < shorts.size) {
            val len = minOf(frame, shorts.size - offset)
            out += shorts.copyOfRange(offset, offset + len)
            offset += len
        }
        return out.toTypedArray()
    }

    private fun onTurnFailure(t: Throwable) {
        AgentLog.w(TAG, "turn failed: ${t.javaClass.simpleName}: ${t.message}")
        val message = when (t) {
            is ApiException -> when {
                t.isAuth -> "That API key was rejected. Check it in Settings."
                t.isQuota -> "Provider quota exhausted. Falling back where possible."
                else -> "Provider error (${t.statusCode})."
            }
            else -> "Something went wrong: ${t.message ?: t.javaClass.simpleName}"
        }
        _events.tryEmit(AgentEvent.Failure(message))
        playback.stop()
        _state.update {
            it.copy(phase = AgentPhase.Error, errorMessage = message, agentSpeaking = false)
        }
    }

    private fun applySettings(newSettings: DuganSettings) {
        settings = newSettings
        echo.configure(newSettings)
        ttsCache.enabled = newSettings.ttsCaching
        _state.update { it.copy(listeningMode = newSettings.listeningMode) }
    }

    private fun appendEntry(speaker: Speaker, text: String, latencyMs: Long? = null) {
        val entry = TranscriptEntry(
            id = System.nanoTime(),
            speaker = speaker,
            text = text,
            timestampMs = System.currentTimeMillis(),
            latencyMs = latencyMs,
        )
        _transcript.update { it + entry }
        scope.launch { runCatching { history.append(speaker, text, latencyMs) } }
    }

    /** Which echo-defence layers are live. Surfaced in Settings > Audio. */
    fun aecStatus() = aecController.status(
        hardwareAecAvailable = capture.diagnostics?.hardwareAec == true,
        softwareEnabled = settings.softwareAecEnabled,
        micGating = settings.micGatingEnabled,
        bargeIn = settings.bargeInEnabled,
        textDefense = settings.textEchoDefenseEnabled,
    )

    private fun resetTurnTracking() {
        inSpeech = false
        silenceMs = 0
        _state.update { it.copy(inputLevel = 0f, outputLevel = 0f, agentSpeaking = false) }
    }

    private companion object {
        const val TAG = "Orchestrator"
        const val BARGE_IN_LEVEL = 0.12f
        const val CAPTURE_RATE = 16_000
        const val PLAYBACK_RATE = 24_000
        const val SPECULATION_AGREEMENT = 0.9f

        /**
         * Byte-identical between turns on purpose: providers key their KV prefix
         * cache off the system prompt, so any per-turn variation invalidates it.
         * Anything dynamic (contact names, time of day) belongs in the user turn.
         */
        const val SYSTEM_PROMPT =
            "You are Dugan, a personal voice assistant on a phone call.\n" +
                "Rules:\n" +
                "- Reply in one or two short spoken sentences. This is speech, not text.\n" +
                "- Never use markdown, bullet points, lists, tables or emoji.\n" +
                "- Spell out numbers the way a person would say them.\n" +
                "- If you do not know, say so plainly instead of guessing.\n" +
                "- Never repeat the caller's words back to them verbatim.\n" +
                "- You cannot see a screen. Do not refer to anything visual."
    }
}
