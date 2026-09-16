import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../core/constants/platform_meta.dart';
import '../../core/di/injection.dart';
import '../../core/theme/context_x.dart';
import '../../core/utils/url_utils.dart';
import '../../core/widgets/error_card.dart';
import '../../core/widgets/section_header.dart';
import '../../domain/entities/app_settings.dart';
import '../../domain/entities/media_info.dart';
import '../../domain/usecases/download_control.dart';
import '../../domain/usecases/parse_url.dart';
import '../../domain/usecases/recent_parses.dart';
import '../../domain/usecases/settings_control.dart';
import '../bloc/home/home_bloc.dart';
import '../bloc/home/home_event.dart';
import '../bloc/home/home_state.dart';
import '../cubit/settings/settings_cubit.dart';
import '../format_sheet/format_selection_sheet.dart';
import '../routes.dart';
import '../shell/root_shell.dart';
import 'widgets/best_quality_tile.dart';
import 'widgets/media_preview_card.dart';
import 'widgets/parse_skeleton.dart';
import 'widgets/platform_shortcuts.dart';
import 'widgets/recent_parses_row.dart';
import 'widgets/url_input_card.dart';

/// Primary action surface: paste, parse and download.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final TextEditingController _urlController = TextEditingController();
  String? _lastQueuedMessage;
  String? _lastSheetMediaId;

  @override
  void dispose() {
    _urlController.dispose();
    super.dispose();
  }

  void _openFormatSheet(MediaInfo media, AppSettings settings) {
    showFormatSelectionSheet(
      context: context,
      media: media,
      settings: settings,
      onDownload: (request) {
        context.read<HomeBloc>().add(HomeDownloadRequested(request));
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return BlocProvider(
      create: (_) => HomeBloc(
        parseUrl: getIt<ParseUrl>(),
        getRecentParses: getIt<GetRecentParses>(),
        clearRecentParses: getIt<ClearRecentParses>(),
        loadSettings: getIt<LoadSettings>(),
        startDownload: getIt<StartDownload>(),
      ),
      child: BlocBuilder<HomeBloc, HomeState>(
        builder: (context, state) {
          return BlocListener<HomeBloc, HomeState>(
            listenWhen: (previous, current) =>
                current.queuedMessage != null &&
                current.queuedMessage != previous.queuedMessage &&
                current.queuedMessage != _lastQueuedMessage,
            listener: (context, state) {
              _lastQueuedMessage = state.queuedMessage;
              ScaffoldMessenger.of(context)
                ..hideCurrentSnackBar()
                ..showSnackBar(
                  SnackBar(
                    content: Text(state.queuedMessage!),
                    action: SnackBarAction(
                      label: 'View',
                      onPressed: () =>
                          context.read<RootShellCubit>().showDownloads(),
                    ),
                  ),
                );
              context.read<HomeBloc>().add(HomeQueuedMessageConsumed());
            },
            child: BlocListener<HomeBloc, HomeState>(
              listenWhen: (previous, current) =>
                  current.status == HomeStatus.loaded &&
                  current.media != null &&
                  current.media!.id != _lastSheetMediaId,
              listener: (context, state) {
                final media = state.media;
                if (media == null) {
                  return;
                }
                _lastSheetMediaId = media.id;
                _openFormatSheet(
                  media,
                  context.read<SettingsCubit>().state.settings,
                );
              },
              child: SafeArea(
                bottom: false,
                child: ListView(
                  padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
                  children: [
                    Row(
                      children: [
                        Image.asset(
                          'assets/logo.png',
                          width: 38,
                          height: 38,
                          fit: BoxFit.contain,
                        ),
                        const SizedBox(width: 10),
                        Text('Dugan',
                            style: Theme.of(context).textTheme.headlineSmall),
                        const Spacer(),
                        IconButton(
                          tooltip: 'Sign in to a platform',
                          icon: Icon(Icons.key_rounded,
                              color: colors.textSecondary),
                          onPressed: () => _openLoginPicker(context),
                        ),
                        IconButton(
                          tooltip: 'Settings',
                          icon: Icon(Icons.settings_outlined,
                              color: colors.textSecondary),
                          onPressed: () => context
                              .read<RootShellCubit>()
                              .selectTab(3),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    UrlInputCard(
                      controller: _urlController,
                      onChanged: (value) => context
                          .read<HomeBloc>()
                          .add(HomeUrlChanged(value)),
                      onParse: () => context
                          .read<HomeBloc>()
                          .add(const HomeParseRequested()),
                      loading: state.status == HomeStatus.loading,
                    ),
                    const SizedBox(height: 16),
                    if (state.status == HomeStatus.loading)
                      const ParseSkeleton()
                    else if (state.status == HomeStatus.error &&
                        state.failure != null)
                      ErrorCard(
                        failure: state.failure!,
                        onRetry: () => context
                            .read<HomeBloc>()
                            .add(const HomeParseRequested()),
                        onLogin: () => _openLoginPicker(context),
                      )
                    else if (state.status == HomeStatus.loaded &&
                        state.media != null)
                      MediaPreviewCard(
                        media: state.media!,
                        onChooseFormat: () => _openFormatSheet(
                          state.media!,
                          context.read<SettingsCubit>().state.settings,
                        ),
                        onDismiss: () => context
                            .read<HomeBloc>()
                            .add(HomeErrorDismissed()),
                      ),
                    const SizedBox(height: 24),
                    const SectionHeader(title: 'Quick Actions'),
                    PlatformShortcuts(
                      onSelected: (platform) {
                        final sample = UrlUtils.sampleUrlFor(platform);
                        _urlController.text = sample;
                        _urlController.selection =
                            TextSelection.fromPosition(
                          TextPosition(offset: sample.length),
                        );
                        context
                            .read<HomeBloc>()
                            .add(HomeUrlChanged(sample));
                      },
                    ),
                    if (state.recentParses.isNotEmpty) ...[
                      const SizedBox(height: 24),
                      SectionHeader(
                        title: 'Recent Parses',
                        trailing: TextButton(
                          onPressed: () => context
                              .read<HomeBloc>()
                              .add(HomeRecentParsesCleared()),
                          child: const Text('Clear'),
                        ),
                      ),
                      RecentParsesRow(
                        items: state.recentParses,
                        onTap: (media) => context
                            .read<HomeBloc>()
                            .add(HomeRecentParseSelected(media)),
                      ),
                    ],
                    const SizedBox(height: 24),
                    BestQualityTile(
                      onTap: () => context
                          .read<HomeBloc>()
                          .add(const HomeParseRequested(autoDownload: true)),
                      enabled: state.url.trim().isNotEmpty &&
                          state.status != HomeStatus.loading,
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  Future<void> _openLoginPicker(BuildContext context) async {
    final platform = await showModalBottomSheet<PlatformMeta>(
      context: context,
      backgroundColor: context.dugan.surfaceTertiary,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (context) => SafeArea(
        child: ListView(
          padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 20),
          children: [
            Text('Sign in to a platform',
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(
              'Unlocks private, age-restricted or members-only content. '
              'Cookies stay on your device.',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 12),
            for (final p in PlatformMeta.all.where((p) => p.loginHost != null))
              ListTile(
                leading: Text(p.emoji,
                    style: const TextStyle(fontSize: 22)),
                title: Text(p.name),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () => Navigator.of(context).pop(p),
              ),
          ],
        ),
      ),
    );
    if (platform != null && context.mounted) {
      await Navigator.of(context).pushNamed(
        AppRoutes.login,
        arguments: LoginScreenArgs(platformId: platform.id),
      );
    }
  }
}
