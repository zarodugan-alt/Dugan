import 'package:equatable/equatable.dart';

/// Base class for every recoverable error surfaced by the domain layer.
///
/// The domain layer never throws — it returns `Either<Failure, T>` — so
/// failures carry everything the presentation layer needs to render a
/// friendly message and offer a recovery action (see [Failure.userMessage]).
sealed class Failure extends Equatable {
  const Failure({this.message = ''});

  /// Raw engine/technical message, used for logging and diagnostics.
  final String message;

  @override
  List<Object?> get props => [message];
}

/// yt-dlp could not resolve the URL at all.
final class InvalidUrlFailure extends Failure {
  const InvalidUrlFailure({super.message = 'That does not look like a valid media link.'});
}

/// Engine (Python / yt-dlp) is not available on this platform or build.
final class EngineUnavailableFailure extends Failure {
  const EngineUnavailableFailure({super.message = 'The download engine is not available on this device.'});
}

/// Video is private, removed or otherwise unavailable.
final class VideoUnavailableFailure extends Failure {
  const VideoUnavailableFailure({super.message = 'This video is private or removed.'});
}

/// Sign-in / age verification / bot-check required.
final class AuthRequiredFailure extends Failure {
  const AuthRequiredFailure({super.message = 'Sign in required to access this content.'});
}

/// Content blocked in the user's region.
final class GeoRestrictedFailure extends Failure {
  const GeoRestrictedFailure({super.message = 'This content is not available in your country.'});
}

/// Network-level problem; usually retryable.
final class NetworkFailure extends Failure {
  const NetworkFailure({super.message = 'Connection lost while contacting the service.'});
}

/// Requested quality/format does not exist for this media.
final class FormatUnavailableFailure extends Failure {
  const FormatUnavailableFailure({super.message = 'This quality is not available for this media.'});
}

/// Local storage problem (disk full, missing folder, permissions…).
final class StorageFailure extends Failure {
  const StorageFailure({super.message = 'The file could not be saved on this device.'});
}

/// WiFi-only mode is on and the device is not on Wi-Fi.
final class WifiRequiredFailure extends Failure {
  const WifiRequiredFailure({super.message = 'Wi-Fi only is enabled — connect to Wi-Fi to download.'});
}

/// Generic fallback.
final class UnknownFailure extends Failure {
  const UnknownFailure({super.message = 'Something went wrong.'});
}
