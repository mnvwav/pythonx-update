package com.mnvstudio.x11.tools

import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.server.Inspector
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Pretty-printing and validation for JSON, XML and YAML-ish text. */
object DataFormat {
    fun pretty(text: String): List<String> {
        val t = text.trim()
        if (t.isEmpty()) return listOf("Nothing to show: pick a file or paste text.")
        return when {
            t.startsWith("{") || t.startsWith("[") -> runCatching {
                val v = JSONTokener(t).nextValue()
                listOf("Valid JSON") + "" + (if (v is JSONObject) v.toString(2) else (v as JSONArray).toString(2)).lines()
            }.getOrElse { listOf("Invalid JSON: ${it.message}") }
            t.startsWith("<") -> runCatching {
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(org.xml.sax.InputSource(StringReader(t)))
                val w = StringWriter()
                TransformerFactory.newInstance().newTransformer().apply {
                    setOutputProperty(OutputKeys.INDENT, "yes"); setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
                }.transform(DOMSource(doc), StreamResult(w))
                listOf("Valid XML (root <${doc.documentElement.tagName}>)") + "" + w.toString().lines()
            }.getOrElse { listOf("Invalid XML: ${it.message}") }
            else -> {
                // YAML: check indentation consistency and show an outline of the keys.
                val problems = t.lines().mapIndexedNotNull { i, l -> if (l.startsWith("\t")) "Line ${i + 1}: tabs aren't allowed in YAML" else null }
                (if (problems.isEmpty()) listOf("Looks like YAML (no tab indentation problems)") else problems) + "" +
                    t.lines().filter { Regex("^\\s*[^#\\s][^:]*:").containsMatchIn(it) }.map { it.substringBefore(':') + ":" }
            }
        }
    }
}

object SystemInfo {
    fun report(context: Context): List<Pair<String, String>> {
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val cpu = runCatching { File("/proc/cpuinfo").readLines() }.getOrDefault(emptyList())
        val linux = runCatching { File(Linux.rootfs(context), "etc/alpine-release").readText().trim() }.getOrNull()
        val stat = android.os.StatFs(context.filesDir.path)
        return listOf(
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Security patch" to Build.VERSION.SECURITY_PATCH,
            "ABIs" to Build.SUPPORTED_ABIS.joinToString(),
            "CPU cores" to "${Runtime.getRuntime().availableProcessors()}",
            "CPU" to (cpu.firstOrNull { it.startsWith("Hardware") || it.startsWith("model name") }?.substringAfter(':')?.trim() ?: Build.HARDWARE),
            "SoC" to (if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.BOARD),
            "RAM" to "${human(mi.availMem)} free of ${human(mi.totalMem)}",
            "Low memory now" to "${mi.lowMemory}",
            "App storage" to "${human(stat.availableBytes)} free of ${human(stat.totalBytes)}",
            "Screen" to context.resources.displayMetrics.let { "${it.widthPixels}×${it.heightPixels} @ ${it.densityDpi} dpi" },
            "Kernel" to (System.getProperty("os.version") ?: "?"),
            "Linux" to (linux?.let { "Alpine $it" } ?: "not set up"),
            "Linux profile" to Linux.activeProfileName(context),
            "Java VM" to "${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}",
        )
    }
}

object ProfileCompare {
    fun compare(context: Context, a: String, b: String): List<String> {
        val dir = Linux.profilesDir(context)
        val names = dir.list()?.sorted() ?: emptyList()
        if (a !in names || b !in names) return listOf("Profiles: ${names.joinToString()}", "Enter two of these names.")
        fun env(p: String) = runCatching { File(dir, "$p/etc/x11-env").readLines().filter { '=' in it } }.getOrDefault(emptyList()).toSet()
        fun pkgs(p: String) = runCatching { File(dir, "$p/etc/apk/world").readLines() }.getOrDefault(emptyList()).toSet()
        val out = ArrayList<String>()
        fun section(title: String, x: Set<String>, y: Set<String>) {
            out += "== $title"
            (x - y).sorted().forEach { out += "  only in $a: $it" }
            (y - x).sorted().forEach { out += "  only in $b: $it" }
            if (x == y) out += "  same"
        }
        section("Installed packages", pkgs(a), pkgs(b))
        section("Environment variables", env(a), env(b))
        return out
    }
}

