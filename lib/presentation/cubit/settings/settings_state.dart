import 'package:equatable/equatable.dart';

import '../../../domain/entities/app_settings.dart';

class SettingsState extends Equatable {
  const SettingsState({
    this.settings = AppSettings.defaults,
    this.loaded = false,
  });

  final AppSettings settings;
  final bool loaded;

  SettingsState copyWith({
    AppSettings? settings,
    bool? loaded,
  }) {
    return SettingsState(
      settings: settings ?? this.settings,
      loaded: loaded ?? this.loaded,
    );
  }

  @override
  List<Object?> get props => [settings, loaded];
}
