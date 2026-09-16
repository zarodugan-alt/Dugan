import 'package:equatable/equatable.dart';

import '../../../domain/entities/download_task.dart';

abstract class DownloadsEvent extends Equatable {
  const DownloadsEvent();

  @override
  List<Object?> get props => [];
}

/// Subscribes the bloc to the download repository stream (idempotent).
final class DownloadsSubscriptionRequested extends DownloadsEvent {}

final class DownloadsUpdated extends DownloadsEvent {
  const DownloadsUpdated(this.tasks);

  final List<DownloadTask> tasks;

  @override
  List<Object?> get props => [tasks];
}

final class DownloadsPauseRequested extends DownloadsEvent {
  const DownloadsPauseRequested(this.taskId);

  final String taskId;

  @override
  List<Object?> get props => [taskId];
}

final class DownloadsResumeRequested extends DownloadsEvent {
  const DownloadsResumeRequested(this.taskId);

  final String taskId;

  @override
  List<Object?> get props => [taskId];
}

final class DownloadsCancelRequested extends DownloadsEvent {
  const DownloadsCancelRequested(this.taskId);

  final String taskId;

  @override
  List<Object?> get props => [taskId];
}

/// Retry a failed/canceled task — also used by the "Undo" snackbar action
/// after a swipe-to-cancel.
final class DownloadsRetryRequested extends DownloadsEvent {
  const DownloadsRetryRequested(this.taskId);

  final String taskId;

  @override
  List<Object?> get props => [taskId];
}

final class DownloadsClearFinishedRequested extends DownloadsEvent {}
