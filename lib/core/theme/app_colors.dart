import 'package:flutter/material.dart';

/// Design tokens for the Dugan premium dark aesthetic, exposed to the widget
/// tree as a `ThemeExtension` so any widget can do
/// `Theme.of(context).extension<DuganColors>()!`.
///
/// Dark palette (OLED-optimised) follows the spec:
///
///  Token           Hex
///  --------------  ----------
///  surfacePrimary  #0A0A0F
///  surfaceSecondary#14141F
///  surfaceTertiary #1E1E2D
///  accentPrimary   #5B47E5
///  accentSecondary #8B5CF6
///  success         #10B981
///  warning         #F59E0B
///  error           #EF4444
///  textPrimary     #F8FAFC
///  textSecondary   #94A3B8
///  glassBorder     rgba(255,255,255,.08)
@immutable
class DuganColors extends ThemeExtension<DuganColors> {
  const DuganColors({
    required this.isDark,
    required this.surfacePrimary,
    required this.surfaceSecondary,
    required this.surfaceTertiary,
    required this.accentPrimary,
    required this.accentSecondary,
    required this.success,
    required this.warning,
    required this.error,
    required this.textPrimary,
    required this.textSecondary,
    required this.glassBorder,
  });

  final bool isDark;
  final Color surfacePrimary;
  final Color surfaceSecondary;
  final Color surfaceTertiary;
  final Color accentPrimary;
  final Color accentSecondary;
  final Color success;
  final Color warning;
  final Color error;
  final Color textPrimary;
  final Color textSecondary;
  final Color glassBorder;

  /// Signature gradient used on progress bars, CTAs, active tab indicators
  /// and the splash logo glow.
  LinearGradient get accentGradient => LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [accentPrimary, accentSecondary],
      );

  /// Slightly stronger border used for selected/focused states.
  Color get accentBorder => accentPrimary.withOpacity(0.55);

  /// Base tone for shimmer skeletons.
  Color get shimmerBase =>
      isDark ? surfaceSecondary : surfaceSecondary.withOpacity(0.6);
  Color get shimmerHighlight =>
      isDark ? surfaceTertiary : surfaceTertiary.withOpacity(0.9);

  /// Colored glow used instead of muddy elevation shadows on dark surfaces.
  List<BoxShadow> get accentGlow => [
        BoxShadow(
          color: accentPrimary.withOpacity(0.30),
          blurRadius: 20,
          offset: const Offset(0, 6),
        ),
      ];

  static DuganColors dark({required Color accent, Color? accentSecondary}) {
    return DuganColors(
      isDark: true,
      surfacePrimary: const Color(0xFF0A0A0F),
      surfaceSecondary: const Color(0xFF14141F),
      surfaceTertiary: const Color(0xFF1E1E2D),
      accentPrimary: accent,
      accentSecondary: accentSecondary ?? const Color(0xFF8B5CF6),
      success: const Color(0xFF10B981),
      warning: const Color(0xFFF59E0B),
      error: const Color(0xFFEF4444),
      textPrimary: const Color(0xFFF8FAFC),
      textSecondary: const Color(0xFF94A3B8),
      glassBorder: Colors.white.withOpacity(0.08),
    );
  }

  static DuganColors light({required Color accent, Color? accentSecondary}) {
    return DuganColors(
      isDark: false,
      surfacePrimary: const Color(0xFFF4F5FA),
      surfaceSecondary: Colors.white,
      surfaceTertiary: const Color(0xFFEDF0F8),
      accentPrimary: accent,
      accentSecondary: accentSecondary ?? const Color(0xFF8B5CF6),
      success: const Color(0xFF0B8F6C),
      warning: const Color(0xFFB47709),
      error: const Color(0xFFDC2626),
      textPrimary: const Color(0xFF0B0D17),
      textSecondary: const Color(0xFF5A6478),
      glassBorder: const Color(0xFF0B0D17).withOpacity(0.07),
    );
  }

  @override
  DuganColors copyWith({
    bool? isDark,
    Color? surfacePrimary,
    Color? surfaceSecondary,
    Color? surfaceTertiary,
    Color? accentPrimary,
    Color? accentSecondary,
    Color? success,
    Color? warning,
    Color? error,
    Color? textPrimary,
    Color? textSecondary,
    Color? glassBorder,
  }) {
    return DuganColors(
      isDark: isDark ?? this.isDark,
      surfacePrimary: surfacePrimary ?? this.surfacePrimary,
      surfaceSecondary: surfaceSecondary ?? this.surfaceSecondary,
      surfaceTertiary: surfaceTertiary ?? this.surfaceTertiary,
      accentPrimary: accentPrimary ?? this.accentPrimary,
      accentSecondary: accentSecondary ?? this.accentSecondary,
      success: success ?? this.success,
      warning: warning ?? this.warning,
      error: error ?? this.error,
      textPrimary: textPrimary ?? this.textPrimary,
      textSecondary: textSecondary ?? this.textSecondary,
      glassBorder: glassBorder ?? this.glassBorder,
    );
  }

  @override
  DuganColors lerp(covariant ThemeExtension<DuganColors>? other, double t) {
    if (other is! DuganColors) {
      return this;
    }
    return DuganColors(
      isDark: t < 0.5 ? isDark : other.isDark,
      surfacePrimary: Color.lerp(surfacePrimary, other.surfacePrimary, t)!,
      surfaceSecondary:
          Color.lerp(surfaceSecondary, other.surfaceSecondary, t)!,
      surfaceTertiary: Color.lerp(surfaceTertiary, other.surfaceTertiary, t)!,
      accentPrimary: Color.lerp(accentPrimary, other.accentPrimary, t)!,
      accentSecondary: Color.lerp(accentSecondary, other.accentSecondary, t)!,
      success: Color.lerp(success, other.success, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
      error: Color.lerp(error, other.error, t)!,
      textPrimary: Color.lerp(textPrimary, other.textPrimary, t)!,
      textSecondary: Color.lerp(textSecondary, other.textSecondary, t)!,
      glassBorder: Color.lerp(glassBorder, other.glassBorder, t)!,
    );
  }
}

/// One selectable accent color in the Settings → Appearance picker.
@immutable
class AccentOption {
  const AccentOption({
    required this.name,
    required this.color,
    required this.secondary,
  });

  final String name;
  final Color color;

  /// Gradient partner used as `accentSecondary` when this accent is active.
  final Color secondary;
}

/// Accent presets. The default is the spec's indigo → violet pairing.
const List<AccentOption> kAccentOptions = [
  AccentOption(
    name: 'Indigo',
    color: Color(0xFF5B47E5),
    secondary: Color(0xFF8B5CF6),
  ),
  AccentOption(
    name: 'Violet',
    color: Color(0xFF8B5CF6),
    secondary: Color(0xFFC084FC),
  ),
  AccentOption(
    name: 'Teal',
    color: Color(0xFF14B8A6),
    secondary: Color(0xFF2DD4BF),
  ),
  AccentOption(
    name: 'Rose',
    color: Color(0xFFF43F5E),
    secondary: Color(0xFFFB7185),
  ),
  AccentOption(
    name: 'Amber',
    color: Color(0xFFF59E0B),
    secondary: Color(0xFFFBBF24),
  ),
  AccentOption(
    name: 'Emerald',
    color: Color(0xFF10B981),
    secondary: Color(0xFF34D399),
  ),
];
