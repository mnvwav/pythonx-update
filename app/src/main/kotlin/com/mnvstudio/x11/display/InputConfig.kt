package com.mnvstudio.x11.display

import android.view.KeyEvent
import com.mnvstudio.x11.Prefs

/** What a gesture or button does. */
enum class Action(val label: String) {
    NONE("Nothing"), LEFT("Left click"), RIGHT("Right click"), MIDDLE("Middle click / paste"), DOUBLE("Double click"),
    KEYBOARD("Show keyboard"), SWITCHER("Windows & desktops"), MENU("Display menu"), ESCAPE("Escape key"), ENTER("Enter key"),
    LEAVE("Leave the display");

    companion object { fun of(name: String) = entries.firstOrNull { it.name == name } ?: NONE }
}

/**
 * All input settings for the display, resolved from Prefs and the per-app profile of the focused window.
 * Immutable: the view gets a new one when settings or the focused app change.
 */
data class InputConfig(
    val tap2: Action = Action.RIGHT,
    val tap3: Action = Action.MIDDLE,
    val tap4: Action = Action.KEYBOARD,
    val swipe3Up: Action = Action.SWITCHER,
    val swipe3Down: Action = Action.KEYBOARD,
    val longPressMs: Int = 400,
    val dragSlopDp: Float = 8f,
    val doubleClickMs: Int = 80,
    /** 0 = linear; higher makes fast finger movement go further (like mouse acceleration). */
    val accel: Float = 0.6f,
    val edgeRejectDp: Float = 0f,
    val palmReject: Boolean = true,
    val stylusButton: Action = Action.RIGHT,
    val showPointer: Boolean = true,
    val trail: Boolean = false,
    /** Game controller button (Android key code) to X keycode; the left stick moves the pointer. */
    val gamepad: Map<Int, Int> = DEFAULT_GAMEPAD,
    val stickMouse: Boolean = true,
) {
    companion object {
        /** A/B/X/Y → Enter, Escape, Space, Tab; shoulders → mouse buttons via actions; d-pad → arrows; Start → Enter. */
        val DEFAULT_GAMEPAD = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to KeyMap.ENTER, KeyEvent.KEYCODE_BUTTON_B to KeyMap.ESC, KeyEvent.KEYCODE_BUTTON_X to 65,
            KeyEvent.KEYCODE_BUTTON_Y to KeyMap.TAB, KeyEvent.KEYCODE_BUTTON_START to KeyMap.ENTER, KeyEvent.KEYCODE_BUTTON_SELECT to KeyMap.ESC,
            KeyEvent.KEYCODE_DPAD_UP to KeyMap.UP, KeyEvent.KEYCODE_DPAD_DOWN to KeyMap.DOWN, KeyEvent.KEYCODE_DPAD_LEFT to KeyMap.LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT to KeyMap.RIGHT,
        )

        val GAMEPAD_BUTTONS = listOf(
            KeyEvent.KEYCODE_BUTTON_A to "A", KeyEvent.KEYCODE_BUTTON_B to "B", KeyEvent.KEYCODE_BUTTON_X to "X", KeyEvent.KEYCODE_BUTTON_Y to "Y",
            KeyEvent.KEYCODE_BUTTON_L1 to "L1", KeyEvent.KEYCODE_BUTTON_R1 to "R1", KeyEvent.KEYCODE_BUTTON_START to "Start",
            KeyEvent.KEYCODE_BUTTON_SELECT to "Select",
        )

        /** Builds the config from settings, with the per-app profile for [appClass] (if any) on top. */
        fun from(p: Prefs, appClass: String = ""): InputConfig {
            val profile = AppProfiles.get(p, appClass)
            fun a(key: String, def: String) = Action.of(profile[key] ?: def)
            val pad = p.gamepadMap.split(',').mapNotNull { e ->
                val (k, v) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val code = GAMEPAD_BUTTONS.firstOrNull { it.second == k }?.first ?: return@mapNotNull null
                (ExtraKeys.keycode(v) ?: v.toIntOrNull())?.let { code to it }
            }.toMap()
            return InputConfig(
                tap2 = a("tap2", p.gestureTap2), tap3 = a("tap3", p.gestureTap3), tap4 = a("tap4", p.gestureTap4),
                swipe3Up = a("swipe3Up", p.gestureSwipeUp), swipe3Down = a("swipe3Down", p.gestureSwipeDown),
                longPressMs = p.longPressMs, dragSlopDp = p.dragSlop.toFloat(), doubleClickMs = p.doubleClickMs,
                accel = p.mouseAccel, edgeRejectDp = p.edgeReject.toFloat(), palmReject = p.palmReject,
                stylusButton = Action.of(p.stylusButton), showPointer = p.showPointer, trail = p.pointerTrail,
                gamepad = DEFAULT_GAMEPAD + pad, stickMouse = p.stickMouse,
            )
        }
    }
}

/**
 * Per-app input profiles, keyed by the app's window class (e.g. "XTerm"): gesture overrides and an extra-keys layout.
 * Stored in Prefs as "Class|key=value|key=value;…".
 */
object AppProfiles {
    fun all(p: Prefs): Map<String, Map<String, String>> = p.appInput.split(';').mapNotNull { e ->
        val parts = e.split('|')
        val cls = parts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        cls to parts.drop(1).mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    }.toMap()

    fun get(p: Prefs, cls: String): Map<String, String> = if (cls.isEmpty()) emptyMap() else all(p)[cls] ?: emptyMap()

    fun set(p: Prefs, cls: String, values: Map<String, String>) {
        val m = all(p).toMutableMap()
        if (values.isEmpty()) m.remove(cls) else m[cls] = values
        p.appInput = m.entries.joinToString(";") { (k, v) -> (listOf(k) + v.map { "${it.key}=${it.value.replace(";", ",").replace("|", "/")}" }).joinToString("|") }
    }
}
