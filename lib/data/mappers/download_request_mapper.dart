import '../../domain/entities/app_settings.dart';
import '../../domain/entities/download_request.dart';
import '../../domain/entities/download_task.dart';
import '../../domain/entities/format_option.dart';
import '../../domain/entities/media_info.dart';

/// Translates a [DownloadRequest] + user settings into the native task map
/// consumed by `ytdlp_bridge.py::start`.
///
/// The yt-dlp format selector is composed here so the Python side stays a
/// dumb executor:
///   audio only           → `<audioId>` or `bestaudio/best`
///   video + audio        → `<videoId>+<audioId>`
///   video-only + merge   → `<videoId>+bestaudio/best`
///   progressive video    → `<videoId>`
class DownloadRequestMapper {
  const DownloadRequestMapper._();

  static Map<String, dynamic> toNativeTask({
    required DownloadTask task,
    required DownloadRequest request,
    required AppSettings settings,
    required String outputDir,
    String? cookieFile,
  }) {
    final media = request.media;

    var template = request.filenameTemplate.trim();
    if (template.isEmpty) {
      template = '%(title)s [%(id)s].%(ext)s';
    }
    if (!template.contains('%(ext)s')) {
      template = '$template.%(ext)s';
    }

    return <String, dynamic>{
      'taskId': task.id,
      'title': task.title,
      'url': media.sourceUrl,
      'formatSelector': _selector(request, media),
      'mergeFormat': request.audioOnly ? null : (request.mergeContainer ?? 'mp4'),
      'audioOnly': request.audioOnly,
      'audioContainer': request.audioContainer,
      'audioBitrateKbps': request.audioBitrateKbps,
      'embedMetadata': request.embedMetadata,
      'embedThumbnail': request.embedThumbnail,
      'embedSubs': request.embedSubtitles,
      'subLangs': request.subtitleLanguages.toList(),
      'outputDir': outputDir,
      'filenameTemplate': template,
      'userAgent':
          settings.customUserAgent.trim().isEmpty ? null : settings.customUserAgent.trim(),
      'cookieFile': cookieFile,
      'extraArgs': splitRawArgs(settings.customYtDlpArgs),
      'maxConcurrent': settings.maxConcurrentDownloads,
    };
  }

  static String _selector(DownloadRequest request, MediaInfo media) {
    if (request.audioOnly) {
      final audioId = request.audioFormatId;
      if (audioId != null && audioId.isNotEmpty) {
        return audioId;
      }
      return media.bestAudio?.formatId ?? 'bestaudio/best';
    }

    final videoId = request.videoFormatId ??
        media.bestVideo?.formatId ??
        media.bestProgressive?.formatId ??
        'b';
    final videoFormat = _findFormat(media, videoId);

    // Progressive streams already carry audio — no merge needed.
    if (videoFormat != null && videoFormat.isProgressive) {
      return videoId;
    }

    final audioId = request.audioFormatId;
    if (audioId != null && audioId.isNotEmpty) {
      return '$videoId+$audioId';
    }
    if (request.mergeWithBestAudio && media.bestAudio != null) {
      return '$videoId+bestaudio/best';
    }
    return videoId;
  }

  static FormatOption? _findFormat(MediaInfo media, String formatId) {
    for (final f in media.formats) {
      if (f.formatId == formatId) {
        return f;
      }
    }
    for (final f in media.audioFormats) {
      if (f.formatId == formatId) {
        return f;
      }
    }
    return null;
  }

  /// Short human summary, e.g. `MP4 · 1080p60` or `MP3 · 192 kbps`.
  static String formatSummary(DownloadRequest request, MediaInfo media) {
    if (request.audioOnly) {
      return '${request.audioContainer.toUpperCase()} · ${request.audioBitrateKbps} kbps';
    }
    final videoId = request.videoFormatId ?? media.bestVideo?.formatId;
    final format = videoId == null ? null : _findFormat(media, videoId);
    final container = (request.mergeContainer ?? 'mp4').toUpperCase();
    if (format == null) {
      return container;
    }
    return '$container · ${format.label}';
  }

  /// Resolution label for cards/badges (`1080p60`), `audio` for audio-only.
  static String resolutionLabel(DownloadRequest request, MediaInfo media) {
    if (request.audioOnly) {
      return 'audio';
    }
    final videoId = request.videoFormatId ?? media.bestVideo?.formatId;
    if (videoId == null) {
      return 'video';
    }
    final format = _findFormat(media, videoId);
    return format?.label ?? 'video';
  }

  /// Splits a raw `--key value` argument string into tokens; only tokens
  /// starting with `--` (and their following value) are forwarded, and the
  /// native bridge further allow-lists them.
  static List<String> splitRawArgs(String raw) {
    final tokens = raw
        .trim()
        .split(RegExp(r'\s+'))
        .where((t) => t.isNotEmpty)
        .toList();
    final result = <String>[];
    for (var i = 0; i < tokens.length; i++) {
      if (tokens[i].startsWith('--')) {
        result.add(tokens[i]);
        if (i + 1 < tokens.length && !tokens[i + 1].startsWith('--')) {
          result.add(tokens[i + 1]);
          i++;
        }
      }
    }
    return result;
  }
}
