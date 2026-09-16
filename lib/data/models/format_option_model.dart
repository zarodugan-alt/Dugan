import '../../domain/entities/format_option.dart';

/// Serialisation for [FormatOption] against the sanitised yt-dlp schema
/// produced by the native bridge.
class FormatOptionModel {
  const FormatOptionModel._();

  static FormatOption fromJson(Map<String, dynamic> json) {
    final vcodec = json['vcodec'] as String?;
    final acodec = json['acodec'] as String?;
    return FormatOption(
      formatId: '${json['formatId'] ?? ''}',
      ext: '${json['ext'] ?? ''}',
      resolution: json['resolution'] as String?,
      width: (json['width'] as num?)?.toInt(),
      height: (json['height'] as num?)?.toInt(),
      fps: (json['fps'] as num?)?.toInt(),
      vcodec: (vcodec == null || vcodec == 'none') ? null : vcodec,
      acodec: (acodec == null || acodec == 'none') ? null : acodec,
      filesize: (json['filesize'] as num?)?.toInt(),
      filesizeApprox: (json['filesizeApprox'] as num?)?.toInt(),
      tbr: (json['tbr'] as num?)?.toInt(),
      abr: (json['abr'] as num?)?.toInt(),
      hasVideo: vcodec != null && vcodec != 'none',
      hasAudio: acodec != null && acodec != 'none',
      formatNote: json['formatNote'] as String?,
      protocol: '${json['protocol'] ?? ''}',
    );
  }

  static Map<String, dynamic> toJson(FormatOption f) {
    return <String, dynamic>{
      'formatId': f.formatId,
      'ext': f.ext,
      'resolution': f.resolution,
      'width': f.width,
      'height': f.height,
      'fps': f.fps,
      'vcodec': f.vcodec,
      'acodec': f.acodec,
      'filesize': f.filesize,
      'filesizeApprox': f.filesizeApprox,
      'tbr': f.tbr,
      'abr': f.abr,
      'formatNote': f.formatNote,
      'protocol': f.protocol,
    };
  }
}
