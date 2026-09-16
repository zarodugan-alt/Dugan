import 'package:dartz/dartz.dart';

import '../entities/engine_status.dart';
import '../entities/media_info.dart';
import '../../core/error/failures.dart';

/// Contract for media extraction (yt-dlp `--dump-single-json`) and the
/// recently-parsed cache.
abstract class MediaRepository {
  /// Native engine health check (Python, yt-dlp, FFmpeg).
  Future<Either<Failure, EngineStatus>> checkEngineStatus();

  /// Extracts full metadata + format list for [url].
  Future<Either<Failure, MediaInfo>> parseUrl(String url);

  /// Most-recent-first cache of parsed media for the home screen rail.
  Future<List<MediaInfo>> getRecentParses();

  Future<void> saveRecentParse(MediaInfo info);

  Future<void> clearRecentParses();
}
