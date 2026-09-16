import 'package:flutter_test/flutter_test.dart';
import 'package:dugan/data/models/media_info_model.dart';
import 'package:dugan/domain/entities/format_option.dart';
import 'package:dugan/domain/entities/media_info.dart';

/// Fixture shaped exactly like the sanitised payload produced by
/// `android/app/src/main/python/ytdlp_bridge.py::_sanitize`.
Map<String, dynamic> _ytDlpFixture() {
  return <String, dynamic>{
    'id': 'dQw4w9WgXcQ',
    'title': 'Sample Video',
    'uploader': 'Sample Channel',
    'thumbnail': 'https://example.com/thumb.webp',
    'duration': 213,
    'sourceUrl': 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    'platform': 'Youtube',
    'isLive': false,
    'viewCount': 1234567,
    'formats': [
      <String, dynamic>{
        'formatId': '139',
        'ext': 'm4a',
        'protocol': 'https',
        'vcodec': null,
        'acodec': 'mp4a.40.5',
        'height': null,
        'width': null,
        'fps': null,
        'tbr': 48,
        'abr': 48,
        'filesize': 1300000,
      },
      <String, dynamic>{
        'formatId': '140',
        'ext': 'm4a',
        'protocol': 'https',
        'vcodec': null,
        'acodec': 'mp4a.40.2',
        'height': null,
        'width': null,
        'fps': null,
        'tbr': 129,
        'abr': 129,
        'filesize': 3400000,
      },
      <String, dynamic>{
        'formatId': '134',
        'ext': 'mp4',
        'protocol': 'https',
        'vcodec': 'avc1.4d401e',
        'acodec': null,
        'height': 360,
        'width': 640,
        'fps': 30,
        'tbr': 300,
        'abr': null,
        'filesize': null,
        'filesizeApprox': 8000000,
      },
      <String, dynamic>{
        'formatId': '137',
        'ext': 'mp4',
        'protocol': 'https',
        'vcodec': 'avc1.640028',
        'acodec': null,
        'height': 1080,
        'width': 1920,
        'fps': 30,
        'tbr': 2000,
        'abr': null,
        'filesize': 245000000,
      },
      // Storyboard junk that must be filtered out.
      <String, dynamic>{
        'formatId': 'sb0',
        'ext': 'mhtml',
        'protocol': 'mhtml',
        'vcodec': null,
        'acodec': null,
      },
    ],
    'subtitles': [
      <String, dynamic>{
        'language': 'en',
        'ext': 'vtt',
        'name': 'English',
        'isAutoCaption': false,
      },
      <String, dynamic>{
        'language': 'es',
        'ext': 'vtt',
        'name': null,
        'isAutoCaption': true,
      },
    ],
  };
}

void main() {
  test('parses sanitised yt-dlp JSON into a MediaInfo entity', () {
    final media = MediaInfoModel.fromJson(_ytDlpFixture());

    expect(media.id, 'dQw4w9WgXcQ');
    expect(media.title, 'Sample Video');
    expect(media.uploader, 'Sample Channel');
    expect(media.duration, const Duration(minutes: 3, seconds: 33));
    expect(media.platform, 'youtube');
    expect(media.viewCount, 1234567);

    // Video and audio formats are split into their own buckets.
    expect(media.formats.length, 2, reason: 'storyboard/mhtml filtered out');
    expect(media.audioFormats.length, 2);

    // Sorted best-first.
    expect(media.formats.first.formatId, '137');
    expect(media.audioFormats.first.formatId, '140');

    // Derived helpers.
    expect(media.bestVideo?.formatId, '137');
    expect(media.bestAudio?.formatId, '140');
    expect(media.maxHeight, 1080);
    expect(media.formatsByResolution.length, 2);

    // Subtitles.
    expect(media.subtitles.length, 2);
    expect(media.subtitles.first.displayLabel, 'English');
    expect(media.subtitles.last.isAutoCaption, isTrue);
  });

  test('codec labels are humanised', () {
    final media = MediaInfoModel.fromJson(_ytDlpFixture());
    expect(media.bestVideo!.codecLabel, 'H.264');
    expect(media.bestAudio!.codecLabel, 'AAC');
    expect(media.bestVideo!.label, '1080p');
  });

  test('round-trips through toJson for the recent-parses cache', () {
    final original = MediaInfoModel.fromJson(_ytDlpFixture());
    final restored = MediaInfoModel.fromJson(MediaInfoModel.toJson(original));
    expect(restored, original);
  });

  test('FormatOption quality ordering prefers height then bitrate', () {
    const hd = FormatOption(
      formatId: 'hd',
      hasVideo: true,
      vcodec: 'avc1',
      height: 1080,
      tbr: 2000,
    );
    const sd = FormatOption(
      formatId: 'sd',
      hasVideo: true,
      vcodec: 'vp09',
      height: 480,
      tbr: 500,
    );
    expect(hd.qualityScore > sd.qualityScore, isTrue);
    expect(sd.codecLabel, 'VP9');
  });
}
