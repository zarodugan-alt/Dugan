package com.dugan.agent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dugan.agent.data.repository.SettingsRepository
import com.dugan.agent.domain.model.DuganSettings
import com.dugan.agent.domain.telecom.PhoneAccountRegistrar
import com.dugan.agent.ui.dialer.DialerScreen
import com.dugan.agent.ui.main.MainScreen
import com.dugan.agent.ui.navigation.Destination
import com.dugan.agent.ui.onboarding.OnboardingScreen
import com.dugan.agent.ui.settings.SettingsScreen
import com.dugan.agent.ui.theme.AppTheme
import com.dugan.agent.ui.theme.DuganTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var registrar: PhoneAccountRegistrar

    private val dialerRoleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // The banner on the Agent screen reflects the outcome; nothing to do here.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        registrar.register()

        val settings = settingsRepository.settings
            .stateIn(lifecycleScope, SharingStarted.WhileSubscribed(5_000), DuganSettings())

        setContent {
            val current by settings.collectAsStateWithLifecycle()
            DuganTheme(
                theme = AppTheme.fromId(current.themeId),
                dynamicColor = current.dynamicColor,
            ) {
                AppRoot(
                    onboardingCompleted = current.onboardingCompleted,
                    onRequestDialerRole = ::requestDialerRole,
                )
            }
        }
    }

    private fun requestDialerRole() {
        val intent = registrar.requestDefaultDialerIntent() ?: return
        runCatching { dialerRoleRequest.launch(intent) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

/**
 * Root: onboarding gates everything on first run, then a three-tab shell.
 *
 * The call UI is deliberately NOT a route here -- Telecom needs it on its own
 * task, so it lives in [com.dugan.agent.ui.call.CallActivity].
 */
@Composable
private fun AppRoot(
    onboardingCompleted: Boolean,
    onRequestDialerRole: () -> Unit,
) {
    if (!onboardingCompleted) {
        OnboardingScreen(onComplete = { /* settings flip, recomposition handles it */ })
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val tabs = listOf(Destination.Agent, Destination.Dialer)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Settings is reachable from the Agent screen's top bar, so it does
            // not need a third tab competing for width.
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Agent.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Agent.route) {
                MainScreen(onOpenSettings = { navController.navigate(Destination.Settings.route) })
            }
            composable(Destination.Dialer.route) {
                DialerScreen(onVoiceDial = { navController.navigate(Destination.Agent.route) })
            }
            composable(Destination.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
