import '../../core/constants/platform_meta.dart';
import '../../core/utils/map_deep.dart';
import '../../domain/entities/format_option.dart';
import '../../domain/entities/media_info.dart';
import '../../domain/entities/subtitle_track.dart';
import 'format_option_model.dart';
import 'subtitle_track_model.dart';

/// Maps the sanitised yt-dlp JSON payload (see
/// `android/app/src/main/python/ytdlp_bridge.py::_sanitize`) onto
/// [MediaInfo], and back for the recent-parses cache.
class MediaInfoModel {
  const MediaInfoModel._();

  static MediaInfo fromJson(Map<String, dynamic> json) {
    final rawFormats = asMapList(json['formats']);
    final rawSubs = asMapList(json['subtitles']);

    final videoFormats = <FormatOption>[];
    final audioFormats = <FormatOption>[];
    for (final raw in rawFormats) {
      final f = FormatOptionModel.fromJson(raw);
      if (!f.hasVideo && !f.hasAudio) {
        continue;
      }
      if (f.hasVideo) {
        videoFormats.add(f);
      } else {
        audioFormats.add(f);
      }
    }

    videoFormats.sort((a, b) => b.qualityScore.compareTo(a.qualityScore));
    audioFormats.sort((a, b) => (b.abr ?? b.tbr ?? 0).compareTo(a.abr ?? a.tbr ?? 0));

    // Prefer the extractor key for platform detection, falling back to the
    // source URL hosts.
    final platformMeta = PlatformMeta.fromExtractor(json['platform'] as String?) == PlatformMeta.generic
        ? PlatformMeta.fromUrl('${json['sourceUrl'] ?? ''}')
        : PlatformMeta.fromExtractor(json['platform'] as String?);

    final durationSec = (json['duration'] as num?)?.toInt();

    return MediaInfo(
      id: '${json['id'] ?? ''}',
      title: '${json['title'] ?? 'Untitled'}',
      uploader: json['uploader'] as String?,
      thumbnailUrl: json['thumbnail'] as String?,
      duration: durationSec != null && durationSec > 0
          ? Duration(seconds: durationSec)
          : null,
      sourceUrl: '${json['sourceUrl'] ?? ''}',
      platform: platformMeta.id,
      isLive: json['isLive'] as bool? ?? false,
      viewCount: (json['viewCount'] as num?)?.toInt(),
      formats: videoFormats,
      audioFormats: audioFormats,
      subtitles: rawSubs.map(SubtitleTrackModel.fromJson).toList(),
    );
  }

  static Map<String, dynamic> toJson(MediaInfo info) {
    return <String, dynamic>{
      'id': info.id,
      'title': info.title,
      'uploader': info.uploader,
      'thumbnail': info.thumbnailUrl,
      'duration': info.duration?.inSeconds,
      'sourceUrl': info.sourceUrl,
      'platform': info.platform,
      'isLive': info.isLive,
      'viewCount': info.viewCount,
      'formats': info.formats.map(FormatOptionModel.toJson).toList(),
      'subtitles': info.subtitles.map(SubtitleTrackModel.toJson).toList(),
    };
  }
}
