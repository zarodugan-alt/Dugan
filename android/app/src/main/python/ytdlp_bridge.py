"""Dugan native bridge — yt-dlp on Android via Chaquopy.

Exposed to Kotlin:
    check_status(ffmpeg_dir, work_dir) -> JSON string
    extract_info(url, options_json)    -> JSON string
    start(task_json, max_workers)      -> True
    pause(task_id)                     -> True
    cancel(task_id)                    -> True

Every function returns/raises synchronously (Kotlin calls them on worker
threads). Download progress is streamed back through the Java class
``com.dugan.downloader.DownloadEvents`` as event dicts:

    { taskId, status, progress, downloadedBytes, totalBytes, speedBps,
      stage, error, code, filePath, title }

status ∈ downloading | postprocessing | paused | completed | failed | canceled
"""

import json
import os
import shutil
import threading
import time

import yt_dlp
from java import jclass

_Events = jclass("com.dugan.downloader.DownloadEvents")

_lock = threading.RLock()
_pause_flags = {}      # task_id -> threading.Event (set = pause requested)
_cancel_flags = {}     # task_id -> bool
_task_files = {}       # task_id -> {filename: (downloaded, total)}
_throttle = {}         # task_id -> last emit timestamp
_executor = None
_executor_workers = 0
_ffmpeg_dir_cache = None


class TaskPaused(yt_dlp.utils.DownloadCancelled):
    """Raised inside a progress hook to suspend the download."""


class TaskCanceled(yt_dlp.utils.DownloadCancelled):
    """Raised inside a progress hook to abort the download."""


# ── Public API ──────────────────────────────────────────────────────────────


def check_status(ffmpeg_dir, work_dir):
    """Engine health check: Python/yt-dlp versions + FFmpeg availability."""
    import platform

    ffmpeg_ok = _prepare_ffmpeg(ffmpeg_dir, work_dir) is not None
    return json.dumps(
        {
            "ok": True,
            "data": {
                "pythonVersion": platform.python_version(),
                "ytdlpVersion": yt_dlp.version.__version__,
                "ffmpegAvailable": ffmpeg_ok,
            },
        }
    )


def extract_info(url, options_json):
    """``yt-dlp --dump-single-json --no-download`` equivalent.

    options keys: userAgent, cookieFile, extraArgs, noplaylist.
    """
    opts = _parse_json(options_json)
    params = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": bool(opts.get("noplaylist", True)),
        "socket_timeout": 15,
        "retries": 2,
    }
    _apply_common_params(params, opts)
    _apply_extra_args(params, opts.get("extraArgs") or [])

    try:
        with yt_dlp.YoutubeDL(params) as ydl:
            info = ydl.extract_info(url, download=False)
    except Exception as exc:  # noqa: BLE001 - mapped below
        return _error_response(exc)

    if info is None:
        return _error_payload("UNAVAILABLE", "No media found at this URL.")

    # Playlists: use the first entry (v1 scope is single media).
    if info.get("_type") == "playlist":
        entries = info.get("entries") or []
        if not entries:
            return _error_payload("UNAVAILABLE", "This playlist is empty.")
        info = entries[0]

    return json.dumps({"ok": True, "data": _sanitize(info, url)})


def start(task_json, max_workers):
    """Enqueues a download task on the shared thread pool."""
    task = _parse_json(task_json)
    task_id = task["taskId"]
    with _lock:
        _cancel_flags[task_id] = False
        _pause_flags[task_id] = threading.Event()
        _task_files[task_id] = {}
    _get_executor(max_workers).submit(_run_task, task)
    return True


def pause(task_id):
    with _lock:
        event = _pause_flags.get(task_id)
        if event is not None:
            event.set()
    return True


def cancel(task_id):
    with _lock:
        _cancel_flags[task_id] = True
        event = _pause_flags.get(task_id)
        if event is not None:
            event.set()
    return True


# ── Download execution ──────────────────────────────────────────────────────


