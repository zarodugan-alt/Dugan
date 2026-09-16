import 'package:flutter/services.dart';

import '../../../core/utils/map_deep.dart';
import '../../../domain/entities/engine_status.dart';
import '../../../domain/entities/media_info.dart';
import '../../models/engine_status_model.dart';
import '../../models/media_info_model.dart';
import 'native_bridge.dart';

/// Calls into the native engine for health checks and metadata extraction
/// (`yt-dlp --dump-single-json --no-download`).
class EngineNativeDataSource {
  const EngineNativeDataSource();

  Future<EngineStatus> checkStatus() async {
    try {
      final dynamic raw =
          await NativeBridge.engine.invokeMethod<dynamic>('checkStatus');
      return EngineStatusModel.fromJson(asStringKeyedMap(raw));
    } on PlatformException catch (e) {
      throw EngineException(e.code, e.message ?? 'Engine check failed.');
    } on MissingPluginException {
      throw const EngineException(
        'ENGINE',
        'Native engine is not available on this platform. '
        'Run Dugan on an Android device with the Chaquopy build.',
      );
    }
  }

  /// [options] may contain `userAgent`, `cookieFile`, `extraArgs`
  /// (allow-listed) and `noplaylist`.
  Future<MediaInfo> extractInfo({
    required String url,
    Map<String, dynamic> options = const {},
  }) async {
    try {
      final dynamic raw = await NativeBridge.engine.invokeMethod<dynamic>(
        'extractInfo',
        <String, dynamic>{'url': url, 'options': options},
      );
      return MediaInfoModel.fromJson(asStringKeyedMap(raw));
    } on PlatformException catch (e) {
      throw EngineException(e.code, e.message ?? 'Extraction failed.');
    } on MissingPluginException {
      throw const EngineException(
        'ENGINE',
        'Native engine is not available on this platform.',
      );
    }
  }
}
