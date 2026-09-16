import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:permission_handler/permission_handler.dart';

import '../../../core/constants/app_constants.dart';
import '../../../core/di/injection.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/context_x.dart';
import '../../../data/datasources/update_checker.dart';
import '../../../domain/entities/app_settings.dart';
import '../../cubit/settings/settings_cubit.dart';
import '../../cubit/settings/settings_state.dart';

/// Settings screen sections, split into widgets for readability.

class _SectionShell extends StatelessWidget {
  const _SectionShell({required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      decoration: BoxDecoration(
        color: colors.surfaceSecondary.withOpacity(0.85),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: colors.glassBorder),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(top: 12, bottom: 4),
            child: Text(
              title.toUpperCase(),
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                letterSpacing: 1.0,
                color: colors.accentSecondary,
              ),
            ),
          ),
          ...children,
        ],
      ),
    );
  }
}

class _SettingsTile extends StatelessWidget {
  const _SettingsTile({
    required this.leading,
    required this.title,
    this.subtitle,
    this.trailing,
    this.onTap,
  });

  final IconData leading;
  final String title;
  final String? subtitle;
  final Widget? trailing;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(leading),
      title: Text(title),
      subtitle: subtitle == null
          ? null
          : Text(subtitle!, style: Theme.of(context).textTheme.bodySmall),
      trailing: trailing,
      onTap: onTap,
      contentPadding: EdgeInsets.zero,
    );
  }
}

// ── Appearance ─────────────────────────────────────────────────────────────

