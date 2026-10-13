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
import java.net.InetSocketAddress
import java.net.Socket
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
    val PACKAGES = listOf("xvfb", "xkbcomp", "xkeyboard-config", "font-dejavu", "mesa-dri-gallium", "dbus-x11")

    fun installed(context: Context) = File(Linux.rootfs(context), "usr/bin/Xvfb").exists()

    /** Starts Xvfb on [display] in the background; [onReady] runs on the main thread once input and screen are hooked up. */
    @Synchronized
    fun start(context: Context, w: Int, h: Int, dpi: Int, display: Int, onReady: () -> Unit, onFail: (String) -> Unit) {
        if (state == State.STARTING || state == State.RUNNING) return
        val app = context.applicationContext
        state = State.STARTING; status = "Starting X.Org…"; width = w; height = h
        Thread {
            try {
                Linux.check(app)
                if (Linux.state != Linux.State.READY) error("Set up Linux first (Linux tab), X.Org runs inside it.")
                if (!installed(app)) {
                    ui { status = "Installing X.Org (one time, about 25 MB)…" }
                    val (code, out) = capture(app, "apk add ${PACKAGES.joinToString(" ")} 2>&1 | tail -3")
                    if (code != 0 || !installed(app)) error("Couldn't install X.Org: ${out.takeLast(2).joinToString(" ")}")
                }
                val root = Linux.rootfs(app)
                val fbDir = File(root, "tmp/x11fb").apply { deleteRecursively(); mkdirs() }
                File(root, "tmp/.X$display-lock").delete(); File(root, "tmp/.X11-unix/X$display").delete()
                ui { status = "Starting X.Org…" }
                val cmd = "exec Xvfb :$display -screen 0 ${w}x${h}x24 -dpi $dpi -fbdir /tmp/x11fb -listen tcp -ac " +
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
                // Wait until Xvfb accepts connections and has created its framebuffer file.
                val port = 6000 + display
                val deadline = System.currentTimeMillis() + 20_000
                var c: XorgClient? = null; var f: XwdFrame? = null
                while (System.currentTimeMillis() < deadline && (c == null || f == null)) {
                    if (!p.isAlive) error("X.Org didn't start: " + synchronized(log) { log.takeLast(3).joinToString(" · ") })
                    if (f == null) f = runCatching { XwdFrame.open(fbDir) }.getOrNull()
                    if (c == null && runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300) } }.isSuccess)
                        c = runCatching { XorgClient.connect("127.0.0.1", port) }.onFailure { AppLog.e("XTEST connect", it) }.getOrNull()
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
