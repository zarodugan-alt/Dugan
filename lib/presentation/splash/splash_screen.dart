import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:permission_handler/permission_handler.dart';

import '../../core/di/injection.dart';
import '../../core/theme/context_x.dart';
import '../../core/widgets/gradient_button.dart';
import '../../domain/usecases/check_engine_status.dart';
import '../routes.dart';
import 'splash_bloc.dart';

/// Brand moment + initialization: pulsing gradient logo on an OLED canvas
/// with a thin gradient progress line, while the engine status is checked.
class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (_) =>
          SplashBloc(checkEngineStatus: getIt<CheckEngineStatus>())
            ..add(SplashStarted()),
      child: const _SplashView(),
    );
  }
}

class _SplashView extends StatelessWidget {
  const _SplashView();

  void _onReady(BuildContext context) {
    // Ask for notification permission (Android 13+) so download-progress
    // notifications can show; denial never blocks or delays the app.
    unawaited(
      Permission.notification.request().then(
        (status) => debugPrint('Notification permission → $status'),
      ),
    );
    Navigator.of(context).pushReplacementNamed(AppRoutes.shell);
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;

    return BlocListener<SplashBloc, SplashState>(
      listener: (context, state) {
        if (state.status == SplashStatus.ready) {
          _onReady(context);
        }
      },
      child: Scaffold(
        backgroundColor: colors.surfacePrimary,
        body: Stack(
          children: [
            // Ambient gradient glows.
            Positioned(
              top: -120,
              left: -80,
              child: _Glow(
                color: colors.accentPrimary.withOpacity(0.35),
                size: 340,
              ),
            ),
            Positioned(
              bottom: -140,
              right: -100,
              child: _Glow(
                color: colors.accentSecondary.withOpacity(0.25),
                size: 380,
              ),
            ),
            Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Logo with gradient glow pulse.
                  Container(
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      boxShadow: colors.accentGlow,
                    ),
                    child: Image.asset(
                      'assets/logo.png',
                      width: 120,
                      height: 120,
                      fit: BoxFit.contain,
                    ),
                  )
                      .animate(
                        onPlay: (controller) =>
                            controller.repeat(reverse: true),
                      )
                      .scaleXY(
                        begin: 0.96,
                        end: 1.05,
                        duration: 1600.ms,
                        curve: Curves.easeInOut,
                      ),
                  const SizedBox(height: 28),
                  Text(
                    'Dugan',
                    style: Theme.of(context).textTheme.displaySmall,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Premium video downloader',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            ),
            // Bottom status + thin gradient progress line.
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: SafeArea(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 32),
                      child: BlocBuilder<SplashBloc, SplashState>(
                        builder: (context, state) =>
                            _StatusLine(state: state),
                      ),
                    ),
                    const SizedBox(height: 18),
                    const _BottomProgressBar(),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatusLine extends StatelessWidget {
  const _StatusLine({required this.state});

  final SplashState state;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    switch (state.status) {
      case SplashStatus.initializing:
        return Text(
          'Initializing engine…',
          style: Theme.of(context)
              .textTheme
              .bodySmall!
              .copyWith(color: colors.textSecondary),
        );
      case SplashStatus.failed:
        return Column(
          children: [
            Text(
              state.failure?.message ?? 'Engine check failed',
              textAlign: TextAlign.center,
              style: Theme.of(context)
                  .textTheme
                  .bodySmall!
                  .copyWith(color: colors.error),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: GradientButton(
                    label: 'Retry',
                    icon: Icons.refresh_rounded,
                    height: 44,
                    onPressed: () => context
                        .read<SplashBloc>()
                        .add(SplashRetryRequested()),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: GradientButton(
                    label: 'Continue anyway',
                    height: 44,
                    onPressed: () => Navigator.of(context)
                        .pushReplacementNamed(AppRoutes.shell),
                  ),
                ),
              ],
            ),
          ],
        );
      case SplashStatus.ready:
        return Text(
          'yt-dlp ${state.engine.ytdlpVersion} · '
          'FFmpeg ${state.engine.ffmpegAvailable ? 'ready' : 'missing'}',
          style: Theme.of(context)
              .textTheme
              .bodySmall!
              .copyWith(color: colors.success),
        );
    }
  }
}

class _BottomProgressBar extends StatelessWidget {
  const _BottomProgressBar();

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return Container(
      height: 3,
      margin: const EdgeInsets.only(bottom: 20),
      width: 220,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(3),
        gradient: LinearGradient(
          colors: [
            colors.accentPrimary,
            colors.accentSecondary,
            colors.accentPrimary,
          ],
        ),
      ),
    )
        .animate(onPlay: (c) => c.repeat())
        .shimmer(
          duration: 1400.ms,
          color: colors.surfacePrimary.withOpacity(0.6),
        );
  }
}

class _Glow extends StatelessWidget {
  const _Glow({required this.color, required this.size});

  final Color color;
  final double size;

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: RadialGradient(
            colors: [color, color.withOpacity(0)],
          ),
        ),
      ),
    );
  }
}
