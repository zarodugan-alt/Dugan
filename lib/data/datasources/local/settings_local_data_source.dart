import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:hive_flutter/hive_flutter.dart';

import '../../../core/utils/listenable_stream.dart';
import '../../../domain/entities/app_settings.dart';
import '../../models/app_settings_model.dart';

/// Hive-backed key/value store for [AppSettings]. Values are stored as a
/// single JSON string under `settings` — no code-generated adapters needed.
class SettingsLocalDataSource {
  SettingsLocalDataSource(this._box);

  final Box _box;

  static const String _key = 'settings';

  AppSettings load() {
    final raw = _box.get(_key);
    if (raw is! String || raw.isEmpty) {
      return AppSettings.defaults;
    }
    try {
      final decoded = jsonDecode(raw) as Map<String, dynamic>;
      return AppSettingsModel.fromJson(decoded);
    } on Exception catch (e) {
      debugPrint('SettingsLocalDataSource: corrupted settings ($e)');
      return AppSettings.defaults;
    }
  }

  Future<void> save(AppSettings settings) {
    return _box.put(_key, jsonEncode(AppSettingsModel.toJson(settings)));
  }

  Stream<AppSettings> watch() {
    return listenableToStream(_box.listenable(), load);
  }
}
