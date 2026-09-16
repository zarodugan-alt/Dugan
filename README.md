# Dugan 📥⚡

**A premium video downloader for Android, powered by [yt-dlp](https://github.com/yt-dlp/yt-dlp).**

Dugan downloads video and audio from 1800+ sites (YouTube, TikTok, Instagram,
X/Twitter, Bilibili, Facebook, Reddit, Vimeo, Twitch…) with a gorgeous
OLED-dark, glassmorphism UI — clean architecture, BLoC state management and a
real native download engine with FFmpeg post-processing.

```
┌─────────────────────────────────────────────────────┐
│                 PRESENTATION LAYER                   │
│  (Flutter UI + BLoC/Cubit State Management)         │
│  HomeScreen │ DownloadsScreen │ Library │ Player …   │
├─────────────────────────────────────────────────────┤
│                   DOMAIN LAYER                       │
│  UseCases: ParseUrl, StartDownload, GetFormats …     │
│  Entities: MediaInfo, DownloadTask, FormatOption     │
│  Repository Interfaces (abstract contracts)          │
├─────────────────────────────────────────────────────┤
│                    DATA LAYER                         │
│  Repositories (implementation)                       │
│  Data Sources: yt-dlp engine, FFmpeg, Hive DB        │
│  Platform Channels → Android native (Chaquopy)       │
└─────────────────────────────────────────────────────┘
```

## Features

- **Paste → Parse → Download** flow with shimmer loading, recent-parses rail
  and a one-tap **Best Quality** action (auto-merges best video + audio).
- **Format selection sheet** — resolution/codec/FPS grid, audio-only
  extraction (MP3/M4A/OPUS/FLAC/WAV with 64–320 kbps bitrate), subtitle
  language multi-select, filename templates, merge toggles.
- **Live download monitor** — animated gradient progress, colour-coded
  speed (green > 5 MB/s · amber 1–5 · red < 1), ETA, FFmpeg
  post-processing stages, pause/resume (resumes `.part` files),
  swipe-to-cancel with undo, auto-retry with exponential backoff (3×).
- **Library** — searchable/sortable history with thumbnails, batch
  share/delete, re-download, open-with, built-in player (Chewie + speed
  control).
- **WebView login** — sign in to YouTube/Bilibili/Instagram/Facebook;
  cookies (including HttpOnly) are captured in Netscape format and injected
  into yt-dlp for private/age-restricted content.
- **Premium dark theme** — exact spec palette (#0A0A0F surfaces,
  #5B47E5 → #8B5CF6 accents), glass cards (blur 20 + 8% borders), glow
  shadows instead of elevation, Plus Jakarta Sans, 6 accent presets,
  light/dark/system modes, optional gradient backdrop.
- **Background-safe downloads** — Android foreground service with progress
  notification; downloads continue while the app is minimized.
- **Fully offline settings** — default quality, download folder, 1–5
  concurrent downloads, Wi-Fi-only, save-to-gallery, custom User-Agent,
  cookie jar, allow-listed extra yt-dlp args.

## Requirements

| Tool | Version | Purpose |
|------|---------|---------|
| Flutter | ≥ 3.24 | SDK floor (Dart ≥ 3.5) |
| Android SDK | API 34+ | compileSdk via Flutter |
| NDK | any recent | builds ABIs arm64-v8a / armeabi-v7a / x86_64 |
| JDK | 17 | AGP 8.3 requirement |

## Getting started

```bash
git clone https://github.com/zarodugan-alt/Dugan.git
cd Dugan

# 0. One-time: regenerate platform scaffolding that is not committed
#    (gradle wrapper, local.properties, .metadata). Existing files are kept.
flutter create --platforms android .

# 1. Restore Dart dependencies
flutter pub get

# 2. Run the unit + widget tests (domain, blocs, cubits, models)
flutter test

# 3. Run on a device (Android only — the engine is Chaquopy/Python based)
flutter run
```

The first build downloads the Chaquopy toolchain and pip-installs
`yt-dlp` + `mutagen` into the APK (adds ~25 MB).

### FFmpeg (required for merging, audio extraction, embedding)

yt-dlp needs an executable `ffmpeg`. Dugan expects it at
`android/app/src/main/jniLibs/<abi>/libffmpeg.so` (it is copied to the app
cache and passed as `ffmpeg_location`). Binaries are **git-ignored**:

```bash
# Option A — build with the NDK (recommended):
git clone https://github.com/Javernaut/ffmpeg-android-maker.git
cd ffmpeg-android-maker && ./make.sh
cp externals/*/bin/ffmpeg <dugan>/android/app/src/main/jniLibs/arm64-v8a/libffmpeg.so
# …repeat for armeabi-v7a and x86_64

# Option B — use any trusted static Android ffmpeg build
```

Full details in [`android/app/src/main/jniLibs/README.md`](android/app/src/main/jniLibs/README.md).
Without the binary the app still parses and downloads progressive formats,
but merging/embedding will fail (splash screen shows `FFmpeg missing`).

### iOS status

The Dart layer and platform channels are platform-agnostic, but the engine
bridge currently ships Android-only (Chaquopy). iOS support requires
embedding Python via [Python-Apple-support](https://github.com/beeware/Python-Apple-support)
and a Swift bridge implementing the same four channels — see
[`ARCHITECTURE.md`](ARCHITECTURE.md#ios-roadmap).

## Project structure

```
lib/
├── core/                  # theme, DI, errors, utils, shared widgets
│   ├── theme/             # DuganColors ThemeExtension + AppTheme
│   ├── di/injection.dart  # get_it service locator (no codegen)
│   ├── error/             # Failure hierarchy + ErrorMapper
│   └── widgets/           # GlassCard, GradientButton, progress bars…
├── domain/                # PURE Dart — entities, repo contracts, use cases
├── data/                  # models, Hive datasources, native bridge, repos
└── presentation/          # screens, blocs/cubits, feature widgets
android/
├── app/src/main/python/ytdlp_bridge.py   # yt-dlp engine (progress hooks…)
├── app/src/main/kotlin/…/                # MainActivity, foreground service
├── app/src/main/jniLibs/                 # ffmpeg binaries (user-provided)
└── app/build.gradle                      # Chaquopy 15 + pip install
test/                      # domain, bloc, cubit and model tests
```

## Testing

```bash
flutter test
```

Covers: formatters, URL/platform detection, yt-dlp JSON → entity mapping
(with a realistic fixture), error-code mapping, `HomeBloc` (parse/auto-download/CTA),
`DownloadsBloc` (stream folding + control events), `FormatSelectionCubit`
(validation rules, size estimates, request building) and `HistoryBloc`
(search/sort/batch).

## Deviations from the original spec (and why)

| Spec item | Shipped | Reason |
|-----------|---------|--------|
| `flutter_ytdlp_plugin` | Custom Chaquopy bridge | The plugin is extraction-only (no downloads/progress) and Android-only; the data-flow spec (progress events → BLoC) needs the custom bridge. |
| `flutter_downloader` | Native engine via Chaquopy + foreground service | It can't run yt-dlp post-processing or merges; the spec's own flow streams progress through the platform channel. |
| `injectable` (+build_runner) | Hand-written `get_it` module | Zero codegen, faster builds; same decoupling. |
| `awesome_video_player` | `video_player` + `chewie` | Compile-safe, actively maintained, covers HLS/DASH/local files with controls; DRM not needed for local playback. |
| `flare_glass` | Custom `BackdropFilter` GlassCard | Spec itself offers BackdropFilter as the alternative; no extra dependency. |
| Hive codegen adapters | JSON strings in Hive boxes | No `hive_generator`/build_runner needed. |

## License

MIT.
