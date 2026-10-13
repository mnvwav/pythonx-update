package com.mnvstudio.x11.xorg

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnvstudio.x11.AppLog
import com.mnvstudio.x11.linux.Linux
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The full X.Org engine, the same idea as XSDL: the real X.Org server (Xvfb) runs inside the built-in Alpine Linux,
 * so every extension exists (RENDER, MIT-SHM, GLX through Mesa, XInput2, RANDR, XKB…) and Firefox, GIMP, games and
 * whole desktops behave exactly as on a PC. Xvfb keeps its screen in a memory-mapped file the app maps too
 * ([XwdFrame]); touch and keys go in through XTEST ([XorgClient]).
 */
object XorgEngine {
    enum class State { STOPPED, STARTING, RUNNING, FAILED }

    var state by mutableStateOf(State.STOPPED); private set
    var status by mutableStateOf(""); private set
    var width by mutableIntStateOf(0); private set
    var height by mutableIntStateOf(0); private set
    @Volatile var client: XorgClient? = null; private set
    @Volatile var frame: XwdFrame? = null; private set
    private var process: Process? = null
    private val main = Handler(Looper.getMainLooper())
    private fun ui(b: () -> Unit) = main.post(b)

    /** Packages for the X.Org engine: the server, keyboard compiler and layouts, fonts and software OpenGL. */
    // X.Org won't start without its "fixed" and "cursor" fonts; classic apps (xterm, xclock) need the misc fonts too.
    val PACKAGES = listOf("xvfb", "xkbcomp", "xkeyboard-config", "font-misc-misc", "font-cursor-misc", "font-alias", "encodings",
        "font-dejavu", "mesa-dri-gallium", "dbus-x11")

    /** Inside Linux: the Xauthority file with this run's secret cookie (Linux apps get XAUTHORITY pointing here). */
    const val AUTH_FILE = "/tmp/.x11auth"

    fun installed(context: Context) = File(Linux.rootfs(context), "usr/bin/Xvfb").exists() &&
        File(Linux.rootfs(context), "usr/share/fonts/misc").isDirectory

    /** Starts Xvfb on [display] in the background; [onReady] runs on the main thread once input and screen are hooked up. */
    @Synchronized
    fun start(context: Context, w: Int, h: Int, dpi: Int, display: Int, network: Boolean, onReady: () -> Unit, onFail: (String) -> Unit) {
        if (state == State.STARTING || state == State.RUNNING) return
        val app = context.applicationContext
        state = State.STARTING; status = "Starting X.Org…"; width = w; height = h
        Thread {
            try {
                Linux.check(app)
                if (Linux.state != Linux.State.READY) error("Set up Linux first (Linux tab), X.Org runs inside it.")
                if (!installed(app)) {
                    ui { status = "Installing X.Org (one time, about 25 MB)…" }
                    // The server and its fonts must install; the rest (OpenGL, D-Bus…) is added one by one so a missing extra can't block X.Org.
                    val core = PACKAGES.take(8).joinToString(" "); val extras = PACKAGES.drop(8).joinToString(" ")
                    val (code, out) = capture(app, "apk update -q >/dev/null 2>&1; apk add $core 2>&1 | tail -3; for p in $extras; do apk add \$p >/dev/null 2>&1 || echo \"skipped \$p\"; done")
                    if (!installed(app)) error("Couldn't install X.Org (exit $code): ${out.takeLast(2).joinToString(" ")}")
                }
                val root = Linux.rootfs(app)
                val fbDir = File(root, "tmp/x11fb").apply { deleteRecursively(); mkdirs() }
                File(root, "tmp/.X$display-lock").delete(); File(root, "tmp/.X11-unix/X$display").delete()
                // Security: a fresh random cookie each start, and no TCP unless "Allow other devices" is on.
                val cookie = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
                File(root, AUTH_FILE.trimStart('/')).writeBytes(XorgClient.xauthority(cookie))
                ui { status = "Starting X.Org…" }
                val access = if (network) "-listen tcp -ac" else "-nolisten tcp -auth $AUTH_FILE"
                val cmd = "exec Xvfb :$display -screen 0 ${w}x${h}x24 -dpi $dpi -fbdir /tmp/x11fb $access " +
                    "+extension GLX +extension RANDR +extension RENDER -noreset"
                val log = ArrayList<String>()
                val p = Linux.run(app, cmd, display, onOutput = { synchronized(log) { log += it } }) { code ->
                    if (state == State.RUNNING || state == State.STARTING) {
                        val why = synchronized(log) { log.takeLast(3).joinToString(" · ") }
                        AppLog.e("X.Org exited ($code): $why")
                        stopped(if (state == State.STARTING) null else "X.Org stopped (exit $code)")
                    }
                } ?: error("Couldn't start Linux")
                process = p
                // Wait until Xvfb has created its socket and framebuffer, then connect over the Unix socket.
                val sock = File(root, "tmp/.X11-unix/X$display")
                val deadline = System.currentTimeMillis() + 20_000
                var c: XorgClient? = null; var f: XwdFrame? = null
                while (System.currentTimeMillis() < deadline && (c == null || f == null)) {
                    if (!p.isAlive) error("X.Org didn't start: " + synchronized(log) { log.takeLast(3).joinToString(" · ") })
                    if (f == null) f = runCatching { XwdFrame.open(fbDir) }.getOrNull()
                    if (c == null && sock.exists()) c = runCatching {
                        val ls = android.net.LocalSocket()
                        ls.connect(android.net.LocalSocketAddress(sock.path, android.net.LocalSocketAddress.Namespace.FILESYSTEM))
                        XorgClient.connect(ls.inputStream, ls.outputStream, ls, cookie)
                    }.onFailure { AppLog.e("Connecting to X.Org", it) }.getOrNull()
                    if (c == null || f == null) Thread.sleep(200)
                }
                if (c == null || f == null) error("X.Org didn't answer in time")
                client = c; frame = f
                AppLog.i("X.Org running on :$display at ${f.width}x${f.height}")
                ui { width = f.width; height = f.height; state = State.RUNNING; status = ""; onReady() }
            } catch (e: Exception) {
                AppLog.e("X.Org engine", e)
                val msg = e.message ?: "X.Org failed"
                stop()
                ui { state = State.FAILED; status = msg; onFail(msg) }
            }
        }.start()
    }

    private fun stopped(reason: String?) {
        client?.close(); client = null; frame = null; process = null
        ui { state = if (reason == null) State.STOPPED else State.FAILED; status = reason.orEmpty() }
    }

    @Synchronized
    fun stop() {
        client?.close(); client = null; frame = null
        process?.destroy(); process = null
        ui { if (state != State.FAILED) state = State.STOPPED }
    }

    private fun capture(context: Context, command: String): Pair<Int, List<String>> {
        val latch = CountDownLatch(1); var code = -1; var out: List<String> = emptyList()
        ui { Linux.capture(context, command) { c, o -> code = c; out = o; latch.countDown() } }
        latch.await(15, TimeUnit.MINUTES)
        return code to out
    }
}
