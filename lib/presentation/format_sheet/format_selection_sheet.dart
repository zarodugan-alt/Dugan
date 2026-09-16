import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../core/theme/context_x.dart';
import '../../core/utils/formatters.dart';
import '../../core/widgets/gradient_button.dart';
import '../../domain/entities/app_settings.dart';
import '../../domain/entities/download_request.dart';
import '../../domain/entities/format_option.dart';
import '../../domain/entities/media_info.dart';
import '../cubit/format_selection/format_selection_cubit.dart';
import '../cubit/format_selection/format_selection_state.dart';

/// Full-width modal bottom sheet (~85% height) for format selection:
/// Video tab, Audio tab and Subtitles/Options tab, plus a sticky CTA.
Future<void> showFormatSelectionSheet({
  required BuildContext context,
  required MediaInfo media,
  required AppSettings settings,
  required void Function(DownloadRequest request) onDownload,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    backgroundColor: Colors.transparent,
    builder: (sheetContext) {
      return BlocProvider(
        create: (_) => FormatSelectionCubit(media: media, settings: settings),
        child: FormatSelectionSheet(onDownload: onDownload),
      );
    },
  );
}

class FormatSelectionSheet extends StatefulWidget {
  const FormatSelectionSheet({super.key, required this.onDownload});

  final void Function(DownloadRequest request) onDownload;

  @override
  State<FormatSelectionSheet> createState() => _FormatSelectionSheetState();
}

class _FormatSelectionSheetState extends State<FormatSelectionSheet>
    with SingleTickerProviderStateMixin {
  late final TabController _tabController = TabController(length: 3, vsync: this);

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    final height = MediaQuery.of(context).size.height;

    return Container(
      height: height * 0.85,
      decoration: BoxDecoration(
        color: colors.surfaceTertiary,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          children: [
            // Drag handle.
            Container(
              margin: const EdgeInsets.only(top: 10),
              width: 44,
              height: 4,
              decoration: BoxDecoration(
                color: colors.glassBorder,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const _SheetHeader(),
            TabBar(
              controller: _tabController,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              tabs: const [
                Tab(text: 'Video'),
                Tab(text: 'Audio'),
                Tab(text: 'Subtitles & More'),
              ],
            ),
            Expanded(
              child: TabBarView(
                controller: _tabController,
                children: const [
                  _VideoTab(),
                  _AudioTab(),
                  _OptionsTab(),
                ],
              ),
            ),
            _CtaBar(onDownload: widget.onDownload),
          ],
        ),
      ),
    );
  }
}

class _SheetHeader extends StatelessWidget {
  const _SheetHeader();

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    final media = context.select(
      (FormatSelectionCubit cubit) => cubit.state.media,
    );
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 14, 12, 10),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: SizedBox(
              width: 56,
              height: 56,
              child: media.thumbnailUrl == null
                  ? ColoredBox(
                      color: colors.surfaceSecondary,
                      child: Icon(Icons.video_file_outlined,
                          color: colors.textSecondary),
                    )
                  : CachedNetworkImage(
                      imageUrl: media.thumbnailUrl!,
                      fit: BoxFit.cover,
                      memCacheWidth: 240,
                      errorWidget: (_, __, ___) => ColoredBox(
                        color: colors.surfaceSecondary,
                        child: Icon(Icons.video_file_outlined,
                            color: colors.textSecondary),
                      ),
                    ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  media.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 2),
                Text(
                  [
                    if (media.uploader?.isNotEmpty == true) media.uploader!,
                    if (media.duration != null)
                      Formatters.duration(media.duration),
                  ].join(' · '),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
          IconButton(
            icon: Icon(Icons.close_rounded, color: colors.textSecondary),
            onPressed: () => Navigator.of(context).pop(),
          ),
        ],
      ),
    );
  }
}

// ── Video tab ──────────────────────────────────────────────────────────────

