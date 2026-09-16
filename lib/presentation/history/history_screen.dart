import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/di/injection.dart';
import '../../core/theme/context_x.dart';
import '../../core/widgets/empty_state.dart';
import '../../data/datasources/native/system_services.dart';
import '../../domain/entities/history_item.dart';
import '../../domain/usecases/download_control.dart';
import '../../domain/usecases/history_control.dart';
import '../../domain/usecases/parse_url.dart';
import '../../domain/usecases/settings_control.dart';
import '../bloc/history/history_bloc.dart';
import '../bloc/history/history_event.dart';
import '../bloc/history/history_state.dart';
import '../routes.dart';
import '../shell/root_shell.dart';
import 'widgets/history_list_item.dart';

/// Download library: search, sort, batch selection, item actions.
class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key});

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  final TextEditingController _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    // HistoryBloc is an app-lifetime singleton provided above MaterialApp.
    context.read<HistoryBloc>().add(HistoryViewStarted());
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return BlocConsumer<HistoryBloc, HistoryState>(
        listenWhen: (previous, current) =>
            current.redownloadMessage != null &&
            current.redownloadMessage != previous.redownloadMessage,
        listener: (context, state) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(state.redownloadMessage!),
              action: SnackBarAction(
                label: 'View',
                onPressed: () =>
                    context.read<RootShellCubit>().showDownloads(),
              ),
            ),
          );
        },
        builder: (context, state) {
          final items = state.visible;
          return Scaffold(
            appBar: state.isSelecting
                ? _SelectionAppBar(selectedCount: state.selectedIds.length)
                : AppBar(
                    title: const Text('Library'),
                    actions: [
                      IconButton(
                        tooltip: 'Sort',
                        icon: Icon(Icons.sort_rounded,
                            color: context.dugan.textSecondary),
                        onPressed: () => _showSortMenu(context),
                      ),
                      if (state.items.isNotEmpty)
                        IconButton(
                          tooltip: 'Clear library',
                          icon: Icon(Icons.delete_sweep_outlined,
                              color: context.dugan.textSecondary),
                          onPressed: () => _confirmClearAll(context),
                        ),
                    ],
                  ),
            body: Column(
              children: [
                if (!state.isSelecting)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(20, 4, 20, 12),
                    child: TextField(
                      controller: _searchController,
                      onChanged: (value) => context
                          .read<HistoryBloc>()
                          .add(HistorySearchChanged(value)),
                      decoration: const InputDecoration(
                        hintText: 'Search by title or platform…',
                        prefixIcon: Icon(Icons.search_rounded),
                        isDense: true,
                      ),
                    ),
                  ),
                Expanded(
                  child: items.isEmpty
                      ? EmptyState(
                          icon: Icons.video_library_outlined,
                          title: state.query.isEmpty
                              ? 'Your library is empty'
                              : 'No matches',
                          subtitle: state.query.isEmpty
                              ? 'Completed downloads are saved here with '
                                  'thumbnails, sizes and dates.'
                              : 'Try a different search term.',
                        )
                      : ListView.separated(
                          padding: const EdgeInsets.fromLTRB(20, 4, 20, 120),
                          itemCount: items.length,
                          separatorBuilder: (_, __) =>
                              const SizedBox(height: 10),
                          itemBuilder: (context, index) {
                            final item = items[index];
                            final selected =
                                state.selectedIds.contains(item.id);
                            return HistoryListItem(
                              item: item,
                              selected: selected,
                              onTap: () {
                                if (state.isSelecting) {
                                  context.read<HistoryBloc>().add(
                                        HistorySelectionToggled(item.id),
                                      );
                                } else {
                                  _play(context, item);
                                }
                              },
                              onLongPress: () => context
                                  .read<HistoryBloc>()
                                  .add(HistorySelectionToggled(item.id)),
                              onPlay: () => _play(context, item),
                              onShare: () =>
                                  Share.shareXFiles([XFile(item.filePath)]),
                              onRedownload: () => context
                                  .read<HistoryBloc>()
                                  .add(HistoryRedownloadRequested(item)),
                              onOpenWith: () => _openWith(context, item),
                              onDelete: () => context.read<HistoryBloc>().add(
                                    HistoryDeleteRequested(item.id),
                                  ),
                            );
                          },
                        ),
                ),
              ],
            ),
          );
        },
      );
  }

  void _play(BuildContext context, HistoryItem item) {
    Navigator.of(context).pushNamed(
      AppRoutes.player,
      arguments: PlayerScreenArgs(
        filePath: item.filePath,
        title: item.title,
        thumbnailUrl: item.thumbnailUrl,
      ),
    );
  }

  void _openWith(BuildContext context, HistoryItem item) {
    // Device-level service accessed through DI; opening a file with an
    // external app has no state worth routing through the bloc.
    getIt<SystemServices>().openFile(item.filePath);
  }

  void _showSortMenu(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: context.dugan.surfaceTertiary,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (sheetContext) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text('Sort by',
                    style: Theme.of(sheetContext).textTheme.titleMedium),
              ),
              for (final sort in HistorySort.values)
                ListTile(
                  leading: Icon(
                    switch (sort) {
                      HistorySort.date => Icons.schedule_rounded,
                      HistorySort.size => Icons.sd_storage_rounded,
                      HistorySort.duration => Icons.timer_outlined,
                      HistorySort.name => Icons.sort_by_alpha_rounded,
                    },
                  ),
                  title: Text(switch (sort) {
                    HistorySort.date => 'Newest first',
                    HistorySort.size => 'Largest first',
                    HistorySort.duration => 'Longest first',
                    HistorySort.name => 'Name (A–Z)',
                  }),
                  onTap: () {
                    sheetContext.read<HistoryBloc>().add(
                          HistorySortChanged(sort),
                        );
                    Navigator.of(sheetContext).pop();
                  },
                ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _confirmClearAll(BuildContext context) async {
    final bloc = context.read<HistoryBloc>();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Clear library?'),
        content: const Text(
          'Removes all entries from the library. The files on disk stay '
          'untouched.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Clear'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      bloc.add(HistoryClearAllRequested());
    }
  }
}

class _SelectionAppBar extends StatelessWidget implements PreferredSizeWidget {
  const _SelectionAppBar({required this.selectedCount});

  final int selectedCount;

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return AppBar(
      backgroundColor: colors.accentPrimary.withOpacity(0.12),
      leading: IconButton(
        icon: Icon(Icons.close_rounded, color: colors.textPrimary),
        onPressed: () =>
            context.read<HistoryBloc>().add(HistorySelectionCleared()),
      ),
      title: Text('$selectedCount selected'),
      actions: [
        IconButton(
          tooltip: 'Share selected',
          icon: Icon(Icons.share_rounded, color: colors.accentSecondary),
          onPressed: () {
            final state = context.read<HistoryBloc>().state;
            final files = state.items
                .where((item) => state.selectedIds.contains(item.id))
                .map((item) => XFile(item.filePath))
                .toList();
            Share.shareXFiles(files);
          },
        ),
        IconButton(
          tooltip: 'Delete selected',
          icon: Icon(Icons.delete_rounded, color: colors.error),
          onPressed: () =>
              context.read<HistoryBloc>().add(HistoryDeleteSelected()),
        ),
      ],
    );
  }
}
