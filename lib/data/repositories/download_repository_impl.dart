import 'dart:async';
import 'dart:io';
import 'dart:math';

import 'package:dartz/dartz.dart';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:rxdart/rxdart.dart';

import '../../core/constants/app_constants.dart';
import '../../core/error/error_mapper.dart';
import '../../core/error/failures.dart';
import '../../domain/entities/app_settings.dart';
import '../../domain/entities/download_request.dart';
import '../../domain/entities/download_task.dart';
import '../../domain/entities/history_item.dart';
import '../../domain/repositories/download_repository.dart';
import '../../domain/repositories/history_repository.dart';
import '../datasources/local/cookie_store.dart';
import '../datasources/local/settings_local_data_source.dart';
import '../datasources/native/download_native_engine.dart';
import '../datasources/native/native_bridge.dart';
import '../datasources/native/system_services.dart';
import '../mappers/download_request_mapper.dart';

/// Orchestrates the download lifecycle:
///
///  1. `startDownload` validates constraints (Wi-Fi-only, live streams),
///     builds the native task and enqueues it.
///  2. The native engine streams progress events; [_onEngineEvent] folds
///     them into [DownloadTask]s and re-emits the whole list.
///  3. Failures classified as retryable are automatically re-enqueued with
///     exponential backoff (2s/4s/8s, max 3 attempts).
///  4. Completed tasks are recorded into the history repository and
///     optionally exported to the system gallery.
class DownloadRepositoryImpl implements DownloadRepository {
  DownloadRepositoryImpl({
    required DownloadNativeEngine engine,
    required SettingsLocalDataSource settingsLocal,
    required HistoryRepository historyRepository,
    required CookieStore cookieStore,
    required SystemServices systemServices,
  })  : _engine = engine,
        _settingsLocal = settingsLocal,
        _historyRepository = historyRepository,
        _cookieStore = cookieStore,
        _systemServices = systemServices {
    _eventsSubscription = _engine.events.listen(
      _onEngineEvent,
      onError: (Object e) {
        debugPrint('DownloadRepositoryImpl: event stream error → $e');
      },
      cancelOnError: false,
    );
  }

  final DownloadNativeEngine _engine;
  final SettingsLocalDataSource _settingsLocal;
  final HistoryRepository _historyRepository;
  final CookieStore _cookieStore;
  final SystemServices _systemServices;

  final Map<String, DownloadTask> _tasks = <String, DownloadTask>{};
  final Map<String, Map<String, dynamic>> _nativeTasks =
      <String, Map<String, dynamic>>{};
  final Map<String, Timer> _retryTimers = <String, Timer>{};

  final BehaviorSubject<List<DownloadTask>> _subject =
      BehaviorSubject<List<DownloadTask>>.seeded(const <DownloadTask>[]);

  late final StreamSubscription<Map<String, dynamic>> _eventsSubscription;

  final Random _random = Random();

  @override
  List<DownloadTask> get tasks => _subject.value;

  @override
  Stream<List<DownloadTask>> watchTasks() => _subject.stream;

  @override
  Future<Either<Failure, DownloadTask>> startDownload(
    DownloadRequest request,
  ) async {
    final media = request.media;
    final settings = _settingsLocal.load();

    if (media.isLive) {
      return const Left(
        VideoUnavailableFailure(message: 'Live streams cannot be downloaded.'),
      );
    }

    if (settings.wifiOnly) {
      final onWifi = await _engine.isWifiConnected();
      if (!onWifi) {
        return const Left(WifiRequiredFailure());
      }
    }

    // Resolve the output directory (user override → app default).
    final String outputDir;
    try {
      outputDir = settings.downloadDirOverride.trim().isNotEmpty
          ? settings.downloadDirOverride.trim()
          : await defaultDownloadDir();
      await Directory(outputDir).create(recursive: true);
    } on Exception catch (e) {
      return Left(StorageFailure(message: 'Download folder unavailable ($e).'));
    }

    final String? cookieFile = settings.cookies.trim().isEmpty
        ? null
        : await _cookieStore.writeCookieFile(settings.cookies);

    final task = DownloadTask(
      id: _newTaskId(),
      title: media.title,
      url: media.sourceUrl,
      platform: media.platform,
      createdAt: DateTime.now(),
      thumbnailUrl: media.thumbnailUrl,
      duration: media.duration,
      resolutionLabel: DownloadRequestMapper.resolutionLabel(request, media),
      formatSummary: DownloadRequestMapper.formatSummary(request, media),
    );

    final nativeTask = DownloadRequestMapper.toNativeTask(
      task: task,
      request: request,
      settings: settings,
      outputDir: outputDir,
      cookieFile: cookieFile,
    );

    _tasks[task.id] = task;
    _nativeTasks[task.id] = nativeTask;
    _emit();

    try {
      await _engine.start(nativeTask);
    } on EngineException catch (e) {
      _tasks[task.id] = task.copyWith(
        status: DownloadStatus.failed,
        errorMessage: e.message,
        errorCode: e.code,
      );
      _emit();
      return Left(ErrorMapper.fromCode(e.code, e.message));
    }

    return Right(task);
  }

