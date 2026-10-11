package com.mnvstudio.x11.tools

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.vector.ImageVector
import com.mnvstudio.x11.linux.Linux

/** An input on a tool's form. */
class Field(
    val key: String,
    val label: String,
    val default: String = "",
    val hint: String = "",
    /** Non-empty: pick one of these instead of typing. */
    val choices: List<String> = emptyList(),
    val multiline: Boolean = false,
)

/** How a tool's output is shown. */
enum class View {
    /** Plain lines, monospace. */
    TEXT,
    /** Lines "number<TAB>label" drawn as proportional bars (storage maps, sizes, timings). */
    BARS,
    /** Lines "key<TAB>value" as a two-column table. */
    TABLE,
}

/** Where a tool's output goes; safe to call from any thread. */
class Out(private val sink: (String) -> Unit, private val finished: () -> Unit) {
    @Volatile var cancelled = false
    private val main = Handler(Looper.getMainLooper())
    fun line(s: String) { if (!cancelled) main.post { sink(s) } }
    fun row(k: String, v: Any?) = line("$k\t$v")
    fun done() = main.post(finished)
    /** Lets a stop button end a Linux command. */
    @Volatile var process: Process? = null
}

/**
 * A built-in tool: a form of [fields] and a [run] that streams output. Most tools are a few lines: either a shell
 * command inside Alpine (see [linux]) or Kotlin running on a background thread (see [native]).
 */
class ToolDef(
    val id: String,
    val name: String,
    val category: String,
    val icon: ImageVector,
    val description: String,
    val fields: List<Field> = emptyList(),
    val view: View = View.TEXT,
    /** Route to open instead of the generic form (for tools with their own screen). */
    val route: String? = null,
    val keywords: String = "",
    val run: (Context, Map<String, String>, Out) -> Unit = { _, _, o -> o.done() },
) {
    fun matches(q: String) = q.isBlank() || listOf(name, description, category, keywords).any { it.contains(q.trim(), true) }
}

/** Runs [command] in Alpine (after installing [needs] if missing), streaming its output. */
fun linux(vararg needs: String, command: (Map<String, String>) -> String): (Context, Map<String, String>, Out) -> Unit = { ctx, v, out ->
    if (Linux.state != Linux.State.READY) { out.line("Set up Linux first (Linux tab)."); out.done() }
    else {
        val install = needs.joinToString("; ") { "command -v ${it.substringAfter(':')} >/dev/null 2>&1 || apk add -q ${it.substringBefore(':')} >/dev/null 2>&1" }
        val cmd = (if (install.isNotEmpty()) "$install; " else "") + command(v)
        out.process = Linux.run(ctx, "($cmd) 2>&1", 0, onOutput = { out.line(it) }) { code -> if (code != 0) out.line("[exit $code]"); out.done() }
    }
}

/** Runs [block] on a background thread; exceptions are printed, not thrown. */
fun native(block: (Context, Map<String, String>, Out) -> Unit): (Context, Map<String, String>, Out) -> Unit = { ctx, v, out ->
    Thread {
        try { block(ctx, v, out) } catch (e: Throwable) { out.line("Error: ${e.javaClass.simpleName}: ${e.message}") }
        out.done()
    }.start()
}

/** Quotes a form value for the shell. */
fun q(s: String?) = Linux.quote(s ?: "")

fun human(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val u = listOf("KB", "MB", "GB", "TB"); var v = bytes / 1024.0; var i = 0
    while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
    return "%.1f %s".format(v, u[i])
}
