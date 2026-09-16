import 'package:flutter/material.dart';

import '../../../core/theme/context_x.dart';

/// One-tap quick-download tile: parses and downloads the best available
/// quality with merged audio + video.
class BestQualityTile extends StatelessWidget {
  const BestQualityTile({super.key, required this.onTap, this.enabled = true});

  final VoidCallback onTap;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: colors.accentGradient,
        borderRadius: BorderRadius.circular(20),
        boxShadow: colors.accentGlow,
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: enabled ? onTap : null,
          borderRadius: BorderRadius.circular(20),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
            child: Row(
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: Colors.white.withOpacity(0.18),
                  ),
                  child: const Icon(
                    Icons.bolt_rounded,
                    color: Colors.white,
                    size: 26,
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Best Quality (Recommended)',
                        style: Theme.of(context)
                            .textTheme
                            .titleSmall!
                            .copyWith(color: Colors.white),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Auto-selects max resolution and merges audio + video',
                        style: Theme.of(context).textTheme.bodySmall!.copyWith(
                              color: Colors.white.withOpacity(0.85),
                            ),
                      ),
                    ],
                  ),
                ),
                Icon(
                  Icons.arrow_forward_rounded,
                  color: Colors.white.withOpacity(0.9),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
