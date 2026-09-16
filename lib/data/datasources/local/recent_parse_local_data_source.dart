import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:hive_flutter/hive_flutter.dart';

import '../../../core/constants/app_constants.dart';
import '../../../domain/entities/media_info.dart';
import '../../models/media_info_model.dart';

/// Cache of recently parsed media for the home screen rail (not downloaded,
/// just parsed) — newest first, capped at [AppConstants.maxRecentParses].
class RecentParseLocalDataSource {
  RecentParseLocalDataSource(this._box);

  final Box _box;

  static const String _key = 'recent';

  List<MediaInfo> loadAll() {
    final raw = _box.get(_key);
    if (raw is! List) {
      return const <MediaInfo>[];
    }
    final items = <MediaInfo>[];
    for (final entry in raw) {
      if (entry is! String) {
        continue;
      }
      try {
        final decoded = jsonDecode(entry) as Map<String, dynamic>;
        items.add(MediaInfoModel.fromJson(decoded));
      } on Exception {
        // Ignore corrupted entries.
      }
    }
    return items;
  }

  Future<void> save(MediaInfo info) async {
    final current = loadAll()
        .where((m) => !(m.id == info.id && m.sourceUrl == info.sourceUrl))
        .toList();
    final encoded = <String>[jsonEncode(MediaInfoModel.toJson(info))];
    for (final m in current.take(AppConstants.maxRecentParses - 1)) {
      encoded.add(jsonEncode(MediaInfoModel.toJson(m)));
    }
    await _box.put(_key, encoded);
  }

  Future<void> clear() => _box.clear();
}
