package com.dugan.agent.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Bottom-navigation destinations. CallScreen is a separate activity, not a route. */
enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Agent("agent", "Agent", Icons.Filled.GraphicEq),
    Dialer("dialer", "Dialer", Icons.Filled.Dialpad),
    Settings("settings", "Settings", Icons.Filled.Settings),
    ;

    companion object {
        val Onboarding = "onboarding"
    }
}
