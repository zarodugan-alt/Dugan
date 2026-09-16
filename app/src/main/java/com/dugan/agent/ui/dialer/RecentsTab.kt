package com.dugan.agent.ui.dialer

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dugan.agent.data.repository.CallDirection
import com.dugan.agent.data.repository.RecentCall
import java.text.DateFormat
import java.util.Date

@Composable
fun RecentsTab(
    recents: List<RecentCall>,
    onCall: (String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (recents.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No recent calls.\nGrant the Call Log permission to see them here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(recents, key = { it.id }) { call ->
            RecentRow(call = call, onCall = { onCall(call.number) }, onDelete = { onDelete(call.id) })
        }
    }
}

@Composable
private fun RecentRow(call: RecentCall, onCall: () -> Unit, onDelete: () -> Unit) {
    val accent = when (call.direction) {
        CallDirection.Missed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initials(call.displayName ?: call.number),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = call.displayName ?: call.number,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (call.direction == CallDirection.Missed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (call.direction) {
                        CallDirection.Incoming -> Icons.AutoMirrored.Filled.CallReceived
                        CallDirection.Outgoing -> Icons.AutoMirrored.Filled.CallMade
                        CallDirection.Missed -> Icons.Filled.CallMissed
                        CallDirection.Unknown -> Icons.AutoMirrored.Filled.CallMade
                    },
                    contentDescription = call.direction.name,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(call.timestampMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = onCall) {
            Icon(
                Icons.AutoMirrored.Filled.CallMade,
                contentDescription = "Call ${call.displayName ?: call.number}",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

internal fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts.first().first()}${parts.last().first()}".uppercase()
    }
}

@Suppress("unused")
private val unused = Arrangement.Top
