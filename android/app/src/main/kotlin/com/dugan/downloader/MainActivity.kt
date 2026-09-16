package com.dugan.downloader

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

/** Thrown when the Python engine returns a structured error. */
class EngineError(val code: String, message: String) : Exception(message)

/**
 * Hosts the platform channels bridging Flutter ↔ Kotlin ↔ Chaquopy/Python
 * (yt-dlp).
 *
 * Channels:
 *  - app.dugan/engine    → checkStatus | extractInfo
 *  - app.dugan/downloads → start | pause | cancel
 *  - app.dugan/system    → isWifiConnected | openFile
 *  - app.dugan/downloads/events (EventChannel, registered by [DownloadEvents])
 */
class MainActivity : FlutterActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(applicationContext))
        }

        val messenger = flutterEngine.dartExecutor.binaryMessenger
        DownloadEvents.register(messenger, applicationContext)

        MethodChannel(messenger, "app.dugan/engine").setMethodCallHandler { call, result ->
            when (call.method) {
                "checkStatus" -> runAsync(result) { pyCheckStatus() }
                "extractInfo" -> {
                    val args = call.arguments as? Map<*, *> ?: emptyMap<Any?, Any?>()
                    val url = args["url"] as? String ?: ""
                    @Suppress("UNCHECKED_CAST")
                    val optionsMap = args["options"] as? Map<Any?, Any?> ?: emptyMap<Any?, Any?>()
                    val options = JSONObject(optionsMap)
                    runAsync(result) { pyExtractInfo(url, options.toString()) }
                }
                else -> result.notImplemented()
            }
        }

        MethodChannel(messenger, "app.dugan/downloads").setMethodCallHandler { call, result ->
            val args = call.arguments as? Map<*, *> ?: emptyMap<Any?, Any?>()
            when (call.method) {
                "start" -> {
                    val task = JSONObject(args).apply {
                        put("ffmpegDir", ffmpegDir())
                        put("workDir", workDir())
                    }
                    try {
                        DownloadForegroundService.start(
                            this,
                            task.optString("title", "Downloading"),
                        )
                    } catch (e: Exception) {
                        // Foreground-service start can be rejected while the
                        // app is backgrounded (Android 12+); downloads still
                        // proceed, just without a visible notification.
                    }
                    val maxConcurrent = task.optInt("maxConcurrent", 2)
                    runAsync(result) { pyCall("start", task.toString(), maxConcurrent) }
                }
                "pause" -> {
                    val taskId = args["taskId"] as? String ?: ""
                    runAsync(result) { pyCall("pause", taskId) }
                }
                "cancel" -> {
                    val taskId = args["taskId"] as? String ?: ""
                    runAsync(result) { pyCall("cancel", taskId) }
                }
                else -> result.notImplemented()
            }
        }

        MethodChannel(messenger, "app.dugan/system").setMethodCallHandler { call, result ->
            when (call.method) {
                "isWifiConnected" -> result.success(isWifiConnected())
                "openFile" -> {
                    val args = call.arguments as? Map<*, *>
                    val path = args?.get("path") as? String ?: ""
                    result.success(openFile(path))
                }
                else -> result.notImplemented()
            }
        }
    }

    // ── Async plumbing ─────────────────────────────────────────────────────

    /** Runs [block] on a worker thread and posts the value (or a structured
     *  error) back to Flutter on the main thread. */
    private fun runAsync(result: MethodChannel.Result, block: () -> Any?) {
        thread {
            try {
                val value = block()
                mainHandler.post { result.success(value) }
            } catch (e: EngineError) {
                mainHandler.post { result.error(e.code, e.message, null) }
            } catch (e: Throwable) {
                mainHandler.post {
                    result.error("ENGINE", e.message ?: e.javaClass.simpleName, null)
                }
            }
        }
    }

    // ── Python calls ───────────────────────────────────────────────────────

    private fun pyCheckStatus(): Map<String, Any?> {
        val py = Python.getInstance()
        val module = py.getModule("ytdlp_bridge")
        val json = module.callAttr("check_status", ffmpegDir(), workDir()).toString()
        val obj = JSONObject(json)
        if (!obj.optBoolean("ok")) {
            throw EngineError(obj.optString("code", "ENGINE"), obj.optString("message"))
        }
        return jsonToMap(obj.getJSONObject("data"))
    }

    private fun pyExtractInfo(url: String, optionsJson: String): Map<String, Any?> {
        val py = Python.getInstance()
        val module = py.getModule("ytdlp_bridge")
        val json = module.callAttr("extract_info", url, optionsJson).toString()
        val obj = JSONObject(json)
        if (!obj.optBoolean("ok")) {
            throw EngineError(obj.optString("code", "EXTRACT_ERROR"), obj.optString("message"))
        }
        return jsonToMap(obj.getJSONObject("data"))
    }

    private fun pyCall(function: String, vararg args: Any?): Boolean {
        val py = Python.getInstance()
        val module = py.getModule("ytdlp_bridge")
        module.callAttr(function, *args)
        return true
    }

    // ── JSON helpers ───────────────────────────────────────────────────────

    private fun jsonToMap(obj: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = when (val value = obj.get(key)) {
                is JSONObject -> jsonToMap(value)
                is JSONArray -> jsonToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }

    private fun jsonToList(array: JSONArray): List<Any?> {
        return (0 until array.length()).map { idx ->
            when (val value = array.get(idx)) {
                is JSONObject -> jsonToMap(value)
                is JSONArray -> jsonToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
    }

    // ── System helpers ─────────────────────────────────────────────────────

    private fun ffmpegDir(): String =
        applicationInfo.nativeLibraryDir ?: ""

    private fun workDir(): String = cacheDir.absolutePath

    private fun isWifiConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun openFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists()) {
                return false
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, contentResolver.getType(uri) ?: "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
