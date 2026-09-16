# jniLibs — FFmpeg binaries

yt-dlp needs a working `ffmpeg` executable for post-processing
(merging video + audio streams, extracting audio, embedding metadata,
thumbnails and subtitles). On Android the reliable way to ship one is as a
"native library" so the packager extracts it to `nativeLibraryDir`.

## What goes here

```
jniLibs/
├── arm64-v8a/libffmpeg.so     ← static ffmpeg build for arm64
├── armeabi-v7a/libffmpeg.so   ← static ffmpeg build for armv7
└── x86_64/libffmpeg.so        ← static ffmpeg build for x86_64 (emulator)
```

Each `libffmpeg.so` is simply an **Android static ffmpeg executable renamed
to the `lib*.so` convention**. At runtime the Python bridge copies it to
`<cache>/bin/ffmpeg` and passes that directory as yt-dlp's
`ffmpeg_location`.

## Where to get builds

Option A — build from source with the NDK (recommended, reproducible):

```bash
# Requires Android NDK + Make; produces ffmpeg binaries per ABI
git clone https://github.com/Javernaut/ffmpeg-android-maker.git
cd ffmpeg-android-maker
# Select ABIs via environment, e.g.:
export FF_ANDROID_ARCHITECTURES=arm arm64 x86_64
./make.sh
# Outputs are in externals/<abi>/bin/ffmpeg
```

Then copy each binary here, renamed to `libffmpeg.so`.

Option B — any trusted provider of static Android ffmpeg builds. Verify the
binary targets Android (bionic) and matches the ABI of the folder you place
it in:

```bash
file libffmpeg.so
# e.g. "ELF 64-bit LSB executable, ARM aarch64, ... dynamically linked ... for Android"
```

> Note: desktop Linux builds (glibc) will **not** run on Android.

## Verification

After installing the binaries, launch Dugan — the splash screen reports
`FFmpeg ready`. Without the binaries the app still parses and downloads
progressive formats, but merging, audio extraction and embedding will fail.

`.so` files are git-ignored on purpose (size/licensing); CI or each
developer provides them locally.
