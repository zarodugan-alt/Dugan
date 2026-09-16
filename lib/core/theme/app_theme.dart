import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import 'app_colors.dart';

/// Builds the light and dark [ThemeData] for Dugan.
///
/// Uses Material 3 with a seeded accent, but every surface token is
/// overridden with the premium palette from the design spec so dynamic
/// (wallpaper-based) color never leaks in.
class AppTheme {
  AppTheme._();

  static const String fontFamily = 'PlusJakartaSans';

  static ThemeData dark({AccentOption accent = kAccentOptions.first}) {
    final colors = DuganColors.dark(
      accent: accent.color,
      accentSecondary: accent.secondary,
    );
    return _build(Brightness.dark, colors);
  }

  static ThemeData light({AccentOption accent = kAccentOptions.first}) {
    final colors = DuganColors.light(
      accent: accent.color,
      accentSecondary: accent.secondary,
    );
    return _build(Brightness.light, colors);
  }

  static ThemeData _build(Brightness brightness, DuganColors colors) {
    final isDark = brightness == Brightness.dark;
    final scheme = ColorScheme(
      brightness: brightness,
      primary: colors.accentPrimary,
      onPrimary: Colors.white,
      primaryContainer: colors.accentPrimary.withOpacity(0.18),
      onPrimaryContainer: colors.accentPrimary,
      secondary: colors.accentSecondary,
      onSecondary: Colors.white,
      secondaryContainer: colors.accentSecondary.withOpacity(0.16),
      onSecondaryContainer: colors.accentSecondary,
      error: colors.error,
      onError: Colors.white,
      surface: colors.surfacePrimary,
      onSurface: colors.textPrimary,
      onSurfaceVariant: colors.textSecondary,
      outline: colors.glassBorder,
      outlineVariant: colors.glassBorder,
    );

    final baseText = isDark
        ? ThemeData(brightness: Brightness.dark).textTheme
        : ThemeData(brightness: Brightness.light).textTheme;

    // Typography scale from the spec:
    //   Display 32/w700 · Headline 20/w600 · Body 14/w400 · Label 12/w500.
    final textTheme =
        GoogleFonts.plusJakartaSansTextTheme(baseText).copyWith(
      displaySmall: baseText.displaySmall!.copyWith(
        fontSize: 32,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.5,
        color: colors.textPrimary,
      ),
      headlineSmall: baseText.headlineSmall!.copyWith(
        fontSize: 20,
        fontWeight: FontWeight.w600,
        letterSpacing: -0.2,
        color: colors.textPrimary,
      ),
      titleMedium: baseText.titleMedium!.copyWith(
        fontSize: 16,
        fontWeight: FontWeight.w600,
        color: colors.textPrimary,
      ),
      titleSmall: baseText.titleSmall!.copyWith(
        fontSize: 14,
        fontWeight: FontWeight.w600,
        color: colors.textPrimary,
      ),
      bodyMedium: baseText.bodyMedium!.copyWith(
        fontSize: 14,
        fontWeight: FontWeight.w400,
        height: 1.35,
        color: colors.textPrimary,
      ),
      bodySmall: baseText.bodySmall!.copyWith(
        fontSize: 12,
        fontWeight: FontWeight.w400,
        color: colors.textSecondary,
      ),
      labelSmall: baseText.labelSmall!.copyWith(
        fontSize: 12,
        fontWeight: FontWeight.w500,
        letterSpacing: 0.6,
        color: colors.textSecondary,
      ),
      labelLarge: baseText.labelLarge!.copyWith(
        fontSize: 15,
        fontWeight: FontWeight.w600,
        color: colors.textPrimary,
      ),
    );

    final inputBorder = OutlineInputBorder(
      borderRadius: BorderRadius.circular(16),
      borderSide: BorderSide(color: colors.glassBorder),
    );

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: scheme,
      scaffoldBackgroundColor: colors.surfacePrimary,
      splashFactory: InkSparkle.splashFactory,
      textTheme: textTheme,
      extensions: [colors],
      appBarTheme: AppBarTheme(
        backgroundColor: colors.surfacePrimary,
        foregroundColor: colors.textPrimary,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: textTheme.headlineSmall,
        iconTheme: IconThemeData(color: colors.textPrimary),
        actionsIconTheme: IconThemeData(color: colors.textSecondary),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: colors.surfaceSecondary,
        indicatorColor: colors.accentPrimary.withOpacity(0.22),
        height: 68,
        elevation: 0,
        labelTextStyle: WidgetStatePropertyAll(
          textTheme.labelSmall!.copyWith(fontWeight: FontWeight.w600),
        ),
        iconTheme: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return IconThemeData(color: colors.accentPrimary);
          }
          return IconThemeData(color: colors.textSecondary);
        }),
      ),
      tabBarTheme: TabBarTheme(
        labelColor: colors.textPrimary,
        unselectedLabelColor: colors.textSecondary,
        labelStyle: textTheme.titleSmall,
        unselectedLabelStyle: textTheme.titleSmall!
            .copyWith(fontWeight: FontWeight.w500),
        indicatorSize: TabBarIndicatorSize.label,
        dividerColor: Colors.transparent,
        indicatorColor: colors.accentPrimary,
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: colors.surfaceTertiary,
        modalBackgroundColor: colors.surfaceTertiary,
        modalBarrierColor: colors.surfacePrimary.withOpacity(0.6),
        showDragHandle: false,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
        ),
      ),
      dialogTheme: DialogTheme(
        backgroundColor: colors.surfaceTertiary,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
        ),
        titleTextStyle: textTheme.titleMedium,
        contentTextStyle: textTheme.bodyMedium,
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: isDark ? const Color(0xFF26263A) : Colors.white,
        contentTextStyle: textTheme.bodyMedium!.copyWith(
          color: isDark ? colors.textPrimary : colors.textPrimary,
        ),
        actionTextColor: colors.accentSecondary,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: isDark
            ? colors.surfaceSecondary.withOpacity(0.72)
            : colors.surfaceSecondary,
        hintStyle: textTheme.bodyMedium!.copyWith(color: colors.textSecondary),
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        enabledBorder: inputBorder,
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(color: colors.accentBorder, width: 1.4),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(color: colors.error.withOpacity(0.6)),
        ),
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return Colors.white;
          }
          return colors.textSecondary;
        }),
        trackColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return colors.accentPrimary;
          }
          return colors.surfaceTertiary;
        }),
        trackOutlineColor:
            const WidgetStatePropertyAll(Colors.transparent),
      ),
      sliderTheme: SliderThemeData(
        activeTrackColor: colors.accentPrimary,
        inactiveTrackColor: colors.surfaceTertiary,
        thumbColor: colors.textPrimary,
        overlayColor: colors.accentPrimary.withOpacity(0.18),
        trackHeight: 4,
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(
        color: colors.accentPrimary,
        linearTrackColor: colors.surfaceTertiary,
        circularTrackColor: colors.surfaceTertiary,
      ),
      dividerTheme: DividerThemeData(
        color: colors.glassBorder,
        thickness: 1,
        space: 1,
      ),
      listTileTheme: ListTileThemeData(
        iconColor: colors.textSecondary,
        textColor: colors.textPrimary,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
      popupMenuTheme: PopupMenuThemeData(
        color: colors.surfaceTertiary,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
        ),
        textStyle: textTheme.bodyMedium,
      ),
      chipTheme: ChipThemeData(
        backgroundColor: colors.surfaceTertiary,
        side: BorderSide(color: colors.glassBorder),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
        labelStyle: textTheme.labelSmall!,
        selectedColor: colors.accentPrimary.withOpacity(0.2),
        showCheckmark: false,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      ),
      iconTheme: IconThemeData(color: colors.textPrimary),
      cardTheme: CardTheme(
        color: colors.surfaceSecondary,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: colors.accentSecondary,
          textStyle: textTheme.titleSmall,
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: colors.accentPrimary,
          foregroundColor: Colors.white,
          textStyle: textTheme.labelLarge,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
        ),
      ),
    );
  }
}
