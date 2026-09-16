import 'dart:async';

import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../domain/entities/download_task.dart';
import '../../../domain/usecases/download_control.dart';
import '../../../domain/usecases/watch_downloads.dart';
import 'downloads_event.dart';
import 'downloads_state.dart';

/// Real-time monitor of all downloads. The bloc subscribes once to the
/// repository stream (via [DownloadsSubscriptionRequested], fired by the
/// app shell at startup) and folds tasks into active/finished buckets.
class DownloadsBloc extends Bloc<DownloadsEvent, DownloadsState> {
  DownloadsBloc({
    required WatchDownloads watchDownloads,
    required PauseDownload pauseDownload,
    required ResumeDownload resumeDownload,
    required CancelDownload cancelDownload,
    required RetryDownload retryDownload,
    required ClearFinishedDownloads clearFinished,
  })  : _watchDownloads = watchDownloads,
        _pauseDownload = pauseDownload,
        _resumeDownload = resumeDownload,
        _cancelDownload = cancelDownload,
        _retryDownload = retryDownload,
        _clearFinished = clearFinished,
        super(const DownloadsState()) {
    on<DownloadsSubscriptionRequested>(_onSubscriptionRequested);
    on<DownloadsUpdated>(_onUpdated);
    on<DownloadsPauseRequested>(_onPauseRequested);
    on<DownloadsResumeRequested>(_onResumeRequested);
    on<DownloadsCancelRequested>(_onCancelRequested);
    on<DownloadsRetryRequested>(_onRetryRequested);
    on<DownloadsClearFinishedRequested>(_onClearFinishedRequested);
  }

  final WatchDownloads _watchDownloads;
  final PauseDownload _pauseDownload;
  final ResumeDownload _resumeDownload;
  final CancelDownload _cancelDownload;
  final RetryDownload _retryDownload;
  final ClearFinishedDownloads _clearFinished;

  StreamSubscription<List<DownloadTask>>? _subscription;

  void _onSubscriptionRequested(
    DownloadsSubscriptionRequested event,
    Emitter<DownloadsState> emit,
  ) {
    _subscription ??= _watchDownloads().listen(
      (tasks) => add(DownloadsUpdated(tasks)),
    );
  }

  void _onUpdated(
    DownloadsUpdated event,
    Emitter<DownloadsState> emit,
  ) {
    final active = event.tasks.where((task) => task.isActive).toList()
      ..sort((a, b) => a.createdAt.compareTo(b.createdAt));
    final finished = event.tasks.where((task) => task.isTerminal).toList()
      ..sort(
        (a, b) => (b.completedAt ?? b.createdAt)
            .compareTo(a.completedAt ?? a.createdAt),
      );
    emit(DownloadsState(active: active, finished: finished));
  }

  Future<void> _onPauseRequested(
    DownloadsPauseRequested event,
    Emitter<DownloadsState> emit,
  ) async {
    await _pauseDownload(event.taskId);
  }

  Future<void> _onResumeRequested(
    DownloadsResumeRequested event,
    Emitter<DownloadsState> emit,
  ) async {
    await _resumeDownload(event.taskId);
  }

  Future<void> _onCancelRequested(
    DownloadsCancelRequested event,
    Emitter<DownloadsState> emit,
  ) async {
    await _cancelDownload(event.taskId);
  }

  Future<void> _onRetryRequested(
    DownloadsRetryRequested event,
    Emitter<DownloadsState> emit,
  ) async {
    await _retryDownload(event.taskId);
  }

  Future<void> _onClearFinishedRequested(
    DownloadsClearFinishedRequested event,
    Emitter<DownloadsState> emit,
  ) async {
    await _clearFinished();
  }

  @override
  Future<void> close() async {
    await _subscription?.cancel();
    return super.close();
  }
}
