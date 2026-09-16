import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/theme/context_x.dart';
import '../../../core/utils/url_utils.dart';
import '../../../core/widgets/glass_card.dart';
import '../../../core/widgets/gradient_button.dart';

/// Glassmorphism URL input card with Paste + Parse actions.
class UrlInputCard extends StatelessWidget {
  const UrlInputCard({
    super.key,
    required this.controller,
    required this.onChanged,
    required this.onParse,
    required this.loading,
  });

  final TextEditingController controller;
  final ValueChanged<String> onChanged;
  final VoidCallback onParse;
  final bool loading;

  Future<void> _pasteFromClipboard(BuildContext context) async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text ?? '';
    final url = UrlUtils.extractUrl(text);
    if (url != null) {
      controller.text = url;
      controller.selection = TextSelection.fromPosition(
        TextPosition(offset: controller.text.length),
      );
      onChanged(url);
      // Auto-parse when the clipboard clearly holds a media link.
      onParse();
    } else if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No link found in the clipboard')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return GlassCard(
      padding: const EdgeInsets.all(6),
      child: Column(
        children: [
          TextField(
            controller: controller,
            onChanged: onChanged,
            enabled: !loading,
            keyboardType: TextInputType.url,
            textInputAction: TextInputAction.go,
            onSubmitted: (_) => onParse(),
            style: Theme.of(context).textTheme.bodyMedium,
            decoration: InputDecoration(
              hintText: 'Paste video URL here…',
              prefixIcon: Icon(
                Icons.link_rounded,
                color: colors.accentSecondary,
              ),
              suffixIcon: ValueListenableBuilder<TextEditingValue>(
                valueListenable: controller,
                builder: (context, value, _) {
                  if (value.text.isEmpty) {
                    return const SizedBox.shrink();
                  }
                  return IconButton(
                    icon: Icon(Icons.close_rounded,
                        size: 18, color: colors.textSecondary),
                    onPressed: () {
                      controller.clear();
                      onChanged('');
                    },
                  );
                },
              ),
            ),
          ),
          const SizedBox(height: 6),
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 0, 12, 6),
            child: Row(
              children: [
                _GhostPill(
                  icon: Icons.content_paste_rounded,
                  label: 'Paste',
                  onTap: loading ? null : () => _pasteFromClipboard(context),
                ),
                const Spacer(),
                GradientPillButton(
                  label: loading ? 'Parsing…' : 'Parse',
                  icon: loading ? null : Icons.search_rounded,
                  onPressed: loading
                      ? null
                      : () {
                          FocusScope.of(context).unfocus();
                          onParse();
                        },
                ),
              ],
            ),
          ),
          if (loading)
            const Padding(
              padding: EdgeInsets.only(bottom: 8),
              child: LinearProgressIndicator(minHeight: 2),
            ),
        ],
      ),
    );
  }
}

class _GhostPill extends StatelessWidget {
  const _GhostPill({
    required this.icon,
    required this.label,
    this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return Material(
      color: colors.surfaceTertiary,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 16, color: colors.textSecondary),
              const SizedBox(width: 6),
              Text(
                label,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: colors.textSecondary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
