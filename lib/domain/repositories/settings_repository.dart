import '../entities/app_settings.dart';

/// Contract for persisted user preferences.
abstract class SettingsRepository {
  Future<AppSettings> load();

  Future<void> save(AppSettings settings);

  /// Emits on every persisted change.
  Stream<AppSettings> watch();
}