  @override
  Future<Either<Failure, void>> pauseDownload(String taskId) async {
    final task = _tasks[taskId];
    if (task == null || !task.isActive) {
      return const Right(null);
    }
    _tasks[taskId] = task.copyWith(
      status: DownloadStatus.paused,
      speedBytesPerSec: 0,
    );
    _emit();
    try {
      await _engine.pause(taskId);
      return const Right(null);
    } on EngineException catch (e) {
      return Left(ErrorMapper.fromCode(e.code, e.message));
    }
  }

  @override
  Future<Either<Failure, void>> resumeDownload(String taskId) async {
    final task = _tasks[taskId];
    if (task == null || task.status != DownloadStatus.paused) {
      return const Right(null);
    }
    final nativeTask = _nativeTasks[taskId];
    if (nativeTask == null) {
      return const Left(UnknownFailure(message: 'This task cannot be resumed.'));
    }
    _tasks[taskId] = task.copyWith(
      status: DownloadStatus.queued,
      speedBytesPerSec: 0,
      stage: null,
    );
    _emit();
    try {
      // yt-dlp resumes from the .part file (continuedl).
      await _engine.start(nativeTask);
      return const Right(null);
    } on EngineException catch (e) {
      return Left(ErrorMapper.fromCode(e.code, e.message));
    }
  }

  @override
  Future<Either<Failure, void>> cancelDownload(String taskId) async {
    final task = _tasks[taskId];
    if (task == null || task.isTerminal) {
      return const Right(null);
    }
    _retryTimers.remove(taskId)?.cancel();
    _tasks[taskId] = task.copyWith(
      status: DownloadStatus.canceled,
      speedBytesPerSec: 0,
      completedAt: DateTime.now(),
    );
    _emit();
    try {
      await _engine.cancel(taskId);
      return const Right(null);
    } on EngineException catch (e) {
      return Left(ErrorMapper.fromCode(e.code, e.message));
    }
  }

  @override
  Future<Either<Failure, void>> retryDownload(String taskId) async {
    final task = _tasks[taskId];
    if (task == null || !task.isTerminal) {
      return const Right(null);
    }
    final nativeTask = _nativeTasks[taskId];
    if (nativeTask == null) {
      return const Left(
        UnknownFailure(message: 'This task cannot be restarted.'),
      );
    }
    _tasks[taskId] = task.copyWith(
      status: DownloadStatus.queued,
      errorMessage: null,
      errorCode: null,
      stage: null,
      speedBytesPerSec: 0,
    );
    _emit();
    try {
      await _engine.start(nativeTask);
      return const Right(null);
    } on EngineException catch (e) {
      return Left(ErrorMapper.fromCode(e.code, e.message));
    }
  }

  @override
  Future<void> clearFinished() async {
    _tasks.removeWhere((_, task) => task.isTerminal);
    _nativeTasks.removeWhere((id, _) => !_tasks.containsKey(id));
    for (final id in _retryTimers.keys.toList()) {
      if (!_tasks.containsKey(id)) {
        _retryTimers.remove(id)?.cancel();
      }
    }
    _emit();
  }

  /// App-specific default download directory:
  /// `<external app storage>/Downloads` on Android, `<documents>/Downloads`
  /// elsewhere.
  Future<String> defaultDownloadDir() async {
    final Directory base;
    if (Platform.isAndroid) {
      base = await getExternalStorageDirectory() ??
          await getApplicationDocumentsDirectory();
    } else {
      base = await getApplicationDocumentsDirectory();
    }
    final root = base.path.endsWith('/') ? base.path : '${base.path}/';
    return '${root}Downloads';
  }

  // ── Engine events ───────────────────────────────────────────────────────

