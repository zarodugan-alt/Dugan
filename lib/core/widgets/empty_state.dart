import 'package:flutter/material.dart';

import '../theme/context_x.dart';
import 'glass_card.dart';

/// Friendly empty state: glass icon bubble + title + subtitle + optional
/// CTA.
class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.subtitle,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 88,
              height: 88,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: colors.accentPrimary.withOpacity(0.10),
                border: Border.all(color: colors.glassBorder),
              ),
              child: Icon(icon, size: 38, color: colors.accentSecondary),
            ),
            const SizedBox(height: 20),
            Text(
              title,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 300),
              child: Text(
                subtitle,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
            if (actionLabel != null) ...[
              const SizedBox(height: 20),
              GlassCard(
                padding: EdgeInsets.zero,
                radius: 14,
                backgroundColor: colors.accentPrimary.withOpacity(0.14),
                child: TextButton(
                  onPressed: onAction,
                  child: Text(actionLabel!),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
