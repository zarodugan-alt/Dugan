import 'dart:convert';

import 'package:hive_flutter/hive_flutter.dart';

import '../../../core/utils/listenable_stream.dart';
import '../../../domain/entities/history_item.dart';
import '../../models/history_item_model.dart';

/// Hive-backed store for the download library. One JSON string per item,
/// keyed by item id.
class HistoryLocalDataSource {
  HistoryLocalDataSource(this._box);

  final Box _box;

  List<HistoryItem> loadAll() {
    final items = <HistoryItem>[];
    for (final raw in _box.values) {
      if (raw is! String) {
        continue;
      }
      try {
        final decoded = jsonDecode(raw) as Map<String, dynamic>;
        items.add(HistoryItemModel.fromJson(decoded));
      } on Exception {
        // Skip corrupted entries rather than crashing the library.
      }
    }
    items.sort((a, b) => b.completedAt.compareTo(a.completedAt));
    return items;
  }

  Future<void> add(HistoryItem item) {
    return _box.put(item.id, jsonEncode(HistoryItemModel.toJson(item)));
  }

  Future<void> delete(List<String> ids) async {
    await _box.deleteAll(ids);
  }

  Future<void> clear() => _box.clear();

  Stream<List<HistoryItem>> watch() {
    return listenableToStream(_box.listenable(), loadAll);
  }
}
