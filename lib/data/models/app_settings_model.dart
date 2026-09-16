import '../../domain/entities/app_settings.dart';

class AppSettingsModel {
  const AppSettingsModel._();

  static AppSettings fromJson(Map<String, dynamic> json) {
    return AppSettings(
      themeMode: _themeModeFrom('${json['themeMode'] ?? 'dark'}'),
      gradientBackground: json['gradientBackground'] as bool? ?? true,
      accentIndex: (json['accentIndex'] as num?)?.toInt() ?? 0,
      defaultQuality: _qualityFrom('${json['defaultQuality'] ?? 'best'}'),
      downloadDirOverride: '${json['downloadDirOverride'] ?? ''}',
      maxConcurrentDownloads: (json['maxConcurrentDownloads'] as num?)?.toInt() ?? 2,
      wifiOnly: json['wifiOnly'] as bool? ?? false,
      saveToGallery: json['saveToGallery'] as bool? ?? false,
      customUserAgent: '${json['customUserAgent'] ?? ''}',
      cookies: '${json['cookies'] ?? ''}',
      customYtDlpArgs: '${json['customYtDlpArgs'] ?? ''}',
    );
  }

  static Map<String, dynamic> toJson(AppSettings settings) {
    return <String, dynamic>{
      'themeMode': settings.themeMode.name,
      'gradientBackground': settings.gradientBackground,
      'accentIndex': settings.accentIndex,
      'defaultQuality': settings.defaultQuality.name,
      'downloadDirOverride': settings.downloadDirOverride,
      'maxConcurrentDownloads': settings.maxConcurrentDownloads,
      'wifiOnly': settings.wifiOnly,
      'saveToGallery': settings.saveToGallery,
      'customUserAgent': settings.customUserAgent,
      'cookies': settings.cookies,
      'customYtDlpArgs': settings.customYtDlpArgs,
    };
  }

  static AppThemeMode _themeModeFrom(String value) {
    switch (value) {
      case 'system':
        return AppThemeMode.system;
      case 'light':
        return AppThemeMode.light;
      default:
        return AppThemeMode.dark;
    }
  }

  static QualityPreference _qualityFrom(String value) {
    switch (value) {
      case 'q2160':
        return QualityPreference.q2160;
      case 'q1080':
        return QualityPreference.q1080;
      case 'q720':
        return QualityPreference.q720;
      case 'q480':
        return QualityPreference.q480;
      case 'audio':
        return QualityPreference.audio;
      default:
        return QualityPreference.best;
    }
  }
}
