package com.dugan.agent.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dugan.agent.domain.model.AgentPhase
import com.dugan.agent.domain.model.CallState
import com.dugan.agent.domain.model.CallTransport

/**
 * Pill at the top of the main screen: agent phase plus call status.
 *
 * The dot pulses while the agent is thinking, which is the one state where
 * nothing else on screen is moving and the user would otherwise wonder whether
 * the app has hung.
 */
@Composable
fun StatePill(
    phase: AgentPhase,
    callState: CallState,
    modifier: Modifier = Modifier,
) {
    val accent = when (phase) {
        AgentPhase.Idle -> MaterialTheme.colorScheme.outline
        AgentPhase.Listening -> MaterialTheme.colorScheme.primary
        AgentPhase.Thinking -> Color(0xFFFFB300)
        AgentPhase.Speaking -> MaterialTheme.colorScheme.secondary
        AgentPhase.Paused -> MaterialTheme.colorScheme.tertiary
        AgentPhase.Error -> MaterialTheme.colorScheme.error
    }
    val animatedAccent by animateColorAsState(accent, tween(400), label = "pill-accent")

    val pulse = rememberInfiniteTransition(label = "pill-pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (phase == AgentPhase.Thinking) 0.25f else 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "pill-pulse-alpha",
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(pulseAlpha),
            ) {
                drawCircle(animatedAccent)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = phase.label(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "  ·  ",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = callState.label(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun AgentPhase.label(): String = when (this) {
    AgentPhase.Idle -> "Idle"
    AgentPhase.Listening -> "Listening"
    AgentPhase.Thinking -> "Thinking"
    AgentPhase.Speaking -> "Speaking"
    AgentPhase.Paused -> "Paused"
    AgentPhase.Error -> "Error"
}

private fun CallState.label(): String = when {
    transport == CallTransport.None -> "No Call Active"
    transport == CallTransport.Sim && agentHandling -> "SIM Call · Agent"
    transport == CallTransport.Sim -> "SIM Call"
    transport == CallTransport.Voip && agentHandling -> "VoIP Call · Agent"
    else -> "VoIP Call"
}
