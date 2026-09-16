import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../core/constants/platform_meta.dart';
import '../../../domain/usecases/auth_control.dart';
import 'login_state.dart';

/// Drives the WebView login flow: tracks navigation, captures cookies for
/// the platform's hosts when the user confirms sign-in.
class LoginCubit extends Cubit<LoginState> {
  LoginCubit({
    required CaptureCookies captureCookies,
    required ClearCookies clearCookies,
    required PlatformMeta platform,
  })  : _captureCookies = captureCookies,
        _clearCookies = clearCookies,
        super(LoginState(platform: platform));

  final CaptureCookies _captureCookies;
  final ClearCookies _clearCookies;

  /// Hosts whose cookies must be captured per platform.
  static const Map<String, List<String>> _captureHosts = {
    'youtube': [
      'https://www.youtube.com',
      'https://accounts.google.com',
    ],
    'bilibili': [
      'https://www.bilibili.com',
      'https://passport.bilibili.com',
    ],
    'instagram': ['https://www.instagram.com'],
    'facebook': ['https://www.facebook.com'],
  };

  void urlChanged(String url) {
    emit(state.copyWith(currentUrl: url));
  }

  Future<void> capture() async {
    emit(state.copyWith(capturing: true, error: null));
    final hosts = _captureHosts[state.platform.id] ??
        (state.platform.loginHost != null
            ? [state.platform.loginHost!]
            : <String>[]);
    final result = await _captureCookies(hosts);
    result.fold(
      (failure) => emit(
        state.copyWith(capturing: false, error: failure.message),
      ),
      (count) => emit(
        state.copyWith(capturing: false, capturedCount: count, saved: true),
      ),
    );
  }

  Future<void> clearSession() async {
    await _clearCookies();
    emit(
      state.copyWith(
        saved: false,
        capturedCount: null,
        error: null,
        currentUrl: state.platform.loginHost ?? '',
      ),
    );
  }
}
