import 'package:dio/dio.dart';

import '../../core/constants/app_constants.dart';

/// Checks GitHub releases for a newer build (Settings → About).
class UpdateChecker {
  UpdateChecker() : _dio = Dio(
          BaseOptions(
            connectTimeout: const Duration(seconds: 8),
            receiveTimeout: const Duration(seconds: 8),
            headers: {'Accept': 'application/vnd.github+json'},
          ),
        );

  final Dio _dio;

  /// Returns the latest published version tag, or `null` when unavailable.
  Future<String?> latestVersion() async {
    try {
      final response = await _dio.get<dynamic>(AppConstants.releasesApiUrl);
      final data = response.data;
      if (data is Map && data['tag_name'] is String) {
        return data['tag_name'] as String;
      }
      return null;
    } on Exception {
      return null;
    }
  }
}
