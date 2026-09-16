package com.dugan.agent.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dugan.agent.domain.model.Speaker
import com.dugan.agent.domain.model.TranscriptEntry

/**
 * Transcript bubbles. User left-aligned on primaryContainer, agent right-aligned
 * on surfaceVariant, capped at 80% width, auto-scrolling to the newest entry.
 */
@Composable
fun TranscriptView(
    entries: List<TranscriptEntry>,
    partialUser: String,
    partialAgent: String,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val visible = buildList {
        addAll(entries)
        if (partialAgent.isNotBlank()) {
            add(
                TranscriptEntry(
                    id = -1,
                    speaker = Speaker.Agent,
                    text = partialAgent,
                    timestampMs = 0,
                    isFinal = false,
                ),
            )
        }
    }

    LaunchedEffect(visible.size, visible.lastOrNull()?.text?.length) {
        if (visible.isNotEmpty()) listState.animateScrollToItem(visible.size - 1)
    }

    if (visible.isEmpty() && partialUser.isBlank()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Say something.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Hold the mic button, or turn on continuous listening.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(visible, key = { it.id }) { entry ->
            Bubble(entry)
        }
        if (partialUser.isNotBlank()) {
            item {
                Bubble(
                    TranscriptEntry(
                        id = -2,
                        speaker = Speaker.User,
                        text = partialUser,
                        timestampMs = 0,
                        isFinal = false,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Bubble(entry: TranscriptEntry) {
    val isUser = entry.speaker == Speaker.User
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = shape,
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = entry.text + if (entry.isFinal) "" else " ▌",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (entry.latencyMs != null && entry.latencyMs > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${entry.latencyMs} ms to first audio",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
