package com.mnvstudio.x11

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * All settings, persisted in SharedPreferences and shared app-wide ([of]). Every setting is observable Compose state,
 * so screens update when it changes. Settings can be reset, exported to JSON and imported again.
 */
class Prefs private constructor(context: Context) {
    private val sp: SharedPreferences = context.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private val res = context.applicationContext.resources
    /** Read live: the size changes when the phone rotates. */
    private val metrics get() = res.displayMetrics
    private val entries = LinkedHashMap<String, Entry<*>>()

    /** One setting: its key, default and live state. */
    inner class Entry<T : Any>(val key: String, val default: T) : ReadWriteProperty<Any?, T> {
        val state = mutableStateOf(load())

        @Suppress("UNCHECKED_CAST")
        private fun load(): T = runCatching {
            when (default) {
                is Boolean -> sp.getBoolean(key, default); is Int -> sp.getInt(key, default); is Float -> sp.getFloat(key, default)
                is Set<*> -> sp.getStringSet(key, default as Set<String>)?.toSet() ?: default
                else -> sp.getString(key, default as String) ?: default
            } as T
        }.getOrDefault(default)

        @Suppress("UNCHECKED_CAST")
        fun write(v: T) {
            state.value = v
            sp.edit().apply {
                when (v) {
                    is Boolean -> putBoolean(key, v); is Int -> putInt(key, v); is Float -> putFloat(key, v)
                    is Set<*> -> putStringSet(key, v as Set<String>)
                    else -> putString(key, v.toString())
                }
            }.apply()
        }

        fun reset() = write(default)
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = write(value)
    }

    private fun <T : Any> pref(key: String, default: T) = Entry(key, default).also { entries[key] = it }

    var onboarded by pref("onboarded", false)

    // Display.
    /** "Native", "Custom" or "WIDTHxHEIGHT". */
    var resolution by pref("resolution", RES_NATIVE)
    /** Fraction of the device's pixels used at native resolution (smaller = bigger UI, faster). */
    var scale by pref("scale", 0.5f)
    var customW by pref("customW", 1280)
    var customH by pref("customH", 720)
    var display by pref("display", 0)
    /** "fit", "stretch" or "pixel" (1:1). */
    var fitMode by pref("fitMode", "fit")
    /** Text scaling reported to apps (Xft.dpi); 0 = automatic. */
    var dpiSetting by pref("dpi", 0)
    var maxFps by pref("maxFps", 60)
    var smoothScaling by pref("smooth", true)
    /** "auto", "landscape" or "portrait" while the display is open. */
    var orientation by pref("orientation", "landscape")
    /** Float the display in picture-in-picture when leaving the app with Linux apps open. */
    var autoPip by pref("autoPip", true)
    var drawInCutout by pref("cutout", false)
    var keepScreenOn by pref("keepScreenOn", true)
    var perfOverlay by pref("perfOverlay", false)
    /** Restart the display at the new size when the phone rotates (apps reconnect). */
    var followRotation by pref("followRotation", false)

    // Windows.
    /** Built-in window manager: title bars, movable and resizable windows, virtual desktops. */
    var desktopMode by pref("desktopMode", false)
    var desktops by pref("desktops", 4)
    var snapping by pref("snapping", true)
    var titleBarHeight by pref("titleBarHeight", 24)
    /** Remembered window geometry by app class: "Class=x,y,w,h;…". */
    var placements by pref("placements", "")
    /** Per-app window rules by app class: "XTerm=fullscreen;…". */
    var appRules by pref("appRules", "")
    var fitWindows by pref("fitWindows", true)
    /** Catalog app ids started with the display. */
    var autostart by pref("autostart", emptySet<String>())

