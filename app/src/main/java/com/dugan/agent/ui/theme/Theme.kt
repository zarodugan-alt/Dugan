package com.dugan.agent.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Lets any component ask which scheme is active (the orb tints per theme). */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.CrimsonNoir }

/**
 * Root theme.
 *
 * [dynamicColor] opts into Material You, which overrides the selected scheme on
 * Android 12+. Off by default: the ten hand-tuned palettes are the product.
 */
@Composable
fun DuganTheme(
    theme: AppTheme = AppTheme.CrimsonNoir,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamic && isSystemInDarkTheme() ->
            androidx.compose.material3.dynamicDarkColorScheme(context)
        useDynamic -> androidx.compose.material3.dynamicLightColorScheme(context)
        else -> theme.colorScheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.value.toInt()
            window.navigationBarColor = colorScheme.background.value.toInt()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = theme.isLight
        }
    }

    CompositionLocalProvider(LocalAppTheme provides theme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DuganTypography,
            content = content,
        )
    }
}

/** Convenience for previews that need a fixed scheme. */
@Composable
fun DuganPreviewTheme(content: @Composable () -> Unit) {
    DuganTheme(theme = AppTheme.CrimsonNoir, content = content)
}

/** Accent used by the orb and status pill, resolved from the active scheme. */
object AgentColors {
    @Composable
    fun idle(): Color = MaterialTheme.colorScheme.outline

    @Composable
    fun listening(): Color = MaterialTheme.colorScheme.primary

    @Composable
    fun thinking(): Color = Color(0xFFFFB300)

    @Composable
    fun speaking(): Color = MaterialTheme.colorScheme.secondary

    @Composable
    fun error(): Color = MaterialTheme.colorScheme.error
}
