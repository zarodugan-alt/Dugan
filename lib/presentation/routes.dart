import 'package:flutter/material.dart';

import 'login/webview_login_screen.dart';
import 'player/player_screen.dart';
import 'shell/root_shell.dart';
import 'splash/splash_screen.dart';

/// Arguments for the player route.
class PlayerScreenArgs {
  const PlayerScreenArgs({
    required this.filePath,
    required this.title,
    this.thumbnailUrl,
  });

  final String filePath;
  final String title;
  final String? thumbnailUrl;
}

/// Arguments for the WebView login route.
class LoginScreenArgs {
  const LoginScreenArgs({required this.platformId});

  final String platformId;
}

class AppRoutes {
  AppRoutes._();

  static const String splash = '/';
  static const String shell = '/home';
  static const String player = '/player';
  static const String login = '/login';

  static Route<dynamic> onGenerateRoute(RouteSettings settings) {
    switch (settings.name) {
      case splash:
        return MaterialPageRoute<void>(
          builder: (_) => const SplashScreen(),
        );
      case shell:
        return MaterialPageRoute<void>(
          builder: (_) => const RootShell(),
        );
      case player:
        final args = settings.arguments;
        return MaterialPageRoute<void>(
          fullscreenDialog: true,
          builder: (_) => PlayerScreen(
            args: args is PlayerScreenArgs
                ? args
                : const PlayerScreenArgs(
                    filePath: '',
                    title: 'Video',
                  ),
          ),
        );
      case login:
        final args = settings.arguments;
        return MaterialPageRoute<void>(
          fullscreenDialog: true,
          builder: (_) => WebViewLoginScreen(
            args: args is LoginScreenArgs
                ? args
                : const LoginScreenArgs(platformId: 'youtube'),
          ),
        );
      default:
        return MaterialPageRoute<void>(
          builder: (_) => const Scaffold(
            body: Center(child: Text('Route not found')),
          ),
        );
    }
  }
}
