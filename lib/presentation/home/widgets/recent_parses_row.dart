import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../../../core/constants/platform_meta.dart';
import '../../../core/theme/context_x.dart';
import '../../../core/utils/formatters.dart';
import '../../../domain/entities/media_info.dart';

/// Horizontal rail of recently parsed media (not yet downloaded).
class RecentParsesRow extends StatelessWidget {
  const RecentParsesRow({super.key, required this.items, required this.onTap});

  final List<MediaInfo> items;
  final ValueChanged<MediaInfo> onTap;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 168,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: items.length,
        separatorBuilder: (_, __) => const SizedBox(width: 12),
        itemBuilder: (context, index) {
          final media = items[index];
          return _RecentParseCard(media: media, onTap: () => onTap(media));
        },
      ),
    );
  }
}

class _RecentParseCard extends StatelessWidget {
  const _RecentParseCard({required this.media, required this.onTap});

  final MediaInfo media;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    final platform = PlatformMeta.byId(media.platform);
    final bestSize =
        media.bestVideo?.effectiveFilesize ?? media.bestProgressive?.effectiveFilesize;
    final meta = [
      if (media.maxHeight > 0) '${media.maxHeight}p',
      if (bestSize != null) Formatters.bytes(bestSize),
    ].join(' · ');

    return Material(
      color: colors.surfaceSecondary.withOpacity(0.85),
      borderRadius: BorderRadius.circular(18),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(18),
        child: Container(
          width: 208,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: colors.glassBorder),
          ),
          clipBehavior: Clip.antiAlias,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                height: 96,
                width: double.infinity,
                child: _Thumbnail(url: media.thumbnailUrl),
              ),
              Padding(
                padding: const EdgeInsets.all(10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      media.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      meta.isEmpty ? platform.name : meta,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Thumbnail extends StatelessWidget {
  const _Thumbnail({required this.url});

  final String? url;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    if (url == null || url!.isEmpty) {
      return ColoredBox(
        color: colors.surfaceTertiary,
        child: Icon(
          Icons.video_file_outlined,
          size: 30,
          color: colors.textSecondary,
        ),
      );
    }
    return CachedNetworkImage(
      imageUrl: url!,
      fit: BoxFit.cover,
      memCacheWidth: 420,
      placeholder: (_, __) => ColoredBox(color: colors.surfaceTertiary),
      errorWidget: (_, __, ___) => ColoredBox(
        color: colors.surfaceTertiary,
        child: Icon(
          Icons.video_file_outlined,
          size: 30,
          color: colors.textSecondary,
        ),
      ),
    );
  }
}
