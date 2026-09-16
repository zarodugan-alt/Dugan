import '../entities/history_item.dart';

/// Contract for the completed-downloads library.
abstract class HistoryRepository {
  /// Live stream of the library, newest first.
  Stream<List<HistoryItem>> watchHistory();

  Future<List<HistoryItem>> getHistory();

  Future<void> add(HistoryItem item);

  Future<void> deleteItems(List<String> ids);

  Future<void> clearHistory();
}
