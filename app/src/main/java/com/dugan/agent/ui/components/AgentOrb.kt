package com.dugan.agent.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dugan.agent.domain.model.AgentPhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The pulsing orb.
 *
 * Radius and glow track RMS amplitude; hue tracks agent phase. Spring physics on
 * the radius so a loud syllable makes it bloom rather than snap.
 */
@Composable
fun AgentOrb(
    phase: AgentPhase,
    inputLevel: Float,
    outputLevel: Float,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 180.dp,
) {
    val target = when (phase) {
        AgentPhase.Idle -> MaterialTheme.colorScheme.outline
        AgentPhase.Listening -> MaterialTheme.colorScheme.primary
        AgentPhase.Thinking -> Color(0xFFFFB300)
        AgentPhase.Speaking -> MaterialTheme.colorScheme.secondary
        AgentPhase.Paused -> MaterialTheme.colorScheme.tertiary
        AgentPhase.Error -> MaterialTheme.colorScheme.error
    }
    val accent by animateColorAsState(target, tween(350), label = "orb-colour")

    val level = if (phase == AgentPhase.Speaking) outputLevel else inputLevel
    val scale by animateFloatAsState(
        targetValue = 0.55f + (level.coerceIn(0f, 1f) * 0.45f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "orb-scale",
    )

    val glowAlpha by animateFloatAsState(
        targetValue = 0.25f + level.coerceIn(0f, 1f) * 0.55f,
        animationSpec = tween(180),
        label = "orb-glow",
    )

    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val baseRadius = (this.size.minDimension / 2f) * 0.42f
        val radius = baseRadius * scale

        // Outer glow.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = glowAlpha), Color.Transparent),
                center = Offset(cx, cy),
                radius = radius * 2.1f,
            ),
            radius = radius * 2.1f,
            center = Offset(cx, cy),
        )

        // Rotating ring of ticks; reads as "alive" even when the level is flat.
        val tickCount = 36
        val rotation = (System.currentTimeMillis() % 6000) / 6000f * (2f * PI.toFloat())
        for (i in 0 until tickCount) {
            val angle = rotation + (i / tickCount.toFloat()) * 2f * PI.toFloat()
            val wobble = 1f + 0.12f * sin(angle * 3f + rotation * 2f) * level.coerceIn(0f, 1f)
            val inner = radius * 1.28f * wobble
            val outer = inner + (this.size.minDimension * 0.035f) * (0.4f + level)
            drawLine(
                color = accent.copy(alpha = 0.25f + 0.5f * level),
                start = Offset(cx + cos(angle) * inner, cy + sin(angle) * inner),
                end = Offset(cx + cos(angle) * outer, cy + sin(angle) * outer),
                strokeWidth = 2f,
            )
        }

        // Core.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.55f)),
                center = Offset(cx - radius * 0.25f, cy - radius * 0.25f),
                radius = radius * 1.4f,
            ),
            radius = radius,
            center = Offset(cx, cy),
        )
    }
}
