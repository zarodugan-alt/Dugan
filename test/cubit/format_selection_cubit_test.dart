import 'package:flutter_test/flutter_test.dart';
import 'package:dugan/domain/entities/app_settings.dart';
import 'package:dugan/domain/entities/format_option.dart';
import 'package:dugan/domain/entities/media_info.dart';
import 'package:dugan/presentation/cubit/format_selection/format_selection_cubit.dart';

MediaInfo _media() => const MediaInfo(
      id: 'abc',
      title: 'Test',
      sourceUrl: 'https://youtu.be/abc',
      platform: 'youtube',
      formats: [
        FormatOption(
          formatId: '137',
          ext: 'mp4',
          hasVideo: true,
          vcodec: 'avc1.640028',
          height: 1080,
          fps: 30,
          tbr: 2000,
          filesize: 245000000,
        ),
        FormatOption(
          formatId: '136',
          ext: 'mp4',
          hasVideo: true,
          vcodec: 'avc1.4d401f',
          height: 720,
          fps: 30,
          tbr: 1200,
          filesize: 61000000,
        ),
        FormatOption(
          formatId: '22',
          ext: 'mp4',
          hasVideo: true,
          hasAudio: true,
          vcodec: 'avc1.64001f',
          acodec: 'mp4a.40.2',
          height: 720,
          fps: 30,
          tbr: 1100,
          filesize: 58000000,
        ),
      ],
      audioFormats: [
        FormatOption(
          formatId: '140',
          ext: 'm4a',
          hasAudio: true,
          acodec: 'mp4a.40.2',
          abr: 129,
          filesize: 3400000,
        ),
        FormatOption(
          formatId: '139',
          ext: 'm4a',
          hasAudio: true,
          acodec: 'mp4a.40.5',
          abr: 48,
          filesize: 1300000,
        ),
      ],
      subtitles: [],
    );

void main() {
  group('initial selection', () {
    test('best available is preselected', () {
      final cubit = FormatSelectionCubit(
        media: _media(),
        settings: AppSettings.defaults,
      );
      expect(cubit.state.videoFormatId, '137');
      expect(cubit.state.audioFormatId, '140');
      expect(cubit.state.canDownload, isTrue);
      expect(cubit.state.estimatedSizeBytes, 245000000 + 3400000);
      expect(cubit.state.downloadSummary, '1080p · H.264');
    });

    test('quality preference caps the preselection', () {
      final cubit = FormatSelectionCubit(
        media: _media(),
        settings: AppSettings.defaults
            .copyWith(defaultQuality: QualityPreference.q720),
      );
      expect(cubit.state.videoFormatId, '136');
    });

    test('audio preference starts in audio-only mode', () {
      final cubit = FormatSelectionCubit(
        media: _media(),
        settings: AppSettings.defaults
            .copyWith(defaultQuality: QualityPreference.audio),
      );
      expect(cubit.state.audioOnly, isTrue);
      expect(cubit.state.canDownload, isTrue);
    });
  });

  group('video-only validation', () {
    test('video-only without audio choice or merge blocks the CTA', () {
      final cubit = FormatSelectionCubit(
        media: _media(),
        settings: AppSettings.defaults,
      )
        ..selectAudio(null)
        ..toggleMergeWithBestAudio();

      expect(cubit.state.canDownload, isFalse);
      expect(cubit.state.validationHint, contains('audio'));
    });

    test('re-enabling merge makes it downloadable', () {
      final cubit = FormatSelectionCubit(
        media: _media(),
        settings: AppSettings.defaults,
      )
        ..selectAudio(null)
        ..toggleMergeWithBestAudio()
        ..toggleMergeWithBestAudio();

      expect(cubit.state.canDownload, isTrue);
    });

    test('progressive formats need no audio selection', () {
      final cubit = FormatSelectionCubit(
        media: _media(),
        settings: AppSettings.defaults,
      )
        ..selectVideo('22')
        ..selectAudio(null)
        ..toggleMergeWithBestAudio();

      expect(cubit.state.canDownload, isTrue);
      expect(cubit.state.selectedVideo!.isProgressive, isTrue);
    });
  });

  group('audio-only flow', () {
    test('container, bitrate and request mapping', () {
      final cubit = FormatSelectionCubit(
        media: _media(),
        settings: AppSettings.defaults,
      )
        ..setAudioOnly(true)
        ..setAudioContainer('flac')
        ..setAudioBitrate(320)
        ..toggleEmbedThumbnail();

      final request = cubit.buildRequest();
      expect(request.audioOnly, isTrue);
      expect(request.videoFormatId, isNull);
      expect(request.audioContainer, 'flac');
      expect(request.audioBitrateKbps, 320);
      expect(request.embedThumbnail, isTrue);
      expect(cubit.state.downloadSummary, 'FLAC · 320 kbps');
    });
  });

  group('filename template', () {
    test('falls back to the default when cleared', () {
      final cubit = FormatSelectionCubit(
        media: _media(),
        settings: AppSettings.defaults,
      )..filenameChanged('   ');

      expect(cubit.buildRequest().filenameTemplate,
          '%(title)s [%(id)s].%(ext)s');
    });
  });
}
