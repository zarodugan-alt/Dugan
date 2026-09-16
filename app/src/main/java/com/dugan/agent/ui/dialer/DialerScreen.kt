package com.dugan.agent.ui.dialer

import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val TABS = listOf("Keypad", "Recents", "Contacts")

@Composable
fun DialerScreen(
    onVoiceDial: () -> Unit,
    viewModel: DialerViewModel = hiltViewModel(),
) {
    var tab by remember { mutableIntStateOf(0) }
    val entered by viewModel.entered.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            TABS.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title) },
                )
            }
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }

        when (tab) {
            0 -> KeypadTab(
                entered = entered,
                onPress = viewModel::press,
                onLongPressZero = viewModel::longPressZero,
                onLongPressStar = viewModel::longPressStar,
                onBackspace = viewModel::backspace,
                onCall = { viewModel.call(entered) },
                onVoiceDial = onVoiceDial,
            )
            1 -> RecentsTab(
                recents = viewModel.recents.collectAsStateWithLifecycle().value,
                onCall = viewModel::call,
                onDelete = viewModel::deleteRecent,
            )
            else -> ContactsTab(
                contacts = viewModel.contacts.collectAsStateWithLifecycle().value,
                query = viewModel.query.collectAsStateWithLifecycle().value,
                onQueryChange = viewModel::setQuery,
                onCall = viewModel::call,
            )
        }
    }
}

private data class Key(val digit: String, val letters: String)

private val KEYS = listOf(
    Key("1", ""), Key("2", "ABC"), Key("3", "DEF"),
    Key("4", "GHI"), Key("5", "JKL"), Key("6", "MNO"),
    Key("7", "PQRS"), Key("8", "TUV"), Key("9", "WXYZ"),
    Key("*", ""), Key("0", "+"), Key("#", ""),
)

@Composable
fun KeypadTab(
    entered: String,
    onPress: (String) -> Unit,
    onLongPressZero: () -> Unit,
    onLongPressStar: () -> Unit,
    onBackspace: () -> Unit,
    onCall: () -> Unit,
    onVoiceDial: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entered.ifBlank { " " },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            KEYS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    row.forEach { key ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(72.dp)
                                .combinedClickable(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (key.digit == "*") onPress("*") else onPress(key.digit)
                                    },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        when (key.digit) {
                                            "0" -> onLongPressZero()
                                            "*" -> onLongPressStar()
                                            else -> onPress(key.digit)
                                        }
                                    },
                                ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    key.digit,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Normal,
                                )
                                if (key.letters.isNotEmpty()) {
                                    Text(
                                        key.letters,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = onBackspace, enabled = entered.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(72.dp)
                    .combinedClickable(
                        onClick = onCall,
                        onLongClick = onCall,
                    ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = "Call",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            FilledIconButton(onClick = onVoiceDial) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice dial")
            }
        }
    }
}
