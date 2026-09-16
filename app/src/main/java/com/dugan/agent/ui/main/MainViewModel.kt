package com.dugan.agent.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dugan.agent.data.repository.KeyRepository
import com.dugan.agent.data.repository.SettingsRepository
import com.dugan.agent.domain.model.AgentCommand
import com.dugan.agent.domain.model.AgentModel
import com.dugan.agent.domain.model.AgentState
import com.dugan.agent.domain.model.ApiProvider
import com.dugan.agent.domain.model.DuganSettings
import com.dugan.agent.domain.model.ListeningMode
import com.dugan.agent.domain.model.ModelCatalog
import com.dugan.agent.domain.model.ThinkingLevel
import com.dugan.agent.domain.model.TranscriptEntry
import com.dugan.agent.domain.orchestrator.AgentEvent
import com.dugan.agent.domain.orchestrator.VoiceAgentOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val agent: AgentState = AgentState(),
    val settings: DuganSettings = DuganSettings(),
    val transcript: List<TranscriptEntry> = emptyList(),
    val keysConfigured: Boolean = false,
    val echoLayersActive: Int = 0,
    val thinkingModels: List<AgentModel> = ModelCatalog.ThinkingModels,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val orchestrator: VoiceAgentOrchestrator,
    private val settingsRepository: SettingsRepository,
    private val keyRepository: KeyRepository,
) : ViewModel() {

    val agentState: StateFlow<AgentState> = orchestrator.state

    val transcript: StateFlow<List<TranscriptEntry>> = orchestrator.transcript

    val events = orchestrator.events

    val settings: StateFlow<DuganSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuganSettings())

    val keysConfigured: StateFlow<Boolean> = keyRepository.keys
        .map { it.size >= ApiProvider.entries.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun start() = orchestrator.dispatch(AgentCommand.Start)

    fun stop() = orchestrator.dispatch(AgentCommand.Stop)

    fun pause() = orchestrator.dispatch(AgentCommand.Pause)

    fun resume() = orchestrator.dispatch(AgentCommand.Continue)

    fun submitText(text: String) = orchestrator.submitText(text)

    fun beginUtterance() = orchestrator.beginUtterance()

    fun endUtterance() = orchestrator.endUtterance()

    /**
     * The mic button is hold-to-talk in Push-to-Talk mode and a toggle in
     * Continuous mode; this is the single entry point for both.
     */
    fun toggleContinuous() {
        val current = orchestrator.state.value
        if (current.listeningMode == ListeningMode.ContinuousListening) {
            if (current.phase.isActive) stop() else start()
        } else {
            start()
        }
    }

    fun selectThinkingLevel(level: ThinkingLevel) {
        orchestrator.setThinkingLevel(level)
        viewModelScope.launch {
            settingsRepository.update { it.copy(thinkingLevel = level) }
        }
    }

    fun selectModel(model: AgentModel) {
        orchestrator.setModel(model)
        viewModelScope.launch {
            settingsRepository.update { it.copy(thinkingModelId = model.id) }
        }
    }

    fun setListeningMode(mode: ListeningMode) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(listeningMode = mode) }
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            orchestrator.dispatch(AgentCommand.Reset)
        }
    }

    fun echoLayersActive(): Int = orchestrator.aecStatus().activeLayerCount

    fun collectEvents(onEvent: (AgentEvent) -> Unit) {
        viewModelScope.launch {
            events.collect(onEvent)
        }
    }
}
