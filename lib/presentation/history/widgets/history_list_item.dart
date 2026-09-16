import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../../../core/constants/platform_meta.dart';
import '../../../core/theme/context_x.dart';
import '../../../core/utils/formatters.dart';
import '../../../domain/entities/history_item.dart';

/// A library row: thumbnail, title, meta line and a popup menu.
class HistoryListItem extends StatelessWidget {
  const HistoryListItem({
    super.key,
    required this.item,
    required this.selected,
    required this.onTap,
    required this.onLongPress,
    required this.onPlay,
    required this.onShare,
    required this.onRedownload,
    required this.onOpenWith,
    required this.onDelete,
  });

  final HistoryItem item;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final VoidCallback onPlay;
  final VoidCallback onShare;
  final VoidCallback onRedownload;
  final VoidCallback onOpenWith;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    final platform = PlatformMeta.byId(item.platform);
    return Material(
      color: selected
          ? colors.accentPrimary.withOpacity(0.14)
          : colors.surfaceSecondary.withOpacity(0.9),
      borderRadius: BorderRadius.circular(18),
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        borderRadius: BorderRadius.circular(18),
        child: Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
              color: selected ? colors.accentBorder : colors.glassBorder,
              width: selected ? 1.5 : 1,
            ),
          ),
          child: Row(
            children: [
              Stack(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(12),
                    child: SizedBox(
                      width: 108,
                      height: 64,
                      child: item.thumbnailUrl == null
                          ? ColoredBox(
                              color: colors.surfaceTertiary,
                              child: Icon(Icons.video_file_outlined,
                                  color: colors.textSecondary),
                            )
                          : CachedNetworkImage(
                              imageUrl: item.thumbnailUrl!,
                              fit: BoxFit.cover,
                              memCacheWidth: 320,
                              errorWidget: (_, __, ___) => ColoredBox(
                                color: colors.surfaceTertiary,
                                child: Icon(Icons.video_file_outlined,
                                    color: colors.textSecondary),
                              ),
                            ),
                    ),
                  ),
                  if (item.duration != null)
                    Positioned(
                      right: 4,
                      bottom: 4,
                      child: _OverlayLabel(
                        label: Formatters.duration(item.duration),
                      ),
                    ),
                ],
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      item.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      [
                        '${platform.emoji} ${platform.name}',
                        if (item.resolutionLabel != null)
                          item.resolutionLabel!,
                        if (item.sizeBytes > 0) Formatters.bytes(item.sizeBytes),
                        Formatters.relativeDate(item.completedAt),
                      ].join(' · '),
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              if (selected)
                Icon(Icons.check_circle_rounded, color: colors.accentPrimary)
              else
                PopupMenuButton<String>(
                  icon: Icon(Icons.more_vert_rounded,
                      color: colors.textSecondary),
                  onSelected: (value) {
                    switch (value) {
                      case 'play':
                        onPlay();
                      case 'share':
                        onShare();
                      case 'redownload':
                        onRedownload();
                      case 'open':
                        onOpenWith();
                      case 'delete':
                        onDelete();
                    }
                  },
                  itemBuilder: (context) => const [
                    PopupMenuItem(
                      value: 'play',
                      child: ListTile(
                        dense: true,
                        leading: Icon(Icons.play_arrow_rounded),
                        title: Text('Play'),
                      ),
                    ),
                    PopupMenuItem(
                      value: 'share',
                      child: ListTile(
                        dense: true,
                        leading: Icon(Icons.share_rounded),
                        title: Text('Share'),
                      ),
                    ),
                    PopupMenuItem(
                      value: 'redownload',
                      child: ListTile(
                        dense: true,
                        leading: Icon(Icons.refresh_rounded),
                        title: Text('Re-download'),
                      ),
                    ),
                    PopupMenuItem(
                      value: 'open',
                      child: ListTile(
                        dense: true,
                        leading: Icon(Icons.open_in_new_rounded),
                        title: Text('Open with…'),
                      ),
                    ),
                    PopupMenuItem(
                      value: 'delete',
                      child: ListTile(
                        dense: true,
                        leading: Icon(Icons.delete_outline_rounded),
                        title: Text('Remove from library'),
                      ),
                    ),
                  ],
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _OverlayLabel extends StatelessWidget {
  const _OverlayLabel({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: Colors.black.withOpacity(0.65),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        label,
        style: const TextStyle(
          fontSize: 10,
          fontWeight: FontWeight.w600,
          color: Colors.white,
        ),
      ),
    );
  }
}
