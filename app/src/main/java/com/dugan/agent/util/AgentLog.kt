package com.dugan.agent.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * logcat plus a bounded in-memory ring buffer.
 *
 * The buffer is what "Export Logs" in Settings shares. Everything routed through
 * here is scrubbed by [redact] first, so a key that somehow reaches a log line
 * still cannot leave the device in a share sheet.
 */
object AgentLog {

    private const val MAX_LINES = 800
    private const val KEY_SHAPE = "\\b(?:gsk_|AIza|ya29\\.)[A-Za-z0-9_\\-]{8,}\\b"

    private val lines = ArrayDeque<String>()
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val keyPattern = Regex(KEY_SHAPE)

    @Synchronized
    private fun record(level: Char, tag: String, message: String) {
        val scrubbed = redact(message)
        lines.addLast("${format.format(Date())} $level/$tag: $scrubbed")
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    /** Replaces anything shaped like an API key with a fixed marker. */
    fun redact(message: String): String =
        keyPattern.replace(message) { it.value.take(6) + "…<redacted>" }

    fun d(tag: String, message: String) {
        Log.d(tag, redact(message))
        record('D', tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, redact(message))
        record('I', tag, message)
    }

    fun w(tag: String, message: String, t: Throwable? = null) {
        Log.w(tag, redact(message), t)
        record('W', tag, message + (t?.let { " (${it.javaClass.simpleName})" } ?: ""))
    }

    fun e(tag: String, message: String, t: Throwable? = null) {
        Log.e(tag, redact(message), t)
        record('E', tag, message + (t?.let { " (${it.javaClass.simpleName})" } ?: ""))
    }

    @Synchronized
    fun dump(): String = buildString {
        appendLine("Dugan diagnostic log — ${Date()}")
        appendLine("Last $MAX_LINES entries. API keys are redacted at the point of logging.")
        appendLine("─".repeat(60))
        lines.forEach { appendLine(it) }
    }

    @Synchronized
    fun clear() = lines.clear()

    @Synchronized
    fun size(): Int = lines.size
}
