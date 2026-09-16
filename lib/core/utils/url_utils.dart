import '../constants/platform_meta.dart' show PlatformMeta;

/// URL helpers: validation, clipboard extraction and platform detection.
enum UrlUtils {
  ;

  static final RegExp _bareUrl = RegExp(
    r'^https?://[^\s/$.?#].[^\s]*$',
    caseSensitive: false,
  );

  static final RegExp _embeddedUrl = RegExp(
    r'https?://[^\s<>"\']+',
    caseSensitive: false,
  );

  /// Whether the string is, on its face, a link we can hand to yt-dlp.
  static bool isProbablyUrl(String input) {
    final trimmed = input.trim();
    if (trimmed.isEmpty || trimmed.length > 2048) {
      return false;
    }
    return _bareUrl.hasMatch(trimmed);
  }

  /// Finds the first URL inside an arbitrary blob of clipboard text.
  static String? extractUrl(String text) {
    if (text.trim().isEmpty) {
      return null;
    }
    final direct = text.trim();
    if (isProbablyUrl(direct)) {
      return direct;
    }
    final match = _embeddedUrl.firstMatch(text);
    return match?.group(0);
  }

  /// Platform metadata for a link (badge name, emoji, login host).
  static PlatformMeta platformOf(String url) => PlatformMeta.fromUrl(url);

  /// Demo/sample links auto-filled by the platform shortcut chips.
  static String sampleUrlFor(PlatformMeta platform) {
    switch (platform.id) {
      case 'youtube':
        return 'https://www.youtube.com/watch?v=aqz-KE-bpKQ';
      case 'tiktok':
        return 'https://www.tiktok.com/@tiktok/video/7106594312292453675';
      case 'instagram':
        return 'https://www.instagram.com/p/C1YkQQtLLg3/';
      case 'twitter':
        return 'https://x.com/SpaceX/status/1732824684683784515';
      case 'bilibili':
        return 'https://www.bilibili.com/video/BV1GJ411x7h7';
      case 'facebook':
        return 'https://www.facebook.com/watch/?v=1093831157687536';
      case 'reddit':
        return 'https://www.reddit.com/r/aww/comments/1abcdef/';
      case 'vimeo':
        return 'https://vimeo.com/76979871';
      case 'twitch':
        return 'https://clips.twitch.tv/TenaciousCleverDumplingsNinjaGrumpy';
      case 'dailymotion':
        return 'https://www.dailymotion.com/video/x8p9v4h';
      case 'soundcloud':
        return 'https://soundcloud.com/forss/flickermood';
      case 'pinterest':
        return 'https://www.pinterest.com/pin/99360735500167749/';
      default:
        return '';
    }
  }
}
