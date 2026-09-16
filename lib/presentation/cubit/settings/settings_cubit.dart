import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../domain/entities/app_settings.dart';
import '../../../domain/usecases/settings_control.dart';
import 'settings_state.dart';

/// Loads, edits and persists user preferences.
class SettingsCubit extends Cubit<SettingsState> {
  SettingsCubit({
    required LoadSettings loadSettings,
    required SaveSettings saveSettings,
  })  : _loadSettings = loadSettings,
        _saveSettings = saveSettings,
        super(const SettingsState());

  final LoadSettings _loadSettings;
  final SaveSettings _saveSettings;

  Future<void> load() async {
    final settings = await _loadSettings();
    emit(state.copyWith(settings: settings, loaded: true));
  }

  Future<void> _update(AppSettings Function(AppSettings current) transform) async {
    final next = transform(state.settings);
    emit(state.copyWith(settings: next));
    await _saveSettings(next);
  }

  Future<void> setThemeMode(AppThemeMode mode) =>
      _update((s) => s.copyWith(themeMode: mode));

  Future<void> setGradientBackground(bool value) =>
      _update((s) => s.copyWith(gradientBackground: value));

  Future<void> setAccentIndex(int index) =>
      _update((s) => s.copyWith(accentIndex: index));

  Future<void> setDefaultQuality(QualityPreference quality) =>
      _update((s) => s.copyWith(defaultQuality: quality));

  Future<void> setDownloadDir(String path) =>
      _update((s) => s.copyWith(downloadDirOverride: path));

  Future<void> setMaxConcurrentDownloads(int value) =>
      _update((s) => s.copyWith(maxConcurrentDownloads: value.clamp(1, 5).toInt()));

  Future<void> setWifiOnly(bool value) =>
      _update((s) => s.copyWith(wifiOnly: value));

  Future<void> setSaveToGallery(bool value) =>
      _update((s) => s.copyWith(saveToGallery: value));

  Future<void> setCustomUserAgent(String value) =>
      _update((s) => s.copyWith(customUserAgent: value));

  Future<void> setCustomYtDlpArgs(String value) =>
      _update((s) => s.copyWith(customYtDlpArgs: value));

  Future<void> setCookies(String netscapeText) =>
      _update((s) => s.copyWith(cookies: netscapeText));
}
