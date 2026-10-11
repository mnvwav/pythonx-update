package com.mnvstudio.x11.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.mnvstudio.x11.ui.*
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

/** Look of each settings category in the list. */
private class SectionInfo(val icon: androidx.compose.ui.graphics.vector.ImageVector, val colors: String, val summary: (Prefs) -> String)

private val SECTION_INFO = mapOf(
    "Display" to SectionInfo(Icons.Filled.Monitor, "Internet") { p -> p.screenSize().let { "${it.first}×${it.second}" } + " · ${p.maxFps} fps · ${p.dpi()} dpi" },
    "Windows" to SectionInfo(Icons.Filled.Window, "Desktops") { p -> if (p.desktopMode) "Desktop mode · ${p.desktops} desktops" else "Full-screen apps" },
    "Input" to SectionInfo(Icons.Filled.TouchApp, "Graphics") { p -> (if (p.touchMode == Prefs.MODE_DIRECT) "Direct touch" else "Trackpad") + if (p.extraKeys) " · extra keys" else "" },
    "Clipboard" to SectionInfo(Icons.Filled.ContentPaste, "Tools") { p -> if (p.clipboardSync) "Shared with Linux" else "Not shared" },
    "Network" to SectionInfo(Icons.Filled.Wifi, "Internet") { p -> if (p.lanAccess) "Other devices allowed" else "This phone only" },
    "Power" to SectionInfo(Icons.Filled.BatteryChargingFull, "Office") { p -> if (p.idleStopMinutes == 0) "Never stops by itself" else "Stops after ${p.idleStopMinutes} min idle" },
    "Look" to SectionInfo(Icons.Filled.Palette, "Developer") { p -> p.themeMode.replaceFirstChar { it.uppercase() } + " · " + p.accent },
    "Linux" to SectionInfo(Icons.Filled.Terminal, "Classic") { p -> if (p.prootNoSeccomp) "Compatibility mode on" else "Standard mode" },
)

