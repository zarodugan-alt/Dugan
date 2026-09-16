import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/theme/context_x.dart';
import '../../core/widgets/empty_state.dart';
import '../../domain/entities/download_task.dart';
import '../bloc/downloads/downloads_bloc.dart';
import '../bloc/downloads/downloads_event.dart';
import '../bloc/downloads/downloads_state.dart';
import '../routes.dart';
import '../shell/root_shell.dart';
import 'widgets/download_card.dart';

/// Active / Completed tabs with real-time progress, swipe-to-cancel with
/// undo, and quick actions on finished items.
class DownloadsScreen extends StatelessWidget {
  const DownloadsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Downloads'),
          bottom: const TabBar(
            tabs: [
              Tab(text: 'Active'),
              Tab(text: 'Completed'),
            ],
          ),
          actions: [
            BlocBuilder<DownloadsBloc, DownloadsState>(
              buildWhen: (previous, current) =>
                  previous.finished.length != current.finished.length,
              builder: (context, state) {
                if (state.finished.isEmpty) {
                  return const SizedBox.shrink();
                }
                return IconButton(
                  tooltip: 'Clear finished',
                  icon: Icon(Icons.cleaning_services_outlined,
                      color: context.dugan.textSecondary),
                  onPressed: () => context
                      .read<DownloadsBloc>()
                      .add(DownloadsClearFinishedRequested()),
                );
              },
            ),
          ],
        ),
        body: const TabBarView(
          children: [_ActiveList(), _FinishedList()],
        ),
      ),
    );
  }
}

class _ActiveList extends StatelessWidget {
  const _ActiveList();

  @override
  Widget build(BuildContext context) {
    // One builder for the whole list: progress events are throttled to
    // ~500 ms by the engine, so rebuilding the (short) list is cheap and
    // every card reads the live state directly.
    return BlocBuilder<DownloadsBloc, DownloadsState>(
      builder: (context, state) {
        if (state.active.isEmpty) {
          return EmptyState(
            icon: Icons.cloud_download_outlined,
            title: 'No active downloads',
            subtitle:
                'Paste a link on the Home screen and pick a format — '
                'progress will show up here.',
            actionLabel: 'Go to Home',
            onAction: () =>
                context.read<RootShellCubit>().selectTab(0),
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 120),
          itemCount: state.active.length,
          separatorBuilder: (_, __) => const SizedBox(height: 12),
          itemBuilder: (context, index) {
            final task = state.active[index];
            return Dismissible(
              key: ValueKey('active-${task.id}'),
              direction: DismissDirection.endToStart,
              background: _SwipeBackground(taskId: task.id),
              onDismissed: (_) {
                context
                    .read<DownloadsBloc>()
                    .add(DownloadsCancelRequested(task.id));
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: const Text('Download canceled'),
                    action: SnackBarAction(
                      label: 'Undo',
                      onPressed: () => context
                          .read<DownloadsBloc>()
                          .add(DownloadsRetryRequested(task.id)),
                    ),
                  ),
                );
              },
              child: DownloadCard(
                task: task,
                onPause: () => context
                    .read<DownloadsBloc>()
                    .add(DownloadsPauseRequested(task.id)),
                onResume: () => context
                    .read<DownloadsBloc>()
                    .add(DownloadsResumeRequested(task.id)),
                onCancel: () => context
                    .read<DownloadsBloc>()
                    .add(DownloadsCancelRequested(task.id)),
              ),
            );
          },
        );
      },
    );
  }
}

class _SwipeBackground extends StatelessWidget {
  const _SwipeBackground({required this.taskId});

  final String taskId;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return Container(
      key: ValueKey('swipe-$taskId'),
      alignment: Alignment.centerRight,
      padding: const EdgeInsets.only(right: 24),
      decoration: BoxDecoration(
        color: colors.error.withOpacity(0.18),
        borderRadius: BorderRadius.circular(18),
      ),
      child: Icon(Icons.delete_rounded, color: colors.error),
    );
  }
}

class _FinishedList extends StatelessWidget {
  const _FinishedList();

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<DownloadsBloc, DownloadsState>(
      builder: (context, state) {
        if (state.finished.isEmpty) {
          return const EmptyState(
            icon: Icons.task_alt_rounded,
            title: 'Nothing here yet',
            subtitle:
                'Finished downloads appear here and in your Library.',
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 120),
          itemCount: state.finished.length,
          separatorBuilder: (_, __) => const SizedBox(height: 12),
          itemBuilder: (context, index) {
            final task = state.finished[index];
            return DownloadCard(
              task: task,
              onPause: () {},
              onResume: () => context
                  .read<DownloadsBloc>()
                  .add(DownloadsRetryRequested(task.id)),
              onCancel: () {},
              onPlay: task.status == DownloadStatus.completed &&
                      task.filePath != null
                  ? () => _play(context, task)
                  : null,
              onShare: task.status == DownloadStatus.completed &&
                      task.filePath != null
                  ? () => Share.shareXFiles([XFile(task.filePath!)])
                  : null,
            );
          },
        );
      },
    );
  }

  void _play(BuildContext context, DownloadTask task) {
    Navigator.of(context).pushNamed(
      AppRoutes.player,
      arguments: PlayerScreenArgs(
        filePath: task.filePath!,
        title: task.title,
        thumbnailUrl: task.thumbnailUrl,
      ),
    );
  }
}
