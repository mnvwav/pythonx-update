package org.softbuddy.pythonx.project

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Files the editor opens as text. Anything else is listed in the explorer but opened with another app. */
val TEXT_EXTS = setOf(
    "py", "pyw", "pyi", "txt", "md", "rst", "json", "csv", "tsv", "toml", "cfg", "ini", "yaml", "yml",
    "html", "htm", "css", "js", "xml", "sh", "env", "sql", "log", "",
)

/** Folders that are generated or private, never shown or exported. */
private val HIDDEN_DIRS = setOf("__pycache__", ".pythonx", ".git", ".venv", "venv", ".mypy_cache", ".pytest_cache", ".idea")

fun projectsDir(ctx: Context) = File(ctx.filesDir, "Projects").apply { mkdirs() }

fun isTextFile(name: String) = File(name).extension.lowercase() in TEXT_EXTS

/** What Run does for a project: which file, and the arguments passed to it. */
data class RunConfig(val entry: String = "", val args: String = "")

/** A project is a folder; its settings and history live in a hidden .pythonx folder inside it. */
class Project(val dir: File) {
    val name: String get() = dir.name
    private val meta get() = File(dir, ".pythonx").apply { mkdirs() }
    private val runFile get() = File(meta, "run.json")

    /** Every file, as a path relative to the project, hidden and generated folders skipped. */
    fun files(textOnly: Boolean = false): List<String> =
        dir.walkTopDown().onEnter { it == dir || (it.name !in HIDDEN_DIRS && !it.name.startsWith(".")) }
            .filter { it.isFile && !it.name.startsWith(".") && (!textOnly || isTextFile(it.name)) }
            .map { it.relativeTo(dir).path }.sorted().toList()

    fun pythonFiles() = files().filter { it.endsWith(".py") }

    var runConfig: RunConfig
        get() = runCatching {
            val o = JSONObject(runFile.readText())
            RunConfig(o.optString("entry"), o.optString("args"))
        }.getOrDefault(RunConfig())
        set(v) = runFile.writeText(JSONObject().put("entry", v.entry).put("args", v.args).toString())

    /** The file Run starts: the configured entry, else main.py, else a file with a __main__ guard, else the first .py. */
    fun entryFile(): String? {
        val py = pythonFiles()
        runConfig.entry.takeIf { it in py }?.let { return it }
        py.firstOrNull { File(it).name == "main.py" }?.let { return it }
        py.firstOrNull { runCatching { "__name__" in File(dir, it).readText() }.getOrDefault(false) }?.let { return it }
        return py.firstOrNull()
    }

    /** The file to open first: the entry file, else any text file. */
    fun mainFile() = entryFile() ?: files(textOnly = true).firstOrNull()

    val requirements get() = File(dir, "requirements.txt").takeIf { it.isFile }