class _VideoTab extends StatelessWidget {
  const _VideoTab();

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return BlocBuilder<FormatSelectionCubit, FormatSelectionState>(
      buildWhen: (previous, current) =>
          previous.videoFormatId != current.videoFormatId ||
          previous.showGrid != current.showGrid ||
          previous.mergeWithBestAudio != current.mergeWithBestAudio,
      builder: (context, state) {
        final formats = state.media.formatsByResolution;
        return Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 4, 12, 4),
              child: Row(
                children: [
                  Expanded(
                    child: _ToggleRow(
                      label: 'Merge with best audio',
                      value: state.mergeWithBestAudio,
                      onChanged: (_) => context
                          .read<FormatSelectionCubit>()
                          .toggleMergeWithBestAudio(),
                    ),
                  ),
                  IconButton(
                    tooltip: state.showGrid ? 'List view' : 'Grid view',
                    icon: Icon(
                      state.showGrid
                          ? Icons.view_list_rounded
                          : Icons.grid_view_rounded,
                      color: colors.textSecondary,
                      size: 20,
                    ),
                    onPressed: () => context
                        .read<FormatSelectionCubit>()
                        .toggleLayout(),
                  ),
                ],
              ),
            ),
            Expanded(
              child: formats.isEmpty
                  ? Center(
                      child: Text(
                        'No video formats available\ntry the Audio tab',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    )
                  : state.showGrid
                      ? GridView.builder(
                          padding: const EdgeInsets.fromLTRB(20, 4, 20, 20),
                          gridDelegate:
                              const SliverGridDelegateWithMaxCrossAxisExtent(
                            maxCrossAxisExtent: 200,
                            mainAxisSpacing: 10,
                            crossAxisSpacing: 10,
                            childAspectRatio: 1.45,
                          ),
                          itemCount: formats.length,
                          itemBuilder: (context, index) => FormatCard(
                            format: formats[index],
                            selected:
                                formats[index].formatId == state.videoFormatId,
                            onTap: () => context
                                .read<FormatSelectionCubit>()
                                .selectVideo(formats[index].formatId),
                          ),
                        )
                      : ListView.separated(
                          padding: const EdgeInsets.fromLTRB(20, 4, 20, 20),
                          itemCount: formats.length,
                          separatorBuilder: (_, __) => const SizedBox(height: 10),
                          itemBuilder: (context, index) => FormatCard(
                            format: formats[index],
                            selected:
                                formats[index].formatId == state.videoFormatId,
                            onTap: () => context
                                .read<FormatSelectionCubit>()
                                .selectVideo(formats[index].formatId),
                          ),
                        ),
            ),
          ],
        );
      },
    );
  }
}

class FormatCard extends StatelessWidget {
  const FormatCard({
    super.key,
    required this.format,
    required this.selected,
    required this.onTap,
  });

  final FormatOption format;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    final size = format.effectiveFilesize;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: selected
              ? colors.accentPrimary.withOpacity(0.14)
              : colors.surfaceSecondary.withOpacity(0.9),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: selected ? colors.accentBorder : colors.glassBorder,
            width: selected ? 1.6 : 1,
          ),
          boxShadow: selected ? colors.accentGlow : const <BoxShadow>[],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    format.label,
                    style: Theme.of(context).textTheme.titleSmall!.copyWith(
                          color: selected ? colors.textPrimary : colors.textPrimary,
                        ),
                  ),
                ),
                if (selected)
                  Icon(Icons.check_circle_rounded,
                      size: 18, color: colors.accentPrimary),
              ],
            ),
            const SizedBox(height: 6),
            Wrap(
              spacing: 6,
              runSpacing: 4,
              children: [
                _MiniBadge(label: format.codecLabel),
                if (format.isProgressive) const _MiniBadge(label: 'A+V'),
                if (format.fps != null && format.fps! > 0)
                  _MiniBadge(label: '${format.fps}fps'),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              size != null ? Formatters.bytes(size) : 'size unknown',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
    );
  }
}

class _MiniBadge extends StatelessWidget {
  const _MiniBadge({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: colors.surfaceTertiary,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 10,
          fontWeight: FontWeight.w600,
          color: colors.textSecondary,
        ),
      ),
    );
  }
}

// ── Audio tab ──────────────────────────────────────────────────────────────

class _AudioTab extends StatelessWidget {
  const _AudioTab();

  static const _containers = ['mp3', 'm4a', 'opus', 'flac', 'wav'];

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return BlocBuilder<FormatSelectionCubit, FormatSelectionState>(
      builder: (context, state) {
        final cubit = context.read<FormatSelectionCubit>();
        return ListView(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 20),
          children: [
            _ToggleRow(
              label: 'Download audio only',
              value: state.audioOnly,
              onChanged: (value) => cubit.setAudioOnly(value ?? false),
              trailing: Switch(
                value: state.audioOnly,
                onChanged: (value) => cubit.setAudioOnly(value),
              ),
            ),
            const SizedBox(height: 16),
            Text('Convert to', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final container in _containers)
                  _ContainerChip(
                    label: container.toUpperCase(),
                    selected: state.audioContainer == container,
                    onTap: () => cubit.setAudioContainer(container),
                  ),
              ],
            ),
            const SizedBox(height: 20),
            Text(
              'Bitrate — ${state.audioBitrateKbps} kbps',
              style: Theme.of(context).textTheme.titleSmall,
            ),
            Slider(
              value: state.audioBitrateKbps.toDouble(),
              min: 64,
              max: 320,
              divisions: ((320 - 64) / 32).round(),
              label: '${state.audioBitrateKbps} kbps',
              onChanged: (value) => cubit.setAudioBitrate(value.round()),
            ),
            const SizedBox(height: 8),
            _ToggleRow(
              label: 'Embed metadata',
              value: state.embedMetadata,
              onChanged: (_) => cubit.toggleEmbedMetadata(),
              trailing: Switch(
                value: state.embedMetadata,
                onChanged: (_) => cubit.toggleEmbedMetadata(),
              ),
            ),
            _ToggleRow(
              label: 'Embed thumbnail (cover art)',
              value: state.embedThumbnail,
              onChanged: (_) => cubit.toggleEmbedThumbnail(),
              trailing: Switch(
                value: state.embedThumbnail,
                onChanged: (_) => cubit.toggleEmbedThumbnail(),
              ),
            ),
            const SizedBox(height: 12),
            Text('Source audio streams',
                style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 10),
            if (state.media.audioFormats.isEmpty)
              Text(
                'No separate audio streams found.',
                style: Theme.of(context).textTheme.bodySmall,
              )
            else
              ...state.media.audioFormats.map(
                (format) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: FormatCard(
                    format: format,
                    selected:
                        !state.audioOnly && format.formatId == state.audioFormatId,
                    onTap: () => cubit.selectAudio(format.formatId),
                  ),
                ),
              ),
            if (colors.isDark) const SizedBox(height: 8),
          ],
        );
      },
    );
  }
}

