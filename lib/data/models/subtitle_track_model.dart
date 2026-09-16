import '../../domain/entities/subtitle_track.dart';

class SubtitleTrackModel {
  const SubtitleTrackModel._();

  static SubtitleTrack fromJson(Map<String, dynamic> json) {
    return SubtitleTrack(
      language: '${json['language'] ?? ''}',
      name: json['name'] as String?,
      isAutoCaption: json['isAutoCaption'] as bool? ?? false,
      ext: '${json['ext'] ?? 'vtt'}',
    );
  }

  static Map<String, dynamic> toJson(SubtitleTrack track) {
    return <String, dynamic>{
      'language': track.language,
      'name': track.name,
      'isAutoCaption': track.isAutoCaption,
      'ext': track.ext,
    };
  }
}
