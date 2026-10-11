package com.mnvstudio.x11

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import com.mnvstudio.x11.linux.Catalog
import com.mnvstudio.x11.linux.Linux
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A saved way of working: display and input settings, which Linux profile, which apps and commands to start,
 * environment variables and what to do when things exit. Launching a session applies all of it in one tap.
 */
data class Session(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    /** Settings to apply (a subset of Prefs, as JSON). */
    val settings: JSONObject = JSONObject(),
    val linuxProfile: String = "default",
    val startupApps: List<String> = emptyList(),
    val startupCommands: List<String> = emptyList(),
    /** Environment variables for everything the session starts. */
    val env: Map<String, String> = emptyMap(),
    /** Extra variables for single apps, by catalog id. */
    val appEnv: Map<String, Map<String, String>> = emptyMap(),
    /** Restart apps that crash (exit with an error), up to 3 times a minute. */
    val restartCrashed: Boolean = true,
    /** "nothing" or "stop": what happens when the last app closes. */
    val onLastAppClosed: String = "nothing",
    val lastUsed: Long = 0,
    /** Milliseconds from launch until the first app connected, last time. */
    val lastStartupMs: Long = 0,
) {
    fun summary(): String {
        val res = settings.optString("resolution", "").ifEmpty { "current display" }
        val apps = startupApps.mapNotNull { Catalog.byId(it)?.name }
        return listOfNotNull(res.replace("Native", "fit screen"), if (settings.optBoolean("desktopMode")) "desktop" else null,
            apps.joinToString(", ").ifEmpty { null }).joinToString(" · ")
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1); put("id", id); put("name", name); put("settings", settings); put("linuxProfile", linuxProfile)
        put("startupApps", JSONArray(startupApps)); put("startupCommands", JSONArray(startupCommands))
        put("env", JSONObject(env as Map<*, *>)); put("appEnv", JSONObject().apply { appEnv.forEach { (k, v) -> put(k, JSONObject(v as Map<*, *>)) } })
        put("restartCrashed", restartCrashed); put("onLastAppClosed", onLastAppClosed); put("lastUsed", lastUsed); put("lastStartupMs", lastStartupMs)
    }

    companion object {
        /** Settings that belong to a session (display, windows, input); the rest stay app-wide. */
        val SETTING_KEYS = listOf("resolution", "scale", "customW", "customH", "fitMode", "dpi", "orientation", "maxFps", "smooth",
            "desktopMode", "fitWindows", "touchMode", "pointerSpeed", "cursorScale", "extraKeys", "keyLayout", "clipboard", "lan", "allowList",
            "gesture2", "gesture3", "gesture4", "doubleClickMs", "longPressMs", "dragSlop", "edgeReject", "pointerTrail", "mouseAccel")

        fun fromJson(o: JSONObject): Session {
            fun strings(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }
            fun map(o: JSONObject?) = o?.keys()?.asSequence()?.associateWith { o.getString(it) } ?: emptyMap()
            val appEnv = o.optJSONObject("appEnv")
            return Session(
                id = o.optString("id").ifEmpty { UUID.randomUUID().toString().take(8) },
                name = o.getString("name"),
                settings = o.optJSONObject("settings") ?: JSONObject(),
                linuxProfile = o.optString("linuxProfile", "default"),
                startupApps = strings(o.optJSONArray("startupApps")),
                startupCommands = strings(o.optJSONArray("startupCommands")),
                env = map(o.optJSONObject("env")),
                appEnv = appEnv?.keys()?.asSequence()?.associateWith { map(appEnv.getJSONObject(it)) } ?: emptyMap(),
                restartCrashed = o.optBoolean("restartCrashed", true),
                onLastAppClosed = o.optString("onLastAppClosed", "nothing"),
                lastUsed = o.optLong("lastUsed"), lastStartupMs = o.optLong("lastStartupMs"),
            )
        }
    }
}

object Sessions {
    /** Bumped on every change so screens reload. */
    val revision = mutableIntStateOf(0)
    private val main = Handler(Looper.getMainLooper())
    private var launchStarted = 0L
    private val crashes = HashMap<String, MutableList<Long>>()
    /** Apps the user stopped on purpose, so crash recovery leaves them alone. */
    private val stoppedByUser = HashSet<Process>()

    private fun dir(context: Context) = File(context.filesDir, "sessions").apply { mkdirs() }
    private fun file(context: Context, id: String) = File(dir(context), "$id.json")
    private fun historyDir(context: Context, id: String) = File(dir(context), "$id.history").apply { mkdirs() }

