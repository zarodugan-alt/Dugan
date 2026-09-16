import 'package:equatable/equatable.dart';

import '../../../domain/entities/download_request.dart';
import '../../../domain/entities/media_info.dart';

abstract class HomeEvent extends Equatable {
  const HomeEvent();

  @override
  List<Object?> get props => [];
}

final class HomeUrlChanged extends HomeEvent {
  const HomeUrlChanged(this.url);

  final String url;

  @override
  List<Object?> get props => [url];
}

/// Triggers a parse of the current URL. With [autoDownload], a "Best
/// Quality" download starts immediately after a successful parse.
final class HomeParseRequested extends HomeEvent {
  const HomeParseRequested({this.autoDownload = false});

  final bool autoDownload;

  @override
  List<Object?> get props => [autoDownload];
}

/// Re-opens the format sheet for a recent parse without hitting the network.
final class HomeRecentParseSelected extends HomeEvent {
  const HomeRecentParseSelected(this.info);

  final MediaInfo info;

  @override
  List<Object?> get props => [info];
}

/// The format sheet's "Download" CTA.
final class HomeDownloadRequested extends HomeEvent {
  const HomeDownloadRequested(this.request);

  final DownloadRequest request;

  @override
  List<Object?> get props => [request];
}

final class HomeRecentParsesCleared extends HomeEvent {}

final class HomeErrorDismissed extends HomeEvent {}

final class HomeQueuedMessageConsumed extends HomeEvent {}

final class HomeRecentParsesLoaded extends HomeEvent {
  const HomeRecentParsesLoaded(this.parses);

  final List<MediaInfo> parses;

  @override
  List<Object?> get props => [parses];
}
