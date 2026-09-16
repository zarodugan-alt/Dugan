import 'dart:io';

import 'package:path_provider/path_provider.dart';

/// Persists the Netscape-format cookie jar to disk so yt-dlp can consume it
/// (`--cookies`), refreshing it before every extraction/download.
class CookieStore {
  const CookieStore();

  Future<String> _cookieFilePath() async {
    final support = await getApplicationSupportDirectory();
    return '${support.path}/cookies.txt';
  }

  /// Writes [netscapeText] and returns the file path, or deletes the file
  /// and returns `null` when the jar is empty.
  Future<String?> writeCookieFile(String netscapeText) async {
    final path = await _cookieFilePath();
    final file = File(path);
    final trimmed = netscapeText.trim();
    if (trimmed.isEmpty) {
      if (await file.exists()) {
        await file.delete();
      }
      return null;
    }
    var content = trimmed;
    if (!content.startsWith('#')) {
      content = '# Netscape HTTP Cookie File\n$content';
    }
    if (!content.endsWith('\n')) {
      content = '$content\n';
    }
    await file.writeAsString(content, flush: true);
    return path;
  }
}