def _run_task(task):
    task_id = task["taskId"]
    out_dir = task.get("outputDir") or ""

    try:
        os.makedirs(out_dir, exist_ok=True)
    except OSError as exc:
        _emit(task_id, "failed", error=str(exc), code="STORAGE")
        _cleanup_flags(task_id)
        return

    selector = task.get("formatSelector") or "bv*+ba/b"

    params = {
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        "noplaylist": True,
        "outtmpl": os.path.join(
            out_dir, task.get("filenameTemplate") or "%(title)s [%(id)s].%(ext)s"
        ),
        "format": selector,
        "progress_hooks": [_make_progress_hook(task_id)],
        "postprocessor_hooks": [_make_pp_hook(task_id)],
        "retries": 3,
        "fragment_retries": 10,
        "socket_timeout": 20,
        "concurrent_fragment_downloads": 4,
        "continuedl": True,  # resume from .part files
        "overwrites": False,
    }

    if task.get("mergeFormat") and "+" in selector:
        params["merge_output_format"] = task["mergeFormat"]

    postprocessors = []
    if task.get("audioOnly"):
        postprocessors.append(
            {
                "key": "FFmpegExtractAudio",
                "preferredcodec": task.get("audioContainer") or "mp3",
                "preferredquality": str(task.get("audioBitrateKbps") or 192),
            }
        )
    if task.get("embedMetadata"):
        postprocessors.append({"key": "FFmpegMetadata"})
    if task.get("embedThumbnail"):
        postprocessors.append({"key": "EmbedThumbnail"})
        params["writethumbnail"] = True
    if task.get("embedSubs") and task.get("subLangs"):
        params["writesubtitles"] = True
        params["subtitleslangs"] = list(task["subLangs"])
        postprocessors.append({"key": "FFmpegEmbedSubtitle"})
    if postprocessors:
        params["postprocessors"] = postprocessors

    ffmpeg_dir = _prepare_ffmpeg(task.get("ffmpegDir") or "", task.get("workDir") or "")
    if ffmpeg_dir:
        params["ffmpeg_location"] = ffmpeg_dir

    _apply_common_params(params, task)
    _apply_extra_args(params, task.get("extraArgs") or [])

    _emit(task_id, "downloading", 0.0)

    try:
        with yt_dlp.YoutubeDL(params) as ydl:
            ydl.download([task["url"]])
        final_path = _find_output(out_dir, task)
        if final_path is None:
            _emit(
                task_id,
                "failed",
                error="Download finished but the output file could not be located.",
                code="STORAGE",
            )
        else:
            _emit(task_id, "completed", 1.0, file_path=final_path)
    except TaskPaused:
        _emit(task_id, "paused")
    except TaskCanceled:
        _delete_part_files(task_id, out_dir)
        _emit(task_id, "canceled")
    except Exception as exc:  # noqa: BLE001 - mapped below
        _emit(
            task_id,
            "failed",
            error=_clean_message(exc),
            code=_classify_error(exc),
        )
    finally:
        _cleanup_flags(task_id)


def _make_progress_hook(task_id):
    def hook(d):
        if _cancel_flags.get(task_id):
            raise TaskCanceled()
        event = _pause_flags.get(task_id)
        if event is not None and event.is_set():
            raise TaskPaused()

        status = d.get("status")
        if status != "downloading":
            return

        filename = os.path.basename(d.get("filename") or "")
        total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
        downloaded = d.get("downloaded_bytes") or 0
        speed = d.get("speed") or 0.0

        # Combine progress across the video + audio streams.
        with _lock:
            files = _task_files.setdefault(task_id, {})
            if filename:
                files[filename] = (downloaded, total)
            total_sum = sum(t for (_, t) in files.values())
            downloaded_sum = sum(x for (x, _) in files.values())

        fraction = (downloaded_sum / total_sum) if total_sum else 0.0
        now = time.time()
        if now - _throttle.get(task_id, 0.0) >= 0.5 or fraction >= 1.0:
            _throttle[task_id] = now
            _emit(
                task_id,
                "downloading",
                min(fraction, 1.0),
                downloaded=downloaded_sum,
                total=total_sum,
                speed=speed,
            )

    return hook


_STAGE_NAMES = {
    "Merger": "Merging audio + video",
    "ExtractAudio": "Converting audio",
    "VideoRemuxer": "Remuxing container",
    "EmbedThumbnail": "Embedding thumbnail",
    "FFmpegMetadata": "Embedding metadata",
    "FFmpegEmbedSubtitle": "Embedding subtitles",
    "MoveFiles": "Moving file",
}


def _make_pp_hook(task_id):
    def hook(d):
        if d.get("status") == "started":
            name = _STAGE_NAMES.get(d.get("postprocessor") or "", "Processing")
            _emit(task_id, "postprocessing", stage=name)

    return hook


