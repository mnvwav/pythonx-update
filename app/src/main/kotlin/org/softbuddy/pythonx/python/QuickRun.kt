package org.softbuddy.pythonx.python

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class QuickResult(val code: String, val out: String, val value: String?, val error: String?, val millis: Long)

/**
 * A long-lived Python session for one-off snippets on the home screen. State (variables, imports) carries over
 * between snippets until [reset]. A snippet that runs too long is stopped and the session restarts.
 */
class QuickRun(private val ctx: Context) {
    private val python = Python(ctx)
    private var process: Process? = null
    private var input: BufferedWriter? = null
    private var output: BufferedReader? = null
    private val reader = Executors.newSingleThreadExecutor()

    private fun script(): File {
        val file = File(ctx.cacheDir, "pythonx_eval.py")
        ctx.assets.open("python/pythonx_eval.py").use { i -> file.outputStream().use { i.copyTo(it) } }
        return file
    }

    private fun start() {
        val p = python.process(listOf("-u", script().path), File(ctx.filesDir, "Projects").apply { mkdirs() })
        process = p
        input = p.outputStream.bufferedWriter()
        output = p.inputStream.bufferedReader()
    }

    @Synchronized
    fun run(code: String, timeoutSec: Long = 8): QuickResult {
        val started = System.currentTimeMillis()
        fun done(out: String, value: String?, error: String?) = QuickResult(code, out, value, error, System.currentTimeMillis() - started)
        if (!python.installed) return done("", null, "Python is still being set up…")
        return try {
            if (process?.isAlive != true) start()
            input!!.apply { write(JSONObject().put("code", code).toString()); write("\n"); flush() }
            val line = reader.submit<String?> { output!!.readLine() }.get(timeoutSec, TimeUnit.SECONDS)
                ?: return done("", null, "The session stopped. It restarts on the next run.").also { stop() }
            val json = JSONObject(line)
            done(json.optString("out"), json.optString("value").takeIf { !json.isNull("value") },
                json.optString("error").takeIf { !json.isNull("error") })
        } catch (e: TimeoutException) {
            stop()
            done("", null, "Stopped after ${timeoutSec}s. Long-running code belongs in a project, where Run has no time limit. Session restarted.")
        } catch (e: Exception) {
            stop()
            done("", null, e.message ?: e.toString())
        }
    }

    /** Forgets all variables by starting a fresh session. */
    @Synchronized
    fun reset() = stop()

    @Synchronized
    fun stop() {
        runCatching { input?.close() }
        process?.destroyForcibly()
        process = null; input = null; output = null
    }

    companion object {
        @Volatile private var shared: QuickRun? = null
        /** One session per app process, so Quick Run history survives leaving the home screen. */
        fun get(ctx: Context) = shared ?: synchronized(this) { shared ?: QuickRun(ctx.applicationContext).also { shared = it } }
    }
}
