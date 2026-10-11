package com.mnvstudio.x11.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnvstudio.x11.AppLog
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A small HTTP file server for sharing Linux files with other devices on the same Wi-Fi: browse, download, and
 * upload from any browser. Every URL carries a random token, so only people you show the link to get in.
 */
object FileServer {
    var running by mutableStateOf(false); private set
    var port by mutableIntStateOf(8080); private set
    var token by mutableStateOf(""); private set
    var root: File? = null; private set
    var readOnly = false
    var requests by mutableIntStateOf(0); private set
    private var server: ServerSocket? = null

    fun start(dir: File, preferredPort: Int = 8080) {
        stop()
        root = dir.canonicalFile
        token = (1..8).map { "abcdefghjkmnpqrstuvwxyz23456789".random() }.joinToString("")
        val ss = (preferredPort until preferredPort + 20).firstNotNullOfOrNull { p -> runCatching { ServerSocket(p) }.getOrNull() } ?: error("No free port")
        server = ss; port = ss.localPort; running = true; requests = 0
        AppLog.i("File sharing on port $port from ${dir.path}")
        Thread({
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                Thread { runCatching { s.use { handle(it) } } }.start()
            }
        }, "file-server").apply { isDaemon = true }.start()
    }

    fun stop() { runCatching { server?.close() }; server = null; running = false }

    private fun handle(s: Socket) {
        s.soTimeout = 30_000
        val inp = s.getInputStream().buffered()
        val request = line(inp)
        val headers = generateSequence { line(inp) }.takeWhile { it.isNotEmpty() }.associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
        val parts = request.split(' ')
        if (parts.size < 2) return
        val method = parts[0]
        val raw = parts[1]
        val out = s.getOutputStream()
        val tokenPart = raw.trimStart('/').substringBefore('/')
        if (tokenPart != token) return send(out, 403, "text/plain", "Wrong or missing link token.".toByteArray())
        ui { requests++ }
        val rel = URLDecoder.decode(raw.trimStart('/').substringAfter('/', "").substringBefore('?'), "UTF-8")
        val base = root ?: return
        val f = File(base, rel).canonicalFile
        if (!f.path.startsWith(base.path)) return send(out, 403, "text/plain", "Outside the shared folder".toByteArray())
        when {
            method == "PUT" && !readOnly -> {
                f.parentFile?.mkdirs()
                val len = headers["content-length"]?.toLongOrNull() ?: 0L
                f.outputStream().use { o -> copy(inp, o, len) }
                AppLog.i("File sharing: received ${f.name} (${human(len)})")
                send(out, 201, "text/plain", "Saved".toByteArray())
            }
            f.isDirectory -> send(out, 200, "text/html; charset=utf-8", page(base, f).toByteArray())
            f.isFile -> {
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: ${mime(f.name)}\r\nContent-Length: ${f.length()}\r\n" +
                    "Content-Disposition: inline; filename=\"${f.name.replace("\"", "")}\"\r\nConnection: close\r\n\r\n").toByteArray())
                f.inputStream().use { it.copyTo(out) }
            }
            else -> send(out, 404, "text/plain", "Not found".toByteArray())
        }
    }

    private fun page(base: File, dir: File): String {
        val rel = dir.path.removePrefix(base.path).trim('/')
        fun href(r: String) = "/$token/" + r.split('/').filter { it.isNotEmpty() }.joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val items = (dir.listFiles() ?: emptyArray()).sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        val rows = buildString {
            if (rel.isNotEmpty()) append("<a class=r href='${href(rel.substringBeforeLast('/', ""))}'>⬆ Up</a>")
            items.forEach { f ->
                val r = if (rel.isEmpty()) f.name else "$rel/${f.name}"
                append("<a class=r href='${href(r)}${if (f.isDirectory) "/" else ""}'><span>${if (f.isDirectory) "📁" else "📄"} ${esc(f.name)}</span><small>${if (f.isFile) human(f.length()) else ""}</small></a>")
            }
        }
        val upload = if (readOnly) "" else """
            <label class=up>Upload files<input type=file multiple onchange="up(this.files)"></label><div id=st></div>
            <script>async function up(fs){for(const f of fs){st.textContent='Uploading '+f.name+'…';
            await fetch(location.pathname.replace(/\/?$/,'/')+encodeURIComponent(f.name),{method:'PUT',body:f});}st.textContent='Done';location.reload()}</script>"""
        return """<!doctype html><meta name=viewport content="width=device-width,initial-scale=1"><title>X11 files</title>
            <style>body{font-family:system-ui,sans-serif;margin:0;background:#111;color:#eee}header{padding:20px;background:linear-gradient(135deg,#5B8CFF,#7C4DFF)}
            h1{margin:0;font-size:20px}p{margin:4px 0 0;opacity:.85}.r{display:flex;justify-content:space-between;padding:14px 20px;border-bottom:1px solid #222;color:#eee;text-decoration:none}
            .r:hover{background:#1c1c20}small{opacity:.6}.up{display:block;margin:16px 20px;padding:14px;border-radius:12px;background:#5B8CFF;text-align:center;cursor:pointer}
            .up input{display:none}#st{margin:0 20px}</style>
            <header><h1>X11 · shared files</h1><p>/${esc(rel)}</p></header>$upload$rows"""
    }

    private fun send(out: OutputStream, code: Int, type: String, body: ByteArray) {
        out.write("HTTP/1.1 $code ${if (code < 300) "OK" else "Error"}\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
    }

    private fun copy(i: InputStream, o: OutputStream, len: Long) {
        val b = ByteArray(65536); var left = len
        while (left > 0) { val n = i.read(b, 0, minOf(b.size.toLong(), left).toInt()); if (n < 0) break; o.write(b, 0, n); left -= n }
    }

    private fun line(i: InputStream): String {
        val sb = StringBuilder(); while (true) { val c = i.read(); if (c < 0 || c == '\n'.code) break; if (c != '\r'.code) sb.append(c.toChar()) }; return sb.toString()
    }

    private fun mime(n: String) = when (n.substringAfterLast('.').lowercase()) {
        "html", "htm" -> "text/html"; "txt", "md", "log", "sh", "py", "conf" -> "text/plain; charset=utf-8"; "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "svg" -> "image/svg+xml"; "pdf" -> "application/pdf"; "json" -> "application/json"
        "mp4" -> "video/mp4"; "mp3" -> "audio/mpeg"; else -> "application/octet-stream"
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private fun ui(b: () -> Unit) { main.post(b) }
}