  void _onEngineEvent(Map<String, dynamic> event) {
    final taskId = event['taskId'] as String?;
    if (taskId == null) {
      return;
    }
    final existing = _tasks[taskId];
    if (existing == null) {
      return;
    }

    final status = _statusFromString(event['status'] as String? ?? '');
    var task = existing;

    switch (status) {
      case DownloadStatus.downloading:
        task = task.copyWith(
          status: DownloadStatus.downloading,
          progress: _asDouble(event['progress']) ?? task.progress,
          downloadedBytes:
              _asInt(event['downloadedBytes']) ?? task.downloadedBytes,
          totalBytes: _asInt(event['totalBytes']) ?? task.totalBytes,
          speedBytesPerSec:
              _asDouble(event['speedBps']) ?? task.speedBytesPerSec,
          stage: null,
          errorMessage: null,
        );
        break;

      case DownloadStatus.postProcessing:
        task = task.copyWith(
          status: DownloadStatus.postProcessing,
          stage: (event['stage'] as String?) ?? 'Processing',
          speedBytesPerSec: 0,
        );
        break;

      case DownloadStatus.paused:
        task = task.copyWith(
          status: DownloadStatus.paused,
          speedBytesPerSec: 0,
        );
        break;

      case DownloadStatus.completed:
        final filePath = event['filePath'] as String?;
        task = task.copyWith(
          status: DownloadStatus.completed,
          progress: 1.0,
          speedBytesPerSec: 0,
          stage: null,
          filePath: filePath,
          completedAt: DateTime.now(),
        );
        unawaited(_onCompleted(task));
        break;

      case DownloadStatus.canceled:
        task = task.copyWith(
          status: DownloadStatus.canceled,
          speedBytesPerSec: 0,
          completedAt: DateTime.now(),
        );
        break;

      case DownloadStatus.failed:
        final code = event['code'] as String?;
        final failure = ErrorMapper.fromCode(code, event['error'] as String?);
        if (ErrorMapper.isRetryable(failure) &&
            task.attempt < AppConstants.maxAutoRetries) {
          final attempt = task.attempt + 1;
          task = task.copyWith(
            status: DownloadStatus.queued,
            attempt: attempt,
            errorCode: code,
            errorMessage: 'Connection lost — retrying ($attempt/'
                '${AppConstants.maxAutoRetries})',
            speedBytesPerSec: 0,
          );
          _scheduleRetry(taskId, attempt);
        } else {
          task = task.copyWith(
            status: DownloadStatus.failed,
            errorCode: code,
            errorMessage: failure.message,
            speedBytesPerSec: 0,
            completedAt: DateTime.now(),
          );
        }
        break;

      case DownloadStatus.queued:
        // The native engine never emits this; nothing to do.
        break;
    }

    _tasks[taskId] = task;
    _emit();
  }

  Future<void> _onCompleted(DownloadTask task) async {
    var sizeBytes = task.totalBytes;
    final path = task.filePath;
    if (path != null && path.isNotEmpty) {
      try {
        final file = File(path);
        if (await file.exists()) {
          sizeBytes = await file.length();
        }
      } on Exception {
        // Keep the tracked size.
      }
    }

    await _historyRepository.add(
      HistoryItem(
        id: task.id,
        title: task.title,
        platform: task.platform,
        thumbnailUrl: task.thumbnailUrl,
        filePath: path ?? '',
        resolutionLabel: task.resolutionLabel,
        sizeBytes: sizeBytes,
        duration: task.duration,
        completedAt: task.completedAt ?? DateTime.now(),
        sourceUrl: task.url,
        formatSummary: task.formatSummary,
      ),
    );

    final settings = _settingsLocal.load();
    if (settings.saveToGallery && path != null && path.isNotEmpty) {
      unawaited(_systemServices.saveToGallery(path));
    }
  }

  void _scheduleRetry(String taskId, int attempt) {
    _retryTimers.remove(taskId)?.cancel();
    // 2s → 4s → 8s backoff.
    final delay = Duration(seconds: 2 << (attempt - 1));
    _retryTimers[taskId] = Timer(delay, () {
      _retryTimers.remove(taskId);
      final nativeTask = _nativeTasks[taskId];
      if (nativeTask == null) {
        return;
      }
      unawaited(
        _engine.start(nativeTask).catchError((Object e) {
          debugPrint('DownloadRepositoryImpl: retry failed → $e');
        }),
      );
    });
  }

  void _emit() {
    _subject.add(List<DownloadTask>.unmodifiable(_tasks.values));
  }

  String _newTaskId() {
    final ts = DateTime.now().microsecondsSinceEpoch.toRadixString(36);
    final rnd = _random.nextInt(0x7FFFFFFF).toRadixString(36);
    return 'd_$ts$rnd';
  }

  static DownloadStatus _statusFromString(String value) {
    switch (value) {
      case 'downloading':
        return DownloadStatus.downloading;
      case 'postprocessing':
        return DownloadStatus.postProcessing;
      case 'paused':
        return DownloadStatus.paused;
      case 'completed':
        return DownloadStatus.completed;
      case 'canceled':
        return DownloadStatus.canceled;
      case 'failed':
        return DownloadStatus.failed;
      default:
        return DownloadStatus.queued;
    }
  }

  static double? _asDouble(dynamic value) {
    if (value is num) {
      return value.toDouble();
    }
    return null;
  }

  static int? _asInt(dynamic value) {
    if (value is num) {
      return value.toInt();
    }
    return null;
  }

  @mustCallSuper
  Future<void> dispose() async {
    await _eventsSubscription.cancel();
    for (final timer in _retryTimers.values) {
      timer.cancel();
    }
    _retryTimers.clear();
    await _subject.close();
  }
}
