import 'package:dartz/dartz.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:bloc_test/bloc_test.dart';
import 'package:dugan/core/error/failures.dart';
import 'package:dugan/domain/entities/app_settings.dart';
import 'package:dugan/domain/entities/download_request.dart';
import 'package:dugan/domain/entities/download_task.dart';
import 'package:dugan/domain/entities/format_option.dart';
import 'package:dugan/domain/entities/media_info.dart';
import 'package:dugan/domain/repositories/download_repository.dart';
import 'package:dugan/domain/repositories/media_repository.dart';
import 'package:dugan/domain/repositories/settings_repository.dart';
import 'package:dugan/domain/usecases/download_control.dart';
import 'package:dugan/domain/usecases/parse_url.dart';
import 'package:dugan/domain/usecases/recent_parses.dart';
import 'package:dugan/domain/usecases/settings_control.dart';
import 'package:dugan/presentation/bloc/home/home_bloc.dart';
import 'package:dugan/presentation/bloc/home/home_event.dart';
import 'package:dugan/presentation/bloc/home/home_state.dart';
import 'package:mocktail/mocktail.dart';

class MockMediaRepository extends Mock implements MediaRepository {}

class MockDownloadRepository extends Mock implements DownloadRepository {}

class MockSettingsRepository extends Mock implements SettingsRepository {}

MediaInfo _media() => MediaInfo(
      id: 'abc',
      title: 'Test Video',
      sourceUrl: 'https://youtu.be/abc',
      platform: 'youtube',
      formats: const [
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
      ],
      audioFormats: const [
        FormatOption(
          formatId: '140',
          ext: 'm4a',
          hasAudio: true,
          acodec: 'mp4a.40.2',
          abr: 129,
          filesize: 3400000,
        ),
      ],
    );

DownloadTask _task(MediaInfo media) => DownloadTask(
      id: 'd1',
      title: media.title,
      url: media.sourceUrl,
      platform: media.platform,
      createdAt: DateTime(2026),
    );

void main() {
  late MockMediaRepository mediaRepository;
  late MockDownloadRepository downloadRepository;
  late MockSettingsRepository settingsRepository;

  setUpAll(() {
    registerFallbackValue(
      DownloadRequest(media: _media()),
    );
  });

  setUp(() {
    mediaRepository = MockMediaRepository();
    downloadRepository = MockDownloadRepository();
    settingsRepository = MockSettingsRepository();

    when(() => mediaRepository.getRecentParses())
        .thenAnswer((_) async => []);
    when(() => mediaRepository.clearRecentParses())
        .thenAnswer((_) async {});
    when(() => settingsRepository.load())
        .thenAnswer((_) async => AppSettings.defaults);
  });

  HomeBloc build() => HomeBloc(
        parseUrl: ParseUrl(mediaRepository),
        getRecentParses: GetRecentParses(mediaRepository),
        clearRecentParses: ClearRecentParses(mediaRepository),
        loadSettings: LoadSettings(settingsRepository),
        startDownload: StartDownload(downloadRepository),
      );

  blocTest<HomeBloc, HomeState>(
    'parses a URL: loading → loaded with media',
    build: build,
    act: (bloc) => bloc
      ..add(const HomeUrlChanged('https://youtu.be/abc'))
      ..add(const HomeParseRequested()),
    setUp: () {
      when(() => mediaRepository.parseUrl(any()))
          .thenAnswer((_) async => Right(_media()));
    },
    expect: () => [
      const HomeState(url: 'https://youtu.be/abc'),
      const HomeState(url: 'https://youtu.be/abc', status: HomeStatus.loading),
      HomeState(
        url: 'https://youtu.be/abc',
        status: HomeStatus.loaded,
        media: _media(),
      ),
    ],
  );

  blocTest<HomeBloc, HomeState>(
    'parse failure: loading → error with failure',
    build: build,
    setUp: () {
      when(() => mediaRepository.parseUrl(any()))
          .thenAnswer((_) async => const Left(AuthRequiredFailure()));
    },
    act: (bloc) => bloc
      ..add(const HomeUrlChanged('https://youtu.be/abc'))
      ..add(const HomeParseRequested()),
    expect: () => [
      const HomeState(url: 'https://youtu.be/abc'),
      const HomeState(url: 'https://youtu.be/abc', status: HomeStatus.loading),
      const HomeState(
        url: 'https://youtu.be/abc',
        status: HomeStatus.error,
        failure: AuthRequiredFailure(),
      ),
    ],
  );

  blocTest<HomeBloc, HomeState>(
    'auto-download (Best Quality tile) starts a download after parsing',
    build: build,
    setUp: () {
      final media = _media();
      when(() => mediaRepository.parseUrl(any()))
          .thenAnswer((_) async => Right(media));
      when(() => downloadRepository.startDownload(any()))
          .thenAnswer((_) async => Right(_task(media)));
    },
    act: (bloc) => bloc
      ..add(const HomeUrlChanged('https://youtu.be/abc'))
      ..add(const HomeParseRequested(autoDownload: true)),
    expect: () => [
      const HomeState(url: 'https://youtu.be/abc'),
      const HomeState(url: 'https://youtu.be/abc', status: HomeStatus.loading),
      isA<HomeState>()
          .having((s) => s.status, 'status', HomeStatus.loaded)
          .having((s) => s.media?.id, 'media.id', 'abc')
          .having((s) => s.queuedMessage, 'queuedMessage', isNull()),
      isA<HomeState>()
          .having((s) => s.status, 'status', HomeStatus.loaded)
          .having((s) => s.queuedMessage, 'queuedMessage',
              '“Test Video” queued'),
    ],
    verify: (_) {
      verify(() => downloadRepository.startDownload(any())).called(1);
    },
  );

  blocTest<HomeBloc, HomeState>(
    'format-sheet CTA enqueues the chosen request',
    build: build,
    setUp: () {
      final media = _media();
      when(() => downloadRepository.startDownload(any()))
          .thenAnswer((_) async => Right(_task(media)));
    },
    act: (bloc) => bloc
      ..add(const HomeUrlChanged('https://youtu.be/abc'))
      ..add(HomeDownloadRequested(
        DownloadRequest(media: _media(), videoFormatId: '137'),
      )),
    expect: () => [
      const HomeState(url: 'https://youtu.be/abc'),
      isA<HomeState>().having(
        (s) => s.queuedMessage,
        'queuedMessage',
        '“Test Video” added to downloads',
      ),
    ],
  );

  blocTest<HomeBloc, HomeState>(
    'selecting a recent parse loads it without a network call',
    build: build,
    act: (bloc) => bloc..add(HomeRecentParseSelected(_media())),
    expect: () => [
      isA<HomeState>()
          .having((s) => s.status, 'status', HomeStatus.loaded)
          .having((s) => s.media?.id, 'media.id', 'abc'),
    ],
    verify: (_) {
      verifyNever(() => mediaRepository.parseUrl(any()));
    },
  );
}