class _ContainerChip extends StatelessWidget {
  const _ContainerChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: selected
              ? colors.accentPrimary.withOpacity(0.2)
              : colors.surfaceSecondary,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: selected ? colors.accentBorder : colors.glassBorder,
          ),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: selected ? colors.accentSecondary : colors.textSecondary,
          ),
        ),
      ),
    );
  }
}

// ── Subtitles & options tab ────────────────────────────────────────────────

class _OptionsTab extends StatefulWidget {
  const _OptionsTab();

  @override
  State<_OptionsTab> createState() => _OptionsTabState();
}

class _OptionsTabState extends State<_OptionsTab> {
  late final TextEditingController _filenameController =
      TextEditingController(
    text: context.read<FormatSelectionCubit>().state.filenameTemplate,
  );

  @override
  void dispose() {
    _filenameController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cubit = context.read<FormatSelectionCubit>();
    return BlocBuilder<FormatSelectionCubit, FormatSelectionState>(
      buildWhen: (previous, current) =>
          previous.subtitleLanguages != current.subtitleLanguages ||
          previous.embedSubtitles != current.embedSubtitles,
      builder: (context, state) {
        return ListView(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 20),
          children: [
            Text('Subtitles', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 10),
            if (state.media.subtitles.isEmpty)
              Text(
                'No subtitles available for this media.',
                style: Theme.of(context).textTheme.bodySmall,
              )
            else
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final track in state.media.subtitles)
                    _ContainerChip(
                      label: track.displayLabel,
                      selected:
                          state.subtitleLanguages.contains(track.language),
                      onTap: () =>
                          cubit.toggleSubtitleLanguage(track.language),
                    ),
                ],
              ),
            const SizedBox(height: 12),
            _ToggleRow(
              label: 'Embed subtitles into the file',
              value: state.embedSubtitles,
              onChanged: (_) => cubit.toggleEmbedSubtitles(),
              trailing: Switch(
                value: state.embedSubtitles,
                onChanged: (_) => cubit.toggleEmbedSubtitles(),
              ),
            ),
            const Divider(height: 32),
            Text('Output', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 10),
            TextField(
              controller: _filenameController,
              onChanged: cubit.filenameChanged,
              style: Theme.of(context).textTheme.bodySmall,
              decoration: const InputDecoration(
                labelText: 'Filename template',
                helperText:
                    'yt-dlp template — e.g. %(title)s [%(id)s].%(ext)s',
                isDense: true,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'The download folder can be changed in Settings → Downloads.',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        );
      },
    );
  }
}

// ── Shared bits ────────────────────────────────────────────────────────────

class _ToggleRow extends StatelessWidget {
  const _ToggleRow({
    required this.label,
    required this.value,
    required this.onChanged,
    this.trailing,
  });

  final String label;
  final bool value;
  final ValueChanged<bool?> onChanged;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(label, style: Theme.of(context).textTheme.titleSmall),
        ),
        trailing ??
            Switch(value: value, onChanged: onChanged),
      ],
    );
  }
}

class _CtaBar extends StatelessWidget {
  const _CtaBar({required this.onDownload});

  final void Function(DownloadRequest request) onDownload;

  void _download(BuildContext context) {
    final cubit = context.read<FormatSelectionCubit>();
    if (!cubit.state.canDownload) {
      return;
    }
    final request = cubit.buildRequest();
    Navigator.of(context).pop();
    onDownload(request);
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return BlocBuilder<FormatSelectionCubit, FormatSelectionState>(
      buildWhen: (previous, current) =>
          previous.canDownload != current.canDownload ||
          previous.downloadSummary != current.downloadSummary ||
          previous.estimatedSizeBytes != current.estimatedSizeBytes ||
          previous.validationHint != current.validationHint,
      builder: (context, state) {
        final estimate = state.estimatedSizeBytes;
        return Container(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 12),
          decoration: BoxDecoration(
            color: colors.surfaceTertiary,
            border: Border(
              top: BorderSide(color: colors.glassBorder),
            ),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Flexible(
                    child: Text(
                      [
                        state.downloadSummary,
                        if (estimate != null) '~${Formatters.bytes(estimate)}',
                      ].join(' · '),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall!.copyWith(
                            color: state.canDownload
                                ? colors.textSecondary
                                : colors.warning,
                          ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              GradientButton(
                label: 'Download',
                icon: Icons.download_rounded,
                onPressed:
                    state.canDownload ? () => _download(context) : null,
              ),
            ],
          ),
        );
      },
    );
  }
}