    /** Set for projects opened from the Learn path; a successful run marks the lesson as done. */
    var example: String?
        get() = File(meta, "example").takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }
        set(v) { if (v == null) File(meta, "example").delete() else File(meta, "example").writeText(v) }

    fun exportZip(out: OutputStream) = ZipOutputStream(out).use { zip ->
        files().forEach { path ->
            zip.putNextEntry(ZipEntry("$name/$path"))
            File(dir, path).inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

/** A starter project: files keyed by relative path. */
data class Template(
    val name: String, val description: String, val files: Map<String, String>,
    val tags: List<String> = emptyList(), val entry: String = "main.py",
)

/** Creates [name] (made unique) from [template] and returns it. */
fun createProject(ctx: Context, name: String, template: Template): Project {
    val dir = uniqueDir(projectsDir(ctx), safeName(name))
    dir.mkdirs()
    template.files.forEach { (path, text) -> File(dir, path).apply { parentFile?.mkdirs(); writeText(text) } }
    val project = Project(dir)
    project.runConfig = RunConfig(entry = template.entry)
    return project
}

fun safeName(name: String) = name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "Untitled" }

fun uniqueDir(parent: File, name: String): File {
    var dir = File(parent, name)
    var n = 2
    while (dir.exists()) dir = File(parent, "$name ${n++}")
    return dir
}

/** Accepts zips made by Export (one top-level folder) or loose files; returns the new project folder. */
fun importProjectZip(ctx: Context, input: InputStream): File? {
    val staging = File(ctx.cacheDir, "import").apply { deleteRecursively(); mkdirs() }
    val root = staging.canonicalPath + File.separator
    ZipInputStream(input.buffered()).use { zip ->
        generateSequence { zip.nextEntry }.forEach { e ->
            val f = File(staging, e.name)
            if (!f.canonicalPath.startsWith(root)) return@forEach
            if (e.isDirectory) f.mkdirs() else { f.parentFile!!.mkdirs(); f.outputStream().use { zip.copyTo(it) } }
        }
    }
    val top = staging.listFiles().orEmpty()
    val src = if (top.size == 1 && top[0].isDirectory) top[0] else staging
    if (src.walkTopDown().none { it.isFile }) return null
    val dest = uniqueDir(projectsDir(ctx), if (src == staging) "Imported" else src.name)
    src.copyRecursively(dest)
    staging.deleteRecursively()
    return dest
}

/**
 * PythonX 2 kept loose .py files directly in Projects/. Move them into a "My scripts" project so they
 * show up (and keep working) in the project-based home screen.
 */
fun migrateLooseScripts(ctx: Context) {
    val root = projectsDir(ctx)
    val loose = root.listFiles().orEmpty().filter { it.isFile }
    if (loose.isEmpty()) return
    val target = File(root, "My scripts").apply { mkdirs() }
    loose.forEach { f ->
        var dest = File(target, f.name); var n = 2
        while (dest.exists()) dest = File(target, "${f.nameWithoutExtension} $n${if (f.extension.isEmpty()) "" else "." + f.extension}").also { n++ }
        f.renameTo(dest)
    }
}

/** Automatic local snapshots of a project's text files, kept in .pythonx/history/<epoch-millis>/. */
object LocalHistory {
    private const val KEEP = 60
    private const val MIN_GAP_MS = 2 * 60_000L
    private const val MAX_FILE = 512 * 1024L

    private fun root(p: Project) = File(p.dir, ".pythonx/history")

    fun snapshots(p: Project): List<Long> = root(p).list().orEmpty().mapNotNull { it.toLongOrNull() }.sortedDescending()

    fun current(p: Project): Map<String, String> = p.files(textOnly = true)
        .filter { File(p.dir, it).length() <= MAX_FILE }.associateWith { File(p.dir, it).readText() }

    /** Saves a snapshot if the files differ from the newest one. [force] skips the time gap (used before runs and restores). */
    @Synchronized
    fun snapshot(p: Project, label: String, force: Boolean = false) {
        val latest = snapshots(p).firstOrNull()
        if (!force && latest != null && System.currentTimeMillis() - latest < MIN_GAP_MS) return
        val now = current(p)
        if (now.isEmpty() || (latest != null && now == read(p, latest))) return
        val dir = File(root(p), System.currentTimeMillis().toString()).apply { mkdirs() }
        now.forEach { (path, text) -> File(dir, path).apply { parentFile!!.mkdirs(); writeText(text) } }
        File(dir, ".label").writeText(label)
        snapshots(p).drop(KEEP).forEach { File(root(p), it.toString()).deleteRecursively() }
    }

    fun read(p: Project, at: Long): Map<String, String> {
        val dir = File(root(p), at.toString())
        return dir.walkTopDown().filter { it.isFile && it.name != ".label" }.associate { it.relativeTo(dir).path to it.readText() }
    }

    fun label(p: Project, at: Long) = File(root(p), "$at/.label").takeIf { it.isFile }?.readText().orEmpty()

    /** Restores the text files of a snapshot; the current state is snapshotted first so this can be undone. */
    fun restore(p: Project, at: Long) {
        snapshot(p, "Before restore", force = true)
        val old = read(p, at)
        current(p).keys.filter { it !in old }.forEach { File(p.dir, it).delete() }
        old.forEach { (path, text) -> File(p.dir, path).apply { parentFile!!.mkdirs(); writeText(text) } }
    }
}

/** Minimal line diff (LCS), good enough for source files; produces "  ", "- " and "+ " prefixed lines. */
fun lineDiff(old: String, new: String): List<String> {
    val a = old.lines(); val b = new.lines()
    if (a.size.toLong() * b.size > 4_000_000) return listOf("  (file too large to compare)")
    val dp = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) for (j in b.indices.reversed())
        dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
    val out = ArrayList<String>(); var i = 0; var j = 0
    while (i < a.size && j < b.size) when {
        a[i] == b[j] -> { out += "  " + a[i]; i++; j++ }
        dp[i + 1][j] >= dp[i][j + 1] -> { out += "- " + a[i]; i++ }
        else -> { out += "+ " + b[j]; j++ }
    }
    while (i < a.size) out += "- " + a[i++]
    while (j < b.size) out += "+ " + b[j++]
    return out
}
