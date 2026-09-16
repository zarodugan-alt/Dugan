import 'package:flutter/material.dart';

import '../../../core/constants/platform_meta.dart';
import '../../../core/theme/context_x.dart';

/// Horizontal rail of platform shortcut chips (auto-fill demo links).
class PlatformShortcuts extends StatelessWidget {
  const PlatformShortcuts({super.key, required this.onSelected});

  final ValueChanged<PlatformMeta> onSelected;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return SizedBox(
      height: 92,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: PlatformMeta.all.length,
        separatorBuilder: (_, __) => const SizedBox(width: 10),
        itemBuilder: (context, index) {
          final platform = PlatformMeta.all[index];
          return _ShortcutChip(
            platform: platform,
            color: colors,
            onTap: () => onSelected(platform),
          );
        },
      ),
    );
  }
}

class _ShortcutChip extends StatelessWidget {
  const _ShortcutChip({
    required this.platform,
    required this.color,
    required this.onTap,
  });

  final PlatformMeta platform;
  final DuganColors color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: color.surfaceSecondary.withOpacity(0.85),
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          width: 76,
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: color.glassBorder),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(platform.emoji, style: const TextStyle(fontSize: 22)),
              const SizedBox(height: 6),
              Text(
                platform.name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: color.textSecondary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
