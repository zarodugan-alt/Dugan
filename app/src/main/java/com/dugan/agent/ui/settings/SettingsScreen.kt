package com.dugan.agent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dugan.agent.domain.model.AgentModel
import com.dugan.agent.domain.model.ApiProvider
import com.dugan.agent.domain.model.InputSource
import com.dugan.agent.domain.model.KeyTestResult
import com.dugan.agent.domain.model.ListeningMode
import com.dugan.agent.ui.components.KeyField
import com.dugan.agent.util.AgentLog
import com.dugan.agent.ui.components.SectionCard
import com.dugan.agent.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val aec by viewModel.aecStatus.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SectionCard(title = "API Keys", icon = "🔑") {
                Text(
                    "Keys are encrypted with a Keystore-backed master key and stored only on " +
                        "this device. They are excluded from Android auto-backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ApiProvider.entries.forEach { provider ->
                    val row = rows[provider.id] ?: KeyRow()
                    KeyField(
                        provider = provider,
                        value = row.draft,
                        storedMask = row.storedMask,
                        dirty = row.isDirty,
                        onValueChange = { viewModel.onDraftChange(provider, it) },
                        onSave = { viewModel.saveKey(provider) },
                        onTest = { viewModel.testKey(provider) },
                        onClear = { viewModel.clearKey(provider) },
                        testResult = row.result,
                        advisory = row.advisory,
                    )
                }
                Text(
                    "Save writes to the encrypted vault on this device and works offline. " +
                        "Test also spends one request to prove the key is live.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            SectionCard(title = "Default Models", icon = "🧠") {
                ModelDropdown(
                    label = "STT Model",
                    options = viewModel.sttModels,
                    selectedId = settings.sttModelId,
                ) { selected -> viewModel.update { current -> current.copy(sttModelId = selected.id) } }
                ModelDropdown(
                    label = "TTS Model",
                    options = viewModel.ttsModels,
                    selectedId = settings.ttsModelId,
                ) { selected -> viewModel.update { current -> current.copy(ttsModelId = selected.id) } }
                ModelDropdown(
                    label = "Thinking Model",
                    options = viewModel.thinkingModels,
                    selectedId = settings.thinkingModelId,
                ) { selected -> viewModel.update { current -> current.copy(thinkingModelId = selected.id) } }
            }

            SectionCard(title = "Agent Mode", icon = "🎙️") {
                SettingsRow(label = "Listening Mode", supporting = settings.listeningMode.name) {
                    Switch(
                        checked = settings.listeningMode == ListeningMode.ContinuousListening,
                        onCheckedChange = { continuous ->
                            viewModel.update {
                                it.copy(
                                    listeningMode = if (continuous) {
                                        ListeningMode.ContinuousListening
                                    } else {
                                        ListeningMode.PushToTalk
                                    },
                                )
                            }
                        },
                    )
                }
                SettingsRow(label = "Auto-Answer VoIP", supporting = "Does not work for SIM calls") {
                    Switch(
                        checked = settings.autoAnswerVoip,
                        onCheckedChange = { v -> viewModel.update { it.copy(autoAnswerVoip = v) } },
                    )
                }
                LabeledSlider(
                    label = "Answer Delay",
                    value = settings.answerDelaySeconds.toFloat(),
                    valueRange = 0f..10f,
                    steps = 9,
                    format = { "${it.toInt()} s" },
                ) { v -> viewModel.update { it.copy(answerDelaySeconds = v.toInt()) } }
                OutlinedTextField(
                    value = settings.greeting,
                    onValueChange = { v -> viewModel.update { it.copy(greeting = v) } },
                    label = { Text("Greeting") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "Audio", icon = "🎛️") {
                InputSourceDropdown(
                    selected = settings.inputSource,
                ) { v -> viewModel.update { it.copy(inputSource = v) } }

                LabeledSlider(
                    label = "VAD Sensitivity",
                    value = settings.vadSensitivity,
                    valueRange = 0f..1f,
                ) { v -> viewModel.update { it.copy(vadSensitivity = v) } }

                LabeledSlider(
                    label = "Silence Threshold",
                    value = settings.silenceThresholdMs.toFloat(),
                    valueRange = 200f..2000f,
                    format = { "${it.toInt()} ms" },
                ) { v -> viewModel.update { it.copy(silenceThresholdMs = v.toInt()) } }

                LabeledSlider(
                    label = "Echo Suppression",
                    value = settings.echoSuppression,
                    valueRange = 0f..1f,
                ) { v -> viewModel.update { it.copy(echoSuppression = v) } }

                LabeledSlider(
                    label = "Playback Speed",
                    value = settings.playbackSpeed,
                    valueRange = -1f..1f,
                    format = { if (it >= 0) "+%.2f".format(it) else "%.2f".format(it) },
                ) { v -> viewModel.update { it.copy(playbackSpeed = v) } }

                Text(
                    "Echo defence layers active: ${aec.activeLayerCount}/5 " +
                        "(hardware ${aec.hardwareAec}, software ${aec.softwareAec}, " +
                        "gate ${aec.micGating}, barge-in ${aec.bargeIn}, text ${aec.textDefense})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Latency", icon = "⚡") {
                ToggleRow("Streaming STT", settings.streamingStt) {
                    viewModel.update { s -> s.copy(streamingStt = it) }
                }
                ToggleRow("Speculative LLM", settings.speculativeLlm) {
                    viewModel.update { s -> s.copy(speculativeLlm = it) }
                }
                ToggleRow("TTS Caching", settings.ttsCaching) {
                    viewModel.update { s -> s.copy(ttsCaching = it) }
                }
                ToggleRow("Prefix Caching", settings.prefixCaching) {
                    viewModel.update { s -> s.copy(prefixCaching = it) }
                }
                ToggleRow("Model Routing", settings.modelRouting) {
                    viewModel.update { s -> s.copy(modelRouting = it) }
                }
            }

            SectionCard(title = "Echo Defence", icon = "🛡️") {
                ToggleRow("Software AEC (layer 2)", settings.softwareAecEnabled) {
                    viewModel.update { s -> s.copy(softwareAecEnabled = it) }
                }
                ToggleRow("Mic Gating (layer 3)", settings.micGatingEnabled) {
                    viewModel.update { s -> s.copy(micGatingEnabled = it) }
                }
                ToggleRow("Barge-In (layer 4)", settings.bargeInEnabled) {
                    viewModel.update { s -> s.copy(bargeInEnabled = it) }
                }
                ToggleRow("Text Defence (layer 5)", settings.textEchoDefenseEnabled) {
                    viewModel.update { s -> s.copy(textEchoDefenseEnabled = it) }
                }
            }

            SectionCard(title = "Theme", icon = "🎨") {
                ThemeGrid(
                    selectedId = settings.themeId,
                    onSelect = { theme -> viewModel.update { it.copy(themeId = theme.id) } },
                )
                SettingsRow(label = "Material You dynamic colour", supporting = "Android 12+") {
                    Switch(
                        checked = settings.dynamicColor,
                        onCheckedChange = { v -> viewModel.update { it.copy(dynamicColor = v) } },
                    )
                }
            }

            SectionCard(title = "Diagnostics", icon = "🩺") {
                val lastCrash by viewModel.lastCrash.collectAsStateWithLifecycle()
                if (lastCrash == null) {
                    SettingsRow(
                        label = "Last crash",
                        supporting = "Nothing recorded. A crash is captured here automatically so it can be read without adb.",
                    ) {
                        Text("None")
                    }
                } else {
                    Text(
                        "Last crash",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        lastCrash.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 14,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::clearCrashLog,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Dismiss")
                    }
                }
            }

            SectionCard(title = "About", icon = "ℹ️") {
                SettingsRow(label = "Version", supporting = null) { Text("1.0.0") }
                SettingsRow(
                    label = "Default dialer",
                    supporting = if (viewModel.isDefaultDialer) {
                        "Full in-call management is available."
                    } else {
                        "Without this role Dugan cannot draw the system in-call screen or manage VoIP calls."
                    },
                ) {
                    Text(if (viewModel.isDefaultDialer) "Yes" else "No")
                }
                if (!viewModel.isDefaultDialer) {
                    val roleIntent = remember { viewModel.requestDialerRoleIntent() }
                    Button(
                        onClick = { roleIntent?.let { context.startActivity(it) } },
                        enabled = roleIntent != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Set as default dialer")
                    }
                }
                TextButton(
                    onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Dugan diagnostic log")
                            putExtra(android.content.Intent.EXTRA_TEXT, AgentLog.dump())
                        }
                        context.startActivity(android.content.Intent.createChooser(send, "Export logs"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Export Logs")
                }
                TextButton(
                    onClick = { showLicenses = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Licenses")
                }
                Button(onClick = { showResetDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset All Keys")
                }
                TextButton(onClick = viewModel::clearCache, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear TTS cache")
                }
            }
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Open-source licenses") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LICENSES.forEach { (name, licence) ->
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            licence,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("Close") } },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all API keys?") },
            text = {
                Text("This deletes both keys from the encrypted vault. You will need to paste them again.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAllKeys()
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsRow(label = label) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    format: (Float) -> String = { "%.2f".format(it) },
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(format(value), style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value, onValueChange = onChange, valueRange = valueRange, steps = steps)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    label: String,
    options: List<AgentModel>,
    selectedId: String,
    onSelect: (AgentModel) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // firstOrNull twice: an unknown id falls back to the first option, and an
    // empty list falls back to nothing rather than throwing.
    val selected = options.firstOrNull { it.id == selectedId } ?: options.firstOrNull()
    if (selected == null) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("${option.provider.badge}  ${option.displayName}") },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputSourceDropdown(selected: InputSource, onSelect: (InputSource) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Input Source") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            InputSource.entries.forEach { source ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(source.label) },
                    onClick = {
                        onSelect(source)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Components this app builds on.
 *
 * Hand-maintained rather than generated by the OSS Licenses plugin: that plugin
 * needs a Gradle plugin plus a generated activity, and for a personal build a
 * readable list in the About screen is worth more than a machine-generated one.
 */
private val LICENSES = listOf(
    "Jetpack Compose / Material 3" to "Apache-2.0 — The Android Open Source Project",
    "AndroidX (Room, DataStore, Lifecycle, Navigation, Security-Crypto)" to "Apache-2.0 — The Android Open Source Project",
    "Kotlin Coroutines & kotlinx.serialization" to "Apache-2.0 — JetBrains",
    "OkHttp" to "Apache-2.0 — Square, Inc.",
    "Dagger Hilt" to "Apache-2.0 — The Dagger Authors",
    "Accompanist Permissions" to "Apache-2.0 — Google",
    "Android Telecom framework" to "Apache-2.0 — The Android Open Source Project",
    "Firebase Realtime Database (optional, -Pdugan.firebase=true)" to "Apache-2.0 — Google",
    "Silero VAD v5 (optional asset, not bundled)" to "MIT — Silero",
    "Smart Turn v3.2 (optional asset, not bundled)" to "BSD-2-Clause — Pipecat",
    "WebRTC AEC3 (referenced design, not bundled)" to "BSD-3-Clause — The WebRTC Authors",
)
