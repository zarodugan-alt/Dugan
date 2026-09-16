import 'package:dartz/dartz.dart';

import '../../core/error/failures.dart';
import '../entities/download_request.dart';
import '../entities/download_task.dart';
import '../repositories/download_repository.dart';

/// Enqueues a download built from the format selection.
class StartDownload {
  const StartDownload(this._repository);

  final DownloadRepository _repository;

  Future<Either<Failure, DownloadTask>> call(DownloadRequest request) =>
      _repository.startDownload(request);
}

class PauseDownload {
  const PauseDownload(this._repository);

  final DownloadRepository _repository;

  Future<Either<Failure, void>> call(String taskId) =>
      _repository.pauseDownload(taskId);
}

class ResumeDownload {
  const ResumeDownload(this._repository);

  final DownloadRepository _repository;

  Future<Either<Failure, void>> call(String taskId) =>
      _repository.resumeDownload(taskId);
}

class CancelDownload {
  const CancelDownload(this._repository);

  final DownloadRepository _repository;

  Future<Either<Failure, void>> call(String taskId) =>
      _repository.cancelDownload(taskId);
}

class RetryDownload {
  const RetryDownload(this._repository);

  final DownloadRepository _repository;

  Future<Either<Failure, void>> call(String taskId) =>
      _repository.retryDownload(taskId);
}

class ClearFinishedDownloads {
  const ClearFinishedDownloads(this._repository);

  final DownloadRepository _repository;

  Future<void> call() => _repository.clearFinished();
}
