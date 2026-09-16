import '../../domain/entities/app_settings.dart';
import '../../domain/repositories/settings_repository.dart';
import '../datasources/local/settings_local_data_source.dart';

class SettingsRepositoryImpl implements SettingsRepository {
  SettingsRepositoryImpl(this._local);

  final SettingsLocalDataSource _local;

  @override
  Future<AppSettings> load() async => _local.load();

  @override
  Future<void> save(AppSettings settings) => _local.save(settings);

  @override
  Stream<AppSettings> watch() => _local.watch();
}