    fun all(context: Context): List<Session> {
        revision.intValue
        return dir(context).listFiles { f -> f.isFile && f.name.endsWith(".json") }?.mapNotNull { f ->
            runCatching { Session.fromJson(JSONObject(f.readText())) }.getOrNull()
        }?.sortedByDescending { it.lastUsed } ?: emptyList()
    }

    fun get(context: Context, id: String) = runCatching { Session.fromJson(JSONObject(file(context, id).readText())) }.getOrNull()

    fun active(context: Context): Session? = Prefs.of(context).activeSession.takeIf { it.isNotEmpty() }?.let { get(context, it) }

    /** Saves a session, keeping the previous version for rollback and diffs (last 20). */
    fun save(context: Context, s: Session, snapshot: Boolean = true) {
        val f = file(context, s.id)
        if (snapshot && f.exists()) {
            val h = historyDir(context, s.id)
            f.copyTo(File(h, "${System.currentTimeMillis()}.json"), true)
            h.listFiles()?.sortedByDescending { it.name }?.drop(20)?.forEach { it.delete() }
        }
        f.writeText(s.toJson().toString(2))
        main.post { revision.intValue++ }
    }

    fun delete(context: Context, id: String) {
        file(context, id).delete(); historyDir(context, id).deleteRecursively()
        val p = Prefs.of(context)
        if (p.activeSession == id) p.activeSession = ""
        revision.intValue++
    }

    /** A new session from the current settings. */
    fun fromCurrent(context: Context, name: String): Session {
        val p = Prefs.of(context)
        return Session(name = name, settings = p.toJson(Session.SETTING_KEYS), linuxProfile = p.linuxProfile, startupApps = p.autostart.toList())
    }

    /** Earlier versions of a session, newest first: time and content. */
    fun history(context: Context, id: String): List<Pair<Long, Session>> =
        historyDir(context, id).listFiles()?.mapNotNull { f ->
            val t = f.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
            runCatching { t to Session.fromJson(JSONObject(f.readText())) }.getOrNull()
        }?.sortedByDescending { it.first } ?: emptyList()

    fun rollback(context: Context, id: String, time: Long) {
        val old = File(historyDir(context, id), "$time.json").takeIf { it.exists() } ?: return
        save(context, Session.fromJson(JSONObject(old.readText())))
        AppLog.i("Session rolled back to ${java.util.Date(time)}")
    }

    /** Line-by-line differences between two sessions' JSON: (key, before, after). */
    fun diff(a: Session, b: Session): List<Triple<String, String, String>> {
        fun flat(o: JSONObject, prefix: String = ""): Map<String, String> = buildMap {
            for (k in o.keys()) {
                if (k == "lastUsed" || k == "lastStartupMs") continue
                when (val v = o.get(k)) { is JSONObject -> putAll(flat(v, "$prefix$k.")); else -> put(prefix + k, v.toString()) }
            }
        }
        val x = flat(a.toJson()); val y = flat(b.toJson())
        return (x.keys + y.keys).sorted().mapNotNull { k -> if (x[k] != y[k]) Triple(k, x[k] ?: "—", y[k] ?: "—") else null }
    }

    /**
     * Checks a session's JSON before importing: name present, settings known and of the right type,
     * apps exist in the catalog, environment variable names valid. Returns problems (empty = fine).
     */
    fun validate(context: Context, json: JSONObject): List<String> {
        val problems = ArrayList<String>()
        if (json.optString("name").isBlank()) problems += "Missing a name"
        val p = Prefs.of(context)
        json.optJSONObject("settings")?.let { st ->
            for (k in st.keys()) {
                val e = p.entry(k)
                if (e == null) { problems += "Unknown setting \"$k\""; continue }
                val v = st.get(k)
                val ok = when (e.default) { is Boolean -> v is Boolean; is Int -> v is Int; is Float -> v is Number; is Set<*> -> v is JSONArray; else -> v is String }
                if (!ok) problems += "Setting \"$k\" has the wrong type"
            }
        }
        json.optJSONArray("startupApps")?.let { a -> for (i in 0 until a.length()) if (Catalog.byId(a.getString(i)) == null) problems += "Unknown app \"${a.getString(i)}\"" }
        json.optJSONObject("env")?.keys()?.forEach { if (!Regex("[A-Za-z_][A-Za-z0-9_]*").matches(it)) problems += "Bad variable name \"$it\"" }
        val cmds = json.optJSONArray("startupCommands")
        if (cmds != null) for (i in 0 until cmds.length()) if (cmds.getString(i).contains("rm -rf /")) problems += "A startup command deletes the whole system"
        return problems
    }

