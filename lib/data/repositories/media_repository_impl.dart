import 'package:dartz/dartz.dart';
import 'package:flutter/foundation.dart';

import '../../core/error/error_mapper.dart';
import '../../core/error/failures.dart';
import '../../core/utils/url_utils.dart';
import '../../domain/entities/engine_status.dart';
import '../../domain/entities/media_info.dart';
import '../../domain/repositories/media_repository.dart';
import '../datasources/local/cookie_store.dart';
import '../datasources/local/recent_parse_local_data_source.dart';
import '../datasources/local/settings_local_data_source.dart';
import '../datasources/native/engine_native_data_source.dart';
import '../datasources/native/native_bridge.dart';
import '../mappers/download_request_mapper.dart';

class MediaRepositoryImpl implements MediaRepository {
  MediaRepositoryImpl({
    required EngineNativeDataSource engine,
    required RecentParseLocalDataSource recentParses,
    required SettingsLocalDataSource settingsLocal,
    required CookieStore cookieStore,
  })  : _engine = engine,
        _recentParses = recentParses,
        _settingsLocal = settingsLocal,
        _cookieStore = cookieStore;

  final EngineNativeDataSource _engine;
  final RecentParseLocalDataSource _recentParses;
  final SettingsLocalDataSource _settingsLocal;
  final CookieStore _cookieStore;

  @override
  Future<Either<Failure, EngineStatus>> checkEngineStatus() async {
    try {
      return Right(await _engine.checkStatus());
    } on EngineException catch (e) {
      return Left(ErrorMapper.fromCode(e.code, e.message));
    } catch (e) {
      return Left(EngineUnavailableFailure(message: e.toString()));
    }
  }

  @override
  Future<Either<Failure, MediaInfo>> parseUrl(String url) async {
    final trimmed = url.trim();
    if (!UrlUtils.isProbablyUrl(trimmed)) {
      return const Left(InvalidUrlFailure());
    }

    final settings = _settingsLocal.load();
    final String? cookieFile =
        settings.cookies.trim().isEmpty ? null : await _cookieStore.writeCookieFile(settings.cookies);

    try {
      final info = await _engine.extractInfo(
        url: trimmed,
        options: <String, dynamic>{
          'userAgent': settings.customUserAgent.trim().isEmpty
              ? null
              : settings.customUserAgent.trim(),
          'cookieFile': cookieFile,
          'extraArgs':
              DownloadRequestMapper.splitRawArgs(settings.customYtDlpArgs),
          'noplaylist': true,
        },
      );
      // Cache for the "Recent Parses" rail; never blocks the parse result.
      unawaitedSafe(_recentParses.save(info));
      return Right(info);
    } on EngineException catch (e) {
      return Left(ErrorMapper.fromCode(e.code, e.message));
    } catch (e) {
      return Left(UnknownFailure(message: e.toString()));
    }
  }

  @override
  Future<List<MediaInfo>> getRecentParses() async {
    return _recentParses.loadAll();
  }

  @override
  Future<void> saveRecentParse(MediaInfo info) {
    return _recentParses.save(info);
  }

  @override
  Future<void> clearRecentParses() {
    return _recentParses.clear();
  }

  void unawaitedSafe(Future<void> future) {
    future.catchError((Object e) {
      debugPrint('MediaRepositoryImpl: recent-parse cache error → $e');
    });
  }
}
