package com.dugan.agent.ui.main

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dugan.agent.domain.model.AgentPhase
import com.dugan.agent.domain.model.ListeningMode

/**
 * Stop / Pause / text input / mic / send.
 *
 * The mic button changes meaning with the listening mode: press-and-hold in
 * Push-to-Talk, a plain toggle in Continuous Listening. That distinction is the
 * whole reason [listeningMode] is a parameter rather than being read from state
 * inside the composable.
 */
@Composable
fun InputBar(
    phase: AgentPhase,
    listeningMode: ListeningMode,
    onStop: () -> Unit,
    onPauseToggle: () -> Unit,
    onSend: (String) -> Unit,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    onMicToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    val micInteraction = remember { MutableInteractionSource() }
    val micPressed by micInteraction.collectIsPressedAsState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onStop,
                enabled = phase.isActive || phase == AgentPhase.Paused,
            ) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
            }

            IconButton(onClick = onPauseToggle, enabled = phase.isActive || phase == AgentPhase.Paused) {
                Icon(
                    imageVector = if (phase == AgentPhase.Paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (phase == AgentPhase.Paused) "Continue" else "Pause",
                )
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type here…") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (draft.isNotBlank()) {
                            onSend(draft.trim())
                            draft = ""
                        }
                    },
                ),
            )

            // Mic: hold-to-talk or toggle, depending on mode.
            Box(contentAlignment = Alignment.Center) {
                if (listeningMode == ListeningMode.PushToTalk) {
                    IconButton(
                        onClick = { /* handled by pointer input below */ },
                        interactionSource = micInteraction,
                        modifier = Modifier.micHold(onMicDown, onMicUp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (micPressed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        ),
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "Hold to talk", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMicToggle()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (phase.isActive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        ),
                    ) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = if (phase.isActive) "Stop listening" else "Start listening",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            FilledIconButton(
                onClick = {
                    onSend(draft.trim())
                    draft = ""
                },
                enabled = draft.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

/**
 * Press-and-hold gesture for the push-to-talk button.
 *
 * `tryAwaitRelease` returns false on cancellation (finger dragged off, pointer
 * stolen by the system), and we must still fire `onUp` in that case or the
 * orchestrator stays stuck mid-utterance.
 */
private fun Modifier.micHold(onDown: () -> Unit, onUp: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                onDown()
                // tryAwaitRelease is false when the gesture is cancelled; either
                // way the utterance has to be closed exactly once.
                tryAwaitRelease()
                onUp()
            },
        )
    },
)
