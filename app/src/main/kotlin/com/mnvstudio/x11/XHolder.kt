package com.mnvstudio.x11

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnvstudio.x11.linux.Catalog
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.server.XServer
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the running X server for the whole app. Screens start it directly (so apps can connect immediately);
 * the foreground service only keeps the process alive in the background.
 * Clients reach it on the abstract socket @/tmp/.X11-unix/X<n> (DISPLAY=:n) and on TCP port 6000+n.
 */
object XHolder {
    var server by mutableStateOf<XServer?>(null); private set
    var clients by mutableIntStateOf(0); private set
    var error by mutableStateOf<String?>(null); private set
    var display by mutableIntStateOf(0); private set
    /** Called with the client count whenever it changes (the service updates its notification). */
    @Volatile var onClientsChanged: ((Int) -> Unit)? = null
    /** Text last copied by a Linux app, so it isn't sent straight back to X. */
    @Volatile var lastFromX: String? = null; private set

    private val main = Handler(Looper.getMainLooper())
    private var idleSince = 0L
    private var appContext: Context? = null

    /** Starts the server now (if needed) and the foreground service that keeps it running. Returns false on failure. */
    fun ensureStarted(context: Context): Boolean {
        val ok = start(context, Prefs.of(context))
        if (ok) XService.start(context)
        return ok
    }

    @Synchronized
    fun start(context: Context, prefs: Prefs): Boolean {
        if (server != null) return true
        val app = context.applicationContext
        appContext = app
        val (w, h) = prefs.screenSize()
        val d = prefs.display
        val s = XServer(w, h, prefs.dpi())
        s.fitToScreen = prefs.fitWindows
        s.setResources("Xft.dpi:\t${prefs.dpi()}\n")
        if (prefs.clipboardSync) {
            val cm = app.getSystemService(ClipboardManager::class.java)
            s.syncPrimary = prefs.syncPrimary
            s.onClipboard = { text -> main.post { lastFromX = text; runCatching { cm.setPrimaryClip(ClipData.newPlainText("X11", text)) } } }
        }
        s.allowList = prefs.allowList.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        s.onRejected = { addr -> AppLog.i("Refused X11 connection from $addr (not in the allow list)") }
        s.onClients = { n -> main.post { updateClients(n) } }
        try {
            s.listenTcp(d, loopbackOnly = !prefs.lanAccess)
        } catch (e: Exception) {
            s.shutdown()
            error = "Display :$d is already in use by another X server. Pick another display number in Settings."
            AppLog.e("Couldn't listen on display :$d", e)
            return false
        }
        // The abstract socket is optional: TCP alone still works if another app holds the name.
        runCatching { listenAbstract(s, d) }.onFailure { AppLog.e("Abstract socket :$d unavailable", it) }
        error = null
        display = d
        clients = 0
        idleSince = SystemClock.elapsedRealtime()
        server = s
        AppLog.i("Server started on :$d at ${w}x$h, ${prefs.dpi()} dpi${if (prefs.lanAccess) ", network on" else ""}")
        afterStart(app, prefs)
        return true
    }

    /** Desktop mode and autostart apps, once the server accepts connections. */
    private fun afterStart(app: Context, prefs: Prefs) {
        Linux.check(app)
        if (Linux.state != Linux.State.READY) return
        // The profile's own startup script (Environment screen).
        if (java.io.File(Linux.rootfs(app), "etc/x11-startup.sh").exists()) Linux.run(app, "sh /etc/x11-startup.sh", prefs.display, jobName = "startup script")
        if (prefs.desktopMode) Catalog.byId("twm")?.let { if (Linux.isInstalled(app, it)) Linux.launch(app, it, prefs.display) }
        for (id in prefs.autostart) {
            val a = Catalog.byId(id) ?: continue
            if (a.id != "twm" && Linux.isInstalled(app, a)) Linux.launch(app, a, prefs.display)
        }
        scheduleIdleCheck(prefs)
    }

    private fun updateClients(n: Int) {
        if (clients == 0 && n > 0) appContext?.let { Sessions.firstClientConnected(it) }
        clients = n
        if (n == 0) idleSince = SystemClock.elapsedRealtime()
        onClientsChanged?.invoke(n)
    }

    /** Stops the server after the chosen idle time with nothing connected and no Linux apps running. */
    private fun scheduleIdleCheck(prefs: Prefs) {
        val minutes = prefs.idleStopMinutes
        if (minutes <= 0) return
        main.postDelayed(object : Runnable {
            override fun run() {
                val ctx = appContext ?: return
                if (server == null) return
                val idle = clients == 0 && Linux.runningCount() == 0 && SystemClock.elapsedRealtime() - idleSince > minutes * 60_000L
                if (idle) { AppLog.i("Stopping the idle server"); XService.stop(ctx) } else main.postDelayed(this, 60_000)
            }
        }, 60_000)
    }

    @Synchronized
    fun stop() {
        val s = server ?: return
        server = null
        clients = 0
        Linux.stopAll()
        AppLog.i("Server stopped")
        Thread { s.shutdown() }.start()
    }

    /** Offers Android's clipboard to Linux apps (Android only allows reading it while the app is in the foreground). */
    fun pushClipboard(context: Context) {
        val s = server ?: return
        val cm = context.getSystemService(ClipboardManager::class.java)
        val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull() ?: return
        if (text.isNotEmpty() && text != lastFromX) s.setClipboard(text)
    }

    private fun listenAbstract(s: XServer, d: Int) {
        val name = "/tmp/.X11-unix/X$d"
        val ss = LocalServerSocket(name)
        val closed = AtomicBoolean(false)
        s.addListener(Closeable {
            closed.set(true)
            // accept() isn't interrupted by close() on every Android version; a dummy connection wakes it first.
            runCatching { LocalSocket().use { it.connect(LocalSocketAddress(name)) } }
            runCatching { ss.close() }
        })
        Thread({
            while (!closed.get()) {
                val sock = runCatching { ss.accept() }.getOrNull() ?: break
                if (closed.get()) { runCatching { sock.close() }; break }
                s.serve(sock.inputStream, sock.outputStream) { runCatching { sock.close() } }
            }
        }, "x11-accept-local").apply { isDaemon = true }.start()
    }
}
