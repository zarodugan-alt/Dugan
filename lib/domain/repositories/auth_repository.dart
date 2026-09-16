import 'package:dartz/dartz.dart';

import '../../core/error/failures.dart';

/// Contract for platform authentication (cookie capture from the WebView
/// login flow, persisted in Netscape format for yt-dlp).
abstract class AuthRepository {
  /// Reads cookies for the given hosts out of the WebView cookie store and
  /// persists them. Returns the number of cookies captured.
  Future<Either<Failure, int>> captureCookies(List<String> hosts);

  /// Current Netscape-format cookie jar ('' when none).
  Future<String> cookieJar();

  /// Replaces the cookie jar with hand-imported text. Returns the parsed
  /// cookie count, or a failure when the text is not valid.
  Future<Either<Failure, int>> importCookies(String netscapeText);

  Future<void> clearCookies();
}
