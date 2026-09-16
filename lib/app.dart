import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import 'core/di/injection.dart';
import 'core/theme/app_colors.dart';
import 'core/theme/app_theme.dart';
import 'domain/entities/app_settings.dart';
import 'presentation/bloc/downloads/downloads_bloc.dart';
import 'presentation/bloc/downloads/downloads_event.dart';
import 'presentation/bloc/history/history_bloc.dart';
import 'presentation/bloc/history/history_event.dart';
import 'presentation/cubit/settings/settings_cubit.dart';
import 'presentation/cubit/settings/settings_state.dart';
import 'presentation/routes.dart';

/// Root widget: theme (driven by SettingsCubit) + app-level bloc providers.
class DuganApp extends StatelessWidget {
  const DuganApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: [
        BlocProvider.value(value: getIt<SettingsCubit>()),
        BlocProvider.value(
          value: getIt<DownloadsBloc>()
            ..add(DownloadsSubscriptionRequested()),
        ),
        BlocProvider.value(value: getIt<HistoryBloc>()),
      ],
      child: BlocBuilder<SettingsCubit, SettingsState>(
        builder: (context, state) {
          final settings = state.settings;
          final accent = _accentFor(settings.accentIndex);
          return MaterialApp(
            title: 'Dugan',
            debugShowCheckedModeBanner: false,
            theme: AppTheme.light(accent: accent),
            darkTheme: AppTheme.dark(accent: accent),
            themeMode: switch (settings.themeMode) {
              AppThemeMode.system => ThemeMode.system,
              AppThemeMode.dark => ThemeMode.dark,
              AppThemeMode.light => ThemeMode.light,
            },
            initialRoute: AppRoutes.splash,
            onGenerateRoute: AppRoutes.onGenerateRoute,
          );
        },
      ),
    );
  }

  AccentOption _accentFor(int index) {
    if (index < 0 || index >= kAccentOptions.length) {
      return kAccentOptions.first;
    }
    return kAccentOptions[index];
  }
}
