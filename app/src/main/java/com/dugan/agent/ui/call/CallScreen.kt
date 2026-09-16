package com.dugan.agent.ui.call

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dugan.agent.domain.model.CallTransport
import com.dugan.agent.ui.dialer.initials
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    onFinish: () -> Unit,
    viewModel: CallViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val call = ui.call

    var seconds by remember { mutableLongStateOf(0) }
    LaunchedEffect(call.connectedAtMs) {
        val started = call.connectedAtMs
        if (started == null) {
            seconds = 0
            return@LaunchedEffect
        }
        while (true) {
            seconds = ((System.currentTimeMillis() - started) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onFinish) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                if (call.transport == CallTransport.Sim) "SIM Call" else "VoIP Call",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(120.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initials(call.displayName ?: call.handle ?: "?"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            call.displayName ?: "Unknown",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            call.handle.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = when {
                call.isRinging -> "Ringing…"
                call.isOnHold -> "On hold"
                else -> formatDuration(seconds)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CallControl(
                label = if (call.isMuted) "Unmute" else "Mute",
                active = call.isMuted,
                onClick = viewModel::toggleMute,
            ) { Icon(Icons.Filled.MicOff, contentDescription = null) }

            CallControl(
                label = "Speaker",
                active = call.isSpeakerphoneOn,
                onClick = viewModel::toggleSpeaker,
            ) { Icon(Icons.Filled.VolumeUp, contentDescription = null) }

            CallControl(
                label = "Hold",
                active = call.isOnHold,
                onClick = viewModel::toggleHold,
            ) { Icon(Icons.Filled.Pause, contentDescription = null) }
        }

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Let Agent Handle", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (call.transport == CallTransport.Sim && !call.isSpeakerphoneOn) {
                            "Turn on the speaker first — SIM audio is only reachable that way."
                        } else {
                            "Listens, thinks and speaks on this call."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = call.agentHandling,
                    onCheckedChange = viewModel::setAgentHandling,
                    enabled = call.transport != CallTransport.Sim || call.isSpeakerphoneOn,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = viewModel::endCall,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("End Call", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CallControl(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            onClick = onClick,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
