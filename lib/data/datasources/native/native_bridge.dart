import 'package:flutter/services.dart';

import '../../../core/constants/app_constants.dart';

/// Thrown by the native bridge when the engine reports an error. The [code]
/// matches the classification produced in `ytdlp_bridge.py` and is mapped to
/// a typed [Failure] by [ErrorMapper].
class EngineException implements Exception {
  const EngineException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'EngineException($code): $message';
}

/// Central access to the platform channels talking to the native
/// (Chaquopy/Python) engine.
class NativeBridge {
  NativeBridge._();

  static const MethodChannel engine =
      MethodChannel(AppConstants.engineChannel);

  static const MethodChannel downloads =
      MethodChannel(AppConstants.downloadsChannel);

  static const MethodChannel system =
      MethodChannel(AppConstants.systemChannel);

  static const EventChannel downloadEvents =
      EventChannel(AppConstants.downloadEventsChannel);
}
