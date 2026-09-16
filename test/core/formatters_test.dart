import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:dugan/core/theme/app_colors.dart';
import 'package:dugan/core/utils/formatters.dart';

void main() {
  group('Formatters.bytes', () {
    test('formats byte ranges', () {
      expect(Formatters.bytes(500), '500 B');
      expect(Formatters.bytes(812 * 1024), '812 KB');
      expect(Formatters.bytes(245 * 1024 * 1024), '245 MB');
      expect(Formatters.bytes(null), '—');
      expect(Formatters.bytes(0), '—');
    });
  });

  group('Formatters.duration', () {
    test('mm:ss and h:mm:ss', () {
      expect(Formatters.duration(const Duration(minutes: 12, seconds: 34)), '12:34');
      expect(
        Formatters.duration(const Duration(hours: 1, minutes: 2, seconds: 3)),
        '1:02:03',
      );
      expect(Formatters.duration(null), '—');
    });

    test('compact', () {
      expect(
        Formatters.durationCompact(const Duration(seconds: 45)),
        '45s',
      );
      expect(
        Formatters.durationCompact(const Duration(minutes: 2, seconds: 14)),
        '2m 14s',
      );
      expect(
        Formatters.durationCompact(const Duration(hours: 2, minutes: 14)),
        '2h 14m',
      );
    });
  });

  group('Formatters.speed / eta', () {
    test('speed formats', () {
      expect(Formatters.speed(4.2 * 1024 * 1024), '4.2 MB/s');
      expect(Formatters.speed(null), '—');
    });

    test('eta estimates', () {
      expect(
        Formatters.eta(remainingBytes: 100, speed: 10),
        '10s left',
      );
      expect(Formatters.eta(remainingBytes: 100, speed: null), '—');
      expect(Formatters.eta(remainingBytes: 0, speed: 10), '—');
    });
  });

  group('Formatters.compactCount', () {
    test('abbreviates counts', () {
      expect(Formatters.compactCount(950), '950');
      expect(Formatters.compactCount(1200), '1.2K');
      expect(Formatters.compactCount(4500000), '4.5M');
      expect(Formatters.compactCount(null), '—');
    });
  });

  group('Formatters.relativeDate', () {
    test('today / yesterday / days', () {
      final now = DateTime.now();
      expect(Formatters.relativeDate(now), 'Today');
      expect(Formatters.relativeDate(now.subtract(const Duration(days: 1))), 'Yesterday');
      expect(Formatters.relativeDate(now.subtract(const Duration(days: 3))), '3 days ago');
      expect(Formatters.relativeDate(now.subtract(const Duration(days: 14))), '2 weeks ago');
    });
  });

  group('Formatters.speedColor', () {
    test('colour coding per spec', () {
      final colors = DuganColors.dark(accent: const Color(0xFF5B47E5));
      expect(
        Formatters.speedColor(6 * 1024 * 1024, colors),
        colors.success,
      ); // >5 MB/s → green
      expect(
        Formatters.speedColor(2 * 1024 * 1024, colors),
        colors.warning,
      ); // 1–5 MB/s → amber
      expect(
        Formatters.speedColor(500 * 1024, colors),
        colors.error,
      ); // <1 MB/s → red
      expect(Formatters.speedColor(null, colors), colors.textSecondary);
    });
  });

  group('Formatters.resolutionLabel', () {
    test('label with fps suffix', () {
      expect(Formatters.resolutionLabel(1080), '1080p');
      expect(Formatters.resolutionLabel(1080, 60), '1080p60');
      expect(Formatters.resolutionLabel(null), 'audio');
    });
  });
}