class AppearanceSection extends StatelessWidget {
  const AppearanceSection();

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    final settings = context.select(
      (SettingsCubit cubit) => cubit.state.settings,
    );
    return _SectionShell(
      title: 'Appearance',
      children: [
        ListTile(
          contentPadding: EdgeInsets.zero,
          leading: const Icon(Icons.dark_mode_outlined),
          title: const Text('Theme mode'),
          subtitle: Padding(
            padding: const EdgeInsets.only(top: 8),
            child: SegmentedButton<AppThemeMode>(
              segments: const [
                ButtonSegment(
                  value: AppThemeMode.system,
                  icon: Icon(Icons.settings_suggest_outlined, size: 16),
                  label: Text('System'),
                ),
                ButtonSegment(
                  value: AppThemeMode.dark,
                  icon: Icon(Icons.dark_mode_outlined, size: 16),
                  label: Text('Dark'),
                ),
                ButtonSegment(
                  value: AppThemeMode.light,
                  icon: Icon(Icons.light_mode_outlined, size: 16),
                  label: Text('Light'),
                ),
              ],
              selected: {settings.themeMode},
              onSelectionChanged: (selection) => context
                  .read<SettingsCubit>()
                  .setThemeMode(selection.first),
            ),
          ),
        ),
        _SettingsTile(
          leading: Icons.gradient_outlined,
          title: 'Gradient background',
          subtitle: 'Soft accent glows behind screens',
          trailing: Switch(
            value: settings.gradientBackground,
            onChanged: (value) => context
                .read<SettingsCubit>()
                .setGradientBackground(value),
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Accent color', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 12),
              SizedBox(
                height: 44,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  itemCount: kAccentOptions.length,
                  separatorBuilder: (_, __) => const SizedBox(width: 12),
                  itemBuilder: (context, index) {
                    final accent = kAccentOptions[index];
                    final selected = settings.accentIndex == index;
                    return GestureDetector(
                      onTap: () => context
                          .read<SettingsCubit>()
                          .setAccentIndex(index),
                      child: Container(
                        width: 44,
                        height: 44,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: LinearGradient(
                            colors: [accent.color, accent.secondary],
                          ),
                          border: Border.all(
                            color: selected
                                ? colors.textPrimary
                                : Colors.transparent,
                            width: 2.5,
                          ),
                        ),
                        child: selected
                            ? const Icon(Icons.check_rounded,
                                color: Colors.white)
                            : null,
                      ),
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

// ── Downloads ──────────────────────────────────────────────────────────────

class DownloadsSection extends StatelessWidget {
  const DownloadsSection();

  Future<void> _pickDirectory(BuildContext context) async {
    final cubit = context.read<SettingsCubit>();
    final picked = await FilePicker.platform.getDirectoryPath();
    if (picked == null || !context.mounted) {
      return;
    }
    // Custom locations outside app storage need All-Files-Access on
    // Android 11+.
    final status = await Permission.manageExternalStorage.request();
    if (status.isGranted) {
      await cubit.setDownloadDir(picked);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Downloads will be saved to $picked')),
        );
      }
    } else if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Permission denied — keeping the default app folder. '
            'Enable “All files access” for Dugan to use that folder.',
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    final settings = context.select(
      (SettingsCubit cubit) => cubit.state.settings,
    );
    return _SectionShell(
      title: 'Downloads',
      children: [
        ListTile(
          contentPadding: EdgeInsets.zero,
          leading: const Icon(Icons.tune_rounded),
          title: const Text('Default quality'),
          subtitle: Text(
            settings.defaultQuality.label,
            style: Theme.of(context).textTheme.bodySmall,
          ),
          trailing: DropdownButton<QualityPreference>(
            value: settings.defaultQuality,
            dropdownColor: colors.surfaceTertiary,
            underline: const SizedBox.shrink(),
            items: [
              for (final quality in QualityPreference.values)
                DropdownMenuItem(
                  value: quality,
                  child: Text(
                    quality.label,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ),
            ],
            onChanged: (value) {
              if (value != null) {
                context.read<SettingsCubit>().setDefaultQuality(value);
              }
            },
          ),
        ),
        _SettingsTile(
          leading: Icons.folder_outlined,
          title: 'Download path',
          subtitle: settings.downloadDirOverride.isEmpty
              ? 'App folder → Dugan/Downloads'
              : settings.downloadDirOverride,
          trailing: TextButton(
            onPressed: () => _pickDirectory(context),
            child: const Text('Change'),
          ),
        ),
        ListTile(
          contentPadding: EdgeInsets.zero,
          leading: const Icon(Icons.speed_rounded),
          title: Text(
              'Concurrent downloads — ${settings.maxConcurrentDownloads}'),
          subtitle: Slider(
            value: settings.maxConcurrentDownloads.toDouble(),
            min: 1,
            max: 5,
            divisions: 4,
            label: '${settings.maxConcurrentDownloads}',
            onChanged: (value) => context
                .read<SettingsCubit>()
                .setMaxConcurrentDownloads(value.round()),
          ),
        ),
        _SettingsTile(
          leading: Icons.wifi_rounded,
          title: 'Wi-Fi only',
          subtitle: 'Pause downloads on metered connections',
          trailing: Switch(
            value: settings.wifiOnly,
            onChanged: (value) =>
                context.read<SettingsCubit>().setWifiOnly(value),
          ),
        ),
        _SettingsTile(
          leading: Icons.photo_library_outlined,
          title: 'Save to gallery',
          subtitle: 'Copy finished videos to the system gallery',
          trailing: Switch(
            value: settings.saveToGallery,
            onChanged: (value) => context
                .read<SettingsCubit>()
                .setSaveToGallery(value),
          ),
        ),
      ],
    );
  }
}

// ── Advanced ───────────────────────────────────────────────────────────────

class AdvancedSection extends StatefulWidget {
  const AdvancedSection();

  @override
  State<AdvancedSection> createState() => _AdvancedSectionState();
}

class _AdvancedSectionState extends State<AdvancedSection> {
  late final TextEditingController _userAgentController;
  late final TextEditingController _argsController;

  @override
  void initState() {
    super.initState();
    final settings = context.read<SettingsCubit>().state.settings;
    _userAgentController =
        TextEditingController(text: settings.customUserAgent);
    _argsController =
        TextEditingController(text: settings.customYtDlpArgs);
  }

  @override
  void didUpdateWidget(AdvancedSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    final settings = context.read<SettingsCubit>().state.settings;
    if (_userAgentController.text != settings.customUserAgent &&
        settings.customUserAgent.isNotEmpty) {
      _userAgentController.text = settings.customUserAgent;
    }
  }

  @override
  void dispose() {
    _userAgentController.dispose();
    _argsController.dispose();
    super.dispose();
  }

  Future<void> _importCookiesDialog(BuildContext context) async {
    final controller = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Import cookies'),
        content: TextField(
          controller: controller,
          maxLines: 6,
          decoration: const InputDecoration(
            hintText: 'Paste Netscape-format cookie text…',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Import'),
          ),
        ],
      ),
    );
    if (confirmed == true && context.mounted) {
      await context.read<SettingsCubit>().setCookies(controller.text.trim());
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Cookie jar updated')),
        );
      }
    }
    controller.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final settings = context.select(
      (SettingsCubit cubit) => cubit.state.settings,
    );
    return _SectionShell(
      title: 'Advanced',
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Custom User-Agent',
                  style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 8),
              TextField(
                controller: _userAgentController,
                style: Theme.of(context).textTheme.bodySmall,
                decoration: const InputDecoration(
                  isDense: true,
                  hintText: 'Leave empty for the default yt-dlp agent',
                ),
                onSubmitted: (value) => context
                    .read<SettingsCubit>()
                    .setCustomUserAgent(value.trim()),
              ),
            ],
          ),
        ),
        _SettingsTile(
          leading: Icons.cookie_outlined,
          title: 'Cookies (Netscape format)',
          subtitle: settings.cookies.isEmpty
              ? 'No session cookies saved'
              : 'Session saved — ${settings.cookies.split('\n').where((l) => l.isNotEmpty && !l.startsWith('#')).length} cookies',
          trailing: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              IconButton(
                tooltip: 'Import from text',
                icon: const Icon(Icons.content_paste_rounded, size: 20),
                onPressed: () => _importCookiesDialog(context),
              ),
              IconButton(
                tooltip: 'Clear',
                icon: const Icon(Icons.delete_outline_rounded, size: 20),
                onPressed: () async {
                  await context.read<SettingsCubit>().setCookies('');
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Cookies cleared')),
                    );
                  }
                },
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Custom yt-dlp arguments',
                  style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 8),
              TextField(
                controller: _argsController,
                maxLines: 3,
                style: Theme.of(context).textTheme.bodySmall,
                decoration: const InputDecoration(
                  isDense: true,
                  hintText: 'e.g. --retries 5 --no-check-certificates',
                ),
                onSubmitted: (value) => context
                    .read<SettingsCubit>()
                    .setCustomYtDlpArgs(value.trim()),
              ),
              const SizedBox(height: 6),
              Text(
                'Applied to extractions and downloads; only an allow-list '
                'of options is honoured by the engine.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 8),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  onPressed: () => context
                      .read<SettingsCubit>()
                      .setCustomYtDlpArgs(_argsController.text.trim()),
                  child: const Text('Apply'),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

// ── About ──────────────────────────────────────────────────────────────────

class AboutSection extends StatelessWidget {
  const AboutSection();

  Future<void> _checkForUpdates(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    messenger.showSnackBar(
      const SnackBar(content: Text('Checking for updates…')),
    );
    final latest = await getIt<UpdateChecker>().latestVersion();
    if (!context.mounted) {
      return;
    }
    final messenger2 = ScaffoldMessenger.of(context);
    messenger2.hideCurrentSnackBar();
    messenger2.showSnackBar(
      SnackBar(
        content: Text(
          latest == null
              ? 'Could not reach the update service.'
              : 'Latest release: $latest (installed ${AppConstants.appVersion})',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return _SectionShell(
      title: 'About',
      children: [
        _SettingsTile(
          leading: Icons.info_outline_rounded,
          title: 'Version',
          subtitle: '${AppConstants.appVersion} · powered by yt-dlp',
        ),
        _SettingsTile(
          leading: Icons.description_outlined,
          title: 'Open source licenses',
          onTap: () => showLicensePage(
            context: context,
            applicationName: AppConstants.appName,
            applicationVersion: AppConstants.appVersion,
          ),
        ),
        _SettingsTile(
          leading: Icons.update_rounded,
          title: 'Check for updates',
          onTap: () => _checkForUpdates(context),
        ),
      ],
    );
  }
}
