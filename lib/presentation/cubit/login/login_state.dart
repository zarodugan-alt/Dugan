import 'package:equatable/equatable.dart';

import '../../../core/constants/platform_meta.dart';

class LoginState extends Equatable {
  const LoginState({
    required this.platform,
    this.currentUrl = '',
    this.capturing = false,
    this.capturedCount,
    this.error,
    this.saved = false,
  });

  final PlatformMeta platform;
  final String currentUrl;
  final bool capturing;
  final int? capturedCount;
  final String? error;
  final bool saved;

  LoginState copyWith({
    PlatformMeta? platform,
    String? currentUrl,
    bool? capturing,
    int? capturedCount,
    Object? error = _unset,
    bool? saved,
  }) {
    return LoginState(
      platform: platform ?? this.platform,
      currentUrl: currentUrl ?? this.currentUrl,
      capturing: capturing ?? this.capturing,
      capturedCount: capturedCount ?? this.capturedCount,
      error: identical(error, _unset) ? this.error : error as String?,
      saved: saved ?? this.saved,
    );
  }

  static const Object _unset = Object();

  @override
  List<Object?> get props => [
        platform,
        currentUrl,
        capturing,
        capturedCount,
        error,
        saved,
      ];
}
