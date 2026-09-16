# Dugan — Architecture

Implementation of the "Premium Video Downloader" architecture spec, mapped
file-by-file. Start with the [README](README.md) for setup.

## 1. Layers

```
presentation/                     ← Flutter + BLoC/Cubit (rebuilds UI only)
  splash/ home/ format_sheet/ downloads/ history/ player/ settings/ login/
  bloc/   → HomeBloc, DownloadsBloc, HistoryBloc, SplashBloc
  cubit/  → FormatSelectionCubit, SettingsCubit, LoginCubit, RootShellCubit
domain/                           ← pure Dart, zero Flutter imports
  entities/    → MediaInfo, FormatOption, SubtitleTrack, DownloadRequest,
                 DownloadTask (+DownloadStatus), HistoryItem, AppSettings,
                 EngineStatus
  repositories/→ 5 abstract contracts
  usecases/    → ParseUrl, StartDownload, Pause/Resume/Cancel/Retry,
                 WatchDownloads, Watch/Delete/ClearHistory, Load/SaveSettings,
                 Capture/Import/ClearCookies, CheckEngineStatus, RecentParses
data/                             ← implementations
  models/      → yt-dlp JSON ⇄ entity mappers
  datasources/
    native/    → engine + download + system platform channels
    local/     → Hive (settings, history, recent parses), cookie store
  repositories/→ 5 implementations
  mappers/     → DownloadRequest → native task map
core/                             ← theme (DuganColors ThemeExtension), DI,
                                    failures, ErrorMapper, utils, widgets
```

**Dependency rule:** `presentation → domain ← data`. Entities and use cases
never import Flutter — the whole download pipeline is unit-testable without
a device (see `test/`). Swapping yt-dlp for another extractor means
implementing `MediaRepository` + `DownloadRepository` against different data
sources; nothing else changes.

## 2. Download pipeline (URL → file)

```
User pastes URL
   ↓ HomeScreen → HomeBloc → ParseUrl
MediaRepositoryImpl
   ├─ validates URL (UrlUtils)
   ├─ writes cookie jar to <app-support>/cookies.txt (if any)
   └─ MethodChannel app.dugan/engine → extractInfo
        ↓ Kotlin (worker thread) → Chaquopy
        ytdlp_bridge.extract_info(): yt-dlp --dump-single-json --no-download
        ↓ sanitised JSON (formats, subtitles, thumbnails…)
MediaInfoModel.fromJson → MediaInfo
   ↓ HomeBloc emits HomeStatus.loaded → format sheet opens
FormatSelectionCubit → DownloadRequest (selector, container, subs, template)
   ↓ HomeBloc → StartDownload
DownloadRepositoryImpl
   ├─ guards: live streams, Wi-Fi-only, output dir creation
   ├─ DownloadRequestMapper → native task (format selector composed here)
   └─ MethodChannel app.dugan/downloads → start
        ↓ Kotlin: injects ffmpegDir/workDir, starts foreground service,
          calls Python start(task, maxWorkers)
        ↓ ThreadPoolExecutor → _run_task()
             yt-dlp: progress_hooks (500 ms throttled, combined
                     video+audio byte totals) + postprocessor_hooks
                     (Merger/ExtractAudio/EmbedThumbnail/…)
             ↘ DownloadEvents.emitEvent() (java jclass) → EventChannel
DownloadRepositoryImpl._onEngineEvent
   ├─ folds progress into DownloadTask (copyWith)
   ├─ NETWORK failures: auto-retry (2 s → 4 s → 8 s, max 3)
   ├─ completed: file size probed, HistoryItem persisted to Hive,
   │            optional gallery export
   └─ BehaviorSubject<List<DownloadTask>> re-emits
        ↓ DownloadsBloc (single subscription, folds active/finished)
        ↓ BlocBuilder rebuilds only the affected cards
```

Pause/resume/cancel: Kotlin → Python flags checked inside the yt-dlp
progress hook (raising `DownloadCancelled` subclasses). Resume re-runs the
same task; `continuedl` picks up the `.part` file. Cancel deletes tracked
`.part` files.

## 3. Native bridge contracts

