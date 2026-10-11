package org.softbuddy.pythonx.term

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import org.softbuddy.pythonx.Prefs
import org.softbuddy.pythonx.python.Python
import java.io.File

/**
 * One terminal: a PTY session rendered by Termux's emulator, plus sticky CTRL/ALT state for the extra keys.
 * Owns its session; [start] replaces it (a fresh shell, the REPL, or a program launched by Run).
 */
class Term(private val ctx: Context, fontPx: Int, val key: String) {
    private val python = Python(ctx)
    /** Short name for the notification, e.g. the project folder. */
    val label get() = if (key == REPL) "Python REPL" else File(key.substringAfter(':')).name.ifBlank { "Terminal" }
    var view: TerminalView? = null
    var session by mutableStateOf<TerminalSession?>(null); private set
    var running by mutableStateOf(false); private set
    var title by mutableStateOf("")
    /** The session is a program started by Run (not a shell). */
    var program by mutableStateOf(false); private set
    var startedAt = 0L; private set
    var exitCode by mutableStateOf<Int?>(null); private set
    var finishedAt by mutableStateOf(0L); private set
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
    var fontPx = fontPx; private set
    /** Called with the exit status when a session ends. */
    var onExit: (Int, Long) -> Unit = { _, _ -> }
    private var lastCwd = ctx.filesDir.path
    /** Where the last session started. */
    val cwd get() = File(lastCwd)

    fun mods() = (if (ctrl) KeyHandler.KEYMOD_CTRL else 0) or (if (alt) KeyHandler.KEYMOD_ALT else 0)
    fun clearMods() { ctrl = false; alt = false }

    private val clipboard get() = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val client = object : TerminalSessionClient {
        override fun onTextChanged(s: TerminalSession) { if (s == session) view?.onScreenUpdated() }
        override fun onTitleChanged(s: TerminalSession) { if (s == session) title = s.title.orEmpty() }
        override fun onSessionFinished(s: TerminalSession) {
            if (s != session) return
            running = false; exitCode = s.exitStatus; finishedAt = System.currentTimeMillis()
            val ms = finishedAt - startedAt
            onExit(s.exitStatus, ms); Terms.changed(ctx)
            if (program) {
                val c = s.exitStatus
                Notify.event(ctx, key.hashCode(), if (c == 0) "$label finished" else "$label stopped",
                    (if (c > 128) "Stopped by signal ${c - 128}" else "Exited with code $c") + " after ${ms / 1000}s",
                    TerminalService.routeFor(key), c == 0)
            }
        }
        override fun onCopyTextToClipboard(s: TerminalSession, text: String?) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text.orEmpty()))
        }
        override fun onPasteTextFromClipboard(s: TerminalSession?) {
            clipboard.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let { s?.emulator?.paste(it) }
        }
        override fun onBell(s: TerminalSession) {}
        override fun onColorsChanged(s: TerminalSession) { view?.invalidate() }
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun setTerminalShellPid(s: TerminalSession, pid: Int) {}
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }

    private fun env() = python.env() + mapOf(
        "TERM" to "xterm-256color", "COLORTERM" to "truecolor", "SHELL" to "/system/bin/sh",
        "PS1" to "\$PWD \$ ", "PROJECTS" to File(ctx.filesDir, "Projects").path,
    )

    /** An interactive shell in [cwd]; [command] runs first, then the shell stays open. */
    fun startShell(cwd: File, command: String? = null) {
        if (command == null) start(cwd, arrayOf("-sh"))  // argv[0] starting with "-" makes it a login shell
        else start(cwd, arrayOf("sh", "-c", "$command; echo; exec /system/bin/sh -l"))
    }

    /** The interactive Python prompt. */
    fun startRepl(cwd: File) = start(cwd, arrayOf("sh", "-c", "exec python3 -q"), program = true)

    /**
     * Runs [command] as a program; the terminal shows a status line when it ends and Enter opens a shell.
     * [clear] wipes the screen and scrollback first.
     */
    fun startProgram(cwd: File, command: String, clear: Boolean) =
        start(cwd, arrayOf("sh", "-c", (if (clear) "printf '\\033[H\\033[2J\\033[3J'; " else "") + "exec $command"), program = true)

    /** Replaces the current session with /system/bin/sh run with [args] as its full argv. */
    private fun start(cwd: File, args: Array<String>, program: Boolean = false) {
        session?.finishIfRunning()
        this.program = program; startedAt = System.currentTimeMillis(); exitCode = null
        lastCwd = cwd.takeIf { it.isDirectory }?.path ?: ctx.filesDir.path
        val s = TerminalSession("/system/bin/sh", lastCwd, args, env().map { (k, v) -> "$k=$v" }.toTypedArray(), 3000, client)
        session = s; running = true; title = ""
        view?.attachSession(s)
        view?.invalidate()
        Terms.changed(ctx)
    }

    fun stop() = session?.finishIfRunning()

    /** Sends Ctrl-C, like pressing it on a keyboard. */
    fun interrupt() { session?.write("\u0003") }

    fun write(text: String) { session?.write(text) }

    fun showKeyboard() {
        view?.let { v -> v.requestFocus(); (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(v, 0) }
    }

    fun setFont(px: Int) { fontPx = px.coerceIn(18, 80); view?.setTextSize(fontPx) }

    /** The whole transcript, for copying or sharing output. */
    fun transcript(): String = session?.emulator?.screen?.transcriptText.orEmpty()

    val viewClient = object : TerminalViewClient {
        override fun onScale(scale: Float): Float {
            if (scale < 0.9f || scale > 1.1f) { setFont((fontPx * scale).toInt()); return 1f }
            return scale
        }
        override fun onSingleTapUp(e: MotionEvent?) = showKeyboard()
        override fun shouldBackButtonBeMappedToEscape() = false
        override fun shouldEnforceCharBasedInput() = true
        override fun shouldUseCtrlSpaceWorkaround() = false
        override fun isTerminalViewSelected() = true
        override fun copyModeChanged(copyMode: Boolean) {}
        override fun onKeyDown(keyCode: Int, e: KeyEvent?, s: TerminalSession?): Boolean {
            if (keyCode == KeyEvent.KEYCODE_ENTER && !running) { startShell(File(lastCwd)); return true }
            return false
        }
        override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false
        override fun onLongPress(event: MotionEvent?) = false
        override fun readControlKey() = ctrl
        override fun readAltKey() = alt
        override fun readShiftKey() = false
        override fun readFnKey() = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, s: TerminalSession?): Boolean { clearMods(); return false }
        override fun onEmulatorSet() {}
        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }

    /** Sends a special key (arrows, ESC, TAB…) with the latched modifiers. */
    fun key(code: Int) { view?.handleKeyCode(code, mods()); clearMods() }

    companion object {
        const val REPL = "repl"
    }
}

