import 'package:flutter_test/flutter_test.dart';
import 'package:bloc_test/bloc_test.dart';
import 'package:dugan/domain/entities/download_request.dart';
import 'package:dugan/domain/entities/history_item.dart';
import 'package:dugan/domain/entities/media_info.dart';
import 'package:dugan/domain/repositories/download_repository.dart';
import 'package:dugan/domain/repositories/history_repository.dart';
import 'package:dugan/domain/repositories/media_repository.dart';
import 'package:dugan/domain/repositories/settings_repository.dart';
import 'package:dugan/domain/usecases/download_control.dart';
import 'package:dugan/domain/usecases/history_control.dart';
import 'package:dugan/domain/usecases/parse_url.dart';
import 'package:dugan/domain/usecases/settings_control.dart';
import 'package:dugan/presentation/bloc/history/history_bloc.dart';
import 'package:dugan/presentation/bloc/history/history_event.dart';
import 'package:dugan/presentation/bloc/history/history_state.dart';
import 'package:mocktail/mocktail.dart';

class MockHistoryRepository extends Mock implements HistoryRepository {}

class MockMediaRepository extends Mock implements MediaRepository {}

class MockSettingsRepository extends Mock implements SettingsRepository {}

class MockDownloadRepository extends Mock implements DownloadRepository {}

HistoryItem _item(
  String id, {
  String title = 'Item',
  String platform = 'youtube',
  int sizeBytes = 1000,
  int durationSec = 60,
  DateTime? completedAt,
}) =>
    HistoryItem(
      id: id,
      title: title,
      platform: platform,
      filePath: '/tmp/$id.mp4',
      sourceUrl: 'https://youtu.be/$id',
      sizeBytes: sizeBytes,
      duration: Duration(seconds: durationSec),
      completedAt: completedAt ?? DateTime(2026, 1, 1),
    );

void main() {
  late MockHistoryRepository repository;
  late MockMediaRepository mediaRepository;
  late MockSettingsRepository settingsRepository;
  late MockDownloadRepository downloadRepository;
  late StreamController<List<HistoryItem>> controller;

  setUpAll(() {
    registerFallbackValue(
      DownloadRequest(media: MediaInfo(
        id: 'x',
        title: 'x',
        sourceUrl: 'https://x',
        platform: 'web',
      )),
    );
  });

  setUp(() {
    repository = MockHistoryRepository();
    mediaRepository = MockMediaRepository();
    settingsRepository = MockSettingsRepository();
    downloadRepository = MockDownloadRepository();
    controller = StreamController<List<HistoryItem>>();

    when(() => repository.watchHistory()).thenAnswer((_) => controller.stream);
    when(() => repository.deleteItems(any())).thenAnswer((_) async {});
    when(() => repository.clearHistory()).thenAnswer((_) async {});
  });

  tearDown(() => controller.close());

  HistoryBloc build() => HistoryBloc(
        watchHistory: WatchHistory(repository),
        deleteHistoryItems: DeleteHistoryItems(repository),
        clearHistory: ClearHistory(repository),
        parseUrl: ParseUrl(mediaRepository),
        loadSettings: LoadSettings(settingsRepository),
        startDownload: StartDownload(downloadRepository),
      );

  blocTest<HistoryBloc, HistoryState>(
    'subscribes to the library stream',
    build: build,
    act: (bloc) {
      bloc.add(HistoryViewStarted());
      controller.add([_item('a'), _item('b')]);
    },
    expect: () => [
      isA<HistoryState>()
          .having((s) => s.items.length, 'items', 2)
          .having((s) => s.visible.length, 'visible', 2),
    ],
  );

  blocTest<HistoryBloc, HistoryState>(
    'filters by title or platform query',
    build: build,
    act: (bloc) {
      bloc
        ..add(HistoryViewStarted())
        ..add(const HistorySearchChanged('tik'));
      controller.add([
        _item('a', title: 'Cat video', platform: 'youtube'),
        _item('b', title: 'Dance', platform: 'tiktok'),
      ]);
    },
    expect: () => [
      // The search event lands before the stream delivers the items.
      isA<HistoryState>()
          .having((s) => s.query, 'query', 'tik')
          .having((s) => s.items.length, 'items before stream', 0),
      isA<HistoryState>()
          .having((s) => s.items.length, 'items', 2)
          .having((s) => s.visible.length, 'visible after filter', 1)
          .having((s) => s.visible.first.id, 'visible id', 'b'),
    ],
  );

  blocTest<HistoryBloc, HistoryState>(
    'sorts by size, duration and name',
    build: build,
    seed: () => HistoryState(
      items: [
        _item('small', title: 'Zebra', sizeBytes: 10, durationSec: 10),
        _item('big', title: 'Apple', sizeBytes: 9000, durationSec: 900),
      ],
    ),
    act: (bloc) => bloc
      ..add(HistorySortChanged(HistorySort.size))
      ..add(HistorySortChanged(HistorySort.duration))
      ..add(HistorySortChanged(HistorySort.name)),
    expect: () => [
      isA<HistoryState>()
          .having((s) => s.visible.first.id, 'largest first', 'big'),
      isA<HistoryState>()
          .having((s) => s.visible.first.id, 'longest first', 'big'),
      isA<HistoryState>()
          .having((s) => s.visible.first.id, 'alphabetical', 'big'),
    ],
  );

  blocTest<HistoryBloc, HistoryState>(
    'batch selection deletes the chosen items',
    build: build,
    act: (bloc) => bloc
      ..add(const HistorySelectionToggled('a'))
      ..add(const HistorySelectionToggled('b'))
      ..add(const HistorySelectionToggled('b')) // deselect
      ..add(HistoryDeleteSelected()),
    expect: () => [
      isA<HistoryState>().having((s) => s.selectedIds, 'selected', {'a'}),
      isA<HistoryState>().having((s) => s.selectedIds, 'selected', {'a', 'b'}),
      isA<HistoryState>().having((s) => s.selectedIds, 'selected', {'a'}),
      isA<HistoryState>().having((s) => s.selectedIds, 'selected', <String>{}),
    ],
    verify: (_) {
      verify(() => repository.deleteItems(['a'])).called(1);
    },
  );

  blocTest<HistoryBloc, HistoryState>(
    'clear all wipes the library',
    build: build,
    act: (bloc) => bloc..add(HistoryClearAllRequested()),
    expect: () => <HistoryState>[],
    verify: (_) {
      verify(() => repository.clearHistory()).called(1);
    },
  );
}
