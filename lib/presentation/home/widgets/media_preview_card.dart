import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../../../core/theme/context_x.dart';
import '../../../core/utils/formatters.dart';
import '../../../domain/entities/media_info.dart';

/// Compact parsed-media preview shown after the format sheet closes, with a
/// shortcut to re-open format selection.
class MediaPreviewCard extends StatelessWidget {
  const MediaPreviewCard({
    super.key,
    required this.media,
    required this.onChooseFormat,
    required this.onDismiss,
  });

  final MediaInfo media;
  final VoidCallback onChooseFormat;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return Stack(
      children: [
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: colors.surfaceSecondary.withOpacity(0.9),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: colors.glassBorder),
          ),
          child: Row(
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: SizedBox(
                  width: 108,
                  height: 62,
                  child: media.thumbnailUrl == null
                      ? ColoredBox(
                          color: colors.surfaceTertiary,
                          child: Icon(Icons.video_file_outlined,
                              color: colors.textSecondary),
                        )
                      : CachedNetworkImage(
                          imageUrl: media.thumbnailUrl!,
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
                    const SizedBox(height: 4),
                    Text(
                      [
                        if (media.uploader?.isNotEmpty == true) media.uploader!,
                        if (media.duration != null)
                          Formatters.duration(media.duration),
                        if (media.maxHeight > 0) 'up to ${media.maxHeight}p',
                      ].join(' · '),
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    const SizedBox(height: 8),
                    GestureDetector(
                      onTap: onChooseFormat,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 6,
                        ),
                        decoration: BoxDecoration(
                          gradient: colors.accentGradient,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Text(
                          'Choose format & download',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: colors.isDark
                                ? Colors.white
                                : Colors.white,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        Positioned(
          top: 4,
          right: 4,
          child: IconButton(
            visualDensity: VisualDensity.compact,
            icon: Icon(Icons.close_rounded,
                size: 18, color: colors.textSecondary),
            onPressed: onDismiss,
          ),
        ),
      ],
    );
  }
}
