package com.dugan.agent.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dugan.agent.domain.model.ThinkingLevel

/** Quick / Balanced / Deep -> Gemini thinkingBudget. */
@Composable
fun ThinkingSelector(
    selected: ThinkingLevel,
    onSelect: (ThinkingLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val levels = ThinkingLevel.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        levels.forEachIndexed { index, level ->
            SegmentedButton(
                selected = level == selected,
                onClick = { onSelect(level) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = levels.size),
            ) {
                Text(level.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
