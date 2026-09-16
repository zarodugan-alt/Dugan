import 'package:flutter/services.dart';
import 'package:gallery_saver/gallery_saver.dart';

import 'native_bridge.dart';

/// Device-level integrations: gallery export, opening files with external
/// apps.
class SystemServices {
  const SystemServices();

  /// Copies a finished video into the system gallery (Android MediaStore /
  /// iOS Photos).
  Future<bool> saveToGallery(String filePath) async {
    try {
      return await GallerySaver.saveVideo(filePath, albumName: 'Dugan') ??
          false;
    } on Exception {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  /// Opens the file with an external app ("Open with…" action).
  Future<bool> openFile(String filePath) async {
    try {
      final result = await NativeBridge.system
          .invokeMethod<bool>('openFile', <String, dynamic>{'path': filePath});
      return result ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }
}
