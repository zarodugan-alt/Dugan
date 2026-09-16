import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../core/error/failures.dart';
import '../../domain/entities/engine_status.dart';
import '../../domain/usecases/check_engine_status.dart';

enum SplashStatus { initializing, ready, failed }

class SplashState extends Equatable {
  const SplashState({
    this.status = SplashStatus.initializing,
    this.engine = const EngineStatus.unavailable(),
    this.failure,
  });

  final SplashStatus status;
  final EngineStatus engine;
  final Failure? failure;

  @override
  List<Object?> get props => [status, engine, failure];
}

sealed class SplashEvent {}

final class SplashStarted extends SplashEvent {}

final class SplashRetryRequested extends SplashEvent {}

/// Runs the initialization checks (yt-dlp binary, FFmpeg availability,
/// storage readiness) while the brand moment plays.
class SplashBloc extends Bloc<SplashEvent, SplashState> {
  SplashBloc({required CheckEngineStatus checkEngineStatus})
      : _checkEngineStatus = checkEngineStatus,
        super(const SplashState()) {
    on<SplashStarted>(_onStarted);
    on<SplashRetryRequested>(_onStarted);
  }

  final CheckEngineStatus _checkEngineStatus;

  Future<void> _onStarted(
    SplashEvent event,
    Emitter<SplashState> emit,
  ) async {
    emit(
      state.copyWith(
        status: SplashStatus.initializing,
        failure: null,
      ),
    );

    // Keep the brand moment on screen for at least 1.4s.
    final minWait =
        Future<void>.delayed(const Duration(milliseconds: 1400));
    final result = await _checkEngineStatus();
    await minWait;

    result.fold(
      (failure) => emit(
        state.copyWith(status: SplashStatus.failed, failure: failure),
      ),
      (engine) => emit(state.copyWith(status: SplashStatus.ready, engine: engine)),
    );
  }
}

extension _SplashStateX on SplashState {
  SplashState copyWith({
    SplashStatus? status,
    EngineStatus? engine,
    Object? failure = _sentinel,
  }) {
    return SplashState(
      status: status ?? this.status,
      engine: engine ?? this.engine,
      failure: identical(failure, _sentinel) ? this.failure : failure as Failure?,
    );
  }
}

const Object _sentinel = Object();
