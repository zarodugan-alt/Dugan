package com.dugan.agent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
 * One BYOK key: masked input, signup link, and a Test Connection button that
 * makes the smallest valid call the provider accepts.
 */
@Composable
fun KeyField(
    provider: ApiProvider,
    value: String,
    onValueChange: (String) -> Unit,
    onTest: () -> Unit,
    testResult: KeyTestResult,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
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
                    Text("Open")
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Paste key") },
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
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onTest, enabled = value.isNotBlank() && testResult !is KeyTestResult.Testing) {
                    Text(if (testResult is KeyTestResult.Testing) "Testing…" else "Test Connection")
                }
                TestStatusBadge(testResult)
            }

            if (!compact && testResult is KeyTestResult.Invalid) {
                Text(
                    testResult.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TestStatusBadge(result: KeyTestResult) {
    when (result) {
        is KeyTestResult.Valid -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Valid",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("OK", style = MaterialTheme.typography.labelMedium)
        }

        is KeyTestResult.Invalid -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = "Invalid",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Failed", style = MaterialTheme.typography.labelMedium)
        }

        else -> Spacer(Modifier.height(20.dp))
    }
}
