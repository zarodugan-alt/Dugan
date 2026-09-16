import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import 'app.dart';
import 'core/di/injection.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  if (kDebugMode) {
    Bloc.observer = _DuganBlocObserver();
  }

  await configureDependencies();

  runApp(const DuganApp());
}

/// Logs bloc lifecycle events in debug builds only.
class _DuganBlocObserver extends BlocObserver {
  @override
  void onCreate(BlocBase<dynamic> bloc) {
    super.onCreate(bloc);
    debugPrint('onCreate → ${bloc.runtimeType}');
  }

  @override
  void onTransition(
    BlocBase<dynamic> bloc,
    Transition<dynamic, dynamic> transition,
  ) {
    super.onTransition(bloc, transition);
    debugPrint('$transition');
  }

  @override
  void onError(BlocBase<dynamic> bloc, Object error, StackTrace stackTrace) {
    super.onError(bloc, error, stackTrace);
    debugPrint('${bloc.runtimeType} error → $error');
  }
}
