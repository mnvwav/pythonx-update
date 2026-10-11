package com.mnvstudio.x11

import android.os.Build

/** How a setting is edited. */
enum class Kind { TOGGLE, CHOICE, SLIDER, TEXT }

/**
 * One row in Settings. Describing settings as data lets the screen search them, hide advanced ones,
 * undo changes and reset whole sections.
 */
class SettingItem(
    val key: String,
    val section: String,
    val title: String,
    val summary: String = "",
    val kind: Kind,
    val advanced: Boolean = false,
    /** For CHOICE: value to label. */
    val options: List<Pair<String, String>> = emptyList(),
    val range: ClosedFloatingPointRange<Float> = 0f..1f,
    val steps: Int = 0,
    val format: (Float) -> String = { "%.1f".format(it) },
    val keywords: String = "",
    val enabled: () -> Boolean = { true },
    val visible: () -> Boolean = { true },
    /** Settings that only apply when the display (re)starts. */
    val needsRestart: Boolean = false,
) {
    fun matches(q: String) = q.isBlank() || listOf(title, summary, section, keywords, key).any { it.contains(q.trim(), ignoreCase = true) }
}

/** A named bundle of settings, applied in one tap (and undoable). */
class Preset(val name: String, val description: String, val values: Map<String, Any>)

object SettingsModel {
    val SECTIONS = listOf("Display", "Windows", "Input", "Clipboard", "Network", "Power", "Look", "Linux")

    fun items(p: Prefs): List<SettingItem> = listOf(
        // Display
        SettingItem("resolution", "Display", "Resolution", "Size of the Linux screen", Kind.CHOICE, needsRestart = true,
            options = Prefs.RESOLUTIONS.map { it to if (it == Prefs.RES_NATIVE) "Fit my screen" else it }, keywords = "size pixels"),
        SettingItem("scale", "Display", "Detail", "Lower makes apps bigger and faster", Kind.SLIDER, range = 0.25f..1f, steps = 2,
            format = { "${(it * 100).toInt()}%" }, visible = { p.resolution == Prefs.RES_NATIVE }, needsRestart = true, keywords = "scale"),
        SettingItem("customW", "Display", "Custom width", "", Kind.TEXT, visible = { p.resolution == Prefs.RES_CUSTOM }, needsRestart = true),
        SettingItem("customH", "Display", "Custom height", "", Kind.TEXT, visible = { p.resolution == Prefs.RES_CUSTOM }, needsRestart = true),
        SettingItem("fitMode", "Display", "Fit to phone", "How the screen is scaled", Kind.CHOICE,
            options = listOf("fit" to "Fit", "stretch" to "Stretch", "pixel" to "1:1 pixels")),
        SettingItem("dpi", "Display", "Text size (DPI)", "Higher makes text bigger in apps that follow Xft.dpi", Kind.CHOICE, needsRestart = true,
            options = listOf("0" to "Auto (${p.dpi()})", "96" to "96", "120" to "120", "144" to "144", "192" to "192", "240" to "240"), keywords = "font scaling"),
        SettingItem("orientation", "Display", "Orientation", "", Kind.CHOICE, options = listOf("landscape" to "Landscape", "portrait" to "Portrait", "auto" to "Auto-rotate")),
        SettingItem("followRotation", "Display", "Resize when rotating", "Restart the display at the new size when you turn the phone", Kind.TOGGLE, advanced = true),
        SettingItem("maxFps", "Display", "Frame rate limit", "", Kind.CHOICE, options = listOf("30" to "30 fps", "60" to "60 fps", "120" to "120 fps"), keywords = "fps refresh battery"),
        SettingItem("smooth", "Display", "Smooth scaling", "Filter the picture when it's scaled", Kind.TOGGLE),
        SettingItem("cutout", "Display", "Use the notch area", "Draw behind the camera cutout", Kind.TOGGLE, advanced = true),
        SettingItem("keepScreenOn", "Display", "Keep screen on", "While the display is open", Kind.TOGGLE),
        SettingItem("perfOverlay", "Display", "Performance overlay", "Frames per second on the display", Kind.TOGGLE, advanced = true),
        SettingItem("display", "Display", "Display number", "DISPLAY=:N for apps", Kind.CHOICE, advanced = true, needsRestart = true,
            options = (0..3).map { "$it" to ":$it" }),
        // Windows
        SettingItem("desktopMode", "Windows", "Desktop mode", "Windows get title bars: move, resize, snap, minimize, virtual desktops", Kind.TOGGLE,
            needsRestart = true, keywords = "window manager floating windowed"),
        SettingItem("desktops", "Windows", "Virtual desktops", "", Kind.CHOICE, options = (1..6).map { "$it" to "$it" }, visible = { p.desktopMode }, needsRestart = true),
        SettingItem("snapping", "Windows", "Snap to edges", "Drag to the left or right edge for half the screen, top to maximize", Kind.TOGGLE,
            visible = { p.desktopMode }, needsRestart = true),
        SettingItem("titleBarHeight", "Windows", "Title bar height", "", Kind.CHOICE, options = listOf("20" to "Small", "24" to "Medium", "32" to "Large (touch)"),
            visible = { p.desktopMode }, needsRestart = true, keywords = "decorations"),
        SettingItem("fitWindows", "Windows", "Full-screen apps", "Without a window manager, each window fills the screen", Kind.TOGGLE,
            enabled = { !p.desktopMode }, needsRestart = true),
        // Input
        SettingItem("touchMode", "Input", "Touch mode", "", Kind.CHOICE, options = listOf(Prefs.MODE_TOUCHPAD to "Trackpad", Prefs.MODE_DIRECT to "Direct touch"),
            keywords = "mouse pointer"),
        SettingItem("pointerSpeed", "Input", "Pointer speed", "", Kind.SLIDER, range = 0.5f..3f, format = { "%.1f×".format(it) }, keywords = "acceleration"),
        SettingItem("cursorScale", "Input", "Cursor size", "", Kind.SLIDER, range = 0.6f..3f, format = { "%.1f×".format(it) }, keywords = "pointer large accessibility"),
        SettingItem("extraKeys", "Input", "Extra keys bar", "Esc, Tab, Ctrl, Alt, Super, arrows, F1–F12", Kind.TOGGLE),
        SettingItem("keyLayout", "Input", "Extra keys layout", "Key names separated by spaces; other text is typed as is", Kind.TEXT, advanced = true),
        SettingItem("haptics", "Input", "Vibrate on taps", "", Kind.TOGGLE, keywords = "haptic feedback"),
        // Clipboard
        SettingItem("clipboard", "Clipboard", "Share clipboard", "Copy and paste between Android and Linux", Kind.TOGGLE, needsRestart = true),
        SettingItem("syncPrimary", "Clipboard", "Include mouse selections", "Also copy text you select in Linux apps", Kind.TOGGLE,
            enabled = { p.clipboardSync }, advanced = true, needsRestart = true),
        // Network
        SettingItem("lan", "Network", "Allow other devices", "Accept X11 apps from computers on your network", Kind.TOGGLE, needsRestart = true, keywords = "remote tcp"),
        SettingItem("allowList", "Network", "Allowed addresses", "e.g. 192.168.1.20, 192.168.1.*", Kind.TEXT, visible = { p.lanAccess }, needsRestart = true),
        // Power
        SettingItem("wakeLock", "Power", "Keep the CPU awake", "For long installs with the screen off", Kind.TOGGLE, advanced = true),
        SettingItem("idleStop", "Power", "Stop when idle", "With no apps connected", Kind.CHOICE,
            options = listOf("0" to "Never", "10" to "10 min", "30" to "30 min", "60" to "60 min"), keywords = "battery"),
        // Look
        SettingItem("themeMode", "Look", "Theme", "", Kind.CHOICE,
            options = listOf("dark" to "Dark", "amoled" to "AMOLED black", "light" to "Light", "system" to "System"), keywords = "dark light black"),
        SettingItem("accent", "Look", "Accent color", "", Kind.CHOICE, options = com.mnvstudio.x11.ui.ACCENTS.keys.map { it to it }, keywords = "colour"),
        SettingItem("dynamicColor", "Look", "Wallpaper colors", if (Build.VERSION.SDK_INT >= 31) "Material You colors from your wallpaper" else "Needs Android 12",
            Kind.TOGGLE, enabled = { Build.VERSION.SDK_INT >= 31 }),
        SettingItem("highContrast", "Look", "High contrast", "Brighter text and borders", Kind.TOGGLE, keywords = "accessibility"),
        SettingItem("textScale", "Look", "App text size", "", Kind.SLIDER, range = 0.85f..1.4f, steps = 10, format = { "${(it * 100).toInt()}%" },
            keywords = "font accessibility"),
        SettingItem("navLayout", "Look", "Navigation", "Bottom bar or side rail", Kind.CHOICE,
            options = listOf("auto" to "Automatic", "bottom" to "Bottom bar", "rail" to "Side rail"), advanced = true),
        SettingItem("showSplash", "Look", "Opening animation", "", Kind.TOGGLE, advanced = true, keywords = "splash loading"),
        // Linux
        SettingItem("noSeccomp", "Linux", "Compatibility mode", "Slower proot mode for phones where Linux commands fail (Diagnostics sets this)", Kind.TOGGLE,
            advanced = true, keywords = "proot seccomp"),
    )

