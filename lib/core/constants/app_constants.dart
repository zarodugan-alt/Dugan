/// App-wide constants: platform channel names and default values.
library;

class AppConstants {
  AppConstants._();

  // ── Platform channels ───────────────────────────────────────────────────
  static const engineChannel = 'app.dugan/engine';
  static const downloadsChannel = 'app.dugan/downloads';
  static const downloadEventsChannel = 'app.dugan/downloads/events';
  static const systemChannel = 'app.dugan/system';

  // ── Storage ─────────────────────────────────────────────────────────────
  static const settingsBox = 'dugan_settings';
  static const historyBox = 'dugan_history';
  static const recentParsesBox = 'dugan_recent_parses';

  // ── Behaviour ───────────────────────────────────────────────────────────
  static const maxRecentParses = 10;
  static const maxConcurrentDownloads = 5;
  static const maxAutoRetries = 3;
  static const appName = 'Dugan';
  static const appVersion = '1.0.0';
  static const sourceUrl =
      'https://github.com/zarodugan-alt/Dugan';
  static const releasesApiUrl =
      'https://api.github.com/repos/zarodugan-alt/Dugan/releases/latest';
}
