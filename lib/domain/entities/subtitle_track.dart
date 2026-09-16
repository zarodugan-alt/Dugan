import 'package:equatable/equatable.dart';

/// A subtitle track offered by the source media.
class SubtitleTrack extends Equatable {
  const SubtitleTrack({
    required this.language,
    required this.isAutoCaption,
    this.name,
    this.ext = 'vtt',
  });

  /// ISO-ish language code from yt-dlp (`en`, `es-419`, `pt`…).
  final String language;

  /// Human label when provided (`English`, `Spanish (Latin America)`…).
  final String? name;

  /// Auto-generated captions rather than real subtitles.
  final bool isAutoCaption;

  /// Available container for the track (`vtt`, `srt`…).
  final String ext;

  /// Display label: `English`, `en (auto)`.
  String get displayLabel {
    final base = name?.isNotEmpty == true ? name! : language.toUpperCase();
    return isAutoCaption ? '$base (auto)' : base;
  }

  @override
  List<Object?> get props => [language, name, isAutoCaption, ext];
}
