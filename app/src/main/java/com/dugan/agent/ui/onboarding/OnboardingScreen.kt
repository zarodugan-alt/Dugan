package com.dugan.agent.ui.onboarding

import android.Manifest
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dugan.agent.domain.model.ApiProvider
import com.dugan.agent.domain.model.KeyTestResult
import com.dugan.agent.ui.components.AgentOrb
import com.dugan.agent.ui.components.KeyField
import com.dugan.agent.ui.settings.ThemeGrid
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

private data class PermissionSpec(
    val permission: String,
    val title: String,
    val rationale: String,
    val critical: Boolean,
)

private val PERMISSIONS = listOf(
    PermissionSpec(Manifest.permission.RECORD_AUDIO, "Microphone", "To hear your calls.", true),
    PermissionSpec(Manifest.permission.READ_CONTACTS, "Contacts", "To call people by name.", false),
    PermissionSpec(Manifest.permission.READ_CALL_LOG, "Call log", "To show recent calls in the dialer.", false),
    PermissionSpec(Manifest.permission.POST_NOTIFICATIONS, "Notifications", "To show when the agent is active.", false),
    PermissionSpec(Manifest.permission.READ_PHONE_STATE, "Phone state", "To detect when a SIM call is active.", false),
    PermissionSpec(Manifest.permission.CALL_PHONE, "Place calls", "To dial from the keypad or by voice.", false),
)

@OptIn(ExperimentalPermissionsApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val advisories by viewModel.advisories.collectAsStateWithLifecycle()
    val themeId by viewModel.themeId.collectAsStateWithLifecycle()

    val permissionState = rememberMultiplePermissionsState(PERMISSIONS.map { it.permission })
    val granted = permissionState.permissions.associate { it.permission to it.status.isGranted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Welcome to Voice Agent") },
                navigationIcon = {
                    if (step > 0) {
                        IconButton(onClick = viewModel::back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            LinearProgressIndicator(
                progress = { (step + 1) / 3f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                AgentOrb(
                    phase = com.dugan.agent.domain.model.AgentPhase.Idle,
                    inputLevel = 0f,
                    outputLevel = 0f,
                    modifier = Modifier.size(120.dp),
                )
            }

            AnimatedContent(targetState = step, label = "onboarding-step") { current ->
                when (current) {
                    0 -> KeysStep(
                        keys = keys,
                        results = results,
                        advisories = advisories,
                        onKeyChange = viewModel::onKeyChange,
                        onTest = viewModel::test,
                        canContinue = viewModel.allKeysValid,
                        onContinue = viewModel::next,
                    )

                    1 -> PermissionsStep(
                        granted = granted,
                        onGrant = { permissionState.launchMultiplePermissionRequest() },
                        onSkip = viewModel::skipPermission,
                        onContinue = viewModel::next,
                    )

                    else -> ThemeStep(
                        themeId = themeId,
                        onSelect = viewModel::selectTheme,
                        onContinue = {
                            viewModel.finish()
                            onComplete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeysStep(
    keys: Map<String, String>,
    results: Map<String, KeyTestResult>,
    advisories: Map<String, String?>,
    onKeyChange: (ApiProvider, String) -> Unit,
    onTest: (ApiProvider) -> Unit,
    canContinue: Boolean,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Step 1 of 3 · API Keys", style = MaterialTheme.typography.titleMedium)
        Text(
            "This app is BYOK: your keys are encrypted with a Keystore-backed master key and " +
                "stored only on this device. They are never sent to any server, and they are " +
                "excluded from Android auto-backup.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "You need three keys. All three have a usable free tier. Paste one and it is " +
                "checked against the provider automatically — a green tick means the key " +
                "works and has been stored.",
            style = MaterialTheme.typography.bodyMedium,
        )

        ApiProvider.entries.forEach { provider ->
            KeyField(
                provider = provider,
                value = keys[provider.id].orEmpty(),
                storedMask = null,
                // Onboarding requires a green Test to continue, so any non-blank
                // draft is by definition a change from the empty vault.
                dirty = keys[provider.id].orEmpty().isNotBlank(),
                onValueChange = { onKeyChange(provider, it) },
                onSave = { onTest(provider) },
                onTest = { onTest(provider) },
                testResult = results[provider.id] ?: KeyTestResult.Untested,
                advisory = advisories[provider.id],
            )
        }

        Button(
            onClick = onContinue,
            enabled = canContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (canContinue) "Continue" else "Waiting for all three keys to check out")
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionsStep(
    granted: Map<String, Boolean>,
    onGrant: () -> Unit,
    onSkip: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Step 2 of 3 · Permissions", style = MaterialTheme.typography.titleMedium)
        Text(
            "Everything except the microphone is optional. Declining one only disables the " +
                "feature it powers.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PERMISSIONS.forEach { spec ->
            val isGranted = granted[spec.permission] == true
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            spec.title + if (spec.critical) " (required)" else "",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            spec.rationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isGranted) {
                        Text(
                            "Granted",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        TextButton(onClick = onGrant) { Text("Grant") }
                    }
                }
            }
            if (!isGranted && !spec.critical) {
                TextButton(onClick = { onSkip(spec.permission) }) {
                    Text("Continue without this feature")
                }
            }
        }

        Button(
            onClick = onContinue,
            enabled = granted[Manifest.permission.RECORD_AUDIO] == true,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun ThemeStep(
    themeId: String,
    onSelect: (com.dugan.agent.ui.theme.AppTheme) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Step 3 of 3 · Theme", style = MaterialTheme.typography.titleMedium)
        Text(
            "Pick a starting palette. You can change it any time in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ThemeGrid(selectedId = themeId, onSelect = onSelect)

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Finish")
        }
    }
}
