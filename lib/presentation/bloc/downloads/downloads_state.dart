import 'package:equatable/equatable.dart';

import '../../../domain/entities/download_task.dart';

class DownloadsState extends Equatable {
  const DownloadsState({
    this.active = const <DownloadTask>[],
    this.finished = const <DownloadTask>[],
  });

  /// Queued, downloading, paused and post-processing tasks.
  final List<DownloadTask> active;

  /// Completed, failed and canceled tasks (this session).
  final List<DownloadTask> finished;

  @override
  List<Object?> get props => [active, finished];
}
