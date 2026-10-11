package com.mnvstudio.x11.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Kind
import com.mnvstudio.x11.Launcher
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.Preset
import com.mnvstudio.x11.SettingItem
import com.mnvstudio.x11.SettingsModel
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.XService
import com.mnvstudio.x11.ui.Accent
import com.mnvstudio.x11.ui.AccentSoft
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.CardHigh
import com.mnvstudio.x11.ui.Chips
import com.mnvstudio.x11.ui.Confirm
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Line
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.SecondaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeL
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.SliderRow
import com.mnvstudio.x11.ui.SurfaceCard
import com.mnvstudio.x11.ui.ToggleRow
import com.mnvstudio.x11.ui.TopBar
import com.mnvstudio.x11.ui.copyText
import kotlinx.coroutines.delay
import org.json.JSONObject

/** One undoable change: the settings it touched and their previous values. */
private class Change(val label: String, val before: JSONObject)

@Composable
fun SettingsScreen(prefs: Prefs, navigate: (String) -> Unit, onReplayOnboarding: () -> Unit, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var undo by remember { mutableStateOf<Change?>(null) }
    var confirmReset by remember { mutableStateOf<String?>(null) }
    val all = SettingsModel.items(prefs)
    LaunchedEffect(undo) { if (undo != null) { delay(6000); undo = null } }

    fun change(keys: Collection<String>, label: String, block: () -> Unit) {
        undo = Change(label, prefs.toJson(keys))
        block()
    }

    Box(Modifier.fillMaxSize().background(Page)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Settings", onBack = onBack)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 680.dp)) {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = ShapeM,
                        placeholder = { Text("Search settings", color = Faint) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Label) },
                        trailingIcon = { if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Clear search", tint = Label, modifier = Modifier.clickable { query = "" }) },
                        textStyle = TextStyle(color = Ink, fontSize = 15.sp))
                    Spacer(Modifier.height(10.dp))
                    ToggleRow("Advanced settings", "Show options for power users", prefs.advanced) { prefs.advanced = it }

                    if (XHolder.server != null) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(ShapeM).background(AccentSoft).clickable { XService.stop(context) }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, tint = Accent)
                            Text("Settings marked ↻ apply when the display restarts. Tap to stop it now.", color = Ink, fontSize = 13.sp,
                                modifier = Modifier.padding(start = 10.dp))
                        }
                    }

                    if (query.isBlank()) {
                        SectionTitle("Presets")
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsModel.PRESETS.forEach { pr -> PresetCard(pr) {
                                change(SettingsModel.items(prefs).map { it.key } + pr.values.keys, "Applied ${pr.name}") {
                                    if (pr.values.isEmpty()) prefs.reset() else pr.values.forEach { (k, v) -> SettingsModel.set(prefs, k, v) }
                                }
                            } }
                        }
                    }

                    val shown = all.filter { it.matches(query) && it.visible() && (prefs.advanced || !it.advanced || query.isNotBlank()) }
                    SettingsModel.SECTIONS.forEach { section ->
                        val items = shown.filter { it.section == section }
                        if (items.isEmpty()) return@forEach
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(section, Modifier.weight(1f))
                            Text("Reset", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp).clip(CircleShape)
                                .clickable { confirmReset = section }.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                        SurfaceCard(padding = 16.dp) {
                            items.forEach { item -> SettingRow(prefs, item) { block -> change(listOf(item.key), item.title, block) } }
                        }
                    }
                    if (shown.isEmpty()) Text("No settings match \"$query\".", color = Label, modifier = Modifier.padding(top = 24.dp))

                    if (query.isBlank()) {
                        SectionTitle("More")
                        SurfaceCard {
                            var exempt by remember { mutableStateOf(Launcher.ignoringBatteryOptimizations(context)) }
                            ToggleRow("Run without battery limits", if (exempt) "Allowed" else "Recommended so the display survives switching apps",
                                exempt, enabled = !exempt) { Launcher.requestBatteryExemption(context); exempt = true }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SecondaryButton("Copy settings", Modifier.weight(1f)) { copyText(context, prefs.toJson().toString(2), "Settings copied as JSON") }
                                SecondaryButton("Paste settings", Modifier.weight(1f)) { pasteSettings(context, prefs) { ch, n -> undo = ch; toast(context, "$n settings applied") } }
                            }
                            Spacer(Modifier.height(10.dp))
                            SecondaryButton("Replay setup", Modifier.fillMaxWidth()) { onReplayOnboarding() }
                            Spacer(Modifier.height(10.dp))
                            SecondaryButton("Reset all settings", Modifier.fillMaxWidth(), icon = Icons.Filled.RestartAlt) { confirmReset = "all" }
                        }
                        SectionTitle("About")
                        SurfaceCard {
                            Text("X11 1.0", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("No tracking, no accounts. Works offline once Linux is set up. Fonts: misc-fixed (public domain); proot (GPL-2.0).",
                                color = Faint, fontSize = 12.sp, lineHeight = 17.sp)
                            Text("Help & guide", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp).clickable { navigate("help") })
                        }
                    }
                    Spacer(Modifier.height(96.dp))
                }
            }
        }
        // Undo bar.
        AnimatedVisibility(undo != null, Modifier.align(Alignment.BottomCenter).padding(16.dp),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            val u = undo
            Row(Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(ShapeL).background(CardHigh).border(1.dp, Line, ShapeL).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(u?.label ?: "", color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("Undo", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { u?.let { prefs.fromJson(it.before) }; undo = null }.padding(8.dp))
            }
        }
    }
    confirmReset?.let { section ->
        Confirm(if (section == "all") "Reset all settings?" else "Reset $section settings?", "They go back to their defaults. You can undo right after.",
            "Reset", onDismiss = { confirmReset = null }) {
            val keys = if (section == "all") all.map { it.key } else all.filter { it.section == section }.map { it.key }
            change(keys, "Reset ${if (section == "all") "all settings" else section}") { prefs.reset(if (section == "all") null else keys) }
        }
    }
}

