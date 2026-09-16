import 'dart:async';

import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../domain/entities/app_settings.dart';
import '../../../domain/entities/download_request.dart';
import '../../../domain/entities/format_option.dart';
import '../../../domain/entities/history_item.dart';
import '../../../domain/entities/media_info.dart';
import '../../../domain/usecases/download_control.dart';
import '../../../domain/usecases/history_control.dart';
import '../../../domain/usecases/parse_url.dart';
import '../../../domain/usecases/settings_control.dart';
import 'history_event.dart';
import 'history_state.dart';

/// Library of completed downloads: search, sort, batch selection,
/// delete/share and re-download.
class HistoryBloc extends Bloc<HistoryEvent, HistoryState> {
  HistoryBloc({
    required WatchHistory watchHistory,
    required DeleteHistoryItems deleteHistoryItems,
    required ClearHistory clearHistory,
    required ParseUrl parseUrl,
    required LoadSettings loadSettings,
    required StartDownload startDownload,
  })  : _watchHistory = watchHistory,
        _deleteHistoryItems = deleteHistoryItems,
        _clearHistory = clearHistory,
        _parseUrl = parseUrl,
        _loadSettings = loadSettings,
        _startDownload = startDownload,
        super(const HistoryState()) {
    on<HistoryViewStarted>(_onViewStarted);
    on<HistoryUpdated>(_onUpdated);
    on<HistorySearchChanged>(_onSearchChanged);
    on<HistorySortChanged>(_onSortChanged);
    on<HistorySelectionToggled>(_onSelectionToggled);
    on<HistorySelectionCleared>(_onSelectionCleared);
    on<HistoryDeleteSelected>(_onDeleteSelected);
    on<HistoryDeleteRequested>(_onDeleteRequested);
    on<HistoryClearAllRequested>(_onClearAllRequested);
    on<HistoryRedownloadRequested>(_onRedownloadRequested);
  }

  final WatchHistory _watchHistory;
  final DeleteHistoryItems _deleteHistoryItems;
  final ClearHistory _clearHistory;
  final ParseUrl _parseUrl;
  final LoadSettings _loadSettings;
  final StartDownload _startDownload;

  StreamSubscription<List<HistoryItem>>? _subscription;

  void _onViewStarted(
    HistoryViewStarted event,
    Emitter<HistoryState> emit,
  ) {
    _subscription ??= _watchHistory().listen(
      (items) => add(HistoryUpdated(items)),
    );
  }

  void _onUpdated(HistoryUpdated event, Emitter<HistoryState> emit) {
    emit(state.copyWith(items: event.items));
  }

  void _onSearchChanged(
    HistorySearchChanged event,
    Emitter<HistoryState> emit,
  ) {
    emit(state.copyWith(query: event.query));
  }

  void _onSortChanged(
    HistorySortChanged event,
    Emitter<HistoryState> emit,
  ) {
    emit(state.copyWith(sort: event.sort));
  }

  void _onSelectionToggled(
    HistorySelectionToggled event,
    Emitter<HistoryState> emit,
  ) {
    final selected = {...state.selectedIds};
    if (!selected.remove(event.id)) {
      selected.add(event.id);
    }
    emit(state.copyWith(selectedIds: selected));
  }

  void _onSelectionCleared(
    HistorySelectionCleared event,
    Emitter<HistoryState> emit,
  ) {
    emit(state.copyWith(selectedIds: const <String>{}));
  }

  Future<void> _onDeleteSelected(
    HistoryDeleteSelected event,
    Emitter<HistoryState> emit,
  ) async {
    final ids = state.selectedIds.toList();
    emit(state.copyWith(selectedIds: const <String>{}));
    await _deleteHistoryItems(ids);
  }

  Future<void> _onDeleteRequested(
    HistoryDeleteRequested event,
    Emitter<HistoryState> emit,
  ) async {
    await _deleteHistoryItems([event.id]);
  }

  Future<void> _onClearAllRequested(
    HistoryClearAllRequested event,
    Emitter<HistoryState> emit,
  ) async {
    await _clearHistory();
    emit(state.copyWith(selectedIds: const <String>{}));
  }

  Future<void> _onRedownloadRequested(
    HistoryRedownloadRequested event,
    Emitter<HistoryState> emit,
  ) async {
    final parse = await _parseUrl(event.item.sourceUrl);
    await parse.fold(
      (failure) async => emit(
        state.copyWith(
            redownloadMessage: 'Could not re-download — ${failure.message}'),
      ),
      (media) async {
        final settings = await _loadSettings();
        final request = _bestRequest(media, settings);
        final download = await _startDownload(request);
        download.fold(
          (failure) => emit(
            state.copyWith(
                redownloadMessage: 'Could not re-download — ${failure.message}'),
          ),
          (task) => emit(
            state.copyWith(
                redownloadMessage: '“${task.title}” added to downloads'),
          ),
        );
      },
    );
  }

  DownloadRequest _bestRequest(MediaInfo media, AppSettings settings) {
    if (settings.defaultQuality == QualityPreference.audio) {
      return DownloadRequest(media: media, audioOnly: true, embedMetadata: true);
    }
    final cap = settings.defaultQuality.maxHeight;
    FormatOption? chosen;
    if (cap != null) {
      for (final f in media.formats) {
        final height = f.height ?? 0;
        if (height > 0 &&
            height <= cap &&
            (chosen == null || f.qualityScore > chosen.qualityScore)) {
          chosen = f;
        }
      }
    }
    chosen ??= media.bestVideo ?? media.bestProgressive;
    return DownloadRequest(
      media: media,
      videoFormatId: chosen?.formatId,
      audioFormatId: media.bestAudio?.formatId,
      mergeWithBestAudio: true,
      mergeContainer: 'mp4',
      embedMetadata: true,
    );
  }

  @override
  Future<void> close() async {
    await _subscription?.cancel();
    return super.close();
  }
}
