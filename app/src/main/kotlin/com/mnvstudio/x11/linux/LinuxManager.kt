package com.mnvstudio.x11.linux

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnvstudio.x11.AppLog
import com.mnvstudio.x11.Prefs
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Managing Linux environments: profiles (separate root filesystems), snapshots, backup and restore,
 * maintenance (integrity, cleanup, package cache, sources), users and startup scripts.
 * Long operations run on a background thread and report through [busy] / [message].
 */
object LinuxManager {
    var busy by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null)

    private val main = Handler(Looper.getMainLooper())
    private fun ui(b: () -> Unit) { main.post(b) }

    /** Runs [work] in the background with a status line; errors become a readable message. */
    private fun task(label: String, done: () -> Unit = {}, work: () -> String) {
        if (busy != null) { message = "Please wait for \"$busy\" to finish"; return }
        busy = label
        Thread {
            val result = runCatching(work).getOrElse { AppLog.e("$label failed", it); "$label failed: ${it.message}" }
            ui { busy = null; message = result; Linux.bump(); done() }
        }.start()
    }

    private val NAME = Regex("[a-z0-9][a-z0-9-]{0,31}")
    fun validName(n: String) = NAME.matches(n)

    // ---------------------------------------------------------------- profiles

    /** Switches the active profile; running Linux apps are stopped first. */
    fun switchTo(context: Context, name: String) {
        Linux.stopAll()
        Prefs.of(context).linuxProfile = name
        Linux.check(context)
        message = "Using profile \"$name\""
    }

    /** Creates an empty profile and downloads Alpine into it. */
    fun create(context: Context, name: String) {
        if (!validName(name)) { message = "Use lowercase letters, digits and dashes"; return }
        if (File(Linux.profilesDir(context), name).exists()) { message = "\"$name\" already exists"; return }
        switchTo(context, name)
        Linux.install(context)
    }

    fun clone(context: Context, from: String, to: String) {
        if (!validName(to)) { message = "Use lowercase letters, digits and dashes"; return }
        val src = File(Linux.profilesDir(context), from); val dst = File(Linux.profilesDir(context), to)
        if (dst.exists()) { message = "\"$to\" already exists"; return }
        task("Cloning $from") { Archive.copyTree(src, dst); "Cloned \"$from\" to \"$to\"" }
    }

    fun deleteProfile(context: Context, name: String) {
        if (name == Prefs.of(context).linuxProfile) { message = "Switch to another profile before deleting this one"; return }
        task("Deleting $name") { Archive.deleteTree(File(Linux.profilesDir(context), name)); snapshotsDir(context, name).deleteRecursively(); "Deleted \"$name\"" }
    }

    fun profileSize(context: Context, name: String): Long = com.mnvstudio.x11.Metrics.sizeOf(File(Linux.profilesDir(context), name))

    // ---------------------------------------------------------------- snapshots

    fun snapshotsDir(context: Context, profile: String = Linux.activeProfileName(context)) = File(context.filesDir, "snapshots/$profile").apply { mkdirs() }

    /** Snapshots of the active profile: time, label and size. */
    fun snapshots(context: Context): List<Triple<Long, String, Long>> = snapshotsDir(context).listFiles { f -> f.name.endsWith(".tgz") }?.mapNotNull { f ->
        val t = f.name.substringBefore('_').toLongOrNull() ?: return@mapNotNull null
        Triple(t, f.name.substringAfter('_').removeSuffix(".tgz").replace('-', ' '), f.length())
    }?.sortedByDescending { it.first } ?: emptyList()

    fun snapshot(context: Context, label: String) {
        val clean = label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "snapshot" }.take(40)
        val f = File(snapshotsDir(context), "${System.currentTimeMillis()}_$clean.tgz")
        val root = Linux.rootfs(context)
        task("Taking snapshot") { f.outputStream().use { Archive.writeTarGz(root, it) }; "Snapshot saved (${f.length() / (1 shl 20)} MB)" }
    }

    /** Replaces the active profile with a snapshot. */
    fun restoreSnapshot(context: Context, time: Long) {
        val f = snapshotsDir(context).listFiles()?.firstOrNull { it.name.startsWith("${time}_") } ?: return
        Linux.stopAll()
        val root = Linux.rootfs(context)
        task("Restoring snapshot") {
            Archive.deleteTree(root); root.mkdirs()
            GZIPInputStream(f.inputStream().buffered()).use { Tar.extract(it, root) }
            "Snapshot restored"
        }
    }

    fun deleteSnapshot(context: Context, time: Long) { snapshotsDir(context).listFiles()?.firstOrNull { it.name.startsWith("${time}_") }?.delete(); Linux.bump() }

    // ---------------------------------------------------------------- backup & restore

    /** Exports the active profile to a document the user picked, encrypted if [password] isn't empty. */
    fun export(context: Context, uri: Uri, password: String) {
        val root = Linux.rootfs(context)
        task("Exporting backup") {
            val out = context.contentResolver.openOutputStream(uri) ?: error("can't write there")
            val stream = if (password.isNotEmpty()) Archive.encrypting(out, password.toCharArray()) else out
            stream.use { Archive.writeTarGz(root, it) }
            "Backup exported" + if (password.isNotEmpty()) " (encrypted)" else ""
        }
    }

    /** Imports a backup into a new profile [name]; asks for the password through [password] if it's encrypted. */
    fun import(context: Context, uri: Uri, name: String, password: String) {
        if (!validName(name)) { message = "Use lowercase letters, digits and dashes"; return }
        val dst = File(Linux.profilesDir(context), name)
        if (dst.exists()) { message = "\"$name\" already exists"; return }
        task("Importing backup") {
            val head = context.contentResolver.openInputStream(uri)?.use { i -> ByteArray(4).also { i.read(it) } } ?: error("can't read the file")
            val tmp = File(context.cacheDir, "import.tgz")
            try {
                if (Archive.isEncrypted(head)) {
                    if (password.isEmpty()) error("this backup is encrypted; enter its password")
                    Archive.decryptTo(context.contentResolver.openInputStream(uri)!!, password.toCharArray(), tmp)
                } else context.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                dst.mkdirs()
                GZIPInputStream(tmp.inputStream().buffered()).use { Tar.extract(it, dst) }
                if (!File(dst, "bin/sh").exists() && !java.nio.file.Files.isSymbolicLink(File(dst, "bin/sh").toPath())) {
                    Archive.deleteTree(dst); error("that file isn't a Linux backup")
                }
                File(dst, ".x11-ready").writeText("imported")
                "Imported as profile \"$name\""
            } finally { tmp.delete() }
        }
    }

    // ---------------------------------------------------------------- maintenance

    /** Space used by things that can be cleaned, in bytes. */
    fun cleanable(context: Context): List<Pair<String, Long>> {
        val r = Linux.rootfs(context)
        return listOf(
            "Package downloads (/var/cache/apk)" to com.mnvstudio.x11.Metrics.sizeOf(File(r, "var/cache/apk")),
            "Temporary files (/tmp)" to com.mnvstudio.x11.Metrics.sizeOf(File(r, "tmp")),
            "App caches (/root/.cache)" to com.mnvstudio.x11.Metrics.sizeOf(File(r, "root/.cache")),
            "Snapshots of this profile" to com.mnvstudio.x11.Metrics.sizeOf(snapshotsDir(context)),
            "X11's own cache" to com.mnvstudio.x11.Metrics.sizeOf(context.cacheDir),
        )
    }

    fun clean(context: Context, what: String) {
        val r = Linux.rootfs(context)
        task("Cleaning") {
            when {
                what.startsWith("Package") -> File(r, "var/cache/apk").listFiles()?.forEach { if (it.name != "APKINDEX" && !offlineCache(context)) Archive.deleteTree(it) }
                what.startsWith("Temporary") -> File(r, "tmp").listFiles()?.forEach { if (it.name != ".X11-unix") Archive.deleteTree(it) }
                what.startsWith("App caches") -> Archive.deleteTree(File(r, "root/.cache"))
                what.startsWith("Snapshots") -> snapshotsDir(context).listFiles()?.forEach { it.delete() }
                else -> context.cacheDir.listFiles()?.forEach { if (it.name != "proot") Archive.deleteTree(it) }
            }
            "Cleaned: $what"
        }
    }

    /** apk keeps downloaded packages (so they install again offline) when /etc/apk/cache points to a folder. */
    fun offlineCache(context: Context) = java.nio.file.Files.isSymbolicLink(File(Linux.rootfs(context), "etc/apk/cache").toPath()) ||
        File(Linux.rootfs(context), "etc/apk/cache").isDirectory

    fun setOfflineCache(context: Context, on: Boolean) {
        val r = Linux.rootfs(context)
        val link = File(r, "etc/apk/cache")
        runCatching {
            java.nio.file.Files.deleteIfExists(link.toPath())
            if (on) { File(r, "var/cache/apk").mkdirs(); java.nio.file.Files.createSymbolicLink(link.toPath(), java.nio.file.Paths.get("/var/cache/apk")) }
        }.onFailure { message = "Couldn't change the package cache: ${it.message}" }
        Linux.bump()
    }

    /** Package repositories, one URL or local path per line (lines starting with # are off). */
    fun repositories(context: Context): List<String> =
        runCatching { File(Linux.rootfs(context), "etc/apk/repositories").readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())

    fun saveRepositories(context: Context, lines: List<String>) {
        File(Linux.rootfs(context), "etc/apk/repositories").writeText(lines.filter { it.isNotBlank() }.joinToString("\n", postfix = "\n"))
        AppLog.i("Package sources changed: ${lines.size} entries")
        Linux.bump()
    }

    /** Signing keys apk trusts (in /etc/apk/keys). */
    fun trustedKeys(context: Context): List<String> = File(Linux.rootfs(context), "etc/apk/keys").list()?.sorted() ?: emptyList()

    // ---------------------------------------------------------------- users & startup

    /** Normal users in the profile (uid 1000 and up). */
    fun users(context: Context): List<String> = runCatching {
        File(Linux.rootfs(context), "etc/passwd").readLines().mapNotNull { l ->
            val f = l.split(':'); if (f.size > 2 && (f[2].toIntOrNull() ?: 0) >= 1000 && f[0] != "nobody") f[0] else null
        }
    }.getOrDefault(emptyList())

    fun startupScript(context: Context) = File(Linux.rootfs(context), "etc/x11-startup.sh")

    // ---------------------------------------------------------------- package history

    fun logPackages(context: Context, action: String, packages: List<String>, ok: Boolean) {
        runCatching {
            File(Linux.rootfs(context), "var/log").mkdirs()
            File(Linux.rootfs(context), "var/log/x11-packages.log")
                .appendText("${System.currentTimeMillis()} $action ${if (ok) "ok" else "failed"} ${packages.joinToString(" ")}\n")
        }
    }

    /** Package history, newest first: time, action, ok, packages. */
    fun packageHistory(context: Context): List<List<String>> = runCatching {
        File(Linux.rootfs(context), "var/log/x11-packages.log").readLines().mapNotNull { l ->
            val p = l.split(' ', limit = 4); if (p.size < 4) null else p
        }.reversed()
    }.getOrDefault(emptyList())
}
