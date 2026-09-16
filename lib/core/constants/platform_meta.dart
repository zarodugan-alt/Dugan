import 'package:flutter/material.dart';

/// Metadata for every platform Dugan understands out of the box.
///
/// Detection is domain-level logic (see [matches]) so that both the URL
/// validator and the UI badges share one source of truth.
class PlatformMeta {
  const PlatformMeta({
    required this.id,
    required this.name,
    required this.emoji,
    required this.hosts,
    this.loginHost,
  });

  final String id;
  final String name;
  final String emoji;
  final List<String> hosts;

  /// Host opened by the WebView login flow, when the platform supports
  /// cookie-based authentication.
  final String? loginHost;

  bool matches(String url) {
    final lower = url.toLowerCase();
    return hosts.any(lower.contains);
  }

  static const List<PlatformMeta> all = [
    PlatformMeta(
      id: 'youtube',
      name: 'YouTube',
      emoji: '▶️',
      hosts: ['youtube.com', 'youtu.be', 'youtube-nocookie.com'],
      loginHost: 'https://www.youtube.com',
    ),
    PlatformMeta(
      id: 'tiktok',
      name: 'TikTok',
      emoji: '🎵',
      hosts: ['tiktok.com'],
    ),
    PlatformMeta(
      id: 'instagram',
      name: 'Instagram',
      emoji: '📸',
      hosts: ['instagram.com'],
      loginHost: 'https://www.instagram.com',
    ),
    PlatformMeta(
      id: 'twitter',
      name: 'X / Twitter',
      emoji: '𝕏',
      hosts: ['twitter.com', 'x.com', 't.co'],
    ),
    PlatformMeta(
      id: 'bilibili',
      name: 'Bilibili',
      emoji: '📺',
      hosts: ['bilibili.com', 'b23.tv'],
      loginHost: 'https://www.bilibili.com',
    ),
    PlatformMeta(
      id: 'facebook',
      name: 'Facebook',
      emoji: '👥',
      hosts: ['facebook.com', 'fb.watch', 'fb.com'],
      loginHost: 'https://www.facebook.com',
    ),
    PlatformMeta(
      id: 'reddit',
      name: 'Reddit',
      emoji: '👽',
      hosts: ['reddit.com', 'redd.it'],
    ),
    PlatformMeta(
      id: 'vimeo',
      name: 'Vimeo',
      emoji: '🎬',
      hosts: ['vimeo.com'],
    ),
    PlatformMeta(
      id: 'twitch',
      name: 'Twitch',
      emoji: '🟣',
      hosts: ['twitch.tv', 'clips.twitch.tv'],
    ),
    PlatformMeta(
      id: 'dailymotion',
      name: 'Dailymotion',
      emoji: '🎥',
      hosts: ['dailymotion.com', 'dai.ly'],
    ),
    PlatformMeta(
      id: 'soundcloud',
      name: 'SoundCloud',
      emoji: '☁️',
      hosts: ['soundcloud.com'],
    ),
    PlatformMeta(
      id: 'pinterest',
      name: 'Pinterest',
      emoji: '📌',
      hosts: ['pinterest.com', 'pin.it'],
    ),
  ];

  /// Generic fallback for any of the 1800+ sites yt-dlp supports.
  static const PlatformMeta generic = PlatformMeta(
    id: 'web',
    name: 'Web',
    emoji: '🌐',
    hosts: [],
  );

  /// Returns the first platform whose hosts appear in [url].
  static PlatformMeta fromUrl(String url) {
    for (final platform in all) {
      if (platform.matches(url)) {
        return platform;
      }
    }
    return generic;
  }

  static PlatformMeta byId(String id) {
    return all.where((p) => p.id == id).firstOrNull ?? generic;
  }

  /// Maps an extractor key coming back from yt-dlp (e.g. `Youtube`,
  /// `TikTok`) onto a known platform when possible.
  static PlatformMeta fromExtractor(String? extractor) {
    if (extractor == null || extractor.isEmpty) {
      return generic;
    }
    final lower = extractor.toLowerCase();
    for (final platform in all) {
      if (lower.contains(platform.id)) {
        return platform;
      }
    }
    return generic;
  }
}
