package com.mnvstudio.x11

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Settings, persisted in SharedPreferences. Setters write through immediately and recompose readers. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private val metrics = context.resources.displayMetrics

    private fun str(key: String, def: String) = mutableStateOf(sp.getString(key, def) ?: def)
    private fun bool(key: String, def: Boolean) = mutableStateOf(sp.getBoolean(key, def))
    private fun int(key: String, def: Int) = mutableStateOf(sp.getInt(key, def))
    private fun float(key: String, def: Float) = mutableStateOf(sp.getFloat(key, def))
    private fun <T : Any> set(state: MutableState<T>, key: String, v: T) {
        state.value = v
        sp.edit().apply {
            when (v) {
                is Boolean -> putBoolean(key, v); is Int -> putInt(key, v); is Float -> putFloat(key, v)
                else -> putString(key, v.toString())
            }
        }.apply()
    }

    private val _onboarded = bool("onboarded", false); var onboarded get() = _onboarded.value; set(v) = set(_onboarded, "onboarded", v)

    /** "Native" or "WIDTHxHEIGHT". */
    private val _resolution = str("resolution", RES_NATIVE); var resolution get() = _resolution.value; set(v) = set(_resolution, "resolution", v)
    /** Fraction of the device's pixels used at native resolution (smaller = bigger UI, faster). */
    private val _scale = float("scale", 0.5f); var scale get() = _scale.value; set(v) = set(_scale, "scale", v)
    private val _display = int("display", 0); var display get() = _display.value; set(v) = set(_display, "display", v)
    private val _fitWindows = bool("fitWindows", true); var fitWindows get() = _fitWindows.value; set(v) = set(_fitWindows, "fitWindows", v)

    /** "touchpad" (relative pointer, tap to click) or "direct" (touch where you want to click). */
    private val _touchMode = str("touchMode", MODE_TOUCHPAD); var touchMode get() = _touchMode.value; set(v) = set(_touchMode, "touchMode", v)
    private val _pointerSpeed = float("pointerSpeed", 1.2f); var pointerSpeed get() = _pointerSpeed.value; set(v) = set(_pointerSpeed, "pointerSpeed", v)
    private val _extraKeys = bool("extraKeys", true); var extraKeys get() = _extraKeys.value; set(v) = set(_extraKeys, "extraKeys", v)
    private val _keepScreenOn = bool("keepScreenOn", true); var keepScreenOn get() = _keepScreenOn.value; set(v) = set(_keepScreenOn, "keepScreenOn", v)
    private val _smooth = bool("smooth", true); var smoothScaling get() = _smooth.value; set(v) = set(_smooth, "smooth", v)
    /** "auto", "landscape" or "portrait" while the display is open. */
    private val _orientation = str("orientation", "landscape"); var orientation get() = _orientation.value; set(v) = set(_orientation, "orientation", v)

    /** Screen size the server is created with. Native follows the device in landscape, scaled and rounded to even sizes. */
    fun screenSize(): Pair<Int, Int> {
        if (resolution != RES_NATIVE) {
            val p = resolution.split('x')
            p.getOrNull(0)?.toIntOrNull()?.let { w -> p.getOrNull(1)?.toIntOrNull()?.let { h -> return w to h } }
        }
        val long = max(metrics.widthPixels, metrics.heightPixels)
        val short = min(metrics.widthPixels, metrics.heightPixels)
        val (w, h) = if (orientation == "portrait") short to long else long to short
        fun even(v: Float) = (v.roundToInt() / 2 * 2).coerceAtLeast(320)
        return even(w * scale) to even(h * scale)
    }

    /** Dots per inch reported to clients, so toolkits pick sensible sizes. */
    fun dpi(): Int = if (resolution == RES_NATIVE) (metrics.densityDpi * scale).roundToInt().coerceIn(72, 240) else 96

    companion object {
        const val RES_NATIVE = "Native"
        const val MODE_TOUCHPAD = "touchpad"
        const val MODE_DIRECT = "direct"
        val RESOLUTIONS = listOf(RES_NATIVE, "1280x720", "1600x900", "1920x1080", "1024x768", "800x600")
    }
}
