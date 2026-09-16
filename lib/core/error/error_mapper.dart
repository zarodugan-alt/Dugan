import 'failures.dart';

/// Maps engine error codes (produced by the native bridge, see
/// `android/app/src/main/python/ytdlp_bridge.py`) onto typed failures and
/// user-facing messages, following the error-handling matrix of the spec:
///
/// | Situation        | User message                              | Recovery            |
/// |------------------|-------------------------------------------|---------------------|
/// | Unavailable      | "This video is private or removed"        | Disable download    |
/// | Sign-in required | "Sign in required — open WebView login"   | Show login CTA      |
/// | Network          | "Connection lost — retrying (n/3)"        | Auto-retry + backoff|
/// | Format missing   | "This quality isn't available"            | Re-open sheet       |
enum ErrorMapper {
  ;

  static Failure fromCode(String? code, String? rawMessage) {
    switch (code) {
      case 'UNSUPPORTED':
        return InvalidUrlFailure(message: rawMessage ?? 'This link is not supported yet.');
      case 'UNAVAILABLE':
      case 'PRIVATE':
        return VideoUnavailableFailure(message: rawMessage ?? 'This video is private or removed.');
      case 'LOGIN_REQUIRED':
        return AuthRequiredFailure(message: rawMessage ?? 'Sign in required to access this content.');
      case 'GEO_RESTRICTED':
        return GeoRestrictedFailure();
      case 'NETWORK':
      case 'TIMEOUT':
        return NetworkFailure(message: rawMessage ?? 'Connection lost.');
      case 'STORAGE':
        return StorageFailure(message: rawMessage ?? 'The file could not be saved.');
      case 'FORMAT':
        return FormatUnavailableFailure();
      case 'ENGINE':
        return EngineUnavailableFailure(message: rawMessage ?? 'The download engine failed to start.');
      default:
        return UnknownFailure(message: rawMessage?.isNotEmpty == true ? rawMessage! : 'Something went wrong.');
    }
  }

  /// Human readable, action-oriented copy for a failure.
  static String userMessage(Failure failure) => failure.message;

  /// Failures worth an automatic retry with exponential backoff.
  static bool isRetryable(Failure failure) =>
      failure is NetworkFailure || failure is UnknownFailure;

  /// Failures that can be resolved by signing in through the WebView.
  static bool needsAuth(Failure failure) => failure is AuthRequiredFailure;
}
