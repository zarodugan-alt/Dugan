import '../../domain/entities/engine_status.dart';

class EngineStatusModel {
  const EngineStatusModel._();

  static EngineStatus fromJson(Map<String, dynamic> json) {
    return EngineStatus(
      pythonVersion: '${json['pythonVersion'] ?? ''}',
      ytdlpVersion: '${json['ytdlpVersion'] ?? ''}',
      ffmpegAvailable: json['ffmpegAvailable'] as bool? ?? false,
    );
  }
}
