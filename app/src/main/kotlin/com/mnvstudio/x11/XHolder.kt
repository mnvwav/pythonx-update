package com.mnvstudio.x11

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnvstudio.x11.server.XServer
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the running X server for the whole app (the service starts and stops it, screens observe it).
 * Clients reach it on the abstract socket @/tmp/.X11-unix/X<n> (DISPLAY=:n, what Termux uses) and on 127.0.0.1:6000+n.
 */
object XHolder {
    var server by mutableStateOf<XServer?>(null); private set
    var clients by mutableIntStateOf(0); private set
    var error by mutableStateOf<String?>(null); private set
    var display by mutableIntStateOf(0); private set
    /** Called with the client count whenever it changes (the service updates its notification). */
    @Volatile var onClientsChanged: ((Int) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    @Synchronized
    fun start(prefs: Prefs): Boolean {
        if (server != null) return true
        val (w, h) = prefs.screenSize()
        val d = prefs.display
        val s = XServer(w, h, prefs.dpi())
        s.fitToScreen = prefs.fitWindows
        s.onClients = { n -> main.post { clients = n; onClientsChanged?.invoke(n) } }
        try {
            s.listenTcp(d)
        } catch (e: Exception) {
            s.shutdown()
            error = "Display :$d is already in use by another X server. Pick another display number in Settings."
            return false
        }
        // The abstract socket is optional: TCP alone still works if another app holds the name.
        runCatching { listenAbstract(s, d) }
        error = null
        display = d
        clients = 0
        server = s
        return true
    }

    @Synchronized
    fun stop() {
        val s = server ?: return
        server = null
        clients = 0
        Thread { s.shutdown() }.start()
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