private fun toast(context: Context, msg: String) = android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

/** Applies settings JSON from the clipboard; invalid JSON or values are ignored. */
private fun pasteSettings(context: Context, prefs: Prefs, done: (Change, Int) -> Unit) {
    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
    val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: return toast(context, "Clipboard is empty")
    val json = runCatching { JSONObject(text) }.getOrNull() ?: return toast(context, "That isn't settings JSON")
    val before = prefs.toJson()
    done(Change("Pasted settings", before), prefs.fromJson(json))
}

@Composable
private fun PresetCard(p: Preset, onClick: () -> Unit) {
    Column(Modifier.width(170.dp).clip(ShapeM).background(Card).border(1.dp, Line, ShapeM).clickable(onClick = onClick).padding(14.dp)) {
        Icon(Icons.Filled.Tune, null, tint = Accent)
        Text(p.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Text(p.description, color = Label, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun SettingRow(prefs: Prefs, item: SettingItem, change: (() -> Unit) -> Unit) {
    val value = SettingsModel.value(prefs, item.key)
    val title = item.title + if (item.needsRestart) "  ↻" else ""
    when (item.kind) {
        Kind.TOGGLE -> ToggleRow(title, item.summary.ifEmpty { null }, value.toBoolean(), enabled = item.enabled()) { v -> change { SettingsModel.set(prefs, item.key, v) } }
        Kind.CHOICE -> Column(Modifier.padding(vertical = 8.dp)) {
            Text(title, color = Ink, fontSize = 15.sp)
            if (item.summary.isNotEmpty()) Text(item.summary, color = Label, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Chips(item.options.map { it.first }, value, label = { v -> item.options.firstOrNull { it.first == v }?.second ?: v }) { v ->
                change { SettingsModel.set(prefs, item.key, v) }
            }
        }
        Kind.SLIDER -> SliderRow(title, item.format(value.toFloatOrNull() ?: 0f), value.toFloatOrNull() ?: 0f, item.range, item.steps) { v ->
            SettingsModel.set(prefs, item.key, v)
        }
        Kind.TEXT -> Column(Modifier.padding(vertical = 8.dp)) {
            Text(title, color = Ink, fontSize = 15.sp)
            var text by remember(value) { mutableStateOf(value) }
            val numeric = prefs.entry(item.key)?.default is Int
            OutlinedTextField(text, { t ->
                text = if (numeric) t.filter(Char::isDigit).take(5) else t
                if (!numeric || text.isNotEmpty()) SettingsModel.set(prefs, item.key, text)
            }, Modifier.fillMaxWidth().padding(top = 8.dp), shape = ShapeM, singleLine = numeric,
                keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Ink))
            if (item.summary.isNotEmpty()) Text(item.summary, color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
