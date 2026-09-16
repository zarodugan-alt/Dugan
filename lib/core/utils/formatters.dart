import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../theme/app_colors.dart';

/// Formatting helpers shared by every screen (sizes, speeds, durations…).
enum Formatters {
  ;

  /// `245 MB`, `1.4 GB`, `812 KB`, `—` for null/invalid.
  static String bytes(num? value) {
    if (value == null || value <= 0) {
      return '—';
    }
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    var size = value.toDouble();
    var unit = 0;
    while (size >= 1024 && unit < units.length - 1) {
      size /= 1024;
      unit++;
    }
    if (unit == 0) {
      return '${value.toInt()} B';
    }
    return size >= 100
        ? '${size.toStringAsFixed(0)} ${units[unit]}'
        : '${size.toStringAsFixed(1)} ${units[unit]}';
  }

  /// `4.2 MB/s`, `812 KB/s`.
  static String speed(num? bytesPerSecond) {
    if (bytesPerSecond == null || bytesPerSecond <= 0) {
      return '—';
    }
    return '${bytes(bytesPerSecond)}/s';
  }

  /// `12:34` or `1:02:03`.
  static String duration(Duration? duration) {
    if (duration == null || duration.inMilliseconds <= 0) {
      return '—';
    }
    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    final seconds = duration.inSeconds.remainder(60).toString().padLeft(2, '0');
    if (hours > 0) {
      return '$hours:${minutes.toString().padLeft(2, '0')}:$seconds';
    }
    return '$minutes:$seconds';
  }

  /// `2m 14s` — compact human duration for cards.
  static String durationCompact(Duration? duration) {
    if (duration == null || duration.inSeconds <= 0) {
      return '—';
    }
    if (duration.inHours > 0) {
      final m = duration.inMinutes.remainder(60);
      return '${duration.inHours}h ${m}m';
    }
    if (duration.inMinutes > 0) {
      return '${duration.inMinutes}m ${duration.inSeconds.remainder(60)}s';
    }
    return '${duration.inSeconds}s';
  }

  /// Estimated time remaining, e.g. `45s left` / `2m 10s left`.
  static String eta({required num remainingBytes, required num? speed}) {
    if (speed == null || speed <= 0 || remainingBytes <= 0) {
      return '—';
    }
    final seconds = (remainingBytes / speed).round();
    return '${durationCompact(Duration(seconds: seconds))} left';
  }

  /// `Today`, `Yesterday`, `3 days ago`, `2 weeks ago`, then `Jan 12, 2026`.
  static String relativeDate(DateTime date) {
    final now = DateTime.now();
    final local = date.toLocal();
    final diff = DateTime(now.year, now.month, now.day)
        .difference(DateTime(local.year, local.month, local.day))
        .inDays;
    if (diff == 0) {
      return 'Today';
    }
    if (diff == 1) {
      return 'Yesterday';
    }
    if (diff < 7) {
      return '$diff days ago';
    }
    if (diff < 30) {
      final weeks = diff ~/ 7;
      return weeks == 1 ? '1 week ago' : '$weeks weeks ago';
    }
    return DateFormat.yMMMd().format(local);
  }

  /// `1.2K`, `4.5M` view counts.
  static String compactCount(num? value) {
    if (value == null || value <= 0) {
      return '—';
    }
    if (value >= 1000000) {
      return '${(value / 1000000).toStringAsFixed(1)}M';
    }
    if (value >= 1000) {
      return '${(value / 1000).toStringAsFixed(1)}K';
    }
    return value.toInt().toString();
  }

  /// Speed colour coding per the spec:
  /// green > 5 MB/s · amber 1–5 MB/s · red < 1 MB/s.
  static Color speedColor(double? bytesPerSecond, DuganColors colors) {
    if (bytesPerSecond == null || bytesPerSecond <= 0) {
      return colors.textSecondary;
    }
    if (bytesPerSecond > 5 * 1024 * 1024) {
      return colors.success;
    }
    if (bytesPerSecond > 1024 * 1024) {
      return colors.warning;
    }
    return colors.error;
  }

  /// `1080p60` from height + fps.
  static String resolutionLabel(int? height, [int? fps]) {
    if (height == null || height <= 0) {
      return 'audio';
    }
    final base = '${height}p';
    if (fps != null && fps > 30) {
      return '${base}${fps.round()}';
    }
    return base;
  }
}
