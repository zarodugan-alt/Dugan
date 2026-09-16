import 'package:equatable/equatable.dart';

import '../../../domain/entities/history_item.dart';

/// Sort orders for the library.
enum HistorySort { date, size, duration, name }

abstract class HistoryEvent extends Equatable {
  const HistoryEvent();

  @override
  List<Object?> get props => [];
}

final class HistoryViewStarted extends HistoryEvent {}

final class HistoryUpdated extends HistoryEvent {
  const HistoryUpdated(this.items);

  final List<HistoryItem> items;

  @override
  List<Object?> get props => [items];
}

final class HistorySearchChanged extends HistoryEvent {
  const HistorySearchChanged(this.query);

  final String query;

  @override
  List<Object?> get props => [query];
}

final class HistorySortChanged extends HistoryEvent {
  const HistorySortChanged(this.sort);

  final HistorySort sort;

  @override
  List<Object?> get props => [sort];
}

final class HistorySelectionToggled extends HistoryEvent {
  const HistorySelectionToggled(this.id);

  final String id;

  @override
  List<Object?> get props => [id];
}

final class HistorySelectionCleared extends HistoryEvent {}

final class HistoryDeleteSelected extends HistoryEvent {}

final class HistoryDeleteRequested extends HistoryEvent {
  const HistoryDeleteRequested(this.id);

  final String id;

  @override
  List<Object?> get props => [id];
}

final class HistoryClearAllRequested extends HistoryEvent {}

/// Re-parse the source URL and download best quality again.
final class HistoryRedownloadRequested extends HistoryEvent {
  const HistoryRedownloadRequested(this.item);

  final HistoryItem item;

  @override
  List<Object?> get props => [item];
}
