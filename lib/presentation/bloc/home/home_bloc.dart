import 'dart:async';

import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../domain/entities/app_settings.dart';
import '../../../domain/entities/download_request.dart';
import '../../../domain/entities/format_option.dart';
import '../../../domain/entities/media_info.dart';
import '../../../domain/usecases/download_control.dart';
import '../../../domain/usecases/parse_url.dart';
import '../../../domain/usecases/recent_parses.dart';
import '../../../domain/usecases/settings_control.dart';
import 'home_event.dart';
import 'home_state.dart';

/// Drives the URL-pasting flow: validation → parse (with shimmer state) →
/// format selection → download start (including the one-tap "Best Quality"
/// quick action).
class HomeBloc extends Bloc<HomeEvent, HomeState> {
  HomeBloc({
    required ParseUrl parseUrl,
    required GetRecentParses getRecentParses,
    required ClearRecentParses clearRecentParses,
    required LoadSettings loadSettings,
    required StartDownload startDownload,
  })  : _parseUrl = parseUrl,
        _getRecentParses = getRecentParses,
        _clearRecentParses = clearRecentParses,
        _loadSettings = loadSettings,
        _startDownload = startDownload,
        super(const HomeState()) {
    on<HomeUrlChanged>(_onUrlChanged);
    on<HomeParseRequested>(_onParseRequested);
    on<HomeRecentParseSelected>(_onRecentParseSelected);
    on<HomeDownloadRequested>(_onDownloadRequested);
    on<HomeRecentParsesCleared>(_onRecentParsesCleared);
    on<HomeErrorDismissed>(_onErrorDismissed);
    on<HomeQueuedMessageConsumed>(_onQueuedMessageConsumed);
    on<HomeRecentParsesLoaded>(_onRecentParsesLoaded);

    unawaited(
      _getRecentParses()
          .then((parses) => add(HomeRecentParsesLoaded(parses))),
    );
  }

  final ParseUrl _parseUrl;
  final GetRecentParses _getRecentParses;
  final ClearRecentParses _clearRecentParses;
  final LoadSettings _loadSettings;
  final StartDownload _startDownload;

  Future<void> _onUrlChanged(
    HomeUrlChanged event,
    Emitter<HomeState> emit,
  ) async {
    emit(state.copyWith(url: event.url));
  }

  Future<void> _onParseRequested(
    HomeParseRequested event,
    Emitter<HomeState> emit,
  ) async {
    if (state.status == HomeStatus.loading) {
      return;
    }
    emit(state.copyWith(status: HomeStatus.loading, failure: null));

    final result = await _parseUrl(state.url);
    await result.fold(
      (failure) async => emit(
        state.copyWith(status: HomeStatus.error, failure: failure),
      ),
      (info) async {
        emit(
          state.copyWith(
            status: HomeStatus.loaded,
            media: info,
            failure: null,
          ),
        );
        if (event.autoDownload) {
          final settings = await _loadSettings();
          final request = _bestRequestFor(info, settings);
          final download = await _startDownload(request);
          download.fold(
            (failure) => emit(
              state.copyWith(
                queuedMessage: 'Download failed — ${failure.message}',
              ),
            ),
            (task) => emit(
              state.copyWith(queuedMessage: '“${task.title}” queued'),
            ),
          );
        }
      },
    );
  }

  Future<void> _onRecentParseSelected(
    HomeRecentParseSelected event,
    Emitter<HomeState> emit,
  ) async {
    emit(
      state.copyWith(
        status: HomeStatus.loaded,
        media: event.info,
        failure: null,
        url: event.info.sourceUrl,
      ),
    );
  }

  Future<void> _onDownloadRequested(
    HomeDownloadRequested event,
    Emitter<HomeState> emit,
  ) async {
    final result = await _startDownload(event.request);
    result.fold(
      (failure) => emit(
        state.copyWith(queuedMessage: 'Download failed — ${failure.message}'),
      ),
      (task) => emit(
        state.copyWith(queuedMessage: '“${task.title}” added to downloads'),
      ),
    );
  }

  Future<void> _onRecentParsesCleared(
    HomeRecentParsesCleared event,
    Emitter<HomeState> emit,
  ) async {
    await _clearRecentParses();
    emit(state.copyWith(recentParses: const []));
  }

  Future<void> _onErrorDismissed(
    HomeErrorDismissed event,
    Emitter<HomeState> emit,
  ) async {
    emit(
      state.copyWith(
        status: HomeStatus.idle,
        media: null,
        failure: null,
      ),
    );
  }

  Future<void> _onQueuedMessageConsumed(
    HomeQueuedMessageConsumed event,
    Emitter<HomeState> emit,
  ) async {
    emit(state.copyWith(queuedMessage: null));
  }

  Future<void> _onRecentParsesLoaded(
    HomeRecentParsesLoaded event,
    Emitter<HomeState> emit,
  ) async {
    emit(state.copyWith(recentParses: event.parses));
  }

  /// "Best Quality" quick action: best video (capped by the user's default
  /// preference) merged with best audio into an MP4.
  DownloadRequest _bestRequestFor(MediaInfo info, AppSettings settings) {
    if (settings.defaultQuality == QualityPreference.audio) {
      return DownloadRequest(media: info, audioOnly: true, embedMetadata: true);
    }

    final cap = settings.defaultQuality.maxHeight;
    FormatOption? chosen;
    if (cap != null) {
      for (final f in info.formats) {
        final height = f.height ?? 0;
        if (height > 0 &&
            height <= cap &&
            (chosen == null || f.qualityScore > chosen.qualityScore)) {
          chosen = f;
        }
      }
    }
    chosen ??= info.bestVideo ?? info.bestProgressive;

    return DownloadRequest(
      media: info,
      videoFormatId: chosen?.formatId,
      audioFormatId: info.bestAudio?.formatId,
      mergeWithBestAudio: true,
      mergeContainer: 'mp4',
      embedMetadata: true,
    );
  }
}
