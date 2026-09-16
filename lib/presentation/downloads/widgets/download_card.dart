import 'package:flutter/material.dart';

import '../../../core/theme/context_x.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/gradient_progress_bar.dart';
import '../../../domain/entities/download_task.dart';

/// One live download card: gradient progress bar, colour-coded speed,
/// post-processing stage, and pause/resume/cancel controls.
class DownloadCard extends StatelessWidget {
  const DownloadCard({
    super.key,
    required this.task,
    required.onPause,
    required onResume,
    required onCancel,
    this.onPlay,
    this.onShare,
    this.onDismiss,
    this.onOpen,
  });

  final DownloadTask task;
  final VoidCallback onPause;
  final VoidCallback onResume;
  final VoidCallback onCancel;
  final VoidCallback? onPlay;
  final VoidCallback? onShare;
  final VoidCallback? onDismiss;
  final VoidCallback? onOpen;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: colors.surfaceSecondary.withOpacity(0.9),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: colors.glassBorder),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              _StatusIcon(task: task),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  task.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleSmall,
                ),
              ),
              _TrailingActions(
                task: task,
                onPause: onPause,
                onResume: onResume,
                onCancel: onCancel,
                onPlay: onPlay,
                onShare: onShare,
                onDismiss: onDismiss,
                onOpen: onOpen,
              ),
            ],
          ),
          const SizedBox(height: 10),
          if (task.status == DownloadStatus.postProcessing) ...[
            PulsingProgressBar(height: 7),
            const SizedBox(height: 6),
            Text(
              task.stage ?? 'Post-processing…',
              style: Theme.of(context)
                  .textTheme
                  .bodySmall!
                  .copyWith(color: colors.accentSecondary),
            ),
          ] else if (task.status == DownloadStatus.paused) ...[
            _PausedBar(progress: task.progress),
            const SizedBox(height: 6),
          ] else if (task.status.isTerminal) ...[
            _TerminalLine(task: task),
          ] else ...[
            GradientProgressBar(
              progress: task.totalBytes > 0 && task.progress > 0
                  ? task.progress
                  : task.progress.clamp(0.0, 0.98).toDouble(),
              height: 7,
            ),
            const SizedBox(height: 6),
          ],
          Text(
            _metaLine(context),
            style: Theme.of(context).textTheme.bodySmall!.copyWith(
                  color: _speedColor(context),
                ),
          ),
        ],
      ),
    );
  }

  String _metaLine(BuildContext context) {
    switch (task.status) {
      case DownloadStatus.queued:
        return [
          task.formatSummary,
          if (task.attempt > 0) 'attempt ${task.attempt}',
          'waiting…',
        ].join(' · ');
      case DownloadStatus.downloading:
        return [
          task.resolutionLabel ?? task.formatSummary,
          if (task.totalBytes > 0)
            '${Formatters.bytes(task.downloadedBytes)} / '
            '${Formatters.bytes(task.totalBytes)}',
          if (task.speedBytesPerSec > 0)
            Formatters.speed(task.speedBytesPerSec),
          if (task.totalBytes > 0 && task.speedBytesPerSec > 0)
            Formatters.eta(
              remainingBytes:
                  (task.totalBytes - task.downloadedBytes).clamp(0, 1 << 62),
              speed: task.speedBytesPerSec,
            ),
        ].join(' · ');
      case DownloadStatus.paused:
        return 'Paused · ${Formatters.bytes(task.downloadedBytes)} downloaded';
      case DownloadStatus.postProcessing:
        return task.formatSummary;
      case DownloadStatus.completed:
        return [
          task.formatSummary,
          if (task.totalBytes > 0) Formatters.bytes(task.totalBytes),
        ].join(' · ');
      case DownloadStatus.failed:
        return task.errorMessage ?? 'Download failed';
      case DownloadStatus.canceled:
        return 'Canceled';
    }
  }

  Color _speedColor(BuildContext context) {
    final colors = context.dugan;
    switch (task.status) {
      case DownloadStatus.downloading:
        return Formatters.speedColor(task.speedBytesPerSec, colors);
      case DownloadStatus.failed:
        return colors.error;
      case DownloadStatus.paused:
      case DownloadStatus.queued:
        return colors.warning;
      default:
        return colors.textSecondary;
    }
  }
}

class _StatusIcon extends StatelessWidget {
  const _StatusIcon({required this.task});

