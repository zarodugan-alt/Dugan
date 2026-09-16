import 'dart:async';

import 'package:dartz/dartz.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:bloc_test/bloc_test.dart';
import 'package:dugan/domain/entities/download_task.dart';
import 'package:dugan/domain/repositories/download_repository.dart';
import 'package:dugan/domain/usecases/download_control.dart';
import 'package:dugan/domain/usecases/watch_downloads.dart';
import 'package:dugan/presentation/bloc/downloads/downloads_bloc.dart';
import 'package:dugan/presentation/bloc/downloads/downloads_event.dart';
import 'package:dugan/presentation/bloc/downloads/downloads_state.dart';
import 'package:mocktail/mocktail.dart';

class MockDownloadRepository extends Mock implements DownloadRepository {}

DownloadTask _task(
  String id, {
  DownloadStatus status = DownloadStatus.downloading,
  DateTime? createdAt,
}) =>
    DownloadTask(
      id: id,
      title: 'Task $id',
      url: 'https://youtu.be/$id',
      platform: 'youtube',
      status: status,
      createdAt: createdAt ?? DateTime(2026, 1, 1),
    );

void main() {
  late MockDownloadRepository repository;
  late StreamController<List<DownloadTask>> controller;

  setUp(() {
    repository = MockDownloadRepository();
    controller = StreamController<List<DownloadTask>>();

    when(() => repository.watchTasks()).thenAnswer((_) => controller.stream);
    when(() => repository.pauseDownload(any()))
        .thenAnswer((_) async => const Right(null));
    when(() => repository.resumeDownload(any()))
        .thenAnswer((_) async => const Right(null));
    when(() => repository.cancelDownload(any()))
        .thenAnswer((_) async => const Right(null));
    when(() => repository.retryDownload(any()))
        .thenAnswer((_) async => const Right(null));
    when(() => repository.clearFinished()).thenAnswer((_) async {});
  });

  tearDown(() => controller.close());

  DownloadsBloc build() => DownloadsBloc(
        watchDownloads: WatchDownloads(repository),
        pauseDownload: PauseDownload(repository),
        resumeDownload: ResumeDownload(repository),
        cancelDownload: CancelDownload(repository),
        retryDownload: RetryDownload(repository),
        clearFinished: ClearFinishedDownloads(repository),
      );

  blocTest<DownloadsBloc, DownloadsState>(
    'splits the task stream into active and finished buckets',
    build: build,
    act: (bloc) {
      bloc.add(DownloadsSubscriptionRequested());
      controller.add([
        _task('a'),
        _task('b', status: DownloadStatus.paused),
        _task('c', status: DownloadStatus.completed),
        _task('d', status: DownloadStatus.failed),
      ]);
    },
    expect: () => [
      isA<DownloadsState>()
          .having((s) => s.active.length, 'active', 2)
          .having((s) => s.finished.length, 'finished', 2)
          .having((s) => s.active.first.id, 'first active', 'a')
          .having((s) => s.finished.first.id, 'first finished', 'c'),
    ],
  );

  blocTest<DownloadsBloc, DownloadsState>(
    'pause/resume/cancel/retry delegate to the repository without emitting',
    build: build,
    act: (bloc) {
      bloc
        ..add(DownloadsSubscriptionRequested())
        ..add(const DownloadsPauseRequested('a'))
        ..add(const DownloadsResumeRequested('a'))
        ..add(const DownloadsCancelRequested('a'))
        ..add(const DownloadsRetryRequested('a'));
    },
    expect: () => <DownloadsState>[],
    verify: (_) {
      verify(() => repository.pauseDownload('a')).called(1);
      verify(() => repository.resumeDownload('a')).called(1);
      verify(() => repository.cancelDownload('a')).called(1);
      verify(() => repository.retryDownload('a')).called(1);
    },
  );
}
