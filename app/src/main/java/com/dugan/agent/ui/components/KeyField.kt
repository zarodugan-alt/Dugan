package com.dugan.agent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dugan.agent.domain.model.ApiProvider
import com.dugan.agent.domain.model.KeyTestResult

/**
 * One BYOK key.
 *
 * Save and Test are separate actions on purpose. Saving is a purely local write
 * to the encrypted vault and must work with no network at all; Test additionally
 * spends a request proving the key is live. Requiring a green Test before a key
 * could be stored would make the app unusable offline and would burn quota on
 * every edit.
 *
 * @param storedMask masked form of the key already in the vault, or null
 * @param dirty true when [value] differs from what is stored -- enables Save
 */
@Composable
fun KeyField(
    provider: ApiProvider,
    value: String,
    storedMask: String?,
    dirty: Boolean,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    testResult: KeyTestResult,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    var revealed by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(provider.emoji, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${provider.displayName} (${provider.role})",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        provider.signupUrl.removePrefix("https://"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { runCatching { uriHandler.openUri(provider.signupUrl) } }) {
                    Text("Sign up")
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Paste key") },
                placeholder = {
                    Text(storedMask ?: "gsk_… / AIza… / …")
                },
                visualTransformation = if (revealed) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (revealed) "Hide key" else "Show key",
                        )
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                supportingText = {
                    when {
                        dirty && storedMask != null ->
                            Text("Unsaved change — stored key is $storedMask")
                        storedMask != null ->
                            Text("Stored: $storedMask")
                        else ->
                            Text("Not configured")
                    }
                },
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSave,
                    enabled = dirty && value.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
                Button(
                    onClick = onTest,
                    // Test saves first so the client reads the new key from the vault.
                    enabled = value.isNotBlank() && testResult !is KeyTestResult.Testing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (testResult is KeyTestResult.Testing) "Testing…" else "Test")
                }
                if (onClear != null && storedMask != null) {
                    TextButton(onClick = onClear) { Text("Remove") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TestStatusBadge(testResult)
            }
        }
    }
}

@Composable
private fun TestStatusBadge(result: KeyTestResult) {
    when (result) {
        is KeyTestResult.Testing -> Text(
            "Contacting provider…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is KeyTestResult.Valid -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Valid",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Reachable — ${result.detail}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        is KeyTestResult.Invalid -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = "Invalid",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                result.detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        KeyTestResult.Untested -> Spacer(Modifier.height(18.dp))
    }
}
