import 'package:equatable/equatable.dart';

import 'media_info.dart';

/// Everything needed to start one download — produced by the format
/// selection sheet (or the "Best Quality" quick action).
class DownloadRequest extends Equatable {
  const DownloadRequest({
    required this.media,
    this.videoFormatId,
    this.audioFormatId,
    this.mergeWithBestAudio = true,
    this.audioOnly = false,
    this.mergeContainer = 'mp4',
    this.audioContainer = 'mp3',
    this.audioBitrateKbps = 192,
    this.embedMetadata = true,
    this.embedThumbnail = false,
    this.embedSubtitles = false,
    this.subtitleLanguages = const [],
    this.filenameTemplate = '%(title)s [%(id)s].%(ext)s',
  });

  final MediaInfo media;

  /// Chosen video format; `null` when downloading audio-only.
  final String? videoFormatId;

  /// Explicitly chosen audio format; may be `null` even for video
  /// downloads, in which case best audio is used (when merging).
  final String? audioFormatId;

  /// Merge the (video-only) stream with the best available audio.
  final bool mergeWithBestAudio;

  final bool audioOnly;

  /// Container for the merged output (`mp4`, `mkv`, `webm`).
  final String? mergeContainer;

  /// Target container when extracting audio (`mp3`, `m4a`, `opus`, `flac`,
  /// `wav`).
  final String audioContainer;

  final int audioBitrateKbps;

  final bool embedMetadata;
  final bool embedThumbnail;

  /// Download + embed subtitle tracks for the selected languages.
  final bool embedSubtitles;
  final List<String> subtitleLanguages;

  /// yt-dlp output template (must include `%(ext)s`).
  final String filenameTemplate;

  @override
  List<Object?> get props => [
        media,
        videoFormatId,
        audioFormatId,
        mergeWithBestAudio,
        audioOnly,
        mergeContainer,
        audioContainer,
        audioBitrateKbps,
        embedMetadata,
        embedThumbnail,
        embedSubtitles,
        subtitleLanguages,
        filenameTemplate,
      ];
}
