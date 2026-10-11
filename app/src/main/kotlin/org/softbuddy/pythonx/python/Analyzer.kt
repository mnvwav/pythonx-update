package org.softbuddy.pythonx.python

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File

/** A problem reported for a line/column range (1-based, end exclusive). */
data class Problem(val line: Int, val col: Int, val endLine: Int, val endCol: Int, val message: String, val error: Boolean)

/** A class, function or method in the open file. */
data class Symbol(val name: String, val kind: String, val line: Int, val depth: Int)

data class Analysis(val problems: List<Problem>, val symbols: List<Symbol>)

/**
 * Talks to one long-running `python3 pythonx_analyze.py` process, so checking a file after an edit costs a few
 * milliseconds instead of a Python start-up. Restarts the server if it dies.
 */
class Analyzer(private val ctx: Context) {
    private val python = Python(ctx)
    private var process: Process? = null
    private var input: BufferedWriter? = null
    private var output: BufferedReader? = null

    private fun script(): File {
        val file = File(ctx.cacheDir, "pythonx_analyze.py")
        // Re-copy after app updates so the server matches this version.
        val stamp = File(ctx.cacheDir, "pythonx_analyze.stamp")
        val version = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime.toString()
        if (!file.isFile || stamp.takeIf { it.isFile }?.readText() != version) {
            ctx.assets.open("python/pythonx_analyze.py").use { i -> file.outputStream().use { i.copyTo(it) } }
            stamp.writeText(version)
        }
        return file
    }

    private fun start() {
        val p = python.process(listOf("-u", script().path), ctx.cacheDir)
        process = p
        input = p.outputStream.bufferedWriter()
        output = p.inputStream.bufferedReader()
    }

    @Synchronized
    fun analyze(path: String, source: String): Analysis {
        if (!python.installed) return Analysis(emptyList(), emptyList())
        repeat(2) { attempt ->
            try {
                if (process?.isAlive != true) start()
                val request = JSONObject().put("path", File(path).name).put("source", source).toString()
                input!!.apply { write(request); write("\n"); flush() }
                val reply = output!!.readLine() ?: throw java.io.IOException("analyzer exited")
                return parse(JSONObject(reply))
            } catch (e: Exception) {
                stop()
                if (attempt == 1) return Analysis(emptyList(), emptyList())
            }
        }
        return Analysis(emptyList(), emptyList())
    }

    @Synchronized
    fun stop() {
        runCatching { input?.close() }
        process?.destroy()
        process = null; input = null; output = null
    }

    private fun parse(json: JSONObject): Analysis {
        val problems = json.optJSONArray("problems")?.let { a ->
            (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                Problem(p.getInt("line"), p.getInt("col"), p.getInt("endLine"), p.getInt("endCol"), p.getString("msg"), p.getBoolean("error"))
            }
        }.orEmpty()
        val symbols = json.optJSONArray("symbols")?.let { a ->
            (0 until a.length()).map { i ->
                val s = a.getJSONObject(i)
                Symbol(s.getString("name"), s.getString("kind"), s.getInt("line"), s.getInt("depth"))
            }
        }.orEmpty()
        return Analysis(problems, symbols)
    }
}
