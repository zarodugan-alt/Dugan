import 'package:flutter/services.dart';

import '../../../core/utils/map_deep.dart';
import 'native_bridge.dart';

/// Drives the native download engine and consumes its progress events.
///
/// The native side runs yt-dlp inside a Python thread pool (Chaquopy) and
/// streams progress maps over an [EventChannel]:
///
/// ```
/// { taskId, status: downloading|postprocessing|paused|completed|failed|canceled,
///   progress, downloadedBytes, totalBytes, speedBps,
///   stage, error, code, filePath, title }
/// ```
class DownloadNativeEngine {
  const DownloadNativeEngine();

  /// Enqueues (or re-enqueues after pause/failure) a native task.
  Future<void> start(Map<String, dynamic> task) async {
    try {
      await NativeBridge.downloads.invokeMethod<void>('start', task);
    } on PlatformException catch (e) {
      throw EngineException(e.code, e.message ?? 'Could not start download.');
    } on MissingPluginException {
      throw const EngineException(
        'ENGINE',
        'Native download engine is not available on this platform.',
      );
    }
  }

  Future<void> pause(String taskId) async {
    try {
      await NativeBridge.downloads
          .invokeMethod<void>('pause', <String, dynamic>{'taskId': taskId});
    } on PlatformException catch (e) {
      throw EngineException(e.code, e.message ?? 'Could not pause download.');
    } on MissingPluginException {
      throw const EngineException('ENGINE', 'Engine unavailable.');
    }
  }

  Future<void> cancel(String taskId) async {
    try {
      await NativeBridge.downloads
          .invokeMethod<void>('cancel', <String, dynamic>{'taskId': taskId});
    } on PlatformException catch (e) {
      throw EngineException(e.code, e.message ?? 'Could not cancel download.');
    } on MissingPluginException {
      throw const EngineException('ENGINE', 'Engine unavailable.');
    }
  }

  /// `true` when the device is on Wi-Fi (unknown platforms report `true` so
  /// the Wi-Fi-only guard never dead-ends).
  Future<bool> isWifiConnected() async {
    try {
      final result =
          await NativeBridge.system.invokeMethod<bool>('isWifiConnected');
      return result ?? true;
    } on PlatformException {
      return true;
    } on MissingPluginException {
      return true;
    }
  }

  /// Broadcast stream of download progress events.
  Stream<Map<String, dynamic>> get events {
    return NativeBridge.downloadEvents
        .receiveBroadcastStream()
        .map((dynamic event) => asStringKeyedMap(event));
  }
}
