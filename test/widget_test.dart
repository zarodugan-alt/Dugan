import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:dugan/core/theme/app_colors.dart';
import 'package:dugan/core/theme/app_theme.dart';
import 'package:dugan/core/widgets/glass_card.dart';
import 'package:dugan/core/widgets/gradient_button.dart';
import 'package:dugan/core/widgets/gradient_progress_bar.dart';

void main() {
  // Pumps widgets inside the real Dugan theme so the ThemeExtension is
  // available — also guards against regressions in the design tokens.
  Widget wrap(Widget child) {
    return MaterialApp(
      theme: AppTheme.dark(),
      home: Scaffold(body: Center(child: child)),
    );
  }

  testWidgets('GradientButton renders and taps', (tester) async {
    var taps = 0;
    await tester.pumpWidget(
      wrap(
        GradientButton(
          label: 'Download',
          icon: Icons.download_rounded,
          onPressed: () => taps++,
        ),
      ),
    );

    expect(find.text('Download'), findsOneWidget);
    await tester.tap(find.byType(GradientButton));
    await tester.pumpAndSettle();
    expect(taps, 1);
  });

  testWidgets('GradientButton is disabled when onPressed is null',
      (tester) async {
    await tester.pumpWidget(
      wrap(const GradientButton(label: 'Download')),
    );
    final button = tester.widget<GradientButton>(
      find.byType(GradientButton),
    );
    expect(button.onPressed, isNull);
  });

  testWidgets('GlassCard and progress bar render with the theme extension',
      (tester) async {
    await tester.pumpWidget(
      wrap(
        const Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            GlassCard(child: Text('Inside glass')),
            SizedBox(height: 12),
            GradientProgressBar(progress: 0.5),
          ],
        ),
      ),
    );

    expect(find.text('Inside glass'), findsOneWidget);
    await tester.pumpAndSettle();
  });

  testWidgets('theme carries the spec palette', (tester) async {
    await tester.pumpWidget(wrap(const SizedBox()));

    final context = tester.element(find.byType(Scaffold));
    final colors = Theme.of(context).extension<DuganColors>()!;
    expect(colors.surfacePrimary, const Color(0xFF0A0A0F));
    expect(colors.surfaceSecondary, const Color(0xFF14141F));
    expect(colors.surfaceTertiary, const Color(0xFF1E1E2D));
    expect(colors.accentPrimary, const Color(0xFF5B47E5));
    expect(colors.accentSecondary, const Color(0xFF8B5CF6));
    expect(colors.success, const Color(0xFF10B981));
    expect(colors.warning, const Color(0xFFF59E0B));
    expect(colors.error, const Color(0xFFEF4444));
    expect(colors.textPrimary, const Color(0xFFF8FAFC));
    expect(colors.textSecondary, const Color(0xFF94A3B8));
  });
}
