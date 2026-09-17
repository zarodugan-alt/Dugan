package com.dugan.agent.ui.components

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dugan.agent.domain.model.AgentModel
import com.dugan.agent.domain.model.ModelProvider

/** Single-letter provider badge: "G" for Gemini, "Q" for Groq. */
@Composable
fun ProviderBadge(provider: ModelProvider, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(18.dp),
        shape = CircleShape,
        color = if (provider == ModelProvider.Gemini) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Text(
            text = provider.badge,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (provider == ModelProvider.Gemini) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            // Surface's content lambda is not a BoxScope, so Modifier.align is not
            // available here -- centre with wrapContentSize instead.
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun ModelChip(
    model: AgentModel,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onSelect,
        modifier = modifier,
        label = { Text(model.displayName, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = { ProviderBadge(model.provider) },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** Horizontally scrollable row of thinking models. */
@Composable
fun ModelSelectorRow(
    models: List<AgentModel>,
    selectedId: String,
    onSelect: (AgentModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        items(models.size) { index ->
            val model = models[index]
            Row {
                Spacer(Modifier.width(0.dp))
                ModelChip(
                    model = model,
                    selected = model.id == selectedId,
                    onSelect = { onSelect(model) },
                )
            }
        }
    }
}
