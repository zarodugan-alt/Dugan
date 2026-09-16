import 'package:dartz/dartz.dart';

import '../../core/error/failures.dart';
import '../entities/media_info.dart';
import '../repositories/media_repository.dart';

/// Extracts metadata and formats for a media URL.
class ParseUrl {
  const ParseUrl(this._repository);

  final MediaRepository _repository;

  Future<Either<Failure, MediaInfo>> call(String url) =>
      _repository.parseUrl(url);
}
