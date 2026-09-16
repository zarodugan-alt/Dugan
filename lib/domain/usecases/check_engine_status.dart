import 'package:dartz/dartz.dart';

import '../../core/error/failures.dart';
import '../entities/engine_status.dart';
import '../repositories/media_repository.dart';

class CheckEngineStatus {
  const CheckEngineStatus(this._repository);

  final MediaRepository _repository;

  Future<Either<Failure, EngineStatus>> call() =>
      _repository.checkEngineStatus();
}
