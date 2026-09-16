import '../entities/media_info.dart';
import '../repositories/media_repository.dart';

/// Reads the recent-parses cache (most recent first).
class GetRecentParses {
  const GetRecentParses(this._repository);

  final MediaRepository _repository;

  Future<List<MediaInfo>> call() => _repository.getRecentParses();
}

/// Adds/refreshes a media entry at the head of the recent-parses cache.
class SaveRecentParse {
  const SaveRecentParse(this._repository);

  final MediaRepository _repository;

  Future<void> call(MediaInfo info) => _repository.saveRecentParse(info);
}

class ClearRecentParses {
  const ClearRecentParses(this._repository);

  final MediaRepository _repository;

  Future<void> call() => _repository.clearRecentParses();
}
