import 'package:dartz/dartz.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:dugan/core/error/error_mapper.dart';
import 'package:dugan/core/error/failures.dart';
import 'package:dugan/domain/entities/media_info.dart';
import 'package:dugan/domain/repositories/media_repository.dart';
import 'package:dugan/domain/usecases/parse_url.dart';
import 'package:mocktail/mocktail.dart';

class MockMediaRepository extends Mock implements MediaRepository {}

void main() {
  late MockMediaRepository repository;

  const media = MediaInfo(
    id: 'x',
    title: 'Title',
    sourceUrl: 'https://youtu.be/x',
    platform: 'youtube',
  );

  setUp(() {
    repository = MockMediaRepository();
  });

  test('delegates to the repository and returns Right on success', () async {
    when(() => repository.parseUrl(any()))
        .thenAnswer((_) async => const Right(media));

    final result = await ParseUrl(repository)('https://youtu.be/x');

    expect(result, const Right(media));
    verify(() => repository.parseUrl('https://youtu.be/x')).called(1);
  });

  test('propagates failures as Left', () async {
    when(() => repository.parseUrl(any()))
        .thenAnswer((_) async => const Left(AuthRequiredFailure()));

    final result = await ParseUrl(repository)('https://youtu.be/x');

    expect(result.isLeft(), isTrue);
  });

  group('ErrorMapper', () {
    test('maps engine codes to typed failures', () {
      expect(
        ErrorMapper.fromCode('PRIVATE', null),
        isA<VideoUnavailableFailure>(),
      );
      expect(
        ErrorMapper.fromCode('LOGIN_REQUIRED', null),
        isA<AuthRequiredFailure>(),
      );
      expect(
        ErrorMapper.fromCode('NETWORK', null),
        isA<NetworkFailure>(),
      );
      expect(
        ErrorMapper.fromCode('UNSUPPORTED', null),
        isA<InvalidUrlFailure>(),
      );
      expect(
        ErrorMapper.fromCode('whatever', 'boom'),
        isA<UnknownFailure>(),
      );
    });

    test('retryable and auth classifications', () {
      expect(ErrorMapper.isRetryable(const NetworkFailure()), isTrue);
      expect(
        ErrorMapper.isRetryable(const VideoUnavailableFailure()),
        isFalse,
      );
      expect(ErrorMapper.needsAuth(const AuthRequiredFailure()), isTrue);
      expect(ErrorMapper.needsAuth(const NetworkFailure()), isFalse);
    });

    test('user messages follow the spec table', () {
      expect(
        ErrorMapper.userMessage(const VideoUnavailableFailure()),
        'This video is private or removed.',
      );
    });
  });
}
