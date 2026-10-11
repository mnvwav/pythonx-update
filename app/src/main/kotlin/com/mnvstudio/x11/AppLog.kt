package com.mnvstudio.x11

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The app's own log (server, Linux and errors), shown in Diagnostics so problems can be copied and reported. */
object AppLog {
    private val lines = ArrayDeque<String>()
    private var file: File? = null
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val main = Handler(Looper.getMainLooper())
    /** Bumped on every new line so screens showing the log recompose. */
    val revision = mutableIntStateOf(0)

    fun init(context: Context) {
        file = File(context.filesDir, "logs/app.log").apply { parentFile?.mkdirs(); if (length() > 512 * 1024) delete() }
    }

    fun i(message: String) = add("I", message)
    fun e(message: String, t: Throwable? = null) = add("E", message + (t?.let { ": ${it.javaClass.simpleName}: ${it.message}" } ?: ""))

    private fun add(level: String, message: String) {
        val line = "${time.format(Date())} $level $message"
        synchronized(lines) { lines.addLast(line); while (lines.size > 500) lines.removeFirst() }
        runCatching { file?.appendText(line + "\n") }
        main.post { revision.intValue++ }
    }

    fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    /** The whole log including earlier sessions, for copying. */
    fun fullText(): String = runCatching { file?.readText() }.getOrNull() ?: snapshot().joinToString("\n")

    fun clear() { synchronized(lines) { lines.clear() }; runCatching { file?.delete() }; main.post { revision.intValue++ } }
}