def _find_output(out_dir, task):
    """Locates the final file: prefers the expected extension, then the
    most recently modified candidate."""
    if not os.path.isdir(out_dir):
        return None
    ignored_suffixes = (".part", ".ytdl", ".temp", ".f", ".jpg", ".jpeg", ".png", ".webp", ".vtt")
    candidates = []
    for name in os.listdir(out_dir):
        if name.endswith(ignored_suffixes):
            continue
        if task.get("audioOnly") and name.lower().endswith(
            "." + (task.get("audioContainer") or "mp3").lower()
        ):
            return os.path.join(out_dir, name)
        candidates.append(name)
    if not candidates:
        return None
    candidates.sort(key=lambda n: os.path.getmtime(os.path.join(out_dir, n)), reverse=True)
    return os.path.join(out_dir, candidates[0])


def _delete_part_files(task_id, out_dir):
    with _lock:
        names = set(_task_files.get(task_id, {}).keys())
    if not names or not os.path.isdir(out_dir):
        return
    for name in os.listdir(out_dir):
        base = name
        for suffix in (".part", ".ytdl", ".temp"):
            if base.endswith(suffix):
                base = base[: -len(suffix)]
                break
        if base in names:
            try:
                os.remove(os.path.join(out_dir, name))
            except OSError:
                pass


# ── Helpers ─────────────────────────────────────────────────────────────────


def _emit(
    task_id,
    status,
    progress=0.0,
    downloaded=0,
    total=0,
    speed=0.0,
    stage=None,
    error=None,
    code=None,
    file_path=None,
    title=None,
):
    _Events.emitEvent(
        {
            "taskId": task_id,
            "status": status,
            "progress": float(progress),
            "downloadedBytes": int(downloaded),
            "totalBytes": int(total),
            "speedBps": float(speed),
            "stage": stage,
            "error": error,
            "code": code,
            "filePath": file_path,
            "title": title,
        }
    )


def _get_executor(workers):
    global _executor, _executor_workers
    with _lock:
        if _executor is None or _executor_workers != workers:
            if _executor is not None:
                _executor.shutdown(wait=False)
            import concurrent.futures

            _executor = concurrent.futures.ThreadPoolExecutor(
                max_workers=max(1, min(int(workers), 5)),
            )
            _executor_workers = workers
        return _executor


def _cleanup_flags(task_id):
    with _lock:
        _pause_flags.pop(task_id, None)
        _cancel_flags.pop(task_id, None)
        _task_files.pop(task_id, None)
        _throttle.pop(task_id, None)


def _prepare_ffmpeg(ffmpeg_dir, work_dir):
    """Copies ``libffmpeg.so`` (bundled in jniLibs) to ``<work>/bin/ffmpeg``
    so yt-dlp can execute it — yt-dlp appends the program name to
    ``ffmpeg_location``, so the binary must be named exactly ``ffmpeg``."""
    global _ffmpeg_dir_cache
    if _ffmpeg_dir_cache:
        return _ffmpeg_dir_cache
    if not ffmpeg_dir or not work_dir:
        return None
    src = os.path.join(ffmpeg_dir, "libffmpeg.so")
    if not os.path.isfile(src):
        return None
    bin_dir = os.path.join(work_dir, "bin")
    os.makedirs(bin_dir, exist_ok=True)
    dst = os.path.join(bin_dir, "ffmpeg")
    if not os.path.isfile(dst) or os.path.getsize(dst) != os.path.getsize(src):
        shutil.copyfile(src, dst)
        os.chmod(dst, 0o755)
    _ffmpeg_dir_cache = bin_dir
    return bin_dir


def _apply_common_params(params, opts):
    headers = {}
    user_agent = opts.get("userAgent")
    if user_agent:
        headers["User-Agent"] = user_agent
    if headers:
        params["http_headers"] = headers
    cookie_file = opts.get("cookieFile")
    if cookie_file and os.path.isfile(str(cookie_file)):
        params["cookiefile"] = str(cookie_file)


# Allow-list of user-overridable options (Settings → Advanced).
_EXTRA_ARGS_ALLOWLIST = {
    "--retries": ("retries", int),
    "--fragment-retries": ("fragment_retries", int),
    "--concurrent-fragments": ("concurrent_fragment_downloads", int),
    "--socket-timeout": ("socket_timeout", int),
    "--no-check-certificates": ("nocheckcertificate", None),
    "--no-playlist": ("noplaylist", None),
    "--force-ipv4": ("force_ipv4", None),
}