    /** Imports a session from JSON; returns problems if it isn't valid. */
    fun import(context: Context, text: String): List<String> {
        val json = runCatching { JSONObject(text) }.getOrElse { return listOf("Not valid JSON") }
        val problems = validate(context, json)
        if (problems.isNotEmpty()) return problems
        val s = Session.fromJson(json).copy(id = UUID.randomUUID().toString().take(8))
        save(context, s, snapshot = false)
        return emptyList()
    }

    /**
     * Launches a session: applies its settings and Linux profile, restarts the display if it was running with
     * other settings, then starts its apps and commands. Returns false if the display couldn't start.
     */
    fun launch(context: Context, s: Session): Boolean {
        val app = context.applicationContext
        val p = Prefs.of(app)
        val switching = XHolder.server != null && p.activeSession != s.id
        if (switching) { Linux.stopAll(); XHolder.stop() }
        p.fromJson(s.settings)
        p.linuxProfile = s.linuxProfile
        p.activeSession = s.id
        Linux.check(app)
        launchStarted = SystemClock.elapsedRealtime()
        if (!XHolder.ensureStarted(app)) return false
        p.wasRunning = true
        save(app, s.copy(lastUsed = System.currentTimeMillis()), snapshot = false)
        if (Linux.state == Linux.State.READY) {
            for (id in s.startupApps) Catalog.byId(id)?.let { a -> if (Linux.isInstalled(app, a)) startApp(app, s, a.id, a.name, a.command) }
            s.startupCommands.forEachIndexed { i, c -> startApp(app, s, "cmd$i", c.substringBefore(' ').take(24), c) }
        }
        AppLog.i("Session \"${s.name}\" launched")
        return true
    }

    /** Starts one program for a session, restarting it if it crashes and the session asks for that. */
    private fun startApp(context: Context, s: Session, key: String, name: String, command: String) {
        val env = s.env + (s.appEnv[key] ?: emptyMap())
        var proc: Process? = null
        proc = Linux.run(context, command, XHolder.display, jobName = name, env = env) { code ->
            val byUser = proc?.let { stoppedByUser.remove(it) } == true
            if (code != 0 && !byUser && s.restartCrashed && XHolder.server != null && Prefs.of(context).activeSession == s.id) {
                val now = SystemClock.elapsedRealtime()
                val times = crashes.getOrPut(key) { mutableListOf() }.apply { removeAll { now - it > 60_000 }; add(now) }
                if (times.size <= 3) {
                    AppLog.e("$name crashed (exit $code); restarting (${times.size}/3)")
                    main.postDelayed({ startApp(context, s, key, name, command) }, 1500)
                } else AppLog.e("$name keeps crashing; not restarting again")
            }
            if (Linux.jobs.isEmpty() && s.onLastAppClosed == "stop" && XHolder.server != null) {
                AppLog.i("Last app closed: stopping the display (session rule)")
                XService.stop(context)
            }
        }
    }

    /** Marks a job as stopped on purpose, so it isn't restarted. */
    fun userStopped(job: Linux.Job) { stoppedByUser += job.process }

    /** Called when the first app connects after a launch: records startup time. */
    fun firstClientConnected(context: Context) {
        if (launchStarted == 0L) return
        val ms = SystemClock.elapsedRealtime() - launchStarted
        launchStarted = 0
        val s = active(context) ?: return
        save(context, s.copy(lastStartupMs = ms), snapshot = false)
        AppLog.i("Session \"${s.name}\" ready in $ms ms")
    }

    /** Suggested sessions for a first start. */
    fun templates(context: Context): List<Session> {
        val p = Prefs.of(context)
        fun st(vararg kv: Pair<String, Any>) = p.toJson(Session.SETTING_KEYS).apply { kv.forEach { (k, v) -> put(k, v) } }
        return listOf(
            Session(name = "Terminal", settings = st("fitWindows" to true, "desktopMode" to false), startupApps = listOf("xterm")),
            Session(name = "Desktop", settings = st("desktopMode" to true, "resolution" to "1920x1080"), startupApps = listOf("twm", "xterm", "xclock")),
            Session(name = "Touch", settings = st("touchMode" to Prefs.MODE_DIRECT, "dpi" to 144, "cursorScale" to 1.6f)),
        )
    }
}
