package com.mnvstudio.x11.linux

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * A built-in Alpine Linux, run with proot (no root needed). The app downloads Alpine's minimal root filesystem
 * (a few MB) on first use; programs are installed with `apk` and shown on the X server through DISPLAY.
 *
 * proot ships inside the APK as libproot.so / libproot-loader.so, because Android only lets apps execute
 * files from their native library directory.
 */
object Linux {
    enum class State { MISSING, INSTALLING, READY, FAILED }

    var state by mutableStateOf(State.MISSING); private set
    var progress by mutableFloatStateOf(0f); private set
    var status by mutableStateOf(""); private set
    /** Bumped when packages change, so screens re-check what's installed. */
    var revision by mutableIntStateOf(0); private set

    private val main = Handler(Looper.getMainLooper())
    private val running = ArrayList<Process>()

    fun rootfs(context: Context) = File(context.filesDir, "alpine")
    private fun marker(context: Context) = File(rootfs(context), ".mnv-ready")

    fun check(context: Context) {
        if (state != State.INSTALLING) state = if (marker(context).exists()) State.READY else State.MISSING
    }

    fun prootAvailable(context: Context) = File(context.applicationInfo.nativeLibraryDir, "libproot.so").exists()

    private val arch: String get() = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "aarch64"; "x86_64" -> "x86_64"; "armeabi-v7a" -> "armv7"; "x86" -> "x86"; else -> "aarch64"
    }

    private fun ui(block: () -> Unit) = main.post(block)

    /** Downloads and unpacks Alpine, then prepares DNS and the X11 socket directory. Runs in the background. */
    fun install(context: Context) {
        if (state == State.INSTALLING) return
        state = State.INSTALLING; progress = 0f; status = "Finding the latest Alpine Linux…"
        val app = context.applicationContext
        Thread {
            try {
                val base = "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/$arch/"
                val yaml = URL(base + "latest-releases.yaml").readText()
                val file = Regex("file:\\s*(alpine-minirootfs-[^\\s]+\\.tar\\.gz)").find(yaml)?.groupValues?.get(1)
                    ?: error("Couldn't find the Alpine download")
                ui { status = "Downloading $file" }
                val tmp = File(app.cacheDir, "rootfs.tar.gz")
                download(base + file, tmp) { p -> ui { progress = p * 0.8f } }
                val root = rootfs(app)
                root.deleteRecursively(); root.mkdirs()
                ui { status = "Unpacking…" }
                var n = 0
                GZIPInputStream(tmp.inputStream().buffered()).use { Tar.extract(it, root) { if (++n % 50 == 0) ui { progress = 0.8f + minOf(0.19f, n / 6000f) } } }
                tmp.delete()
                File(root, "etc/resolv.conf").apply { delete(); writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n") }
                File(root, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
                File(root, "tmp/.X11-unix").mkdirs()
                File(root, "root").mkdirs()
                marker(app).writeText(file)
                ui { progress = 1f; status = "Ready"; state = State.READY; revision++ }
            } catch (e: Exception) {
                ui { status = "Setup failed: ${e.message ?: e.javaClass.simpleName}. Check your internet connection and try again."; state = State.FAILED }
            }
        }.start()
    }

    private fun download(url: String, to: File, progress: (Float) -> Unit) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            to.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n); done += n
                    if (total > 0) progress(done.toFloat() / total)
                }
            }
        }
    }

    /**
     * Runs a shell command inside Alpine. Output lines go to [onOutput]; [onExit] gets the exit code.
     * Programs see DISPLAY=:[display], so X11 apps appear on this app's server.
     */
    fun run(context: Context, command: String, display: Int, onOutput: (String) -> Unit = {}, onExit: (Int) -> Unit = {}): Process? {
        val app = context.applicationContext
        val lib = app.applicationInfo.nativeLibraryDir
        val root = rootfs(app)
        val tmp = File(app.cacheDir, "proot").apply { mkdirs() }
        val args = listOf(
            "$lib/libproot.so", "--kill-on-exit", "-0", "--link2symlink",
            "-r", root.path,
            "-b", "/dev", "-b", "/proc", "-b", "/sys", "-b", "${File(root, "tmp").path}:/dev/shm",
            "-w", "/root",
            "/bin/sh", "-lc", command,
        )
        val pb = ProcessBuilder(args).redirectErrorStream(true)
        pb.environment().apply {
            clear()
            put("PROOT_LOADER", "$lib/libproot-loader.so")
            if (File(lib, "libproot-loader32.so").exists()) put("PROOT_LOADER_32", "$lib/libproot-loader32.so")
            put("PROOT_TMP_DIR", tmp.path)
            put("HOME", "/root")
            put("USER", "root")
            put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            put("TERM", "xterm-256color")
            put("LANG", "C.UTF-8")
            put("TMPDIR", "/tmp")
            put("DISPLAY", ":$display")
        }
        val p = try { pb.start() } catch (e: Exception) {
            onOutput("Couldn't start Linux: ${e.message}"); onExit(-1); return null
        }
        synchronized(running) { running += p }
        Thread {
            runCatching { p.inputStream.bufferedReader().forEachLine { line -> ui { onOutput(line) } } }
            val code = runCatching { p.waitFor() }.getOrDefault(-1)
            synchronized(running) { running -= p }
            ui { onExit(code) }
        }.start()
        return p
    }

    /** Installs Alpine packages, reporting output; [onDone] gets whether it worked. */
    fun installPackages(context: Context, packages: List<String>, display: Int, onOutput: (String) -> Unit, onDone: (Boolean) -> Unit) {
        run(context, "apk update && apk add ${packages.joinToString(" ")}", display, onOutput) { code ->
            revision++
            onDone(code == 0)
        }
    }

    fun isInstalled(context: Context, binary: String): Boolean =
        File(rootfs(context), "usr/bin/$binary").exists() || File(rootfs(context), "bin/$binary").exists()

    /** Stops every Linux program this app started. */
    fun stopAll() {
        synchronized(running) { running.toList() }.forEach { it.destroy() }
    }

    fun runningCount() = synchronized(running) { running.size }

    /** Deletes the Linux system (installed apps and their files). */
    fun remove(context: Context) {
        stopAll()
        rootfs(context).deleteRecursively()
        state = State.MISSING; revision++
    }
}
