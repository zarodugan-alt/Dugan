import 'package:flutter/material.dart';

import '../theme/context_x.dart';

/// Optional animated ambience behind screens: two soft radial accent glows
/// over the base surface. Toggled by Settings → Appearance → Gradient Dark.
class GradientBackdrop extends StatelessWidget {
  const GradientBackdrop({super.key, required this.enabled, required this.child});

  /// When false the child sits on a flat OLED-black surface.
  final bool enabled;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    if (!enabled) {
      return ColoredBox(
        color: colors.surfacePrimary,
        child: child,
      );
    }
    return Stack(
      fit: StackFit.expand,
      children: [
        ColoredBox(color: colors.surfacePrimary),
        Positioned(
          top: -160,
          left: -120,
          child: _RadialGlow(
            color: colors.accentPrimary.withOpacity(0.14),
            size: 420,
          ),
        ),
        Positioned(
          bottom: -180,
          right: -140,
          child: _RadialGlow(
            color: colors.accentSecondary.withOpacity(0.10),
            size: 460,
          ),
        ),
        child,
      ],
    );
  }
}

class _RadialGlow extends StatelessWidget {
  const _RadialGlow({required this.color, required this.size});

  final Color color;
  final double size;

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: RadialGradient(
            colors: [color, color.withOpacity(0)],
          ),
        ),
      ),
    );
  }
}
