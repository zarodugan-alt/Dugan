import 'package:flutter/material.dart';

import '../theme/context_x.dart';

/// Animated gradient progress bar (indigo → violet) with a soft glow.
class GradientProgressBar extends StatelessWidget {
  const GradientProgressBar({
    super.key,
    required this.progress,
    this.height = 8,
    this.showGlow = true,
  })  : assert(progress >= 0 && progress <= 1, 'progress must be 0..1');

  /// Normalised progress in `0..1`.
  final double progress;
  final double height;
  final bool showGlow;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(end: progress.clamp(0.0, 1.0).toDouble()),
      duration: const Duration(milliseconds: 350),
      curve: Curves.easeOutCubic,
      builder: (context, value, _) {
        return Container(
          height: height,
          decoration: BoxDecoration(
            color: colors.surfaceTertiary,
            borderRadius: BorderRadius.circular(height),
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(height),
            child: Stack(
              children: [
                FractionallySizedBox(
                  widthFactor: value <= 0 ? 0.001 : value,
                  child: Container(
                    decoration: BoxDecoration(
                      gradient: colors.accentGradient,
                      boxShadow: showGlow
                          ? [
                              BoxShadow(
                                color: colors.accentSecondary.withOpacity(0.55),
                                blurRadius: 12,
                              ),
                            ]
                          : null,
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

/// Indeterminate pulsing bar used for post-processing stages (FFmpeg merge,
/// subtitle embed…) where there is no byte-level progress to show.
class PulsingProgressBar extends StatefulWidget {
  const PulsingProgressBar({super.key, this.height = 8});

  final double height;

  @override
  State<PulsingProgressBar> createState() => _PulsingProgressBarState();
}

class _PulsingProgressBarState extends State<PulsingProgressBar>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1400),
  )..repeat();

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
        // A gradient band sweeping across the track: alignment travels
        // from the left edge to the right edge of the remaining space.
        final t = Curves.easeInOut.transform(_controller.value);
        return Container(
          height: widget.height,
          decoration: BoxDecoration(
            color: colors.surfaceTertiary,
            borderRadius: BorderRadius.circular(widget.height),
          ),
          clipBehavior: Clip.antiAlias,
          child: Align(
            alignment: Alignment(-1.0 + 1.2 * t, 0),
            child: FractionallySizedBox(
              widthFactor: 0.45,
              child: Container(
                decoration: BoxDecoration(
                  gradient: colors.accentGradient,
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
