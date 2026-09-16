import 'package:flutter_test/flutter_test.dart';
import 'package:dugan/core/constants/platform_meta.dart';
import 'package:dugan/core/utils/url_utils.dart';

void main() {
  group('UrlUtils.isProbablyUrl', () {
    test('accepts http(s) URLs', () {
      expect(UrlUtils.isProbablyUrl('https://youtu.be/abc'), isTrue);
      expect(UrlUtils.isProbablyUrl('http://example.com/x'), isTrue);
    });

    test('rejects garbage', () {
      expect(UrlUtils.isProbablyUrl(''), isFalse);
      expect(UrlUtils.isProbablyUrl('not a url'), isFalse);
      expect(UrlUtils.isProbablyUrl('www.youtube.com'), isFalse);
    });
  });

  group('UrlUtils.extractUrl', () {
    test('finds the first URL in a blob of text', () {
      const blob = 'Check this out!\nhttps://www.tiktok.com/@user/video/123 lol';
      expect(UrlUtils.extractUrl(blob), 'https://www.tiktok.com/@user/video/123');
    });

    test('returns the trimmed string when it is already a URL', () {
      expect(UrlUtils.extractUrl('  https://x.com/a  '), 'https://x.com/a');
    });

    test('returns null when no URL exists', () {
      expect(UrlUtils.extractUrl('nothing here'), isNull);
    });
  });

  group('PlatformMeta.fromUrl', () {
    test('detects known platforms', () {
      expect(PlatformMeta.fromUrl('https://www.youtube.com/watch?v=x').id, 'youtube');
      expect(PlatformMeta.fromUrl('https://youtu.be/x').id, 'youtube');
      expect(PlatformMeta.fromUrl('https://vm.tiktok.com/x').id, 'tiktok');
      expect(PlatformMeta.fromUrl('https://www.instagram.com/p/x/').id, 'instagram');
      expect(PlatformMeta.fromUrl('https://x.com/a/status/1').id, 'twitter');
      expect(PlatformMeta.fromUrl('https://www.bilibili.com/video/BV1').id, 'bilibili');
      expect(PlatformMeta.fromUrl('https://vimeo.com/1').id, 'vimeo');
    });

    test('falls back to generic web', () {
      expect(PlatformMeta.fromUrl('https://example.com/video').id, 'web');
    });
  });

  group('PlatformMeta.fromExtractor', () {
    test('maps yt-dlp extractor keys', () {
      expect(PlatformMeta.fromExtractor('Youtube').id, 'youtube');
      expect(PlatformMeta.fromExtractor('TikTok').id, 'tiktok');
      expect(PlatformMeta.fromExtractor('BiliBili').id, 'bilibili');
      expect(PlatformMeta.fromExtractor('Generic').id, 'web');
      expect(PlatformMeta.fromExtractor(null).id, 'web');
    });
  });
}
