import 'package:flutter/material.dart';

/// Section heading used across screens, e.g. "Quick Actions",
/// "Recent Parses".
class SectionHeader extends StatelessWidget {
  const SectionHeader({
    super.key,
    required this.title,
    this.trailing,
  });

  final String title;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        children: [
          Expanded(
            child: Text(
              title,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
          ),
          if (trailing != null) trailing!,
        ],
      ),
    );
  }
}

/// Small uppercase badge — 12px/w500 with tracking, per the type scale.
class LabelBadge extends StatelessWidget {
  const LabelBadge({
    super.key,
    required this.text,
    this.color,
    this.backgroundColor,
    this.icon,
  });

  final String text;
  final Color? color;
  final Color? backgroundColor;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: backgroundColor ?? colors.accentPrimary.withOpacity(0.16),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 12, color: color ?? colors.accentSecondary),
            const SizedBox(width: 4),
          ],
          Text(
            text.toUpperCase(),
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              letterSpacing: 0.8,
              color: color ?? colors.accentSecondary,
            ),
          ),
        ],
      ),
    );
  }
}