| Channel | Method | Payload |
|---------|--------|---------|
| `app.dugan/engine` | `checkStatus` | → `{pythonVersion, ytdlpVersion, ffmpegAvailable}` |
| | `extractInfo` | `{url, options{userAgent, cookieFile, extraArgs, noplaylist}}` → sanitised media JSON |
| `app.dugan/downloads` | `start` | full native task map (below) |
| | `pause` / `cancel` | `{taskId}` |
| `app.dugan/system` | `isWifiConnected`, `openFile` | |
| `app.dugan/downloads/events` | EventChannel | progress maps (below) |

Native task map (Dart-built; Kotlin adds `ffmpegDir`, `workDir`):
`taskId, title, url, formatSelector, mergeFormat, audioOnly, audioContainer,
audioBitrateKbps, embedMetadata, embedThumbnail, embedSubs, subLangs,
outputDir, filenameTemplate, userAgent, cookieFile, extraArgs, maxConcurrent`.

Event map:
`taskId, status(downloading|postprocessing|paused|completed|failed|canceled),
progress, downloadedBytes, totalBytes, speedBps, stage, error, code,
filePath, title`.

Error codes are classified in Python (`PRIVATE`, `LOGIN_REQUIRED`,
`GEO_RESTRICTED`, `NETWORK`, `UNSUPPORTED`, `UNAVAILABLE`, `STORAGE`) and
mapped in Dart by `ErrorMapper` to typed failures with spec-compliant user
messages and recovery actions (retry / login CTA / auto-retry).

## 4. State management

- **App-lifetime singletons** (via `get_it`, exposed with `BlocProvider.value`
  in `app.dart`): `SettingsCubit`, `DownloadsBloc`, `HistoryBloc`.
- **Screen-scoped**: `HomeBloc` (parse flow), `FormatSelectionCubit` (per
  sheet), `SplashBloc`, `LoginCubit`, `RootShellCubit`.
- Downloads subscription uses the canonical pattern: one event subscribes to
  the repository stream and re-dispatches internal `DownloadsUpdated` events
  (no `emit.forEach` deadlocks; other events stay processable).
- `BlocSelector`-style fine-grained rebuilds via `context.select` /
  `buildWhen` — only the changing download card rebuilds.

## 5. Design system

`core/theme/app_colors.dart` holds the exact spec tokens as a
`ThemeExtension<DuganColors>` (dark + light variants, 6 accent presets).
`AppTheme` builds Material 3 `ThemeData` with overridden surfaces,
`GoogleFonts.plusJakartaSans` (Display 32/700 · Headline 20/600 · Body 14/400
· Label 12/500-tracking) and component themes (nav bar, tabs, sheets,
dialogs, switches, sliders, chips). Glass = `BackdropFilter(blur 20)` +
72–85% surface + 1px 8%-white border. Glow shadows replace elevation.

## 6. Persistence

Hive boxes store JSON strings (no codegen):

- `dugan_settings` — one `settings` key (`AppSettingsModel`).
- `dugan_history` — one key per `HistoryItem`.
- `dugan_recent_parses` — capped ordered list of `MediaInfo` JSONs.

Live updates flow through `box.listenable()` → `listenableToStream` →
repository streams → blocs.

## 7. iOS roadmap

1. Regenerate platform folder: `flutter create --platforms ios .`
2. Embed Python: add [Python-Apple-support](https://github.com/beeware/Python-Apple-support)
   frameworks for arm64 simulator/device via SPM/CocoaPods.
3. Reuse `ytdlp_bridge.py` verbatim; replace the `jclass` forwarder with a
   closure passed from Swift (or keep a tiny `DownloadEvents` NSObject).
4. Implement `app.dugan/*` channels in `AppDelegate.swift`
   (`PythonBridge` via `PythonKit`/`Rubicon`), mirror `MainActivity.kt`.
5. FFmpeg: `ffmpeg-kit` is retired; build ffmpeg for iOS (arm64) and expose
   the binary path the same way.
6. Foreground service → `URLSession` background tasks or just keep the
   process alive with `beginBackgroundTask` for short downloads.

## 8. Testing strategy

- **Domain**: use-case delegation + `ErrorMapper` matrix (`parse_url_test.dart`).
- **Models**: yt-dlp fixture → entity mapping incl. storyboard filtering,
  sorting, round-trip (`media_info_model_test.dart`).
- **Blocs**: `bloc_test` covers parse states, auto-download, stream folding,
  search/sort/selection, CTA mapping.
- **Cubits**: format-selection validation matrix (video-only needs audio,
  progressive exempt, size estimates, template fallback).
- Manual/E2E on device: real downloads (network + Chaquopy).
