import 'package:flutter/material.dart';

import '../../../core/widgets/glass_card.dart';
import '../../../core/widgets/shimmer.dart';

/// Shimmer skeleton shown while yt-dlp extracts metadata.
class ParseSkeleton extends StatelessWidget {
  const ParseSkeleton({super.key});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      child: Row(
        children: [
          const ShimmerThumbnail(width: 132, height: 76),
          const SizedBox(width: 14),
          const Expanded(child: ShimmerLines(lineWidths: [190, 120, 90])),
          Container(
            width: 28,
            height: 28,
            margin: const EdgeInsets.only(left: 8),
            child: const CircularProgressIndicator(strokeWidth: 2.4),
          ),
        ],
      ),
    );
  }
}
