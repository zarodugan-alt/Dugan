import '../entities/history_item.dart';
import '../repositories/history_repository.dart';

class WatchHistory {
  const WatchHistory(this._repository);

  final HistoryRepository _repository;

  Stream<List<HistoryItem>> call() => _repository.watchHistory();
}

class GetHistory {
  const GetHistory(this._repository);

  final HistoryRepository _repository;

  Future<List<HistoryItem>> call() => _repository.getHistory();
}

class DeleteHistoryItems {
  const DeleteHistoryItems(this._repository);

  final HistoryRepository _repository;

  Future<void> call(List<String> ids) => _repository.deleteItems(ids);
}

class ClearHistory {
  const ClearHistory(this._repository);

  final HistoryRepository _repository;

  Future<void> call() => _repository.clearHistory();
}
