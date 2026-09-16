package com.dugan.downloader

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel

/**
 * Forwards progress events from the Python engine to Flutter over an
 * EventChannel, and keeps the foreground-service notification in sync.
 *
 * Python calls [emitEvent] (via Chaquopy's `jclass`) from its worker
 * threads; the event is posted to the main thread before being sunk.
 */
object DownloadEvents {

    private const val CHANNEL = "app.dugan/downloads/events"
    private const val NOTIFY_THROTTLE_MS = 1000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var sink: EventChannel.EventSink? = null
    private val activeTasks = LinkedHashSet<String>()
    private var lastNotifyAt = 0L
    private var appContext: Context? = null

    fun register(messenger: BinaryMessenger, context: Context) {
        appContext = context.applicationContext
        EventChannel(messenger, CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    sink = events
                }

                override fun onCancel(arguments: Any?) {
                    sink = null
                }
            },
        )
    }

    @JvmStatic
    fun emitEvent(event: Map<String, Any?>) {
        mainHandler.post {
            sink?.success(event)
            updateService(event)
        }
    }

    private fun updateService(event: Map<String, Any?>) {
        val context = appContext ?: return
        val taskId = event["taskId"] as? String ?: return
        when (event["status"] as? String ?: return) {
            "downloading", "postprocessing" -> {
                activeTasks.add(taskId)
                maybeNotify(context, event, force = false)
            }
            "completed", "failed", "canceled", "paused" -> {
                activeTasks.remove(taskId)
                if (activeTasks.isEmpty()) {
                    DownloadForegroundService.stop(context)
                } else {
                    maybeNotify(context, event, force = true)
                }
            }
        }
    }

    private fun maybeNotify(context: Context, event: Map<String, Any?>, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotifyAt < NOTIFY_THROTTLE_MS) {
            return
        }
        lastNotifyAt = now
        val progress = (((event["progress"] as? Double) ?: 0.0) * 100).toInt()
        DownloadForegroundService.update(
            context = context,
            count = activeTasks.size,
            title = (event["title"] as? String) ?: "Downloading",
            progress = progress,
            stage = event["stage"] as? String,
        )
    }
}
