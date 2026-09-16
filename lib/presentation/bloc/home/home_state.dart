import 'package:equatable/equatable.dart';

import '../../../core/error/failures.dart';
import '../../../domain/entities/media_info.dart';

enum HomeStatus { idle, loading, loaded, error }

class HomeState extends Equatable {
  const HomeState({
    this.status = HomeStatus.idle,
    this.url = '',
    this.media,
    this.failure,
    this.recentParses = const [],
    this.queuedMessage,
  });

  final HomeStatus status;

  /// Current URL input value.
  final String url;

  /// Parsed media (present after a successful parse).
  final MediaInfo? media;

  /// Parse failure, when [status] is [HomeStatus.error].
  final Failure? failure;

  final List<MediaInfo> recentParses;

  /// One-shot snackbar text, cleared by [HomeQueuedMessageConsumed].
  final String? queuedMessage;

  bool get canParse => url.trim().isNotEmpty && status != HomeStatus.loading;

  static const Object _unset = Object();

  HomeState copyWith({
    HomeStatus? status,
    String? url,
    Object? media = _unset,
    Object? failure = _unset,
    List<MediaInfo>? recentParses,
    Object? queuedMessage = _unset,
  }) {
    return HomeState(
      status: status ?? this.status,
      url: url ?? this.url,
      media: identical(media, _unset) ? this.media : media as MediaInfo?,
      failure: identical(failure, _unset) ? this.failure : failure as Failure?,
      recentParses: recentParses ?? this.recentParses,
      queuedMessage: identical(queuedMessage, _unset)
          ? this.queuedMessage
          : queuedMessage as String?,
    );
  }

  @override
  List<Object?> get props => [
        status,
        url,
        media,
        failure,
        recentParses,
        queuedMessage,
      ];
}
