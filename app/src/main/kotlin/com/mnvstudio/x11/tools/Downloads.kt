package com.mnvstudio.x11.tools

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import com.mnvstudio.x11.AppLog
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.linux.Linux
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/**
 * Downloads into Linux (default /root/Downloads): a queue running two at a time, pause and resume with HTTP ranges,
 * several connections per file when the server allows it, and a history kept across restarts.
 */
object Downloads {
    enum class State { QUEUED, RUNNING, PAUSED, DONE, FAILED }

    class Item(val id: Long, val url: String, val file: File, var total: Long = -1, var state: State = State.QUEUED,
               var error: String = "", val added: Long = System.currentTimeMillis(), var finished: Long = 0,
               /** Per connection: start, end (inclusive), bytes done. */
               var parts: MutableList<LongArray> = mutableListOf()) {
        val done get() = parts.sumOf { it[2] }.let { if (parts.isEmpty()) file.length() else it }
        @Volatile var speed = 0L
        @Volatile var stop = false
        val name get() = file.name
    }

    val items = mutableStateListOf<Item>()
    /** Bumped whenever progress changes, so screens recompose. */
    val tick = mutableIntStateOf(0)
    private var store: File? = null
    private var appContext: Context? = null
    private const val PARALLEL = 2
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    fun init(context: Context) {
        if (store != null) return
        appContext = context.applicationContext
        store = File(context.filesDir, "downloads.json")
        runCatching {
            val a = JSONArray(store!!.readText())
            for (i in 0 until a.length()) a.getJSONObject(i).let { j ->
                val parts = j.optJSONArray("parts")?.let { p -> (0 until p.length()).map { k -> p.getJSONArray(k).let { longArrayOf(it.getLong(0), it.getLong(1), it.getLong(2)) } } }
                items += Item(j.getLong("id"), j.getString("url"), File(j.getString("file")), j.optLong("total", -1),
                    State.valueOf(j.getString("state")).let { if (it == State.RUNNING || it == State.QUEUED) State.PAUSED else it },
                    j.optString("error"), j.optLong("added"), j.optLong("finished"), parts?.toMutableList() ?: mutableListOf())
            }
        }
    }

