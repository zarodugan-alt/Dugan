package com.dugan.agent.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures the last uncaught exception so a crash can be diagnosed after the
 * fact, from inside the app.
 *
 * A crash that takes the process down takes its logcat with it, and on a device
 * with no `adb` attached that leaves nothing to read. This keeps a single
 * rolling record — the most recent crash only — in the app's private files
 * directory, and mirrors it in memory so the settings screen can show it
 * without touching disk on the main thread.
 *
 * It deliberately does not swallow the exception: after recording, the previous
 * handler is invoked so the normal system behaviour (and any other crash
 * reporter) still runs. Nothing here changes when or why the app dies; it only
 * makes sure the reason survives.
 */
object CrashLog {

    private const val FILE_NAME = "last_crash.txt"

    /** Capped so a pathological trace cannot balloon private storage. */
    private const val MAX_CHARS = 12_000

    @Volatile
    private var lastRecorded: String? = null

    /**
     * The application context, kept so [last] and [clear] need no parameter at
     * the call site. This is the Application itself, so holding it leaks
     * nothing.
     */
    private var appContext: Context? = null

    /**
     * Installs the handler and loads any crash recorded by an earlier run.
     *
     * Call this first thing in `Application.onCreate`, before dependency
     * injection and model loading, so a failure during startup is captured too.
     */
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        appContext = context.applicationContext
        val file = file(context)

        lastRecorded = runCatching {
            if (file.exists()) file.readText().take(MAX_CHARS) else null
        }.getOrNull()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val trace = throwable.stackTraceToString()
                val record = buildString {
                    appendLine("crashed at $stamp on thread '${thread.name}'")
                    appendLine("thread group: ${thread.threadGroup?.name ?: "?"}")
                    appendLine()
                    append(trace)
                }.take(MAX_CHARS)

                lastRecorded = record
                file.parentFile?.mkdirs()
                file.writeText(record)
                AgentLog.e("CrashLog", "uncaught on ${thread.name}", throwable)
            }
            // Never prevent the process from dying: re-hand off to whatever was
            // installed before us.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** The most recent recorded crash, or null when the app has never crashed. */
    fun last(): String? = lastRecorded

    /** Drops the stored record, e.g. after the user has read and acted on it. */
    fun clear() {
        lastRecorded = null
        val context = appContext ?: return
        runCatching { file(context).delete() }
    }

    private fun file(context: Context): File =
        File(context.filesDir, FILE_NAME)
}
