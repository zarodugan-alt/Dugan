import '../entities/download_task.dart';
import '../repositories/download_repository.dart';

/// Live stream of every download task (active + finished this session).
class WatchDownloads {
  const WatchDownloads(this._repository);

  final DownloadRepository _repository;

  Stream<List<DownloadTask>> call() => _repository.watchTasks();
}
