import 'dart:async';

import 'package:flutter/foundation.dart';

/// Bridges a [Listenable] (e.g. Hive's `box.listenable()`) into a broadcast
/// [Stream] that emits [getValue] on every notification.
Stream<T> listenableToStream<T>(Listenable listenable, T Function() getValue) {
  late final StreamController<T> controller;
  void Function()? listener;

  controller = StreamController<T>.broadcast(
    onListen: () {
      listener = () {
        if (!controller.isClosed) {
          controller.add(getValue());
        }
      };
      listenable.addListener(listener!);
    },
    onCancel: () {
      if (listener != null) {
        listenable.removeListener(listener!);
      }
    },
  );

  return controller.stream;
}
