import 'package:equatable/equatable.dart';

/// Lifecycle of a download task.
enum DownloadStatus {
  /// Accepted, waiting for a worker slot.
  queued,

  /// Bytes are moving.
  downloading,

  /// Suspended by the user; resumable from the `.part` file.
  paused,

  /// yt-dlp finished transferring; FFmpeg is merging/embedding.
  postProcessing,

  /// Done — [DownloadTask.filePath] points at the output.
  completed,

  /// Terminated by error (see [DownloadTask.errorMessage]).
  failed,

  /// Cancelled by the user.
  canceled;

  bool get isActive =>
      this == queued || this == downloading || this == paused || this == postProcessing;

  bool get isTerminal => this == completed || this == failed || this == canceled;

  String get label {
    switch (this) {
      case DownloadStatus.queued:
        return 'Queued';
      case DownloadStatus.downloading:
        return 'Downloading';
      case DownloadStatus.paused:
        return 'Paused';
      case DownloadStatus.postProcessing:
        return 'Processing';
      case DownloadStatus.completed:
        return 'Completed';
      case DownloadStatus.failed:
        return 'Failed';
      case DownloadStatus.canceled:
        return 'Canceled';
    }
  }
}

/// Live state of one download, updated from native progress events.
class DownloadTask extends Equatable {
  const DownloadTask({
    required this.id,
    required this.title,
    required this.url,
    required this.platform,
    required this.createdAt,
    this.thumbnailUrl,
    this.duration,
    this.resolutionLabel,
    this.formatSummary = '',
    this.status = DownloadStatus.queued,
    this.progress = 0,
    this.downloadedBytes = 0,
    this.totalBytes = 0,
    this.speedBytesPerSec = 0,
    this.stage,
    this.errorMessage,
    this.errorCode,
    this.filePath,
    this.attempt = 0,
    this.completedAt,
  });

  final String id;
  final String title;
  final String url;
  final String platform;
  final DateTime createdAt;

  final String? thumbnailUrl;
  final Duration? duration;
  final String? resolutionLabel;

  /// Human summary of the chosen output, e.g. `MP4 · 1080p60` or
  /// `MP3 · 192 kbps`.
  final String formatSummary;

  final DownloadStatus status;

  /// Normalised 0..1.
  final double progress;
  final int downloadedBytes;
  final int totalBytes;
  final double speedBytesPerSec;

  /// Post-processing stage description (`Merging audio + video`…).
  final String? stage;

  final String? errorMessage;
  final String? errorCode;
  final String? filePath;

  /// 1-based retry attempt (auto-retry with exponential backoff).
  final int attempt;

  final DateTime? completedAt;

  static const Object _unset = Object();

  DownloadTask copyWith({
    Object? status = _unset,
    Object? progress = _unset,
    Object? downloadedBytes = _unset,
    Object? totalBytes = _unset,
    Object? speedBytesPerSec = _unset,
    Object? stage = _unset,
    Object? errorMessage = _unset,
    Object? errorCode = _unset,
    Object? filePath = _unset,
    Object? attempt = _unset,
    Object? completedAt = _unset,
  }) {
    return DownloadTask(
      id: id,
      title: title,
      url: url,
      platform: platform,
      createdAt: createdAt,
      thumbnailUrl: thumbnailUrl,
      duration: duration,
      resolutionLabel: resolutionLabel,
      formatSummary: formatSummary,
      status: identical(status, _unset) ? this.status : status as DownloadStatus,
      progress: identical(progress, _unset) ? this.progress : progress as double,
      downloadedBytes: identical(downloadedBytes, _unset)
          ? this.downloadedBytes
          : downloadedBytes as int,
      totalBytes: identical(totalBytes, _unset) ? this.totalBytes : totalBytes as int,
      speedBytesPerSec: identical(speedBytesPerSec, _unset)
          ? this.speedBytesPerSec
          : speedBytesPerSec as double,
      stage: identical(stage, _unset) ? this.stage : stage as String?,
      errorMessage:
          identical(errorMessage, _unset) ? this.errorMessage : errorMessage as String?,
      errorCode: identical(errorCode, _unset) ? this.errorCode : errorCode as String?,
      filePath: identical(filePath, _unset) ? this.filePath : filePath as String?,
      attempt: identical(attempt, _unset) ? this.attempt : attempt as int,
      completedAt:
          identical(completedAt, _unset) ? this.completedAt : completedAt as DateTime?,
    );
  }

  @override
  List<Object?> get props => [
        id,
        title,
        url,
        platform,
        createdAt,
        thumbnailUrl,
        duration,
        resolutionLabel,
        formatSummary,
        status,
        progress,
        downloadedBytes,
        totalBytes,
        speedBytesPerSec,
        stage,
        errorMessage,
        errorCode,
        filePath,
        attempt,
        completedAt,
      ];
}