    // Input.
    var touchMode by pref("touchMode", MODE_TOUCHPAD)
    var pointerSpeed by pref("pointerSpeed", 1.2f)
    var cursorScale by pref("cursorScale", 1f)
    var extraKeys by pref("extraKeys", true)
    var keyLayout by pref("keyLayout", DEFAULT_KEYS)
    var haptics by pref("haptics", true)
    // Gestures (Action names, see display/InputConfig).
    var gestureTap2 by pref("gesture2", "RIGHT")
    var gestureTap3 by pref("gesture3", "MIDDLE")
    var gestureTap4 by pref("gesture4", "KEYBOARD")
    var gestureSwipeUp by pref("swipe3Up", "SWITCHER")
    var gestureSwipeDown by pref("swipe3Down", "KEYBOARD")
    var longPressMs by pref("longPressMs", 400)
    var dragSlop by pref("dragSlop", 8)
    var doubleClickMs by pref("doubleClickMs", 80)
    var mouseAccel by pref("mouseAccel", 0.6f)
    var edgeReject by pref("edgeReject", 0)
    var palmReject by pref("palmReject", true)
    var stylusButton by pref("stylusButton", "RIGHT")
    var showPointer by pref("showPointer", true)
    var pointerTrail by pref("pointerTrail", false)
    /** Game controller overrides: "A=ENTER,B=ESC,…" (X key names from the extra keys list, or X keycodes). */
    var gamepadMap by pref("gamepadMap", "")
    var stickMouse by pref("stickMouse", true)
    /** Per-app input profiles (see display/AppProfiles). */
    var appInput by pref("appInput", "")
    /** Android-side shortcuts on a hardware keyboard (Ctrl+Alt+…): on/off. */
    var appShortcuts by pref("appShortcuts", true)

    // Integration.
    var clipboardSync by pref("clipboard", true)
    var syncPrimary by pref("syncPrimary", false)

    // Network.
    var lanAccess by pref("lan", false)
    var allowList by pref("allowList", "")

    // Power.
    var wakeLock by pref("wakeLock", false)
    var idleStopMinutes by pref("idleStop", 0)

    // Look.
    /** "dark", "light", "amoled" or "system". */
    var themeMode by pref("themeMode", "dark")
    var accent by pref("accent", "Red")
    var brandGold by pref("brandRed", false)
    var dynamicColor by pref("dynamicColor", false)
    var highContrast by pref("highContrast", false)
    /** Multiplies text size in the app's own screens. */
    var textScale by pref("textScale", 1f)
    /** "auto" (rail on wide screens), "bottom" or "rail". */
    var navLayout by pref("navLayout", "auto")
    var showSplash by pref("showSplash", true)
    /** Show settings meant for power users. */
    var advanced by pref("advanced", false)

    // Dashboard: widget order and the ones turned off.
    var dashboard by pref("dashboard", DASHBOARD)
    var dashboardHidden by pref("dashboardHidden", emptySet<String>())

    // Library.
    var favorites by pref("favorites", emptySet<String>())
    /** User-defined tools, one per line: name|command. */
    var customTools by pref("customTools", "")
    /** Browser: bookmarks and reading list as title|url lines; home page; private mode; light mode (no images/JS). */
    var bookmarks by pref("bookmarks", "")
    var readingList by pref("readingList", "")
    var browserHome by pref("browserHome", "https://duckduckgo.com")
    var browserPrivate by pref("browserPrivate", false)
    var browserLight by pref("browserLight", false)
    var browserDesktop by pref("browserDesktop", false)
    /** Downloads: connections per file and the folder (inside Linux) they go to. */
    var downloadConnections by pref("downloadConnections", 4)
    var downloadDir by pref("downloadDir", "/root/Downloads")
    /** Recently opened app ids, newest first, comma separated. */
    var recents by pref("recents", "")

    /** The session last launched, and whether the display was running when the app was last closed. */
    var activeSession by pref("activeSession", "")
    var wasRunning by pref("wasRunning", false)
    /** Bring the last session back when the app starts again after being closed. */
    var restoreSession by pref("restoreSession", true)

    /** Linux user apps run as ("root" or a user from the Users screen). */
    var linuxUser by pref("linuxUser", "root")

    /** The active Linux profile (a folder under files/profiles). */
    var linuxProfile by pref("linuxProfile", "default")

    // Linux compatibility, set by Diagnostics when it finds what works on this phone.
    var prootNoSeccomp by pref("noSeccomp", false)

