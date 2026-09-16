import 'dart:async';

import '../entities/app_settings.dart';
import '../repositories/settings_repository.dart';

class LoadSettings {
  const LoadSettings(this._repository);

  final SettingsRepository _repository;

  Future<AppSettings> call() => _repository.load();
}

class SaveSettings {
  const SaveSettings(this._repository);

  final SettingsRepository _repository;

  Future<void> call(AppSettings settings) => _repository.save(settings);
}

class WatchSettings {
  const WatchSettings(this._repository);

  final SettingsRepository _repository;

  Stream<AppSettings> call() => _repository.watch();
}
