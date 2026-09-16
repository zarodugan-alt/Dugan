import 'package:equatable/equatable.dart';

import '../../../domain/entities/app_settings.dart';
import '../../../domain/entities/format_option.dart';
import '../../../domain/entities/media_info.dart';

/// State of the format-selection bottom sheet.
class FormatSelectionState extends Equatable {
  const FormatSelectionState({
    required this.media,
    this.videoFormatId,
    this.audioFormatId,
    this.audioOnly = false,
    this.mergeWithBestAudio = true,
    this.audioContainer = 'mp3',
    this.audioBitrateKbps = 192,
    this.embedMetadata = true,
    this.embedThumbnail = false,
    this.embedSubtitles = false,
    this.subtitleLanguages = const <String>{},
    this.filenameTemplate = '%(title)s [%(id)s].%(ext)s',
    this.showGrid = false,
  });

  final MediaInfo media;
  final String? videoFormatId;
  final String? audioFormatId;
  final bool audioOnly;

  /// Merge a video-only stream with the best available audio.
  final bool mergeWithBestAudio;

  final String audioContainer;
  final int audioBitrateKbps;

  final bool embedMetadata;
  final bool embedThumbnail;
  final bool embedSubtitles;
  final Set<String> subtitleLanguages;
  final String filenameTemplate;

  /// Grid (true) or list (false) layout for video formats.
  final bool showGrid;

  FormatOption? get selectedVideo {
    for (final f in media.formats) {
      if (f.formatId == videoFormatId) {
        return f;
      }
    }
    return null;
  }

  FormatOption? get selectedAudio {
    for (final f in media.audioFormats) {
      if (f.formatId == audioFormatId) {
        return f;
      }
    }
    return null;
  }

  /// A video-only selection needs either an audio pick or the merge toggle.
  bool get needsAudioSelection =>
      !audioOnly &&
      selectedVideo != null &&
      selectedVideo!.isVideoOnly &&
      selectedAudio == null &&
      !mergeWithBestAudio;

  bool get canDownload =>
      audioOnly || (selectedVideo != null && !needsAudioSelection);

  /// Why the CTA is disabled, or null when ready.
  String? get validationHint {
    if (audioOnly) {
      return null;
    }
    if (selectedVideo == null) {
      return 'Pick a video format first';
    }
    if (needsAudioSelection) {
      return 'Video-only stream — choose an audio track or enable “Merge with best audio”';
    }
    return null;
  }

  /// Best-effort size estimate for the final file.
  int? get estimatedSizeBytes {
    if (audioOnly) {
      return selectedAudio?.effectiveFilesize ??
          ((audioBitrateKbps * 1024 / 8) *
                  (media.duration?.inSeconds ?? 0))
              .round();
    }
    var total = selectedVideo?.effectiveFilesize ?? 0;
    final audio = selectedAudio?.effectiveFilesize;
    if (selectedVideo?.isVideoOnly == true) {
      total += audio ?? 0;
    }
    return total > 0 ? total : null;
  }

  /// Short CTA summary, e.g. `1080p60 · H.264 · ~245 MB`.
  String get downloadSummary {
    if (audioOnly) {
      return '${audioContainer.toUpperCase()} · $audioBitrateKbps kbps';
    }
    final video = selectedVideo;
    if (video == null) {
      return 'Select a format';
    }
    return [
      video.label,
      video.codecLabel,
    ].join(' · ');
  }

  static const Object _unset = Object();

  FormatSelectionState copyWith({
    Object? videoFormatId = _unset,
    Object? audioFormatId = _unset,
    bool? audioOnly,
    bool? mergeWithBestAudio,
    String? audioContainer,
    int? audioBitrateKbps,
    bool? embedMetadata,
    bool? embedThumbnail,
    bool? embedSubtitles,
    Set<String>? subtitleLanguages,
    String? filenameTemplate,
    bool? showGrid,
  }) {
    return FormatSelectionState(
      media: media,
      videoFormatId: identical(videoFormatId, _unset)
          ? this.videoFormatId
          : videoFormatId as String?,
      audioFormatId: identical(audioFormatId, _unset)
          ? this.audioFormatId
          : audioFormatId as String?,
      audioOnly: audioOnly ?? this.audioOnly,
      mergeWithBestAudio: mergeWithBestAudio ?? this.mergeWithBestAudio,
      audioContainer: audioContainer ?? this.audioContainer,
      audioBitrateKbps: audioBitrateKbps ?? this.audioBitrateKbps,
      embedMetadata: embedMetadata ?? this.embedMetadata,
      embedThumbnail: embedThumbnail ?? this.embedThumbnail,
      embedSubtitles: embedSubtitles ?? this.embedSubtitles,
      subtitleLanguages: subtitleLanguages ?? this.subtitleLanguages,
      filenameTemplate: filenameTemplate ?? this.filenameTemplate,
      showGrid: showGrid ?? this.showGrid,
    );
  }

  /// Initial selection driven by the user's default quality preference.
  factory FormatSelectionState.initial(MediaInfo media, AppSettings settings) {
    var audioOnly = settings.defaultQuality == QualityPreference.audio;

    String? videoId;
    if (!audioOnly) {
      final cap = settings.defaultQuality.maxHeight;
      FormatOption? chosen;
      if (cap != null) {
        for (final f in media.formatsByResolution) {
          final height = f.height ?? 0;
          if (height > 0 && height <= cap) {
            chosen = f;
            break;
          }
        }
      }
      videoId = (chosen ?? media.bestVideo ?? media.bestProgressive)?.formatId;
      if (videoId == null) {
        audioOnly = true;
      }
    }

    return FormatSelectionState(
      media: media,
      videoFormatId: audioOnly ? null : videoId,
      audioFormatId: media.bestAudio?.formatId,
      audioOnly: audioOnly,
      mergeWithBestAudio: true,
      audioContainer: 'mp3',
      audioBitrateKbps: 192,
      embedMetadata: true,
      filenameTemplate: '%(title)s [%(id)s].%(ext)s',
    );
  }

  @override
  List<Object?> get props => [
        media,
        videoFormatId,
        audioFormatId,
        audioOnly,
        mergeWithBestAudio,
        audioContainer,
        audioBitrateKbps,
        embedMetadata,
        embedThumbnail,
        embedSubtitles,
        subtitleLanguages,
        filenameTemplate,
        showGrid,
      ];
}
