import 'package:dartz/dartz.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import '../../core/error/failures.dart';
import '../../domain/repositories/auth_repository.dart';
import '../datasources/local/cookie_store.dart';
import '../datasources/local/settings_local_data_source.dart';

/// Captures cookies from the shared WebView cookie store (including
/// HttpOnly cookies, which `document.cookie` cannot reach) and persists
/// them in Netscape format for yt-dlp.
class AuthRepositoryImpl implements AuthRepository {
  AuthRepositoryImpl({
    required SettingsLocalDataSource settingsLocal,
    required CookieStore cookieStore,
  })  : _settingsLocal = settingsLocal,
        _cookieStore = cookieStore;

  final SettingsLocalDataSource _settingsLocal;
  final CookieStore _cookieStore;

  @override
  Future<Either<Failure, int>> captureCookies(List<String> hosts) async {
    final manager = CookieManager.instance();
    final lines = <String>[
      '# Netscape HTTP Cookie File',
      '# Captured by Dugan — do not edit!',
      '',
    ];
    var count = 0;

    for (final host in hosts) {
      List<Cookie> cookies;
      try {
        cookies = await manager.getCookies(url: WebUri(host));
      } on Exception {
        continue;
      }
      for (final cookie in cookies) {
        var domain = (cookie.domain ?? '').toLowerCase();
        if (domain.isEmpty) {
          domain = Uri.tryParse(host)?.host.toLowerCase() ?? '';
          if (domain.isEmpty) {
            continue;
          }
        }
        final includeSubdomains = domain.startsWith('.') ? 'TRUE' : 'FALSE';
        final path = cookie.path?.isNotEmpty == true ? cookie.path! : '/';
        final secure = (cookie.isSecure ?? false) ? 'TRUE' : 'FALSE';
        final expiresMs = cookie.expirationDate ??
            DateTime.now()
                .add(const Duration(days: 365))
                .millisecondsSinceEpoch;
        final expiresSec = (expiresMs / 1000).round();
        lines.add(
          [
            domain,
            includeSubdomains,
            path,
            secure,
            '$expiresSec',
            cookie.name,
            cookie.value,
          ].join('\t'),
        );
        count++;
      }
    }

    if (count == 0) {
      return const Left(
        UnknownFailure(
          message: 'No cookies captured — finish signing in first, '
              'then tap “Save session”.',
        ),
      );
    }

    final text = '${lines.join('\n')}\n';
    final settings = _settingsLocal.load();
    await _settingsLocal.save(settings.copyWith(cookies: text));
    await _cookieStore.writeCookieFile(text);
    return Right(count);
  }

  @override
  Future<String> cookieJar() async {
    return _settingsLocal.load().cookies;
  }

  @override
  Future<Either<Failure, int>> importCookies(String netscapeText) async {
    var count = 0;
    for (final line in netscapeText.split('\n')) {
      final trimmed = line.trim();
      if (trimmed.isEmpty || trimmed.startsWith('#')) {
        continue;
      }
      if (trimmed.split('\t').length >= 7) {
        count++;
      }
    }
    if (count == 0) {
      return const Left(
        UnknownFailure(
          message: 'That does not look like a Netscape cookie file — '
              'expected 7 tab-separated columns per line.',
        ),
      );
    }
    final settings = _settingsLocal.load();
    await _settingsLocal.save(settings.copyWith(cookies: netscapeText));
    await _cookieStore.writeCookieFile(netscapeText);
    return Right(count);
  }

  @override
  Future<void> clearCookies() async {
    final settings = _settingsLocal.load();
    await _settingsLocal.save(settings.copyWith(cookies: ''));
    await _cookieStore.writeCookieFile('');
  }
}