/**
 * Every terminal session lives here, outside any screen, so leaving the terminal (or the app) doesn't kill it.
 * Sessions end only when the user stops them. While any is running, [TerminalService] keeps the process alive.
 */
object Terms {
    val all = mutableStateMapOf<String, Term>()

    fun get(ctx: Context, key: String, fontPx: Int): Term =
        all.getOrPut(key) { Term(ctx.applicationContext, fontPx, key) }

    fun running() = all.values.filter { it.running }

    /** Starts, updates or stops the foreground service to match the running sessions. */
    fun changed(ctx: Context) {
        val app = ctx.applicationContext
        val i = Intent(app, TerminalService::class.java)
        if (running().isEmpty() || !Prefs(app).termBackground) app.stopService(i)
        else runCatching { app.startForegroundService(i) }
    }

    fun stopAll() = all.values.forEach { it.stop() }
}

/** Foreground service with an ongoing notification while sessions run in the background. */
class TerminalService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALL) { Terms.stopAll(); stopSelf(); return START_NOT_STICKY }
        Notify.channels(this)
        val running = Terms.running().sortedByDescending { it.startedAt }
        val first = running.firstOrNull()
        val stop = PendingIntent.getService(this, 1, Intent(this, TerminalService::class.java).setAction(ACTION_STOP_ALL), PendingIntent.FLAG_IMMUTABLE)
        fun line(t: Term) = (if (t.program) "▶ " else "$ ") + t.title.ifBlank { t.label }
        val title = when {
            running.size > 1 -> "${running.size} sessions running"
            first?.program == true -> "Running ${first.label}"
            else -> "Shell running · ${first?.label ?: ""}"
        }
        val n = Notify.builder(this, Notify.TERMINAL)
            .setContentTitle(title)
            .setContentText(running.joinToString(" · ") { line(it) })
            .setStyle(Notification.InboxStyle().also { st -> running.take(6).forEach { st.addLine(line(it)) } })
            .setWhen(first?.startedAt ?: System.currentTimeMillis()).setUsesChronometer(true).setShowWhen(true)
            .setOngoing(true)
            .setContentIntent(Notify.open(this, first?.key?.let { routeFor(it) }))
            .addAction(Notification.Action.Builder(null, if (running.size > 1) "Stop all" else "Stop", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID, n)
        return START_NOT_STICKY
    }

    companion object {
        private const val ID = 7
        /** The screen that shows a terminal: Run output under "run:", shells under "term:". */
        fun routeFor(key: String) = when {
            key.startsWith("ide:") -> "run:" + key.removePrefix("ide:")
            key == Term.REPL -> "repl"
            else -> key
        }
        const val ACTION_STOP_ALL = "org.softbuddy.pythonx.STOP_ALL_TERMINALS"
    }
}