def _apply_extra_args(params, args):
    if not args:
        return
    tokens = [str(a) for a in args]
    i = 0
    while i < len(tokens):
        token = tokens[i]
        if token in _EXTRA_ARGS_ALLOWLIST:
            key, caster = _EXTRA_ARGS_ALLOWLIST[token]
            if caster is None:
                params[key] = True
            elif i + 1 < len(tokens) and not tokens[i + 1].startswith("--"):
                try:
                    params[key] = caster(tokens[i + 1])
                except (TypeError, ValueError):
                    pass
                i += 1
        i += 1


def _sanitize(info, url):
    """Reduces a full yt-dlp info dict to the fields Dugan needs."""
    formats = []
    for f in info.get("formats") or []:
        if f.get("format_id") is None:
            continue
        ext = (f.get("ext") or "").lower()
        if ext in ("mhtml", "jpeg", "jpg", "png", "webp"):
            continue
        vcodec = f.get("vcodec") or "none"
        acodec = f.get("acodec") or "none"
        formats.append(
            {
                "formatId": str(f.get("format_id")),
                "ext": ext,
                "protocol": f.get("protocol") or "",
                "formatNote": f.get("format_note"),
                "vcodec": None if vcodec == "none" else vcodec,
                "acodec": None if acodec == "none" else acodec,
                "width": f.get("width"),
                "height": f.get("height"),
                "fps": f.get("fps"),
                "tbr": f.get("tbr"),
                "abr": f.get("abr"),
                "filesize": f.get("filesize"),
                "filesizeApprox": f.get("filesize_approx"),
            }
        )

    subtitles = []
    for group, auto in (("subtitles", False), ("automatic_captions", True)):
        for lang, tracks in (info.get(group) or {}).items():
            for track in tracks or []:
                ext = (track.get("ext") or "").lower()
                if ext in ("json", "json3", "srv1", "srv2", "srv3"):
                    continue
                subtitles.append(
                    {
                        "language": lang,
                        "ext": ext,
                        "name": track.get("name"),
                        "isAutoCaption": auto,
                    }
                )
                break  # first usable track per language/group

    duration = info.get("duration")
    return {
        "id": str(info.get("id") or ""),
        "title": info.get("title") or "Untitled",
        "uploader": info.get("uploader") or info.get("channel") or info.get("uploader_id"),
        "thumbnail": _best_thumbnail(info),
        "duration": int(duration) if duration else None,
        "sourceUrl": info.get("webpage_url") or url,
        "platform": info.get("extractor_key") or "",
        "isLive": bool(info.get("is_live")),
        "viewCount": info.get("view_count"),
        "formats": formats,
        "subtitles": subtitles,
    }


def _best_thumbnail(info):
    url = info.get("thumbnail")
    if url:
        return url
    best = None
    best_area = -1
    for thumb in info.get("thumbnails") or []:
        width = thumb.get("width") or 0
        height = thumb.get("height") or 0
        area = width * height
        if area > best_area and thumb.get("url"):
            best_area = area
            best = thumb.get("url")
    return best


def _classify_error(exc):
    message = str(exc).lower()
    if "unsupported url" in message:
        return "UNSUPPORTED"
    if "private" in message or "removed" in message or "deleted" in message:
        return "PRIVATE"
    if (
        "sign in" in message
        or "login" in message
        or "age" in message
        or "cookies" in message
        or "bot" in message
    ):
        return "LOGIN_REQUIRED"
    if "country" in message or "geo" in message or "not available in your" in message:
        return "GEO_RESTRICTED"
    if (
        "timed out" in message
        or "timeout" in message
        or "connection" in message
        or "network" in message
        or "resolve" in message
        or "unreachable" in message
        or "temporary" in message
    ):
        return "NETWORK"
    if "unavailable" in message:
        return "UNAVAILABLE"
    return "EXTRACT_ERROR"


def _clean_message(exc):
    message = str(exc).strip()
    if message.startswith("ERROR:"):
        message = message[len("ERROR:"):].strip()
    return message[:300] or exc.__class__.__name__


def _error_response(exc):
    return json.dumps(
        {
            "ok": False,
            "code": _classify_error(exc),
            "message": _clean_message(exc),
        }
    )


def _error_payload(code, message):
    return json.dumps({"ok": False, "code": code, "message": message})


def _parse_json(raw):
    if isinstance(raw, dict):
        return raw
    try:
        return json.loads(raw or "{}")
    except ValueError:
        return {}
