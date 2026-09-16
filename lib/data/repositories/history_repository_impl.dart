import '../../domain/entities/history_item.dart';
import '../../domain/repositories/history_repository.dart';
import '../datasources/local/history_local_data_source.dart';

class HistoryRepositoryImpl implements HistoryRepository {
  HistoryRepositoryImpl(this._local);

  final HistoryLocalDataSource _local;

  @override
  Stream<List<HistoryItem>> watchHistory() => _local.watch();

  @override
  Future<List<HistoryItem>> getHistory() async => _local.loadAll();

  @override
  Future<void> add(HistoryItem item) => _local.add(item);

  @override
  Future<void> deleteItems(List<String> ids) => _local.delete(ids);

  @override
  Future<void> clearHistory() => _local.clear();
}
