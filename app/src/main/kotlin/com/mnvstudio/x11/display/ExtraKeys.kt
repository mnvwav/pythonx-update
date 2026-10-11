package com.mnvstudio.x11.display

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.ui.CardHigh
import com.mnvstudio.x11.ui.Cyan
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Line
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.ShapeS
import com.mnvstudio.x11.ui.Violet

/**
 * The extra-keys bar. Its layout is a list of key names (editable in Settings):
 * ESC TAB CTRL ALT SHIFT SUPER UP DOWN LEFT RIGHT HOME END PGUP PGDN INS DEL BKSP ENTER F1..F12
 * FN (shows the F1–F12 row), KBD (opens the keyboard), RCLICK; anything else is typed as text, e.g. | or ~.
 */
object ExtraKeys {
    val MODIFIERS = mapOf("CTRL" to KeyMap.CTRL, "ALT" to KeyMap.ALT, "SHIFT" to KeyMap.SHIFT, "SUPER" to SUPER)
    private val KEYS = mapOf(
        "ESC" to KeyMap.ESC, "TAB" to KeyMap.TAB, "UP" to KeyMap.UP, "DOWN" to KeyMap.DOWN, "LEFT" to KeyMap.LEFT, "RIGHT" to KeyMap.RIGHT,
        "HOME" to KeyMap.HOME, "END" to KeyMap.END, "PGUP" to KeyMap.PGUP, "PGDN" to KeyMap.PGDN, "INS" to 118, "DEL" to 119,
        "BKSP" to KeyMap.BACKSPACE, "ENTER" to KeyMap.ENTER,
    ) + (1..12).associate { "F$it" to if (it <= 10) 66 + it else 84 + it }
    const val SUPER = 133

    fun keycode(name: String): Int? = KEYS[name.uppercase()]
    fun parse(layout: String): List<String> = layout.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(40)
    val F_ROW = (1..12).map { "F$it" }
}

/** Sticky modifier state: tap = held for the next key, double-tap = locked until tapped again. */
class Sticky {
    var latched by mutableStateOf(setOf<String>())
    var locked by mutableStateOf(setOf<String>())
    fun active(): List<Int> = (latched + locked).mapNotNull { ExtraKeys.MODIFIERS[it] }
    fun used() { latched = emptySet() }
    fun tap(name: String, double: Boolean) {
        when {
            double -> { locked = locked + name; latched = latched - name }
            name in locked -> locked = locked - name
            name in latched -> latched = latched - name
            else -> latched = latched + name
        }
    }
}

@Composable
fun ExtraKeysBar(layout: String, sticky: Sticky, view: () -> XDisplayView?, onKeyboard: () -> Unit, onRightClick: () -> Unit) {
    var fnRow by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Color(0xFF0E1018))) {
        if (fnRow) KeyRow(ExtraKeys.F_ROW, sticky, view, onKeyboard, onRightClick, toggleFn = { fnRow = !fnRow })
        KeyRow(ExtraKeys.parse(layout), sticky, view, onKeyboard, onRightClick, toggleFn = { fnRow = !fnRow }, fnActive = fnRow)
    }
}

@Composable
private fun KeyRow(keys: List<String>, sticky: Sticky, view: () -> XDisplayView?, onKeyboard: () -> Unit, onRightClick: () -> Unit,
                   toggleFn: () -> Unit, fnActive: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (name in keys) {
            val upper = name.uppercase()
            when {
                upper in ExtraKeys.MODIFIERS -> Key(upper, active = upper in sticky.latched || upper in sticky.locked, locked = upper in sticky.locked,
                    onDouble = { sticky.tap(upper, true) }) { sticky.tap(upper, false) }
                upper == "FN" -> Key("Fn", active = fnActive, onClick = toggleFn)
                upper == "KBD" -> Key(icon = Icons.Filled.Keyboard, description = "Keyboard", accent = true, onClick = onKeyboard)
                upper == "RCLICK" -> Key("R-CLICK", onClick = onRightClick)
                upper == "UP" -> Key(icon = Icons.Filled.KeyboardArrowUp, description = "Up") { view()?.tapKey(KeyMap.UP) }
                upper == "DOWN" -> Key(icon = Icons.Filled.KeyboardArrowDown, description = "Down") { view()?.tapKey(KeyMap.DOWN) }
                upper == "LEFT" -> Key(icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, description = "Left") { view()?.tapKey(KeyMap.LEFT) }
                upper == "RIGHT" -> Key(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, description = "Right") { view()?.tapKey(KeyMap.RIGHT) }
                else -> {
                    val code = ExtraKeys.keycode(upper)
                    Key(if (code != null) upper else name) { if (code != null) view()?.tapKey(code) else view()?.typeText(name) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(label: String? = null, icon: ImageVector? = null, description: String? = null, active: Boolean = false, locked: Boolean = false,
                accent: Boolean = false, onDouble: (() -> Unit)? = null, onClick: () -> Unit) {
    val bg = when { locked -> Cyan; active || accent -> Violet; else -> CardHigh }
    Box(
        Modifier.height(40.dp).widthIn(min = 46.dp).clip(ShapeS).background(bg).border(1.dp, if (active) bg else Line, ShapeS)
            .combinedClickable(onDoubleClick = onDouble, onClick = onClick).padding(horizontal = 11.dp)
            .semantics { contentDescription = description ?: label ?: "" },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        else Text(label ?: "", color = if (active || locked) Color.White else Ink, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