    fun addRecent(id: String) { recents = (listOf(id) + recents.split(',').filter { it.isNotEmpty() && it != id }).take(8).joinToString(",") }
    fun recentIds(): List<String> = recents.split(',').filter { it.isNotEmpty() }

    /** The setting with this key, for generic screens (search, undo, reset). */
    fun entry(key: String): Entry<*>? = entries[key]

    /** Puts the given settings back to their defaults (all of them if [keys] is null), except first-run state. */
    fun reset(keys: Collection<String>? = null) {
        for ((k, e) in entries) if (k != "onboarded" && (keys == null || k in keys)) e.reset()
    }

    /** Settings as JSON, for export, backups and session profiles. */
    fun toJson(keys: Collection<String>? = null): JSONObject = JSONObject().apply {
        for ((k, e) in entries) {
            if (k == "onboarded" || (keys != null && k !in keys)) continue
            when (val v = e.state.value) { is Set<*> -> put(k, JSONArray(v.toList())); else -> put(k, v) }
        }
    }

    /** Applies settings from JSON, ignoring unknown keys and wrong types. Returns how many were applied. */
    @Suppress("UNCHECKED_CAST")
    fun fromJson(json: JSONObject): Int {
        var n = 0
        for ((k, e) in entries) {
            if (k == "onboarded" || !json.has(k)) continue
            val ok = runCatching {
                when (e.default) {
                    is Boolean -> (e as Entry<Boolean>).write(json.getBoolean(k))
                    is Int -> (e as Entry<Int>).write(json.getInt(k))
                    is Float -> (e as Entry<Float>).write(json.getDouble(k).toFloat())
                    is Set<*> -> { val a = json.getJSONArray(k); (e as Entry<Set<String>>).write((0 until a.length()).map { a.getString(it) }.toSet()) }
                    else -> (e as Entry<String>).write(json.getString(k))
                }
            }.isSuccess
            if (ok) n++
        }
        return n
    }

    /** Screen size the server is created with. */
    fun screenSize(): Pair<Int, Int> {
        if (resolution == RES_CUSTOM) return customW.coerceIn(320, 7680) / 2 * 2 to customH.coerceIn(240, 4320) / 2 * 2
        if (resolution != RES_NATIVE) {
            val p = resolution.split('x')
            p.getOrNull(0)?.toIntOrNull()?.let { w -> p.getOrNull(1)?.toIntOrNull()?.let { h -> return w to h } }
        }
        val long = max(metrics.widthPixels, metrics.heightPixels)
        val short = min(metrics.widthPixels, metrics.heightPixels)
        val portrait = orientation == "portrait" || (orientation == "auto" && metrics.heightPixels > metrics.widthPixels)
        val (w, h) = if (portrait) short to long else long to short
        fun even(v: Float) = (v.roundToInt() / 2 * 2).coerceAtLeast(320)
        return even(w * scale) to even(h * scale)
    }

    /** Dots per inch reported to apps. */
    fun dpi(): Int = when {
        dpiSetting > 0 -> dpiSetting
        resolution == RES_NATIVE -> (metrics.densityDpi * scale).roundToInt().coerceIn(72, 240)
        else -> 96
    }

    /** What the device's screen suggests: used by Display suggestions. */
    fun recommendedDpi(): Int = (metrics.densityDpi * scale).roundToInt().coerceIn(96, 192)

    companion object {
        const val RES_NATIVE = "Native"
        const val RES_CUSTOM = "Custom"
        const val MODE_TOUCHPAD = "touchpad"
        const val MODE_DIRECT = "direct"
        const val DEFAULT_KEYS = "ESC TAB CTRL ALT SUPER SHIFT LEFT UP DOWN RIGHT HOME END PGUP PGDN DRAG FN KBD"
        const val DASHBOARD = "status,resources,running,favorites,sessions,tools"
        val RESOLUTIONS = listOf(RES_NATIVE, "1280x720", "1600x900", "1920x1080", "1024x768", RES_CUSTOM)

        @Volatile private var instance: Prefs? = null
        fun of(context: Context): Prefs = instance ?: synchronized(this) { instance ?: Prefs(context).also { instance = it } }
    }
}