    val PRESETS = listOf(
        Preset("Balanced", "The defaults", emptyMap()),
        Preset("Battery saver", "30 fps, lower detail, stops when idle",
            mapOf("maxFps" to 30, "scale" to 0.5f, "idleStop" to 10, "smooth" to false, "wakeLock" to false)),
        Preset("Performance", "120 fps and sharper picture", mapOf("maxFps" to 120, "scale" to 0.75f, "smooth" to true)),
        Preset("Desktop", "Movable windows at 1920×1080", mapOf("desktopMode" to true, "resolution" to "1920x1080", "fitMode" to "fit", "orientation" to "landscape")),
        Preset("Easy to read", "Bigger text, cursor and contrast",
            mapOf("dpi" to 144, "cursorScale" to 1.8f, "textScale" to 1.2f, "highContrast" to true)),
    )

    /** Current value as a string, for generic editors. */
    fun value(p: Prefs, key: String): String = p.entry(key)?.state?.value?.toString() ?: ""

    /** Sets a setting from a string, converting to its type. Returns false if the value doesn't fit. */
    @Suppress("UNCHECKED_CAST")
    fun set(p: Prefs, key: String, value: Any): Boolean {
        val e = p.entry(key) ?: return false
        return runCatching {
            when (e.default) {
                is Boolean -> (e as Prefs.Entry<Boolean>).write(value as? Boolean ?: value.toString().toBoolean())
                is Int -> (e as Prefs.Entry<Int>).write((value as? Number)?.toInt() ?: value.toString().toInt())
                is Float -> (e as Prefs.Entry<Float>).write((value as? Number)?.toFloat() ?: value.toString().toFloat())
                is Set<*> -> (e as Prefs.Entry<Set<String>>).write(value as Set<String>)
                else -> (e as Prefs.Entry<String>).write(value.toString())
            }
        }.isSuccess
    }
}
