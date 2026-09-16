import 'package:flutter/material.dart';

import 'app_colors.dart';

/// Convenience access to the [DuganColors] theme extension.
extension DuganContextX on BuildContext {
  DuganColors get dugan => Theme.of(this).extension<DuganColors>()!;
}
