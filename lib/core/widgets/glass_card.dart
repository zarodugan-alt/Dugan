import 'dart:ui' show ImageFilter;

import 'package:flutter/material.dart';

import '../theme/context_x.dart';

/// Frosted-glass surface card.
///
/// `BackdropFilter(blur 20)` + semi-transparent surface colour + a subtle
/// 1px white border at 8% opacity — the glassmorphism recipe from the spec.
class GlassCard extends StatelessWidget {
  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.margin,
    this.radius = 20,
    this.blur = 20,
    this.backgroundColor,
    this.borderColor,
    this.clipBehavior = Clip.antiAlias,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry? margin;
  final double radius;
  final double blur;
  final Color? backgroundColor;
  final Color? borderColor;
  final Clip clipBehavior;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return MarginOrNot(
      margin: margin,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        clipBehavior: clipBehavior,
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: blur, sigmaY: blur),
          child: Container(
            decoration: BoxDecoration(
              color: backgroundColor ??
                  (colors.isDark
                      ? colors.surfaceSecondary.withOpacity(0.72)
                      : colors.surfaceSecondary.withOpacity(0.82)),
              borderRadius: BorderRadius.circular(radius),
              border: Border.all(
                color: borderColor ?? colors.glassBorder,
                width: 1,
              ),
            ),
            padding: padding,
            child: child,
          ),
        ),
      ),
    );
  }
}

class MarginOrNot extends StatelessWidget {
  const MarginOrNot({super.key, required this.margin, required this.child});

  final EdgeInsetsGeometry? margin;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    if (margin == null) {
      return child;
    }
    return Padding(padding: margin!, child: child);
  }
}
