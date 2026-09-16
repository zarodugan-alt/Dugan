import 'package:get_it/get_it.dart';
import 'package:hive_flutter/hive_flutter.dart';

import '../constants/app_constants.dart' show AppConstants;
import '../../data/datasources/local/cookie_store.dart';
import '../../data/datasources/local/history_local_data_source.dart';
import '../../data/datasources/local/recent_parse_local_data_source.dart';
import '../../data/datasources/local/settings_local_data_source.dart';
import '../../data/datasources/native/download_native_engine.dart';
import '../../data/datasources/native/engine_native_data_source.dart';
import '../../data/datasources/native/system_services.dart';
import '../../data/datasources/update_checker.dart';
import '../../data/repositories/auth_repository_impl.dart';
import '../../data/repositories/download_repository_impl.dart';
import '../../data/repositories/history_repository_impl.dart';
import '../../data/repositories/media_repository_impl.dart';
import '../../data/repositories/settings_repository_impl.dart';
import '../../domain/repositories/auth_repository.dart';
import '../../domain/repositories/download_repository.dart';
import '../../domain/repositories/history_repository.dart';
import '../../domain/repositories/media_repository.dart';
import '../../domain/repositories/settings_repository.dart';
import '../../domain/usecases/auth_control.dart';
import '../../domain/usecases/check_engine_status.dart';
import '../../domain/usecases/download_control.dart';
import '../../domain/usecases/history_control.dart';
import '../../domain/usecases/parse_url.dart';
import '../../domain/usecases/recent_parses.dart';
import '../../domain/usecases/settings_control.dart';
import '../../domain/usecases/watch_downloads.dart';
import '../../presentation/bloc/downloads/downloads_bloc.dart';
import '../../presentation/bloc/history/history_bloc.dart';
import '../../presentation/cubit/settings/settings_cubit.dart';

/// Manual service locator setup (kept hand-written instead of
/// `injectable`+`build_runner` so the project builds with zero codegen).
final GetIt getIt = GetIt.instance;

