import 'package:flutter/material.dart';

import '../theme/context_x.dart';

/// Primary CTA button with the signature indigo→violet gradient and a
/// colored glow shadow (the dark-theme replacement for elevation).
class GradientButton extends StatelessWidget {
  const GradientButton({
    super.key,
    required this.label,
    this.onPressed,
    this.icon,
    this.height = 52,
    this.expanded = true,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final double height;
  final bool expanded;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    final enabled = onPressed != null;

    final content = Row(
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (icon != null) ...[
          Icon(icon, size: 20, color: enabled ? Colors.white : colors.textSecondary),
          const SizedBox(width: 10),
        ],
        Flexible(
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w600,
              color: enabled ? Colors.white : colors.textSecondary,
            ),
          ),
        ),
      ],
    );

    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(16),
    );

    return SizedBox(
      height: height,
      width: expanded ? double.infinity : null,
      child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: enabled ? colors.accentGradient : null,
          color: enabled ? null : colors.surfaceTertiary,
          borderRadius: BorderRadius.circular(16),
          boxShadow: enabled ? colors.accentGlow : const <BoxShadow>[],
        ),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: onPressed,
            borderRadius: BorderRadius.circular(16),
            child: Center(child: content),
          ),
        ),
      ),
    );
  }
}

/// Compact gradient pill (used for the "Parse" action inside the URL card).
class GradientPillButton extends StatelessWidget {
  const GradientPillButton({
    super.key,
    required this.label,
    this.onPressed,
    this.icon,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    final enabled = onPressed != null;
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: enabled ? colors.accentGradient : null,
        color: enabled ? null : colors.surfaceTertiary,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onPressed,
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (icon != null) ...[
                  Icon(
                    icon,
                    size: 16,
                    color: enabled ? Colors.white : colors.textSecondary,
                  ),
                  const SizedBox(width: 6),
                ],
                Text(
                  label,
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: enabled ? Colors.white : colors.textSecondary,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
