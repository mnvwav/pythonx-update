package org.softbuddy.pythonx

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** User profile and settings, persisted in SharedPreferences. Setters write through immediately and recompose readers. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private fun str(key: String, def: String) = mutableStateOf(sp.getString(key, def)!!)
    private fun bool(key: String, def: Boolean) = mutableStateOf(sp.getBoolean(key, def))
    private fun int(key: String, def: Int) = mutableStateOf(sp.getInt(key, def))
    private fun <T : Any> set(state: MutableState<T>, key: String, v: T) {
        state.value = v
        sp.edit().apply {
            when (v) { is Boolean -> putBoolean(key, v); is Int -> putInt(key, v); else -> putString(key, v.toString()) }
        }.apply()
    }

    private val _onboarded = bool("onboarded", false)
    val onboarded get() = _onboarded.value

    // Profile, from onboarding.
    private val _name = str("name", ""); var name get() = _name.value; set(v) = set(_name, "name", v)
    private val _level = str("level", "Beginner"); var level get() = _level.value; set(v) = set(_level, "level", v)
    private val _goal = str("goal", GOAL_LEARN); var goal get() = _goal.value; set(v) = set(_goal, "goal", v)

    // Appearance and editor.
    private val _dark = bool("dark", true); var dark get() = _dark.value; set(v) = set(_dark, "dark", v)
    private val _fontSize = int("fontSize", 14); var fontSize get() = _fontSize.value; set(v) = set(_fontSize, "fontSize", v)
    private val _tabSize = int("tabSize", 4); var tabSize get() = _tabSize.value; set(v) = set(_tabSize, "tabSize", v)
    private val _wordWrap = bool("wordWrap", false); var wordWrap get() = _wordWrap.value; set(v) = set(_wordWrap, "wordWrap", v)
    private val _lineNumbers = bool("lineNumbers", true); var lineNumbers get() = _lineNumbers.value; set(v) = set(_lineNumbers, "lineNumbers", v)
    private val _lineHighlight = bool("lineHighlight", true); var lineHighlight get() = _lineHighlight.value; set(v) = set(_lineHighlight, "lineHighlight", v)
    private val _whitespace = bool("whitespace", false); var showWhitespace get() = _whitespace.value; set(v) = set(_whitespace, "whitespace", v)
    private val _autoClose = bool("autoClose", true); var autoClose get() = _autoClose.value; set(v) = set(_autoClose, "autoClose", v)
    private val _autocomplete = bool("autocomplete", true); var autocomplete get() = _autocomplete.value; set(v) = set(_autocomplete, "autocomplete", v)
    private val _autosave = bool("autosave", true); var autosave get() = _autosave.value; set(v) = set(_autosave, "autosave", v)
    private val _liveErrors = bool("liveErrors", true); var liveErrors get() = _liveErrors.value; set(v) = set(_liveErrors, "liveErrors", v)
    private val _showKeyBar = bool("showKeyBar", true); var showKeyBar get() = _showKeyBar.value; set(v) = set(_showKeyBar, "showKeyBar", v)
    private val _keepAwake = bool("keepAwake", false); var keepAwake get() = _keepAwake.value; set(v) = set(_keepAwake, "keepAwake", v)

    // Running code.
    private val _termFontSize = int("termFontSize", 13); var termFontSize get() = _termFontSize.value; set(v) = set(_termFontSize, "termFontSize", v)
    private val _termBackground = bool("termBackground", true); var termBackground get() = _termBackground.value; set(v) = set(_termBackground, "termBackground", v)
    private val _clearBeforeRun = bool("clearBeforeRun", true); var clearBeforeRun get() = _clearBeforeRun.value; set(v) = set(_clearBeforeRun, "clearBeforeRun", v)
    private val _devMode = bool("devMode", false); var devMode get() = _devMode.value; set(v) = set(_devMode, "devMode", v)

    // Local run telemetry for the home screen.
    private val _lastRun = str("lastRun", "")
    /** "ok|millis|epochMillis" of the most recent run, or empty. */
    val lastRun get() = _lastRun.value
    val runCount get() = sp.getInt("runCount", 0)
    fun recordRun(ok: Boolean, millis: Long) {
        val v = "$ok|$millis|${System.currentTimeMillis()}"
        val log = runLog().toMutableMap()
        val today = java.time.LocalDate.now().toString()
        log[today] = (log[today] ?: 0) + 1
        val keep = log.keys.sortedDescending().take(60).toSet()
        val json = org.json.JSONObject().apply { log.filterKeys { it in keep }.forEach { (k, n) -> put(k, n) } }.toString()
        sp.edit().putString("lastRun", v).putInt("runCount", runCount + 1).putString("runLog", json).apply()
        _lastRun.value = v
        _runLogVersion.intValue++
    }

    private val _runLogVersion = androidx.compose.runtime.mutableIntStateOf(0)
    /** Runs per day ("yyyy-MM-dd" to count), for the home screen's activity chart. */
    fun runLog(): Map<String, Int> {
        _runLogVersion.intValue // read, so composables recompose after a run
        val o = runCatching { org.json.JSONObject(sp.getString("runLog", "{}")!!) }.getOrNull() ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.optInt(it) }
    }

    /** Runs on each of the last [days] days, oldest first. */
    fun runsPerDay(days: Int = 7): List<Pair<java.time.LocalDate, Int>> {
        val log = runLog()
        val today = java.time.LocalDate.now()
        return (days - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (log[it.toString()] ?: 0) }
    }

    /** Consecutive days, ending today or yesterday, with at least one run. */
    fun streak(): Int {
        val log = runLog()
        var day = java.time.LocalDate.now()
        if ((log[day.toString()] ?: 0) == 0) day = day.minusDays(1)
        var n = 0
        while ((log[day.toString()] ?: 0) > 0) { n++; day = day.minusDays(1) }
        return n
    }

    // Learn path: examples that ran successfully.
    private val _learned = mutableStateOf(sp.getStringSet("learned", emptySet())!!.toSet())
    val learned get() = _learned.value
    fun markLearned(title: String) {
        if (title in _learned.value) return
        _learned.value = _learned.value + title
        sp.edit().putStringSet("learned", _learned.value).apply()
    }

    /** Applies defaults for the experience level picked during onboarding. Settings can still override them. */
    fun applyProfile() {
        when (level) {
            "Beginner" -> { liveErrors = true; showWhitespace = false; devMode = false }
            "Intermediate" -> { liveErrors = true; devMode = false }
            else -> { liveErrors = true; devMode = true }
        }
    }

    /** One-line summary of the profile, for onboarding and the home screen. */
    fun profileSummary() = listOfNotNull(
        "Python ${org.softbuddy.pythonx.python.Python.VERSION}",
        if (liveErrors) "live error checking" else null,
        if (devMode) "warnings as -X dev" else "friendly errors",
        "${fontSize}sp editor",
    ).joinToString(" · ")

    /** Restores editor and terminal settings; keeps the profile and onboarding state. */
    fun resetSettings() {
        val keep = setOf("onboarded", "name", "level", "goal", "lastRun", "runCount", "runLog", "learned")
        sp.edit().apply { sp.all.keys.filter { it !in keep }.forEach(::remove) }.apply()
        dark = true; fontSize = 14; tabSize = 4; wordWrap = false; lineNumbers = true; lineHighlight = true; showWhitespace = false
        autoClose = true; autocomplete = true; autosave = true; liveErrors = true; showKeyBar = true; keepAwake = false
        termFontSize = 13; termBackground = true; clearBeforeRun = true; devMode = false
    }

    fun completeOnboarding() = set(_onboarded, "onboarded", true)
    fun resetOnboarding() = set(_onboarded, "onboarded", false)

    companion object {
        const val GOAL_LEARN = "Learn Python"
        const val GOAL_AUTOMATE = "Automate tasks"
        const val GOAL_DATA = "Work with data"
        const val GOAL_WEB = "Web & APIs"
        val GOALS = listOf(GOAL_LEARN, GOAL_AUTOMATE, GOAL_DATA, GOAL_WEB)
    }
}