@Composable
fun SettingsScreen(prefs: Prefs, navigate: (String) -> Unit, onReplayOnboarding: () -> Unit, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<Change?>(null) }
    var confirmReset by remember { mutableStateOf<String?>(null) }
    val all = SettingsModel.items(prefs)
    LaunchedEffect(undo) { if (undo != null) { delay(6000); undo = null } }
    androidx.activity.compose.BackHandler(enabled = open != null) { open = null }

    fun change(keys: Collection<String>, label: String, block: () -> Unit) {
        undo = Change(label, prefs.toJson(keys))
        block()
    }
    fun shown(section: String?) = all.filter { (section == null || it.section == section) && it.matches(query) && it.visible() &&
        (prefs.advanced || !it.advanced || query.isNotBlank()) }

    @Composable
    fun Items(items: List<SettingItem>, showSection: Boolean) {
        SurfaceCard(padding = 0.dp) {
            items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 16.dp))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    if (showSection) Text(item.section.uppercase(), color = Faint, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp))
                    SettingRow(prefs, item) { block -> change(listOf(item.key), item.title, block) }
                }
            }
        }
    }

    @Composable
    fun Detail(section: String, modifier: Modifier) {
        val items = shown(section)
        Column(modifier) {
            val info = SECTION_INFO[section]
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                if (info != null) GradientTile(info.icon, info.colors, 44.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(section, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(info?.summary?.invoke(prefs) ?: "", color = Label, fontSize = 13.sp)
                }
                Text("Reset", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape).clickable { confirmReset = section }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            if (items.any { it.needsRestart }) Text("↻ applies the next time the display starts", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            if (items.isEmpty()) Text("Nothing here yet. Turn on Advanced settings to see more.", color = Label)
            else Items(items, false)
            if (section == "Input") LinkRow(Icons.Filled.Gesture, "Gestures, keys, stylus & gamepad", "Detailed input controls") { navigate("input") }
            if (section == "Linux") {
                LinkRow(Icons.Filled.HealthAndSafety, "Diagnostics", "Self-test and repair") { navigate("diagnostics") }
                LinkRow(Icons.Filled.Tune, "Environment & users", "Variables, startup script, default user") { navigate("environment") }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Page)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            val section = open
            Column(Modifier.fillMaxSize()) {
                TopBar(if (!wide && section != null) section else "Settings", onBack = if (!wide && section != null) ({ open = null }) else onBack)
                Row(Modifier.fillMaxSize()) {
                    if (wide || section == null) Column(Modifier.then(if (wide) Modifier.width(400.dp) else Modifier.fillMaxWidth()).fillMaxHeight()
                        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(Modifier.widthIn(max = 680.dp)) {
                            // Header in the brand style.
                            Row(Modifier.fillMaxWidth().clip(ShapeL).background(androidx.compose.ui.graphics.Brush.linearGradient(BrandGradient))
                                .border(1.dp, BrandGold.copy(alpha = 0.25f), ShapeL).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Logo(52.dp)
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text("X11", color = BrandGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(if (XHolder.server != null) "Display :${XHolder.display} running · ${XHolder.clients} apps" else "Display stopped",
                                        color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                                }
                                if (XHolder.server != null) Text("Restart", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clip(CircleShape).background(BrandGold).clickable { XHolder.restart(context, "settings") }
                                        .padding(horizontal = 14.dp, vertical = 8.dp))
                            }
                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = CircleShape,
                                placeholder = { Text("Search all settings", color = Faint) },
                                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Label) },
                                trailingIcon = { if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Clear search", tint = Label, modifier = Modifier.clickable { query = "" }) },
                                textStyle = TextStyle(color = Ink, fontSize = 15.sp))

                            if (query.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                val hits = shown(null)
                                if (hits.isEmpty()) Text("No settings match \"$query\".", color = Label, modifier = Modifier.padding(top = 16.dp))
                                else Items(hits, true)
                            } else {
                                SectionTitle("Quick presets")
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SettingsModel.PRESETS.forEach { pr -> PresetCard(pr) {
                                        change(SettingsModel.items(prefs).map { it.key } + pr.values.keys, "Applied ${pr.name}") {
                                            if (pr.values.isEmpty()) prefs.reset() else pr.values.forEach { (k, v) -> SettingsModel.set(prefs, k, v) }
                                        }
                                    } }
                                }
                                SectionTitle("Categories")
                                SurfaceCard(padding = 0.dp) {
                                    SettingsModel.SECTIONS.forEachIndexed { i, sec ->
                                        val info = SECTION_INFO[sec]
                                        if (i > 0) HorizontalDivider(color = Line, modifier = Modifier.padding(start = 70.dp))
                                        Row(Modifier.fillMaxWidth().background(if (wide && section == sec) AccentSoft else Color.Transparent)
                                            .clickable { open = sec }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (info != null) GradientTile(info.icon, info.colors, 40.dp)
                                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                                Text(sec, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                                Text(info?.summary?.invoke(prefs) ?: "", color = Label, fontSize = 12.sp, maxLines = 1)
                                            }
                                            Icon(Icons.Filled.ChevronRight, null, tint = Faint)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                SurfaceCard(padding = 16.dp) {
                                    ToggleRow("Advanced settings", "Show options for power users", prefs.advanced) { prefs.advanced = it }
                                    var exempt by remember { mutableStateOf(Launcher.ignoringBatteryOptimizations(context)) }
                                    ToggleRow("Run without battery limits", if (exempt) "Allowed" else "Recommended so the display survives switching apps",
                                        exempt, enabled = !exempt) { Launcher.requestBatteryExemption(context); exempt = true }
                                }
                                SectionTitle("Backup & reset")
                                SurfaceCard(padding = 0.dp) {
                                    LinkRow(Icons.Filled.ContentCopy, "Copy settings", "As JSON, to keep or share") { copyText(context, prefs.toJson().toString(2), "Settings copied as JSON") }
                                    LinkRow(Icons.Filled.ContentPaste, "Paste settings", "Apply JSON from the clipboard (undoable)") {
                                        pasteSettings(context, prefs) { ch, n -> undo = ch; toast(context, "$n settings applied") } }
                                    LinkRow(Icons.Filled.Replay, "Replay setup", "Walk through the first-run guide again") { onReplayOnboarding() }
                                    LinkRow(Icons.Filled.RestartAlt, "Reset all settings", "Back to defaults (undoable)", danger = true) { confirmReset = "all" }
                                }
                                SectionTitle("About")
                                SurfaceCard(padding = 0.dp) {
                                    LinkRow(Icons.AutoMirrored.Filled.MenuBook, "Help & guide", "How everything works") { navigate("help") }
                                    LinkRow(Icons.Filled.HomeRepairService, "Toolbox", "70+ built-in tools") { navigate("toolbox") }
                                    Text("X11 1.0 · No tracking, no accounts, works offline once Linux is set up.\nFonts: misc-fixed (public domain) · proot (GPL-2.0).",
                                        color = Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(16.dp))
                                }
                            }
                            Spacer(Modifier.height(96.dp))
                        }
                    }
                    if (wide || section != null) {
                        val target = section ?: SettingsModel.SECTIONS.first()
                        AnimatedContent(target, Modifier.weight(1f).fillMaxHeight(), transitionSpec = {
                            (fadeIn() + slideInHorizontally { it / 6 }) togetherWith fadeOut()
                        }, label = "section") { sec ->
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Detail(sec, Modifier.widthIn(max = 680.dp))
                                Spacer(Modifier.height(96.dp))
                            }
                        }
                    }
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

@Composable
private fun GradientTile(icon: androidx.compose.ui.graphics.vector.ImageVector, colors: String, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(androidx.compose.ui.graphics.Brush.linearGradient(categoryGradient(colors))),
        contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.52f)) }
}

@Composable
private fun LinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) Bad else Accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = if (danger) Bad else Ink, fontSize = 15.sp)
            Text(body, color = Label, fontSize = 12.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Faint)
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
        Icon(when (p.name) { "Battery saver" -> Icons.Filled.BatterySaver; "Performance" -> Icons.Filled.Speed; "Desktop" -> Icons.Filled.DesktopWindows
            "Easy to read" -> Icons.Filled.TextIncrease; else -> Icons.Filled.Tune }, null, tint = Accent)
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
        Kind.CHOICE -> if (item.options.size > 4) Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 15.sp)
                if (item.summary.isNotEmpty()) Text(item.summary, color = Label, fontSize = 12.sp)
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                Row(Modifier.clip(CircleShape).background(AccentSoft).clickable { menu = true }.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(item.options.firstOrNull { it.first == value }?.second ?: value, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Filled.ArrowDropDown, null, tint = Accent)
                }
                DropdownMenu(menu, { menu = false }, containerColor = Card) {
                    item.options.forEach { (v, l) -> DropdownMenuItem({ Text(l, color = if (v == value) Accent else Ink) }, { menu = false; change { SettingsModel.set(prefs, item.key, v) } }) }
                }
            }
        } else Column(Modifier.padding(vertical = 8.dp)) {
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
