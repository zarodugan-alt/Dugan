import 'package:equatable/equatable.dart';

import 'format_option.dart';
import 'subtitle_track.dart';

/// Fully-parsed metadata for a media URL — the result of a
/// `yt-dlp --dump-single-json --no-download` call.
class MediaInfo extends Equatable {
  const MediaInfo({
    required this.id,
    required this.title,
    required this.sourceUrl,
    required this.platform,
    this.uploader,
    this.thumbnailUrl,
    this.duration,
    this.isLive = false,
    this.viewCount,
    this.formats = const [],
    this.audioFormats = const [],
    this.subtitles = const [],
  });

  final String id;
  final String title;
  final String sourceUrl;

  /// Platform id (`youtube`, `tiktok`, … `web`). See `PlatformMeta.byId`.
  final String platform;

  final String? uploader;
  final String? thumbnailUrl;
  final Duration? duration;
  final bool isLive;
  final int? viewCount;

  /// Every format that carries video (progressive and video-only).
  final List<FormatOption> formats;

  /// Audio-only formats.
  final List<FormatOption> audioFormats;

  final List<SubtitleTrack> subtitles;

  /// Highest-quality video-only format (preference: height, then bitrate).
  FormatOption? get bestVideo {
    if (formats.isEmpty) {
      return null;
    }
    final sorted = [...formats]..sort((a, b) {
        final videoOnlyFirst =
            (a.isVideoOnly ? 1 : 0).compareTo(b.isVideoOnly ? 1 : 0);
        if (videoOnlyFirst != 0) {
          return -videoOnlyFirst;
        }
        return b.qualityScore.compareTo(a.qualityScore);
      });
    return sorted.first;
  }

  /// Highest-quality audio-only format.
  FormatOption? get bestAudio {
    if (audioFormats.isEmpty) {
      return null;
    }
    final sorted = [...audioFormats]
      ..sort((a, b) => (b.abr ?? b.tbr ?? 0).compareTo(a.abr ?? a.tbr ?? 0));
    return sorted.first;
  }

  /// Best combined (progressive) stream, if any.
  FormatOption? get bestProgressive {
    for (final f in formats) {
      if (f.isProgressive) {
        return f;
      }
    }
    return null;
  }

  /// Formats deduplicated per resolution, keeping the best variant of each
  /// height — this drives the format-selection grid.
  List<FormatOption> get formatsByResolution {
    final byHeight = <int, FormatOption>{};
    for (final f in formats) {
      final key = f.height ?? 0;
      final current = byHeight[key];
      if (current == null || f.qualityScore > current.qualityScore) {
        byHeight[key] = f;
      }
    }
    final list = byHeight.values.toList()
      ..sort((a, b) => b.qualityScore.compareTo(a.qualityScore));
    return list;
  }

  /// Highest available resolution height (0 when unknown).
  int get maxHeight =>
      formats.fold<int>(0, (m, f) => f.height != null && f.height! > m ? f.height! : m);

  @override
  List<Object?> get props => [
        id,
        title,
        sourceUrl,
        platform,
        uploader,
        thumbnailUrl,
        duration,
        isLive,
        viewCount,
        formats,
        audioFormats,
        subtitles,
      ];
}
