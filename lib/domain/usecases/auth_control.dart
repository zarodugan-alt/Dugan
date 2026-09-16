import 'package:dartz/dartz.dart';

import '../../core/error/failures.dart';
import '../repositories/auth_repository.dart';

/// Captures cookies for a platform's hosts from the WebView cookie store.
class CaptureCookies {
  const CaptureCookies(this._repository);

  final AuthRepository _repository;

  Future<Either<Failure, int>> call(List<String> hosts) =>
      _repository.captureCookies(hosts);
}

class ImportCookies {
  const ImportCookies(this._repository);

  final AuthRepository _repository;

  Future<Either<Failure, int>> call(String netscapeText) =>
      _repository.importCookies(netscapeText);
}

class ClearCookies {
  const ClearCookies(this._repository);

  final AuthRepository _repository;

  Future<void> call() => _repository.clearCookies();
}
