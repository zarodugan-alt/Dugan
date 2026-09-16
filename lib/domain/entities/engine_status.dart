import 'package:equatable/equatable.dart';

/// Status report for the native engine (Python/yt-dlp/FFmpeg availability).
class EngineStatus extends Equatable {
  const EngineStatus({
    required this.pythonVersion,
    required this.ytdlpVersion,
    required this.ffmpegAvailable,
  });

  final String pythonVersion;
  final String ytdlpVersion;
  final bool ffmpegAvailable;

  bool get isReady => ytdlpVersion.isNotEmpty;

  const EngineStatus.unavailable()
      : pythonVersion = '',
        ytdlpVersion = '',
        ffmpegAvailable = false;

  @override
  List<Object?> get props => [pythonVersion, ytdlpVersion, ffmpegAvailable];
}