Future<void> configureDependencies() async {
  await Hive.initFlutter();

  final settingsBox = await Hive.openBox(AppConstants.settingsBox);
  final historyBox = await Hive.openBox(AppConstants.historyBox);
  final recentParsesBox = await Hive.openBox(AppConstants.recentParsesBox);

  // ── Data sources ────────────────────────────────────────────────────────
  getIt.registerLazySingleton<EngineNativeDataSource>(
    () => const EngineNativeDataSource(),
  );
  getIt.registerLazySingleton<DownloadNativeEngine>(
    () => const DownloadNativeEngine(),
  );
  getIt.registerLazySingleton<SystemServices>(() => const SystemServices());
  getIt.registerLazySingleton<CookieStore>(() => const CookieStore());
  getIt.registerLazySingleton<UpdateChecker>(() => UpdateChecker());
  getIt.registerLazySingleton<SettingsLocalDataSource>(
    () => SettingsLocalDataSource(settingsBox),
  );
  getIt.registerLazySingleton<HistoryLocalDataSource>(
    () => HistoryLocalDataSource(historyBox),
  );
  getIt.registerLazySingleton<RecentParseLocalDataSource>(
    () => RecentParseLocalDataSource(recentParsesBox),
  );

  // ── Repositories ────────────────────────────────────────────────────────
  getIt.registerLazySingleton<SettingsRepository>(
    () => SettingsRepositoryImpl(getIt<SettingsLocalDataSource>()),
  );
  getIt.registerLazySingleton<HistoryRepository>(
    () => HistoryRepositoryImpl(getIt<HistoryLocalDataSource>()),
  );
  getIt.registerLazySingleton<AuthRepository>(
    () => AuthRepositoryImpl(
      settingsLocal: getIt<SettingsLocalDataSource>(),
      cookieStore: getIt<CookieStore>(),
    ),
  );
  getIt.registerLazySingleton<MediaRepository>(
    () => MediaRepositoryImpl(
      engine: getIt<EngineNativeDataSource>(),
      recentParses: getIt<RecentParseLocalDataSource>(),
      settingsLocal: getIt<SettingsLocalDataSource>(),
      cookieStore: getIt<CookieStore>(),
    ),
  );
  getIt.registerLazySingleton<DownloadRepository>(
    () => DownloadRepositoryImpl(
      engine: getIt<DownloadNativeEngine>(),
      settingsLocal: getIt<SettingsLocalDataSource>(),
      historyRepository: getIt<HistoryRepository>(),
      cookieStore: getIt<CookieStore>(),
      systemServices: getIt<SystemServices>(),
    ),
  );

  // ── Use cases ───────────────────────────────────────────────────────────
  getIt.registerLazySingleton(
    () => CheckEngineStatus(getIt<MediaRepository>()),
  );
  getIt.registerLazySingleton(() => ParseUrl(getIt<MediaRepository>()));
  getIt.registerLazySingleton(() => GetRecentParses(getIt<MediaRepository>()));
  getIt.registerLazySingleton(() => SaveRecentParse(getIt<MediaRepository>()));
  getIt.registerLazySingleton(() => StartDownload(getIt<DownloadRepository>()));
  getIt.registerLazySingleton(() => PauseDownload(getIt<DownloadRepository>()));
  getIt.registerLazySingleton(() => ResumeDownload(getIt<DownloadRepository>()));
  getIt.registerLazySingleton(() => CancelDownload(getIt<DownloadRepository>()));
  getIt.registerLazySingleton(() => RetryDownload(getIt<DownloadRepository>()));
  getIt.registerLazySingleton(
    () => ClearFinishedDownloads(getIt<DownloadRepository>()),
  );
  getIt.registerLazySingleton(() => WatchDownloads(getIt<DownloadRepository>()));
  getIt.registerLazySingleton(() => WatchHistory(getIt<HistoryRepository>()));
  getIt.registerLazySingleton(() => GetHistory(getIt<HistoryRepository>()));
  getIt.registerLazySingleton(
    () => DeleteHistoryItems(getIt<HistoryRepository>()),
  );
  getIt.registerLazySingleton(() => ClearHistory(getIt<HistoryRepository>()));
  getIt.registerLazySingleton(() => LoadSettings(getIt<SettingsRepository>()));
  getIt.registerLazySingleton(() => SaveSettings(getIt<SettingsRepository>()));
  getIt.registerLazySingleton(() => CaptureCookies(getIt<AuthRepository>()));
  getIt.registerLazySingleton(() => ImportCookies(getIt<AuthRepository>()));
  getIt.registerLazySingleton(() => ClearCookies(getIt<AuthRepository>()));

  // ── App-level blocs / cubits (singletons, live for the app lifetime) ────
  getIt.registerLazySingleton(
    () => SettingsCubit(
      loadSettings: getIt<LoadSettings>(),
      saveSettings: getIt<SaveSettings>(),
    )..load(),
  );
  getIt.registerLazySingleton(
    () => DownloadsBloc(
      watchDownloads: getIt<WatchDownloads>(),
      pauseDownload: getIt<PauseDownload>(),
      resumeDownload: getIt<ResumeDownload>(),
      cancelDownload: getIt<CancelDownload>(),
      retryDownload: getIt<RetryDownload>(),
      clearFinished: getIt<ClearFinishedDownloads>(),
    ),
  );
  getIt.registerLazySingleton(
    () => HistoryBloc(
      watchHistory: getIt<WatchHistory>(),
      deleteHistoryItems: getIt<DeleteHistoryItems>(),
      clearHistory: getIt<ClearHistory>(),
      parseUrl: getIt<ParseUrl>(),
      loadSettings: getIt<LoadSettings>(),
      startDownload: getIt<StartDownload>(),
    ),
  );
}
