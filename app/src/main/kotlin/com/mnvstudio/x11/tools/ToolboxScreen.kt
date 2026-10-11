package com.mnvstudio.x11.tools

import com.mnvstudio.x11.ui.ambient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.ui.*

/** Every built-in tool, plus the user's own commands. */
object Tools {
    val CATEGORY_ORDER = listOf("Files", "Text & data", "System", "Packages", "Network tests", "Web & HTTP", "Network info", "Remote & sharing",
        "Display & input", "History & help", "My tools")

    fun all(prefs: Prefs): List<ToolDef> = LinuxToolDefs.all + NetToolDefs.all + custom(prefs)

    fun custom(prefs: Prefs): List<ToolDef> = prefs.customTools.lines().filter { '|' in it }.mapIndexed { i, l ->
        val name = l.substringBefore('|'); val cmd = l.substringAfter('|')
        ToolDef("custom$i", name, "My tools", Icons.Filled.Bolt, cmd, listOf(Field("args", "Arguments (added to the end)", "")),
            run = linux { v -> cmd + (v["args"]?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "") })
    }

    fun byId(prefs: Prefs, id: String) = all(prefs).firstOrNull { it.id == id }
}

@Composable
fun ToolboxScreen(prefs: Prefs, navigate: (String) -> Unit, back: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val tools = Tools.all(prefs).filter { it.matches(query) }
    Column(Modifier.fillMaxSize().background(Page).ambient()) {
        TopBar("Toolbox", "${Tools.all(prefs).size} tools", onBack = back) { RoundIcon(Icons.Filled.Add, "Add your own tool") { adding = true } }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search tools") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = CircleShape)
                val groups = tools.groupBy { it.category }
                var n = 0
                Tools.CATEGORY_ORDER.filter { it in groups }.forEach { cat ->
                    SectionTitle(cat)
                    groups.getValue(cat).chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).enter(n++, 30), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { t -> ToolCard(t, Modifier.weight(1f)) { navigate(t.route ?: "tool?${t.id}") } }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (tools.isEmpty()) Text("No tools match.", color = Label, modifier = Modifier.padding(20.dp))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (adding) CustomToolDialog(prefs) { adding = false }
}

@Composable
private fun ToolCard(t: ToolDef, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.heightIn(min = 112.dp).pressable(onClick = onClick).clip(ShapeM).background(Card).border(1.dp, Line, ShapeM).padding(14.dp)) {
        Box(Modifier.size(36.dp).clip(ShapeS).background(Brush.linearGradient(com.mnvstudio.x11.ui.categoryGradient(categoryColorKey(t.category)))),
            contentAlignment = Alignment.Center) { Icon(t.icon, null, tint = com.mnvstudio.x11.ui.TileIcon, modifier = Modifier.size(20.dp)) }
        Text(t.name, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        Text(t.description, color = Label, fontSize = 12.sp, maxLines = 2, lineHeight = 15.sp)
    }
}

/** Maps tool categories onto the app-tile gradients so the whole app shares one palette. */
private fun categoryColorKey(c: String) = when (c) {
    "Files", "Packages" -> "Tools"; "Text & data" -> "Office"; "System", "History & help" -> "Developer"
    "Display & input" -> "Graphics"; "My tools" -> "Desktops"; else -> "Internet"
}

@Composable
private fun CustomToolDialog(prefs: Prefs, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDone, containerColor = Card,
        title = { Text("Your own tool", color = Ink) },
        text = {
            Column {
                Text("A command to run in Linux with one tap. It shows up under My tools; long-press isn't needed, edit by adding again with the same name.",
                    color = Label, fontSize = 13.sp)
                OutlinedTextField(name, { name = it.replace("|", "") }, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("Name") }, singleLine = true)
                OutlinedTextField(cmd, { cmd = it.replace("\n", " ") }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Command") },
                    placeholder = { Text("df -h; free -m") })
                if (Tools.custom(prefs).isNotEmpty()) {
                    Text("Remove:", color = Label, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        Tools.custom(prefs).forEach { t ->
                            AssistChip({ prefs.customTools = prefs.customTools.lines().filter { !it.startsWith(t.name + "|") }.joinToString("\n") },
                                { Text(t.name) }, trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(16.dp)) }, modifier = Modifier.padding(end = 6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                if (name.isNotBlank() && cmd.isNotBlank())
                    prefs.customTools = (prefs.customTools.lines().filter { it.isNotBlank() && !it.startsWith("$name|") } + "$name|$cmd").joinToString("\n")
                onDone()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onDone) { Text("Close") } })
}

/** The generic form + output for any [ToolDef]. */
@Composable
fun ToolRunScreen(prefs: Prefs, id: String, back: () -> Unit) {
    val tool = remember(id) { Tools.byId(prefs, id) } ?: run { Text("Unknown tool", color = Label); return }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val values = remember(id) { mutableStateMapOf<String, String>().apply { tool.fields.forEach { put(it.key, it.default) } } }
    val lines = remember(id) { mutableStateListOf<String>() }
    var running by remember { mutableStateOf<Out?>(null) }
    fun start() {
        lines.clear()
        val o = Out({ lines += it }) { running = null }
        running = o
        tool.run(context, values.toMap(), o)
    }
    LaunchedEffect(id) { if (tool.fields.isEmpty()) start() }
    DisposableEffect(id) { onDispose { running?.let { it.cancelled = true; it.process?.destroy() } } }

    Column(Modifier.fillMaxSize().background(Page)) {
        TopBar(tool.name, tool.category, onBack = back) {
            if (lines.isNotEmpty()) RoundIcon(Icons.Filled.ContentCopy, "Copy output") { clipboard.setText(AnnotatedString(lines.joinToString("\n"))) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 760.dp)) {
                Text(tool.description, color = Label, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                tool.fields.forEach { f ->
                    if (f.choices.isNotEmpty()) {
                        Text(f.label, color = Label, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                        Chips(f.choices, values[f.key] ?: f.default) { values[f.key] = it }
                    } else OutlinedTextField(values[f.key] ?: "", { values[f.key] = it }, Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        label = { Text(f.label) }, placeholder = { if (f.hint.isNotEmpty()) Text(f.hint) }, singleLine = !f.multiline,
                        minLines = if (f.multiline) 3 else 1, textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp))
                }
                Spacer(Modifier.height(8.dp))
                if (running == null) PrimaryButton(if (lines.isEmpty()) "Run" else "Run again", Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow) { start() }
                else SecondaryButton("Stop", Modifier.fillMaxWidth(), icon = Icons.Filled.Stop, color = Bad) {
                    running?.let { it.cancelled = true; it.process?.destroy() }; running = null
                }
                if (running != null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = Accent, trackColor = CardHigh)
                if (lines.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    when (tool.view) {
                        View.BARS -> BarsView(lines)
                        View.TABLE -> TableView(lines)
                        View.TEXT -> TextView(lines)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun TextView(lines: List<String>) {
    SelectionContainer {
        Column(Modifier.fillMaxWidth().clip(ShapeM).background(Color(0xFF0B0B0D)).padding(14.dp).horizontalScroll(rememberScrollState())) {
            lines.takeLast(3000).forEach { Text(it, color = Color(0xFFE6E6E6), fontFamily = FontFamily.Monospace, fontSize = 12.sp, softWrap = false) }
        }
    }
}

@Composable
private fun TableView(lines: List<String>) {
    SelectionContainer {
        Column(Modifier.fillMaxWidth().clip(ShapeM).background(Card).border(1.dp, Line, ShapeM)) {
            lines.forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider(color = Line)
                if ('\t' in l) Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(l.substringBefore('\t'), color = Label, fontSize = 13.sp, modifier = Modifier.weight(0.4f))
                    Text(l.substringAfter('\t'), color = Ink, fontSize = 13.sp, modifier = Modifier.weight(0.6f))
                } else Text(l, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

/** "number<TAB>label" lines as bars; sizes in bytes are shown human-readable. */
@Composable
private fun BarsView(lines: List<String>) {
    val rows = lines.map { l -> (l.substringBefore('\t').toLongOrNull() ?: 0L) to l.substringAfter('\t', l) }
    val max = rows.maxOfOrNull { it.first }?.coerceAtLeast(1) ?: 1
    val bytes = rows.any { it.first > 10_000 }
    Column(Modifier.fillMaxWidth().clip(ShapeM).background(Card).border(1.dp, Line, ShapeM).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (n, label) ->
            Column {
                Row {
                    Text(label, color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    if (n > 0) Text(if (bytes) human(n) else "$n", color = Label, fontSize = 12.sp)
                }
                if (n > 0) Box(Modifier.padding(top = 4.dp).fillMaxWidth(n.toFloat() / max).height(8.dp).clip(CircleShape).background(accentGradient()))
            }
        }
    }
}
