import 'package:flutter/material.dart';

import '../theme/context_x.dart';

/// Dependency-free shimmer skeleton primitive.
///
/// Pulses between `shimmerBase` and `shimmerHighlight` while loading —
/// used to build the parse skeleton, format skeletons and history
/// placeholders.
class ShimmerBox extends StatefulWidget {
  const ShimmerBox({
    super.key,
    this.width,
    this.height = 14,
    this.radius = 8,
  });

  final double? width;
  final double height;
  final double radius;

  @override
  State<ShimmerBox> createState() => _ShimmerBoxState();
}

class _ShimmerBoxState extends State<ShimmerBox>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1100),
  )..repeat(reverse: true);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        final t = Curves.easeInOut.transform(_controller.value);
        return Container(
          width: widget.width,
          height: widget.height,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(widget.radius),
            color: Color.lerp(colors.shimmerBase, colors.shimmerHighlight, t),
          ),
        );
      },
    );
  }
}

/// Skeleton block shaped like a media thumbnail (16:9 by default).
class ShimmerThumbnail extends StatelessWidget {
  const ShimmerThumbnail({super.key, this.width = 128, this.height = 72});

  final double width;
  final double height;

  @override
  Widget build(BuildContext context) {
    return ShimmerBox(width: width, height: height, radius: 12);
  }
}

/// A vertical stack of shimmering text lines.
class ShimmerLines extends StatelessWidget {
  const ShimmerLines({
    super.key,
    this.lineWidths = const [220, 140],
    this.spacing = 8,
    this.height = 13,
  });

  final List<double> lineWidths;
  final double spacing;
  final double height;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final w in lineWidths) ...[
          ShimmerBox(width: w, height: height),
          SizedBox(height: spacing),
        ],
      ],
    );
  }
}
