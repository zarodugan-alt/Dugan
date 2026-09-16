import 'package:equatable/equatable.dart';

import '../../../domain/entities/history_item.dart';
import 'history_event.dart';

class HistoryState extends Equatable {
  const HistoryState({
    this.items = const <HistoryItem>[],
    this.query = '',
    this.sort = HistorySort.date,
    this.selectedIds = const <String>{},
    this.redownloadMessage,
  });

  final List<HistoryItem> items;
  final String query;
  final HistorySort sort;
  final Set<String> selectedIds;

  /// One-shot snackbar message for the re-download flow.
  final String? redownloadMessage;

  bool get isSelecting => selectedIds.isNotEmpty;

  List<HistoryItem> get visible {
    final filtered = query.trim().isEmpty
        ? items
        : items
            .where(
              (item) =>
                  item.title.toLowerCase().contains(query.toLowerCase()) ||
                  item.platform.toLowerCase().contains(query.toLowerCase()),
            )
            .toList();
    switch (sort) {
      case HistorySort.size:
        filtered.sort((a, b) => b.sizeBytes.compareTo(a.sizeBytes));
      case HistorySort.duration:
        filtered.sort(
          (a, b) => (b.duration?.inSeconds ?? 0)
              .compareTo(a.duration?.inSeconds ?? 0),
        );
      case HistorySort.name:
        filtered.sort((a, b) => a.title.compareTo(b.title));
      case HistorySort.date:
        filtered.sort((a, b) => b.completedAt.compareTo(a.completedAt));
    }
    return filtered;
  }

  static const Object _unset = Object();

  HistoryState copyWith({
    List<HistoryItem>? items,
    String? query,
    HistorySort? sort,
    Set<String>? selectedIds,
    Object? redownloadMessage = _unset,
  }) {
    return HistoryState(
      items: items ?? this.items,
      query: query ?? this.query,
      sort: sort ?? this.sort,
      selectedIds: selectedIds ?? this.selectedIds,
      redownloadMessage: identical(redownloadMessage, _unset)
          ? this.redownloadMessage
          : redownloadMessage as String?,
    );
  }

  @override
  List<Object?> get props => [items, query, sort, selectedIds, redownloadMessage];
}