    fun add(context: Context, url: String, name: String? = null): Item {
        init(context)
        val prefs = Prefs.of(context)
        val dir = File(Linux.rootfs(context), prefs.downloadDir.trimStart('/')).apply { mkdirs() }
        var fname = (name ?: URL(url).path.substringAfterLast('/')).ifBlank { "download" }.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        var f = File(dir, fname); var n = 1
        while (f.exists() || items.any { it.file == f }) { f = File(dir, fname.substringBeforeLast('.') + " ($n)" + fname.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }); n++ }
        val item = Item(System.currentTimeMillis(), url, f)
        items.add(0, item); save(); pump()
        return item
    }

    fun pause(item: Item) { item.stop = true; if (item.state == State.QUEUED) item.state = State.PAUSED; changed() }
    fun resume(item: Item) { if (item.state == State.PAUSED || item.state == State.FAILED) { item.state = State.QUEUED; item.error = ""; changed(); pump() } }
    fun remove(item: Item, deleteFile: Boolean) { item.stop = true; items.remove(item); if (deleteFile) item.file.delete(); save() }
    fun clearFinished() { items.removeAll { it.state == State.DONE }; save() }

    fun stats(): List<Pair<String, String>> {
        val done = items.filter { it.state == State.DONE }
        return listOf("Downloads" to "${items.size}", "Finished" to "${done.size}", "Total downloaded" to human(done.sumOf { it.file.length() }),
            "Average speed" to done.filter { it.finished > it.added }.let { d -> if (d.isEmpty()) "-" else human(d.sumOf { it.file.length() } * 1000 / d.sumOf { it.finished - it.added }.coerceAtLeast(1)) + "/s" })
    }

    private fun pump() {
        while (items.count { it.state == State.RUNNING } < PARALLEL) {
            val next = items.lastOrNull { it.state == State.QUEUED } ?: break
            next.state = State.RUNNING; next.stop = false
            Thread { run(next) }.start()
        }
        changed()
    }

    private fun run(item: Item) {
        try {
            val connections = Prefs.of(appContext!!).downloadConnections.coerceIn(1, 8)
            if (item.parts.isEmpty()) {
                // Probe: size and range support decide how many connections to use.
                val c = open(item.url, null)
                item.total = c.contentLengthLong
                val ranges = c.getHeaderField("Accept-Ranges") == "bytes" && item.total > 0
                c.disconnect()
                val n = if (ranges && item.total > 2_000_000) connections else 1
                if (!ranges) item.file.delete()
                val size = if (item.total > 0) item.total else Long.MAX_VALUE
                val chunk = if (n == 1) size else item.total / n
                item.parts = (0 until n).map { i -> longArrayOf(i * chunk, if (i == n - 1) (if (item.total > 0) item.total - 1 else -1) else (i + 1) * chunk - 1, 0) }.toMutableList()
                if (item.total > 0) RandomAccessFile(item.file, "rw").use { it.setLength(item.total) }
                save()
            }
            val counter = AtomicLong(item.done)
            var last = counter.get(); var lastT = System.nanoTime()
            val threads = item.parts.filter { it[1] < 0 || it[0] + it[2] <= it[1] }.map { part -> Thread { downloadPart(item, part, counter) }.apply { start() } }
            while (threads.any { it.isAlive }) {
                Thread.sleep(500)
                val now = System.nanoTime(); val c = counter.get()
                item.speed = ((c - last) * 1e9 / (now - lastT)).toLong(); last = c; lastT = now
                changed()
            }
            item.speed = 0
            item.state = when {
                item.error.isNotEmpty() -> State.FAILED
                item.stop -> State.PAUSED
                else -> { item.finished = System.currentTimeMillis(); AppLog.i("Downloaded ${item.name} (${human(item.file.length())})"); State.DONE }
            }
        } catch (e: Exception) {
            item.state = State.FAILED; item.error = e.message ?: e.javaClass.simpleName
        }
        save(); main.post { pump() }
    }

    private fun downloadPart(item: Item, part: LongArray, counter: AtomicLong) {
        try {
            val from = part[0] + part[2]
            val c = open(item.url, if (item.parts.size > 1 || part[2] > 0) "bytes=$from-${if (part[1] >= 0) part[1] else ""}" else null)
            if (part[2] > 0 && c.responseCode != 206) { part[2] = 0 }  // server ignored the range: start over
            RandomAccessFile(item.file, "rw").use { raf ->
                raf.seek(part[0] + part[2])
                c.inputStream.use { s ->
                    val b = ByteArray(65536); var n = 0
                    while (!item.stop && s.read(b).also { n = it } > 0) { raf.write(b, 0, n); part[2] += n; counter.addAndGet(n.toLong()) }
                }
            }
            c.disconnect()
        } catch (e: Exception) { if (!item.stop) item.error = e.message ?: "connection failed" }
    }

    private fun open(url: String, range: String?): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000; c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "X11-Android/1.0")
        range?.let { c.setRequestProperty("Range", it) }
        if (c.responseCode >= 400) error("HTTP ${c.responseCode}")
        return c
    }

    private fun changed() = main.post { tick.intValue++ }

    @Synchronized
    private fun save() {
        val f = store ?: return
        val a = JSONArray()
        items.toList().forEach { i ->
            a.put(JSONObject().put("id", i.id).put("url", i.url).put("file", i.file.path).put("total", i.total).put("state", i.state.name)
                .put("error", i.error).put("added", i.added).put("finished", i.finished)
                .put("parts", JSONArray(i.parts.map { JSONArray(it.toList()) })))
        }
        runCatching { f.writeText(a.toString()) }
        changed()
    }
}
