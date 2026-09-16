import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import '../../core/constants/platform_meta.dart';
import '../../core/di/injection.dart';
import '../../core/theme/context_x.dart';
import '../../core/widgets/gradient_button.dart';
import '../../domain/usecases/auth_control.dart';
import '../cubit/login/login_cubit.dart';
import '../cubit/login/login_state.dart';
import '../routes.dart';

/// In-app browser with cookie persistence: sign in to a platform, then tap
/// "Save session" — cookies are exported in Netscape format and injected
/// into every yt-dlp request.
class WebViewLoginScreen extends StatelessWidget {
  const WebViewLoginScreen({super.key, required this.args});

  final LoginScreenArgs args;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    final platform = PlatformMeta.byId(args.platformId);
    return BlocProvider(
      create: (_) => LoginCubit(
        captureCookies: getIt<CaptureCookies>(),
        clearCookies: getIt<ClearCookies>(),
        platform: platform,
      ),
      child: Scaffold(
        appBar: AppBar(
          leading: IconButton(
            icon: const Icon(Icons.close_rounded),
            onPressed: () => Navigator.of(context).pop(),
          ),
          title: Text('Sign in — ${platform.name}'),
          actions: [
            IconButton(
              tooltip: 'Clear saved session',
              icon: Icon(Icons.cookie_outlined, color: colors.warning),
              onPressed: () => context.read<LoginCubit>().clearSession(),
            ),
          ],
        ),
        body: Column(
          children: [
            BlocBuilder<LoginCubit, LoginState>(
              buildWhen: (previous, current) =>
                  previous.error != current.error ||
                  previous.saved != current.saved ||
                  previous.capturing != current.capturing,
              builder: (context, state) {
                if (state.error != null) {
                  return _Banner(
                    color: colors.error,
                    text: state.error!,
                  );
                }
                if (state.saved) {
                  return _Banner(
                    color: colors.success,
                    text: 'Session saved — ${state.capturedCount} cookies '
                        'will be used for downloads.',
                  );
                }
                return const SizedBox.shrink();
              },
            ),
            Expanded(
              child: _LoginWebView(initialUrl: platform.loginHost),
            ),
            BlocBuilder<LoginCubit, LoginState>(
              buildWhen: (previous, current) =>
                  previous.capturing != current.capturing ||
                  previous.saved != current.saved,
              builder: (context, state) {
                return SafeArea(
                  top: false,
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: GradientButton(
                      label: state.capturing
                          ? 'Saving session…'
                          : 'Save session',
                      icon: Icons.save_rounded,
                      onPressed: state.capturing
                          ? null
                          : () => context.read<LoginCubit>().capture(),
                    ),
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}

class _Banner extends StatelessWidget {
  const _Banner({required this.color, required this.text});

  final Color color;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      color: color.withOpacity(0.15),
      child: Text(
        text,
        style: TextStyle(color: color, fontSize: 13),
      ),
    );
  }
}

class _LoginWebView extends StatelessWidget {
  const _LoginWebView({required this.initialUrl});

  final String? initialUrl;

  @override
  Widget build(BuildContext context) {
    final cubit = context.read<LoginCubit>();
    final url = initialUrl ?? 'https://www.youtube.com';
    return InAppWebView(
      initialUrlRequest: URLRequest(url: WebUri(url)),
      initialSettings: InAppWebViewSettings(
        javaScriptEnabled: true,
        domStorageEnabled: true,
        supportZoom: false,
      ),
      onUpdateVisitedHistory: (controller, url, isReload) {
        if (url != null) {
          cubit.urlChanged(url.toString());
        }
      },
    );
  }
}
