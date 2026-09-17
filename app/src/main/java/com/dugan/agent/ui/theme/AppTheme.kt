package com.dugan.agent.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The ten selectable schemes.
 *
 * Each entry carries a full [ColorScheme] rather than just a primary colour: the
 * spec's table fixes primary/secondary/background/surface, and the remaining
 * Material roles are derived from them so components do not need per-theme
 * special cases.
 */
enum class AppTheme(
    val id: String,
    val displayName: String,
    /** Initial shown in the Settings theme grid. */
    val initial: Char,
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
) {
    CrimsonNoir(
        id = "crimson_noir",
        displayName = "Crimson Noir",
        initial = 'C',
        primary = Color(0xFFDC143C),
        secondary = Color(0xFF8B0000),
        background = Color(0xFF0A0A0A),
        surface = Color(0xFF1A1A1A),
    ),
    Crystal(
        id = "crystal",
        displayName = "Crystal",
        initial = 'C',
        primary = Color(0xFF37474F),
        secondary = Color(0xFF9E9E9E),
        background = Color(0xFFF5F5F5),
        surface = Color(0xFFFFFFFF),
    ),
    Midnight(
        id = "midnight",
        displayName = "Midnight",
        initial = 'M',
        primary = Color(0xFFBB86FC),
        secondary = Color(0xFF7C4DFF),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
    ),
    Onyx(
        id = "onyx",
        displayName = "Onyx",
        initial = 'O',
        primary = Color(0xFFFFFFFF),
        secondary = Color(0xFFB0B0B0),
        background = Color(0xFF000000),
        surface = Color(0xFF0D0D0D),
    ),
    Pearl(
        id = "pearl",
        displayName = "Pearl",
        initial = 'P',
        primary = Color(0xFF212121),
        secondary = Color(0xFF616161),
        background = Color(0xFFFAFAFA),
        surface = Color(0xFFFFFFFF),
    ),
    Ember(
        id = "ember",
        displayName = "Ember",
        initial = 'E',
        primary = Color(0xFFFF6B35),
        secondary = Color(0xFFF7C59F),
        background = Color(0xFF1A1A2E),
        surface = Color(0xFF16213E),
    ),
    Arctic(
        id = "arctic",
        displayName = "Arctic",
        initial = 'A',
        primary = Color(0xFF00838F),
        secondary = Color(0xFF0097A7),
        background = Color(0xFFECEFF1),
        surface = Color(0xFFFFFFFF),
    ),
    Amethyst(
        id = "amethyst",
        displayName = "Amethyst",
        initial = 'A',
        primary = Color(0xFFCE93D8),
        secondary = Color(0xFF6A1B9A),
        background = Color(0xFF0F0F0F),
        surface = Color(0xFF1A1A1A),
    ),
    Sage(
        id = "sage",
        displayName = "Sage",
        initial = 'S',
        primary = Color(0xFF81C784),
        secondary = Color(0xFF2E7D32),
        background = Color(0xFF0D1B0F),
        surface = Color(0xFF1A2E1C),
    ),
    Solar(
        id = "solar",
        displayName = "Solar",
        initial = 'S',
        primary = Color(0xFFFFC107),
        secondary = Color(0xFFFF9800),
        background = Color(0xFF1A1A1A),
        surface = Color(0xFF2A2A2A),
    ),
    ;

    /** A background lighter than 0.5 luminance gets the light scheme. */
    val isLight: Boolean get() = background.luminance() > 0.5f

    fun colorScheme(): ColorScheme {
        val onBackground = if (isLight) Color(0xFF111111) else Color(0xFFF2F2F2)
        val onSurface = if (isLight) Color(0xFF1A1A1A) else Color(0xFFEDEDED)
        val onPrimary = if (primary.luminance() > 0.6f) Color(0xFF101010) else Color(0xFFFFFFFF)
        val onSecondary = if (secondary.luminance() > 0.6f) Color(0xFF101010) else Color(0xFFFFFFFF)
        val surfaceVariant = blend(surface, onSurface, 0.08f)
        val outline = blend(surface, onSurface, 0.32f)
        val error = Color(0xFFE53935)

        return if (isLight) {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = blend(primary, Color.White, 0.82f),
                onPrimaryContainer = blend(primary, Color.Black, 0.6f),
                secondary = secondary,
                onSecondary = onSecondary,
                secondaryContainer = blend(secondary, Color.White, 0.85f),
                onSecondaryContainer = blend(secondary, Color.Black, 0.6f),
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = blend(onSurface, surface, 0.25f),
                outline = outline,
                error = error,
                onError = Color.White,
                errorContainer = blend(error, Color.White, 0.85f),
                onErrorContainer = blend(error, Color.Black, 0.6f),
            )
        } else {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = blend(primary, Color.Black, 0.62f),
                onPrimaryContainer = blend(primary, Color.White, 0.75f),
                secondary = secondary,
                onSecondary = onSecondary,
                secondaryContainer = blend(secondary, Color.Black, 0.62f),
                onSecondaryContainer = blend(secondary, Color.White, 0.75f),
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = blend(onSurface, surface, 0.3f),
                outline = outline,
                error = error,
                onError = Color.White,
                errorContainer = blend(error, Color.Black, 0.55f),
                onErrorContainer = blend(error, Color.White, 0.85f),
            )
        }
    }

    companion object {
        fun fromId(id: String): AppTheme = entries.firstOrNull { it.id == id } ?: CrimsonNoir
    }
}

/** Rec.709 relative luminance of an sRGB colour. */
private fun Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Linear blend used to derive the Material roles the spec does not pin. */
private fun blend(from: Color, to: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = from.alpha + (to.alpha - from.alpha) * t,
    )
}
