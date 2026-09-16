import 'package:equatable/equatable.dart';

/// A completed download recorded in the library.
class HistoryItem extends Equatable {
  const HistoryItem({
    required this.id,
    required this.title,
    required this.platform,
    required this.filePath,
    required this.sourceUrl,
    required this.completedAt,
    this.thumbnailUrl,
    this.resolutionLabel,
    this.sizeBytes = 0,
    this.duration,
    this.formatSummary = '',
  });

  final String id;
  final String title;
  final String platform;
  final String filePath;
  final String sourceUrl;
  final DateTime completedAt;

  final String? thumbnailUrl;
  final String? resolutionLabel;
  final int sizeBytes;
  final Duration? duration;
  final String formatSummary;

  @override
  List<Object?> get props => [
        id,
        title,
        platform,
        filePath,
        sourceUrl,
        completedAt,
        thumbnailUrl,
        resolutionLabel,
        sizeBytes,
        duration,
        formatSummary,
      ];
}