  final DownloadTask task;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    final (icon, color) = switch (task.status) {
      DownloadStatus.queued => (Icons.schedule_rounded, colors.warning),
      DownloadStatus.downloading => (Icons.downloading_rounded, colors.accentSecondary),
      DownloadStatus.paused => (Icons.pause_circle_rounded, colors.warning),
      DownloadStatus.postProcessing => (Icons.auto_awesome_rounded, colors.accentSecondary),
      DownloadStatus.completed => (Icons.check_circle_rounded, colors.success),
      DownloadStatus.failed => (Icons.error_circle_rounded, colors.error),
      DownloadStatus.canceled => (Icons.cancel_rounded, colors.textSecondary),
    };
    return Icon(icon, color: color, size: 22);
  }
}

class _PausedBar extends StatelessWidget {
  const _PausedBar({required this.progress});

  final double progress;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return Container(
      height: 7,
      decoration: BoxDecoration(
        color: colors.surfaceTertiary,
        borderRadius: BorderRadius.circular(7),
      ),
      child: FractionallySizedBox(
        widthFactor:
            progress <= 0 ? 0.001 : progress.clamp(0.0, 1.0).toDouble(),
        alignment: Alignment.centerLeft,
        child: Container(
          decoration: BoxDecoration(
            color: colors.warning,
            borderRadius: BorderRadius.circular(7),
          ),
        ),
      ),
    );
  }
}

class _TerminalLine extends StatelessWidget {
  const _TerminalLine({required this.task});

  final DownloadTask task;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    if (task.status == DownloadStatus.failed) {
      return Text(
        task.errorMessage ?? 'Download failed',
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: Theme.of(context)
            .textTheme
            .bodySmall!
            .copyWith(color: colors.error),
      );
    }
    if (task.status == DownloadStatus.completed && task.filePath != null) {
      return Text(
        task.filePath!,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: Theme.of(context).textTheme.bodySmall,
      );
    }
    return const SizedBox.shrink();
  }
}

class _TrailingActions extends StatelessWidget {
  const _TrailingActions({
    required this.task,
    required this.onPause,
    required this.onResume,
    required this.onCancel,
    this.onPlay,
    this.onShare,
    this.onDismiss,
    this.onOpen,
  });

  final DownloadTask task;
  final VoidCallback onPause;
  final VoidCallback onResume;
  final VoidCallback onCancel;
  final VoidCallback? onPlay;
  final VoidCallback? onShare;
  final VoidCallback? onDismiss;
  final VoidCallback? onOpen;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    switch (task.status) {
      case DownloadStatus.queued:
      case DownloadStatus.downloading:
      case DownloadStatus.postProcessing:
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              tooltip: 'Pause',
              visualDensity: VisualDensity.compact,
              icon: Icon(Icons.pause_rounded, color: colors.warning),
              onPressed: onPause,
            ),
            IconButton(
              tooltip: 'Cancel',
              visualDensity: VisualDensity.compact,
              icon: Icon(Icons.close_rounded, color: colors.error),
              onPressed: onCancel,
            ),
          ],
        );
      case DownloadStatus.paused:
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              tooltip: 'Resume',
              visualDensity: VisualDensity.compact,
              icon: Icon(Icons.play_arrow_rounded,
                  color: colors.success, size: 26),
              onPressed: onResume,
            ),
            IconButton(
              tooltip: 'Cancel',
              visualDensity: VisualDensity.compact,
              icon: Icon(Icons.close_rounded, color: colors.error),
              onPressed: onCancel,
            ),
          ],
        );
      case DownloadStatus.completed:
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (onPlay != null)
              IconButton(
                tooltip: 'Play',
                visualDensity: VisualDensity.compact,
                icon: Icon(Icons.play_arrow_rounded,
                    color: colors.accentSecondary, size: 26),
                onPressed: onPlay,
              ),
            if (onShare != null)
              IconButton(
                tooltip: 'Share',
                visualDensity: VisualDensity.compact,
                icon: Icon(Icons.share_rounded,
                    color: colors.textSecondary, size: 20),
                onPressed: onShare,
              ),
          ],
        );
      case DownloadStatus.failed:
      case DownloadStatus.canceled:
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              tooltip: 'Retry',
              visualDensity: VisualDensity.compact,
              icon: Icon(Icons.refresh_rounded, color: colors.accentSecondary),
              onPressed: onResume,
            ),
            if (onDismiss != null)
              IconButton(
                tooltip: 'Dismiss',
                visualDensity: VisualDensity.compact,
                icon: Icon(Icons.close_rounded,
                    color: colors.textSecondary, size: 20),
                onPressed: onDismiss,
              ),
          ],
        );
    }
  }
}
