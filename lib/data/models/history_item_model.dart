import '../../domain/entities/history_item.dart';

class HistoryItemModel {
  const HistoryItemModel._();

  static HistoryItem fromJson(Map<String, dynamic> json) {
    final durationSec = (json['duration'] as num?)?.toInt();
    final completedMs = (json['completedAt'] as num?)?.toInt();
    return HistoryItem(
      id: '${json['id'] ?? ''}',
      title: '${json['title'] ?? ''}',
      platform: '${json['platform'] ?? 'web'}',
      thumbnailUrl: json['thumbnailUrl'] as String?,
      filePath: '${json['filePath'] ?? ''}',
      resolutionLabel: json['resolutionLabel'] as String?,
      sizeBytes: (json['sizeBytes'] as num?)?.toInt() ?? 0,
      duration: durationSec != null && durationSec > 0
          ? Duration(seconds: durationSec)
          : null,
      completedAt: completedMs != null
          ? DateTime.fromMillisecondsSinceEpoch(completedMs)
          : DateTime.now(),
      sourceUrl: '${json['sourceUrl'] ?? ''}',
      formatSummary: '${json['formatSummary'] ?? ''}',
    );
  }

  static Map<String, dynamic> toJson(HistoryItem item) {
    return <String, dynamic>{
      'id': item.id,
      'title': item.title,
      'platform': item.platform,
      'thumbnailUrl': item.thumbnailUrl,
      'filePath': item.filePath,
      'resolutionLabel': item.resolutionLabel,
      'sizeBytes': item.sizeBytes,
      'duration': item.duration?.inSeconds,
      'completedAt': item.completedAt.millisecondsSinceEpoch,
      'sourceUrl': item.sourceUrl,
      'formatSummary': item.formatSummary,
    };
  }
}
