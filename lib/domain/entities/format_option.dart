import 'package:equatable/equatable.dart';

/// A single yt-dlp format/stream variant.
class FormatOption extends Equatable {
  const FormatOption({
    required this.formatId,
    this.ext = '',
    this.resolution,
    this.width,
    this.height,
    this.fps,
    this.vcodec,
    this.acodec,
    this.filesize,
    this.filesizeApprox,
    this.tbr,
    this.abr,
    this.hasVideo = false,
    this.hasAudio = false,
    this.formatNote,
    this.protocol = '',
  });

  final String formatId;
  final String ext;

  /// Raw resolution string, e.g. `1920x1080`.
  final String? resolution;
  final int? width;
  final int? height;
  final int? fps;

  /// `null` when the corresponding stream is absent (`none` in yt-dlp).
  final String? vcodec;
  final String? acodec;

  final int? filesize;
  final int? filesizeApprox;

  /// Total / average bitrate in kbps.
  final int? tbr;
  final int? abr;

  final bool hasVideo;
  final bool hasAudio;
  final String? formatNote;
  final String protocol;

  /// Best-known size in bytes (exact, else approx, else null).
  int? get effectiveFilesize => filesize ?? filesizeApprox;

  /// `1080p`, `1080p60`, `audio`…
  String get label {
    if (isAudioOnly) {
      final kbps = abr ?? tbr;
      return kbps != null && kbps > 0 ? '$kbps kbps' : 'audio';
    }
    if (height == null || height! <= 0) {
      return formatNote ?? 'video';
    }
    final base = '${height}p';
    if (fps != null && fps! > 30) {
      return '${base}${fps!.round()}';
    }
    return base;
  }

  /// `H.264`, `VP9`, `AV1`, `HEVC`, `AAC`, `OPUS`…
  String get codecLabel {
    if (isAudioOnly) {
      return _codecName(acodec) ?? ext.toUpperCase();
    }
    if (hasVideo) {
      return _codecName(vcodec) ?? ext.toUpperCase();
    }
    return ext.toUpperCase();
  }

  /// A combined video+audio stream (single file, no merge needed).
  bool get isProgressive => hasVideo && hasAudio;

  /// Video-only stream — needs a separate audio selection to be playable.
  bool get isVideoOnly => hasVideo && !hasAudio;

  /// Audio-only stream.
  bool get isAudioOnly => !hasVideo && hasAudio;

  /// Sort weight: higher is better.
  double get qualityScore =>
      (height ?? 0) * 1000.0 + (tbr ?? abr ?? 0) + (fps ?? 0) / 60.0;

  static String? _codecName(String? codec) {
    if (codec == null) {
      return null;
    }
    final lower = codec.toLowerCase();
    if (lower.contains('avc1') || lower.contains('h264')) {
      return 'H.264';
    }
    if (lower.contains('h265') || lower.contains('hev1') || lower.contains('hevc')) {
      return 'HEVC';
    }
    if (lower.contains('vp09') || lower.contains('vp9')) {
      return 'VP9';
    }
    if (lower.contains('vp8')) {
      return 'VP8';
    }
    if (lower.contains('av01')) {
      return 'AV1';
    }
    if (lower.contains('mp4a') || lower.contains('aac')) {
      return 'AAC';
    }
    if (lower.contains('opus')) {
      return 'OPUS';
    }
    if (lower.contains('vorbis')) {
      return 'Vorbis';
    }
    if (lower.contains('ec-3') || lower.contains('eac3')) {
      return 'E-AC3';
    }
    if (lower.contains('mp3')) {
      return 'MP3';
    }
    if (lower.contains('flac')) {
      return 'FLAC';
    }
    if (lower.contains('wav')) {
      return 'WAV';
    }
    return codec;
  }

  @override
  List<Object?> get props => [
        formatId,
        ext,
        resolution,
        width,
        height,
        fps,
        vcodec,
        acodec,
        filesize,
        filesizeApprox,
        tbr,
        abr,
        hasVideo,
        hasAudio,
        formatNote,
        protocol,
      ];
}
