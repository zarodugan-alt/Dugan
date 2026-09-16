import 'package:equatable/equatable.dart';

/// Theme preference (kept free of Material imports in the domain layer).
enum AppThemeMode { system, dark, light }

/// Default quality used by the "Best Quality" tile and the initial
/// selection of the format sheet.
enum QualityPreference {
  best,
  q2160,
  q1080,
  q720,
  q480,
  audio;

  String get label {
    switch (this) {
      case QualityPreference.best:
        return 'Best available';
      case QualityPreference.q2160:
        return 'Up to 4K (2160p)';
      case QualityPreference.q1080:
        return 'Up to 1080p';
      case QualityPreference.q720:
        return 'Up to 720p';
      case QualityPreference.q480:
        return 'Up to 480p';
      case QualityPreference.audio:
        return 'Audio only';
    }
  }

  /// Height cap in pixels (null = uncapped).
  int? get maxHeight {
    switch (this) {
      case QualityPreference.q2160:
        return 2160;
      case QualityPreference.q1080:
        return 1080;
      case QualityPreference.q720:
        return 720;
      case QualityPreference.q480:
        return 480;
      default:
        return null;
    }
  }
}

/// User preferences persisted locally.
class AppSettings extends Equatable {
  const AppSettings({
    this.themeMode = AppThemeMode.dark,
    this.gradientBackground = true,
    this.accentIndex = 0,
    this.defaultQuality = QualityPreference.best,
    this.downloadDirOverride = '',
    this.maxConcurrentDownloads = 2,
    this.wifiOnly = false,
    this.saveToGallery = false,
    this.customUserAgent = '',
    this.cookies = '',
    this.customYtDlpArgs = '',
  });

  /// Theme mode (dark is the flagship experience).
  final AppThemeMode themeMode;

  /// Paint the animated gradient backdrop behind screens (OLED-black
  /// otherwise).
  final bool gradientBackground;

  /// Index into the accent preset list.
  final int accentIndex;

  final QualityPreference defaultQuality;

  /// Empty → app-specific default directory.
  final String downloadDirOverride;

  /// 1..5 concurrent downloads.
  final int maxConcurrentDownloads;

  final bool wifiOnly;

  /// Copy finished videos into the system gallery.
  final bool saveToGallery;

  final String customUserAgent;

  /// Netscape-format cookie jar (imported via WebView login or file).
  final String cookies;

  /// Extra raw yt-dlp arguments, allow-listed by the native bridge.
  final String customYtDlpArgs;

  static const AppSettings defaults = AppSettings();

  AppSettings copyWith({
    AppThemeMode? themeMode,
    bool? gradientBackground,
    int? accentIndex,
    QualityPreference? defaultQuality,
    String? downloadDirOverride,
    int? maxConcurrentDownloads,
    bool? wifiOnly,
    bool? saveToGallery,
    String? customUserAgent,
    String? cookies,
    String? customYtDlpArgs,
  }) {
    return AppSettings(
      themeMode: themeMode ?? this.themeMode,
      gradientBackground: gradientBackground ?? this.gradientBackground,
      accentIndex: accentIndex ?? this.accentIndex,
      defaultQuality: defaultQuality ?? this.defaultQuality,
      downloadDirOverride: downloadDirOverride ?? this.downloadDirOverride,
      maxConcurrentDownloads:
          maxConcurrentDownloads ?? this.maxConcurrentDownloads,
      wifiOnly: wifiOnly ?? this.wifiOnly,
      saveToGallery: saveToGallery ?? this.saveToGallery,
      customUserAgent: customUserAgent ?? this.customUserAgent,
      cookies: cookies ?? this.cookies,
      customYtDlpArgs: customYtDlpArgs ?? this.customYtDlpArgs,
    );
  }

  @override
  List<Object?> get props => [
        themeMode,
        gradientBackground,
        accentIndex,
        defaultQuality,
        downloadDirOverride,
        maxConcurrentDownloads,
        wifiOnly,
        saveToGallery,
        customUserAgent,
        cookies,
        customYtDlpArgs,
      ];
}