object DisplayTools {
    fun screenshot(context: Context): String {
        val s = XHolder.server ?: return "The display isn't running."
        val bmp = Bitmap.createBitmap(s.width, s.height, Bitmap.Config.ARGB_8888)
        synchronized(s) { bmp.setPixels(s.framebuffer, 0, s.width, 0, 0, s.width, s.height) }
        val name = "X11_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/X11")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return "Couldn't create the image file."
        context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "Saved Pictures/X11/$name (${s.width}×${s.height})"
    }

    fun pixel(x: Int, y: Int): List<Pair<String, String>> {
        val s = XHolder.server ?: return listOf("Display" to "not running")
        if (x !in 0 until s.width || y !in 0 until s.height) return listOf("Position" to "outside the ${s.width}×${s.height} screen")
        val c = synchronized(s) { s.framebuffer[y * s.width + x] } and 0xFFFFFF
        val r = c shr 16; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return listOf("Position" to "$x, $y", "Hex" to "#%06X".format(c), "RGB" to "$r, $g, $b",
            "Window" to (synchronized(s) { s.wm?.windows()?.firstOrNull { it.focused }?.title } ?: "-"))
    }

    fun capabilities(): List<Pair<String, String>> {
        val s = XHolder.server
        return listOf(
            "Running" to if (s != null) "yes, display :${XHolder.display}" else "no",
            "Resolution" to (s?.let { "${it.width}×${it.height}" } ?: "-"),
            "DPI" to (s?.dpi?.toString() ?: "-"), "Depth" to "24-bit TrueColor",
            "Clients" to "${XHolder.clients}",
        ) + Inspector.EXTENSIONS.map { (n, ok, what) -> n to ((if (ok) "yes · " else "no · ") + what) }
    }

    fun clipboard(context: Context): List<Pair<String, String>> {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val clip = runCatching { cm.primaryClip }.getOrNull()
        val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()
        return listOf(
            "Android type" to (clip?.description?.getMimeType(0) ?: "empty"),
            "Android text" to (text?.take(500) ?: "(none or not readable in background)"),
            "Length" to "${text?.length ?: 0} characters",
            "Last from Linux" to (XHolder.lastFromX?.take(500) ?: "(nothing copied in Linux yet)"),
        )
    }
}

/** A small offline reference for common commands. */
object CommandReference {
    private val ENTRIES = listOf(
        "ls -la" to "List files with details and hidden files", "cd DIR" to "Change folder", "pwd" to "Show current folder",
        "cp -r A B" to "Copy files or folders", "mv A B" to "Move or rename", "rm -r X" to "Delete (no undo!)", "mkdir -p A/B" to "Create folders",
        "cat F" to "Print a file", "less F" to "Page through a file", "head -n 20 F" to "First lines", "tail -f F" to "Follow a growing file",
        "grep -rn TEXT DIR" to "Find text in files", "find DIR -name '*.txt'" to "Find files by name", "du -sh *" to "Size of each item",
        "df -h" to "Free disk space", "free -m" to "Memory use", "top / htop" to "Running processes", "ps aux" to "List processes",
        "kill PID" to "Stop a process", "chmod 755 F" to "Make executable (rwxr-xr-x)", "chown user:group F" to "Change owner",
        "ln -s TARGET NAME" to "Create a symbolic link", "tar -czf a.tgz DIR" to "Create archive", "tar -xf a.tgz" to "Extract archive",
        "unzip a.zip" to "Extract zip", "wget URL" to "Download a file", "curl -I URL" to "Show HTTP headers", "ssh user@host" to "Remote shell",
        "scp F user@host:" to "Copy file over SSH", "apk add PKG" to "Install a package", "apk del PKG" to "Remove a package",
        "apk search TEXT" to "Search packages", "apk upgrade" to "Update everything", "env" to "Environment variables",
        "export NAME=value" to "Set a variable", "echo \$DISPLAY" to "Which X display apps use", "which CMD" to "Where a command lives",
        "history" to "Previous commands", "crontab -e" to "Edit scheduled jobs", "ip addr" to "Network interfaces", "nano F" to "Edit a file",
        "python3 F.py" to "Run a Python script", "sh script.sh" to "Run a shell script", "diff -u A B" to "Compare files", "sort | uniq -c" to "Count lines",
        "xargs" to "Build commands from input", "sed 's/a/b/g' F" to "Replace text", "awk '{print \$1}'" to "Pick columns", "wc -l F" to "Count lines",
    )

    fun search(q: String) = ENTRIES.filter { q.isBlank() || it.first.contains(q, true) || it.second.contains(q, true) }
}
