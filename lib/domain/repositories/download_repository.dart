import 'package:dartz/dartz.dart';

import '../entities/download_request.dart';
import '../entities/download_task.dart';
import '../../core/error/failures.dart';

/// Contract for the download engine: queueing, control and live progress.
abstract class DownloadRepository {
  /// Enqueues a new download; returns the created (queued) task.
  Future<Either<Failure, DownloadTask>> startDownload(DownloadRequest request);

  Future<Either<Failure, void>> pauseDownload(String taskId);

  Future<Either<Failure, void>> resumeDownload(String taskId);

  Future<Either<Failure, void>> cancelDownload(String taskId);

  /// Re-runs a failed or canceled task (also used by "undo cancel").
  Future<Either<Failure, void>> retryDownload(String taskId);

  /// Live stream of all tasks (active + finished this session).
  Stream<List<DownloadTask>> watchTasks();

  /// Current snapshot.
  List<DownloadTask> get tasks;

  /// Drops terminal (completed/failed/canceled) tasks from the list.
  Future<void> clearFinished();
}
