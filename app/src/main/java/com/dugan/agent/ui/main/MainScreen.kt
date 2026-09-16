package com.dugan.agent.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dugan.agent.domain.model.AgentPhase
import com.dugan.agent.domain.model.ListeningMode
import com.dugan.agent.domain.model.ModelCatalog
import com.dugan.agent.domain.model.ThinkingLevel
import com.dugan.agent.ui.components.AgentOrb
import com.dugan.agent.ui.components.ModelSelectorRow
import com.dugan.agent.ui.components.StatePill
import com.dugan.agent.ui.components.ThinkingSelector

/**
 * The agent screen: status pill, orb, transcript, thinking selector, model chips
 * and the input bar.
 */
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val agent by viewModel.agentState.collectAsStateWithLifecycle()
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val keysConfigured by viewModel.keysConfigured.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.collectEvents { event ->
            val message = when (event) {
                is com.dugan.agent.domain.orchestrator.AgentEvent.Failure -> event.message
                is com.dugan.agent.domain.orchestrator.AgentEvent.AudioUnavailable -> event.reason
                is com.dugan.agent.domain.orchestrator.AgentEvent.EchoSuppressed ->
                    "Ignored my own voice"
                com.dugan.agent.domain.orchestrator.AgentEvent.MissingKeys ->
                    "Add your API keys in Settings"
                com.dugan.agent.domain.orchestrator.AgentEvent.BargeInDetected -> "Interrupted"
            }
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatePill(phase = agent.phase, callState = agent.callState)
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "Clear conversation",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                AgentOrb(
                    phase = agent.phase,
                    inputLevel = agent.inputLevel,
                    outputLevel = agent.outputLevel,
                    modifier = Modifier.size(180.dp),
                )
                if (agent.phase == AgentPhase.Thinking) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(200.dp)
                            .padding(8.dp),
                        strokeWidth = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    )
                }
            }

            if (!keysConfigured) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                ) {
                    Text(
                        "API keys are not configured. Open Settings to add them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            TranscriptView(
                entries = transcript,
                partialUser = agent.partialTranscript,
                partialAgent = agent.partialResponse,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThinkingSelector(
                    selected = agent.thinkingLevelOverride ?: settings.thinkingLevel,
                    onSelect = viewModel::selectThinkingLevel,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                ModelSelectorRow(
                    models = ModelCatalog.ThinkingModels,
                    selectedId = (agent.modelOverride ?: settings.thinkingModel).id,
                    onSelect = viewModel::selectModel,
                )

                InputBar(
                    phase = agent.phase,
                    listeningMode = settings.listeningMode,
                    onStop = viewModel::stop,
                    onPauseToggle = {
                        if (agent.phase == AgentPhase.Paused) viewModel.resume() else viewModel.pause()
                    },
                    onSend = viewModel::submitText,
                    onMicDown = viewModel::beginUtterance,
                    onMicUp = viewModel::endUtterance,
                    onMicToggle = viewModel::toggleContinuous,
                )

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
