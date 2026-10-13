package com.mnvstudio.x11.linux

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnvstudio.x11.AppLog
import com.mnvstudio.x11.Prefs
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

    /** A program started from the app, shown in the running list with a Stop button. */
    class Job(val name: String, val process: Process, val started: Long = System.currentTimeMillis())

    /** One check in the Linux self-test; [ok] is null while it's pending. */
    data class Check(val name: String, val ok: Boolean? = null, val detail: String = "")

    var state by mutableStateOf(State.MISSING); private set
    var progress by mutableFloatStateOf(0f); private set
    var status by mutableStateOf(""); private set
    /** Bumped when packages change, so screens re-check what's installed. */
    var revision by mutableIntStateOf(0); private set
    /** Programs started from the app that are still running. */
    val jobs = mutableStateListOf<Job>()
    /** Why the last app closed with an error (its last output lines), shown on the display. */
    var lastFailure by mutableStateOf<String?>(null)

    private val main = Handler(Looper.getMainLooper())
    private fun ui(block: () -> Unit) { main.post(block) }

    // ---------------------------------------------------------------- profiles

    /** Each Linux profile is a separate root filesystem under files/profiles/<name>. */
    fun profilesDir(context: Context) = File(context.filesDir, "profiles")

    /** Moves the single environment of earlier versions into the "default" profile. */
    private fun migrate(context: Context) {
        val old = File(context.filesDir, "alpine")
        val target = File(profilesDir(context), "default")
        if (old.isDirectory && !target.exists()) { profilesDir(context).mkdirs(); old.renameTo(target) }
    }

    fun activeProfileName(context: Context): String = Prefs.of(context).linuxProfile

    fun profiles(context: Context): List<String> {
        migrate(context)
        return (profilesDir(context).listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()).sorted()
    }

    fun rootfs(context: Context): File { migrate(context); return File(profilesDir(context), activeProfileName(context)) }
    private fun marker(context: Context) = File(rootfs(context), ".x11-ready")

    /** Tells screens that files or packages changed. */
    fun bump() { revision++ }

    fun check(context: Context) {
        if (state != State.INSTALLING) state = if (marker(context).exists() && !tooNew(context)) State.READY else State.MISSING
        if (state == State.MISSING && marker(context).exists()) status = "Linux needs a quick reinstall (Alpine $ALPINE) so packages can install."
    }

    fun prootAvailable(context: Context) = File(context.applicationInfo.nativeLibraryDir, "libproot.so").exists()

    private val arch: String get() = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "aarch64"; "x86_64" -> "x86_64"; "armeabi-v7a" -> "armv7"; "x86" -> "x86"; else -> "aarch64"
    }

    // ---------------------------------------------------------------- setup

    /** Alpine release to install; see [install] for why it's pinned. */
    const val ALPINE = "3.22"

    /** True for a rootfs from Alpine 3.23 or later, whose apk doesn't work under proot. */
    fun tooNew(context: Context): Boolean = runCatching {
        val v = File(rootfs(context), "etc/alpine-release").readText().trim().split('.')
        v[0].toInt() > 3 || (v[0].toInt() == 3 && v[1].toInt() >= 23)
    }.getOrDefault(false)

    /** Downloads and unpacks Alpine, then prepares DNS, repositories and the X11 socket directory. */
    fun install(context: Context) {
        if (state == State.INSTALLING) return
        state = State.INSTALLING; progress = 0f; status = "Finding Alpine Linux $ALPINE…"
        val app = context.applicationContext
        Thread {
            try {
                // Pinned: Alpine 3.23+ ships apk-tools 3, which fails under proot ("Operation not permitted" /
                // "Function not implemented" while opening APKINDEX). 3.22 still has apk-tools 2.
                val base = "https://dl-cdn.alpinelinux.org/alpine/v$ALPINE/releases/$arch/"
                Prefs.of(app).prootNoSeccomp = false
                val yaml = URL(base + "latest-releases.yaml").readText()
                val file = Regex("file:\\s*(alpine-minirootfs-[^\\s]+\\.tar\\.gz)").find(yaml)?.groupValues?.get(1)
                    ?: error("couldn't find the Alpine download")
                ui { status = "Downloading $file" }
                val tmp = File(app.cacheDir, "rootfs.tar.gz")
                download(base + file, tmp) { p -> ui { progress = p * 0.8f } }
                val root = rootfs(app)
                root.deleteRecursively(); root.mkdirs()
                ui { status = "Unpacking…" }
                var n = 0
                GZIPInputStream(tmp.inputStream().buffered()).use { Tar.extract(it, root) { if (++n % 50 == 0) ui { progress = 0.8f + minOf(0.19f, n / 6000f) } } }
                tmp.delete()
                prepare(root)
                marker(app).writeText(file)
                AppLog.i("Linux installed: $file")
                ui { progress = 1f; status = "Ready"; state = State.READY; revision++ }
            } catch (e: Exception) {
                AppLog.e("Linux setup failed", e)
                ui { status = "Setup failed: ${e.message ?: e.javaClass.simpleName}. Check your internet connection and try again."; state = State.FAILED }
            }
        }.start()
    }

    /** Files Alpine needs on Android: DNS servers, hosts, X11 socket dir and a shared folder. */
    private fun prepare(root: File) {
        File(root, "etc/resolv.conf").apply { delete(); writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n") }
        File(root, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
        File(root, "tmp/.X11-unix").mkdirs()
        File(root, "root/Shared").mkdirs()
        com.mnvstudio.x11.bridge.Bridge.installShims(root)
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

    // ---------------------------------------------------------------- running

    /**
     * Runs a shell command inside Alpine. Output lines go to [onOutput] and [onExit] gets the exit code, both on the
     * main thread. Programs see DISPLAY=127.0.0.1:[display], so X11 apps appear on this app's server.
     * When [jobName] is given the process is listed in [jobs] until it exits.
     */
    fun run(context: Context, command: String, display: Int, jobName: String? = null, env: Map<String, String> = emptyMap(),
            onOutput: (String) -> Unit = {}, onExit: (Int) -> Unit = {}): Process? {
        val app = context.applicationContext
        com.mnvstudio.x11.bridge.Bridge.ensure(app)
        val lib = app.applicationInfo.nativeLibraryDir
        val root = rootfs(app)
        val tmp = File(app.cacheDir, "proot").apply { mkdirs() }
        // Run as the chosen normal user (Users screen) unless this is a system task.
        val user = Prefs.of(app).linuxUser
        val asUser = user.isNotEmpty() && user != "root" && !command.startsWith("apk ")
        // With the X.Org engine an app may start while Xvfb is still booting: wait (up to 20 s) for its socket.
        val waited = if (jobName != null && Prefs.of(app).engine == Prefs.ENGINE_XORG)
            "i=0; while [ ! -e /tmp/.X11-unix/X$display ] && [ \$i -lt 100 ]; do sleep 0.2; i=\$((i+1)); done; $command" else command
        val shellCommand = if (asUser) "su $user -c ${quote(waited)}" else waited
        val args = listOf(
            "$lib/libproot.so", "--kill-on-exit", "-0", "--link2symlink",
            "-r", root.path,
            "-b", "/dev", "-b", "/proc", "-b", "/sys", "-b", "${File(root, "tmp").path}:/dev/shm",
        ) + com.mnvstudio.x11.bridge.PhoneStorage.binds(app, root) + listOf(
            "-w", "/root",
            "/bin/sh", "-lc", shellCommand,
        )
        val pb = ProcessBuilder(args).redirectErrorStream(true)
        pb.environment().apply {
            clear()
            put("PROOT_LOADER", "$lib/libproot-loader.so")
            if (File(lib, "libproot-loader32.so").exists()) put("PROOT_LOADER_32", "$lib/libproot-loader32.so")
            put("PROOT_TMP_DIR", tmp.path)
            // Some kernels break proot's seccomp acceleration; Diagnostics turns this on when it helps.
            if (Prefs.of(app).prootNoSeccomp) put("PROOT_NO_SECCOMP", "1")
            put("HOME", "/root")
            put("USER", "root")
            put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            put("TERM", "xterm-256color")
            put("LANG", "C.UTF-8")
            put("TMPDIR", "/tmp")
            if (Prefs.of(app).engine == Prefs.ENGINE_XORG) {
                // X.Org: its Unix socket plus this run's secret cookie.
                put("DISPLAY", ":$display"); put("XAUTHORITY", com.mnvstudio.x11.xorg.XorgEngine.AUTH_FILE)
            } else {
                // Built-in server: loopback TCP is the most reliable route from inside proot.
                put("DISPLAY", "127.0.0.1:$display")
            }
            // Sound goes to Android through PulseAudio (see bridge/Sound.kt).
            putAll(com.mnvstudio.x11.bridge.Sound.env(app))
            // Profile-wide variables (Environment screen), then the caller's (session or app).
            putAll(userEnv(app))
            putAll(env)
        }
        val p = try { pb.start() } catch (e: Exception) {
            AppLog.e("Couldn't start Linux", e)
            onOutput("Couldn't start Linux: ${e.message}"); onExit(-1); return null
        }
        val job = jobName?.let { Job(it, p) }
        if (job != null) ui { jobs += job }
        if (job != null) ui { lastFailure = null }
        Thread {
            val tail = ArrayDeque<String>()
            runCatching { p.inputStream.bufferedReader().forEachLine { line ->
                synchronized(tail) { tail.addLast(line); if (tail.size > 8) tail.removeFirst() }
                ui { onOutput(line) }
            } }
            val code = runCatching { p.waitFor() }.getOrDefault(-1)
            if (job != null) {
                val out = synchronized(tail) { tail.joinToString("\n") }
                AppLog.i("${job.name} exited with code $code" + if (out.isNotEmpty()) ": " + out.replace("\n", " | ") else "")
                ui { lastFailure = "${job.name} closed (exit $code)" + (if (code == 0) " without opening a window" else "") + if (out.isNotEmpty()) "\n$out" else "" }
            }
            ui { if (job != null) jobs -= job; onExit(code) }
        }.start()
        return p
    }

    /** Single-quotes a string for /bin/sh. */
    fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** Runs a short command and collects its output. */
    fun capture(context: Context, command: String, onDone: (Int, List<String>) -> Unit) {
        val out = ArrayList<String>()
        run(context, command, 0, onOutput = { out += it }, onExit = { onDone(it, out) })
    }

    /** Starts a catalog app on the display. */
    fun launch(context: Context, app: LinuxApp, display: Int, log: (String) -> Unit = {}) {
        AppLog.i("Launching ${app.name}")
        run(context, app.command, display, jobName = app.name, onOutput = log) { code -> if (code != 0) log("${app.name} exited ($code)") }
    }

    fun stop(job: Job) { com.mnvstudio.x11.Sessions.userStopped(job); job.process.destroy() }
    fun stopAll() { jobs.toList().forEach { stop(it) } }

    /** Variables set on the Environment screen, stored in the profile as /etc/x11-env (NAME=value lines). */
    fun userEnv(context: Context): Map<String, String> = runCatching {
        File(rootfs(context), "etc/x11-env").readLines().mapNotNull { l ->
            val i = l.indexOf('='); if (i <= 0 || l.startsWith("#")) null else l.substring(0, i).trim() to l.substring(i + 1)
        }.toMap()
    }.getOrDefault(emptyMap())

    fun saveUserEnv(context: Context, vars: Map<String, String>) {
        File(rootfs(context), "etc/x11-env").writeText(vars.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" })
    }
    fun runningCount() = jobs.size

    // ---------------------------------------------------------------- packages

    fun installPackages(context: Context, packages: List<String>, onOutput: (String) -> Unit, onDone: (Boolean) -> Unit) {
        val out = ArrayList<String>()
        run(context, "apk update && apk add ${packages.joinToString(" ")}", 0, onOutput = { out += it; onOutput(it) }) { code ->
            revision++
            LinuxManager.logPackages(context, "add", packages, code == 0)
            if (code != 0) {
                // Say why in plain words: the usual case is a package Alpine doesn't have under that name.
                val missing = out.filter { "no such package" in it }.map { it.substringAfter("ERROR: ").substringBefore(" (").trim() }
                if (missing.isNotEmpty()) onOutput("Not available in Alpine $ALPINE: ${missing.joinToString()}")
                AppLog.e("apk add ${packages.joinToString(" ")} failed ($code): " + out.takeLast(4).joinToString(" | "))
            }
            onDone(code == 0)
        }
    }

    fun removePackages(context: Context, packages: List<String>, onOutput: (String) -> Unit, onDone: (Boolean) -> Unit) {
        run(context, "apk del ${packages.joinToString(" ")}", 0, onOutput = onOutput) { code ->
            revision++; LinuxManager.logPackages(context, "del", packages, code == 0); onDone(code == 0)
        }
    }

    /** Searches Alpine packages; each result is a name and a description. */
    fun search(context: Context, query: String, onDone: (List<Pair<String, String>>) -> Unit) {
        val q = query.filter { it.isLetterOrDigit() || it in "-_.+" }
        if (q.isEmpty()) { onDone(emptyList()); return }
        capture(context, "[ -s /var/cache/apk/APKINDEX.txt ] || apk update >/dev/null 2>&1; apk search -v -d '*$q*' 2>/dev/null | head -60") { _, lines ->
            onDone(lines.mapNotNull { l ->
                val i = l.indexOf(" - ")
                if (i <= 0) null else l.substring(0, i).replace(Regex("-[0-9][^-]*-r[0-9]+$"), "") to l.substring(i + 3)
            })
        }
    }

    /** Packages the user asked for (apk's world file). */
    fun installedPackages(context: Context): List<String> =
        runCatching { File(rootfs(context), "etc/apk/world").readLines().map { it.trim() }.filter { it.isNotEmpty() } }.getOrDefault(emptyList())

    fun isInstalled(context: Context, app: LinuxApp): Boolean =
        File(rootfs(context), "usr/bin/${app.binary}").exists() || File(rootfs(context), "bin/${app.binary}").exists()

    // ---------------------------------------------------------------- files & storage

    /** Copies a file shared from another app into /root/Shared and returns its Linux path. */
    fun importFile(context: Context, uri: Uri): String {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val safe = name.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifEmpty { "file" }
        val dir = File(rootfs(context), "root/Shared").apply { mkdirs() }
        var out = File(dir, safe)
        var i = 1
        val stem = safe.substringBeforeLast('.'); val ext = if ('.' in safe) "." + safe.substringAfterLast('.') else ""
        while (out.exists()) out = File(dir, "$stem-${i++}$ext")
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: error("can't read $name")
        AppLog.i("Imported $name to /root/Shared")
        return "/root/Shared/${out.name}"
    }

    /** Size of the Linux system on disk in bytes; slow, call off the main thread. */
    fun diskUsage(context: Context): Long = rootfs(context).walkTopDown().onFail { _, _ -> }.filter { it.isFile }.sumOf { it.length() }

    /** Deletes the Linux system and everything installed in it. */
    fun remove(context: Context, onDone: () -> Unit) {
        stopAll()
        val root = rootfs(context.applicationContext)
        Thread {
            root.deleteRecursively()
            AppLog.i("Linux removed")
            ui { state = State.MISSING; revision++; onDone() }
        }.start()
    }

    // ---------------------------------------------------------------- diagnostics

    /**
     * Self-test that also repairs: checks proot and the files, re-writes DNS, retries the shell in proot's
     * compatibility mode if it fails, and falls back to plain-HTTP package mirrors when HTTPS doesn't work.
     */
    fun diagnose(context: Context, onUpdate: (List<Check>) -> Unit, onDone: () -> Unit) {
        val app = context.applicationContext
        val prefs = Prefs.of(app)
        val checks = mutableListOf(Check("proot is bundled"), Check("Linux files present"), Check("Shell runs"),
            Check("Internet from Linux"), Check("Package index (apk update)"), Check("X display reachable from Linux"), Check("Test app opens a window"))
        fun set(i: Int, ok: Boolean, detail: String = "") {
            checks[i] = checks[i].copy(ok = ok, detail = detail)
            if (!ok) AppLog.e("Diagnostics: ${checks[i].name}: $detail")
            val copy = checks.toList(); ui { onUpdate(copy) }
        }
        fun finish() = ui(onDone)
        ui { onUpdate(checks.toList()) }

        if (!prootAvailable(app)) { set(0, false, "Rebuild the app with internet access so Gradle can download proot."); finish(); return }
        set(0, true)
        if (!marker(app).exists()) { set(1, false, "Set up Linux first."); finish(); return }
        if (tooNew(app)) {
            set(1, false, "This Linux is Alpine 3.23 or newer, whose package tool can't run on Android. Tap Reset Linux to reinstall Alpine $ALPINE.")
            finish(); return
        }
        prepare(rootfs(app))
        set(1, true, "DNS settings refreshed")

        // Can Linux apps reach the X server, and does a real app survive on it?
        fun display() {
            if (!com.mnvstudio.x11.XHolder.ensureStarted(app)) { set(5, false, com.mnvstudio.x11.XHolder.error ?: "The X server didn't start"); finish(); return }
            val d = com.mnvstudio.x11.XHolder.display
            val xorg = Prefs.of(app).engine == Prefs.ENGINE_XORG
            // X.Org listens on its Unix socket (TCP stays closed unless network access is on); the built-in server on TCP.
            val probe = if (xorg) "i=0; while [ ! -S /tmp/.X11-unix/X$d ] && [ \$i -lt 100 ]; do sleep 0.2; i=\$((i+1)); done; test -S /tmp/.X11-unix/X$d && echo reachable" else "nc -z -w 3 127.0.0.1 ${6000 + d} && echo reachable"
            capture(app, probe) { _, out ->
                if (out.none { it.trim() == "reachable" }) { set(5, false, if (xorg) "X.Org socket /tmp/.X11-unix/X$d missing (is X.Org still starting?)" else "Port ${6000 + d} not reachable: " + out.joinToString(" · ")); finish(); return@capture }
                set(5, true, if (xorg) "X.Org on :$d (Unix socket, cookie auth)" else "DISPLAY=127.0.0.1:$d")
                capture(app, "command -v xterm >/dev/null || { echo 'xterm not installed: install Terminal first'; exit 2; }; " +
                    "timeout 5 xterm -e 'sleep 30' 2>&1; echo \"exit \$?\"") { _, o ->
                    val code = o.lastOrNull { it.startsWith("exit ") }?.removePrefix("exit ")?.trim()?.toIntOrNull() ?: -1
                    // timeout kills a running xterm (busybox reports 15 or 143, coreutils 124): that means the window stayed open.
                    if (code == 15 || code == 124 || code == 143) set(6, true, "xterm ran for 5 s on :$d")
                    else set(6, false, o.takeLast(6).joinToString(" · ").ifEmpty { "exit $code" })
                    finish()
                }
            }
        }

        // Repair ladder for apk: as is, then plain-HTTP mirrors, then proot's compatibility mode.
        fun apkUpdate(stage: Int) {
            capture(app, "apk update 2>&1") { code, out ->
                if (code == 0) { set(4, true, out.lastOrNull { it.contains("OK") || it.contains("distinct") } ?: "OK"); display(); return@capture }
                AppLog.e("apk update failed (stage $stage): " + out.takeLast(4).joinToString(" | "))
                when {
                    stage == 0 -> {
                        val repos = File(rootfs(app), "etc/apk/repositories")
                        runCatching { repos.writeText(repos.readText().replace("https://", "http://")) }
                        apkUpdate(1)
                    }
                    stage == 1 && !prefs.prootNoSeccomp -> { prefs.prootNoSeccomp = true; apkUpdate(2) }
                    else -> { set(4, false, out.takeLast(3).joinToString(" · ").ifEmpty { "exit code $code" }); finish() }
                }
            }
        }

        fun internet() {
            capture(app, "wget -q -T 10 -O /dev/null http://dl-cdn.alpinelinux.org/alpine/ && echo reachable") { _, out ->
                if (out.any { it.trim() == "reachable" }) { set(3, true); apkUpdate(0) }
                else { set(3, false, out.lastOrNull() ?: "no response from dl-cdn.alpinelinux.org"); finish() }
            }
        }

        fun shell(retried: Boolean) {
            capture(app, "echo ok") { code, out ->
                when {
                    code == 0 && out.any { it.trim() == "ok" } -> { set(2, true, if (prefs.prootNoSeccomp) "compatibility mode on" else ""); internet() }
                    !retried && !prefs.prootNoSeccomp -> { prefs.prootNoSeccomp = true; shell(true) }
                    else -> { set(2, false, out.lastOrNull() ?: "exit code $code"); finish() }
                }
            }
        }
        shell(false)
    }
}
