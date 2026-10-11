package org.softbuddy.pythonx.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.softbuddy.pythonx.Prefs
import org.softbuddy.pythonx.project.EXAMPLES
import org.softbuddy.pythonx.project.Project
import org.softbuddy.pythonx.project.TEMPLATES
import org.softbuddy.pythonx.project.Template
import org.softbuddy.pythonx.project.createProject
import org.softbuddy.pythonx.project.importProjectZip
import org.softbuddy.pythonx.project.projectsDir
import org.softbuddy.pythonx.project.safeName
import org.softbuddy.pythonx.project.templateByName
import org.softbuddy.pythonx.project.uniqueDir
import org.softbuddy.pythonx.python.Pip
import org.softbuddy.pythonx.python.Python
import org.softbuddy.pythonx.ui.Accent
import org.softbuddy.pythonx.ui.Bad
import org.softbuddy.pythonx.ui.CardBg
import org.softbuddy.pythonx.ui.ChipBg
import org.softbuddy.pythonx.ui.CodeBg
import org.softbuddy.pythonx.ui.CodeFaint
import org.softbuddy.pythonx.ui.CodePrompt
import org.softbuddy.pythonx.ui.CodeText
import org.softbuddy.pythonx.ui.ConfirmDialog
import org.softbuddy.pythonx.ui.DividerColor
import org.softbuddy.pythonx.ui.Faint
import org.softbuddy.pythonx.ui.Good
import org.softbuddy.pythonx.ui.Ink
import org.softbuddy.pythonx.ui.Label
import org.softbuddy.pythonx.ui.Mono
import org.softbuddy.pythonx.ui.NameDialog
import org.softbuddy.pythonx.ui.OnSpark
import org.softbuddy.pythonx.ui.PageBg
import org.softbuddy.pythonx.ui.PanelBg
import org.softbuddy.pythonx.ui.PanelHeader
import org.softbuddy.pythonx.ui.PanelItem
import org.softbuddy.pythonx.ui.Pill
import org.softbuddy.pythonx.ui.ShapeL
import org.softbuddy.pythonx.ui.ShapeM
import org.softbuddy.pythonx.ui.ShapeS
import org.softbuddy.pythonx.ui.ShapeXL
import org.softbuddy.pythonx.ui.SidePanel
import org.softbuddy.pythonx.ui.Spark
import org.softbuddy.pythonx.ui.TagChip
import org.softbuddy.pythonx.ui.Track
import org.softbuddy.pythonx.ui.ago
import java.io.File
import java.time.format.TextStyle as DayStyle
import java.util.Locale

private const val PRIVACY_URL = "https://softbuddy.org/privacy-policy-for-pythonx-ide/"
private const val CONTACT_URL = "https://softbuddy.org/contact"

data class ProjectInfo(val project: Project, val files: Int, val modified: Long)

/** Projects, newest first; a folder's own timestamp only changes when entries are added, so use the newest file. */
fun loadProjects(context: android.content.Context): List<ProjectInfo> =
    projectsDir(context).listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }.map { d ->
        val p = Project(d)
        val files = p.files()
        ProjectInfo(p, files.size, (files.maxOfOrNull { File(d, it).lastModified() } ?: 0L).coerceAtLeast(d.lastModified()))
    }.sortedByDescending { it.modified }

/** ">>> PythonX": the brand mark. */
@Composable
fun Wordmark(size: Int = 22) {
    Text(buildAnnotatedString {
        withStyle(SpanStyle(color = Spark, fontFamily = Mono, fontWeight = FontWeight.Bold)) { append(">>> ") }
        withStyle(SpanStyle(color = Ink, fontWeight = FontWeight.Bold)) { append("Python") }
        withStyle(SpanStyle(color = Accent, fontWeight = FontWeight.Bold)) { append("X") }
    }, fontSize = size.sp)
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = Faint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, modifier = Modifier.weight(1f))
        if (action != null) Text(action, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onAction))
    }
}

@Composable
fun HomeTab(prefs: Prefs, navigate: (String) -> Unit, onTab: (Tab) -> Unit, onQuickRun: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<ProjectInfo>?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var palette by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf<String?>(null) }
    val python = remember { Python(context.applicationContext) }
    val packageCount = remember { Pip(python).installed().count { it.name.lowercase() !in setOf("pip", "setuptools", "wheel") } }
    LaunchedEffect(Unit) { projects = withContext(Dispatchers.IO) { loadProjects(context) } }
    val importZip = rememberImportZip(navigate)

    fun newScratch() {
        val n = (projects?.count { it.project.name.startsWith("Scratch") } ?: 0) + 1
        navigate("ide:" + createProject(context, "Scratch $n", templateByName("Empty project")).dir.path)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)
            .padding(bottom = DockClearance)) {
            // Header: avatar (side panel), brand mark, search, settings.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(prefs.name, 40) { drawerOpen = true }
                Box(Modifier.weight(1f).padding(start = 12.dp)) { Wordmark() }
                IconButton({ palette = true }) { Icon(Icons.Filled.Search, "Search everything", tint = Ink) }
                IconButton({ navigate("settings") }) { Icon(Icons.Filled.Settings, "Settings", tint = Ink) }
            }

            // Greeting with the streak, the app's small daily hook.
            Text(greeting() + ",\n" + prefs.name.ifBlank { "coder" } + ".", color = Ink, fontSize = 30.sp, lineHeight = 36.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, modifier = Modifier.padding(top = 18.dp))
            val streak = prefs.streak()
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(Icons.Filled.LocalFireDepartment, if (streak > 0) "$streak-day streak" else "Start a streak", Spark)
                StatChip(Icons.Filled.CheckCircle, "${prefs.learned.size}/${EXAMPLES.size} learned", Good)
            }

            // Quick Run: Python without a project.
            QuickRunCard(onOpen = onQuickRun)

            projects?.firstOrNull()?.let { info ->
                SectionLabel("Continue")
                ContinueCard(info, onOpen = { navigate("ide:" + info.project.dir.path) }, onRun = { navigate("ide:" + info.project.dir.path + "|run") })
            }

            SectionLabel("Start something")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile(Icons.Filled.Add, "New project", "${TEMPLATES.size} templates", Color(0xFF5FD4A0), Modifier.weight(1f)) { showNew = true }
                    QuickTile(Icons.Filled.Edit, "Scratch file", "blank main.py", Color(0xFFE9C46A), Modifier.weight(1f)) { newScratch() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile(Icons.Filled.Bolt, "Python REPL", "interactive", Color(0xFF7FC8C0), Modifier.weight(1f)) { navigate("repl") }
                    QuickTile(Icons.Filled.FolderZip, "Import", ".zip or .py", Color(0xFF7EE2A8), Modifier.weight(1f)) {
                        importZip.launch(arrayOf("application/zip", "application/octet-stream"))
                    }
                }
            }

            SectionLabel("This week")
            ActivityCard(prefs)

            UpNext(prefs) { navigate("learn:" + it) }

            val recent = projects.orEmpty().drop(1).take(3)
            if (recent.isNotEmpty()) {
                SectionLabel("Recent", action = "All projects") { onTab(Tab.PROJECTS) }
                Column(Modifier.fillMaxWidth().clip(ShapeL).background(CardBg)) {
                    recent.forEachIndexed { i, info ->
                        if (i > 0) HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 70.dp))
                        ProjectRow(info, pinned = false, Modifier.clickable { navigate("ide:" + info.project.dir.path) }, onMore = null)
                    }
                }
            }

            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = Label, fontWeight = FontWeight.SemiBold)) { append("CPython ${Python.VERSION}") }
                withStyle(SpanStyle(color = Faint)) { append("  ·  $packageCount packages  ·  runs offline") }
            }, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 28.dp))
        }

        SidePanel(drawerOpen, { drawerOpen = false }) {
            Sidebar(prefs, projects.orEmpty().map { it.project }.take(5), packageCount, onPick = { r ->
                drawerOpen = false
                when (r) {
                    "new" -> showNew = true
                    "scratch" -> newScratch()
                    "import" -> importZip.launch(arrayOf("application/zip", "application/octet-stream"))
                    "about", "licenses" -> about = r
                    "privacy" -> openUrl(context, PRIVACY_URL)
                    "contact" -> openUrl(context, CONTACT_URL)
                    "docs" -> openUrl(context, "https://docs.python.org/${Python.SHORT}/")
                    "rate" -> openUrl(context, "market://details?id=${context.packageName}")
                    "share" -> runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                            "PythonX runs real Python on your phone: https://play.google.com/store/apps/details?id=${context.packageName}"), "Share PythonX"))
                    }
                    "tab:projects" -> onTab(Tab.PROJECTS)
                    "tab:learn" -> onTab(Tab.LEARN)
                    "tab:packages" -> onTab(Tab.PACKAGES)
                    else -> navigate(r)
                }
            })
        }
    }
    about?.let { which ->
        AlertDialog({ about = null }, confirmButton = { TextButton({ about = null }) { Text("Close") } },
            title = { Text(if (which == "about") "About PythonX" else "Open-source licenses") },
            text = { Text(if (which == "about") ABOUT_TEXT else LICENSES_TEXT, modifier = Modifier.verticalScroll(rememberScrollState())) })
    }
    if (showNew) NewProjectDialog({ showNew = false }) { name, t -> showNew = false; navigate("ide:" + createProject(context, name, t).dir.path) }
    if (palette) CommandPalette(projects.orEmpty(), { palette = false }) { r -> palette = false
        when (r) {
            "new" -> showNew = true
            "quickrun" -> onQuickRun("")
            "tab:projects" -> onTab(Tab.PROJECTS)
            "tab:learn" -> onTab(Tab.LEARN)
            "tab:packages" -> onTab(Tab.PACKAGES)
            else -> navigate(r)
        }
    }
}

@Composable
private fun rememberImportZip(navigate: (String) -> Unit) = run {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val dir = withContext(Dispatchers.IO) { runCatching { importProjectZip(context, context.contentResolver.openInputStream(uri)!!) }.getOrNull() }
            if (dir != null) navigate("ide:" + dir.path) else Toast.makeText(context, "Not a project zip", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun Avatar(name: String, size: Int, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(Pill).background(Accent.copy(alpha = 0.18f)).border(2.dp, Spark.copy(alpha = 0.8f), Pill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase().ifEmpty { "P" }, color = Accent, fontWeight = FontWeight.Bold, fontSize = (size * 0.42f).sp)
    }
}

@Composable
private fun StatChip(icon: ImageVector, text: String, tint: Color) {
    Row(Modifier.clip(Pill).background(ChipBg).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(15.dp), tint = tint)
        Text(" $text", color = Label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** A REPL prompt on the home screen; tapping opens the Quick Run sheet. */
@Composable
private fun QuickRunCard(onOpen: (String) -> Unit) {
    val tries = listOf("2 ** 64", "'pythonx'.title()", "sum(range(101))", "import this")
    Column(Modifier.padding(top = 20.dp).fillMaxWidth().clip(ShapeXL).background(CodeBg).border(1.dp, Color(0xFF1A2A22), ShapeXL)
        .clickable { onOpen("") }.dotGrid().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("QUICK RUN", color = CodePrompt, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, modifier = Modifier.weight(1f))
            Text("no project needed", color = CodeFaint, fontSize = 11.sp)
        }
        Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(ShapeM).background(Color(0xFF0F1A15)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(">>> ", color = CodePrompt, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Type any Python…", color = CodeFaint, fontFamily = Mono, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Box(Modifier.size(30.dp).clip(Pill).background(CodePrompt), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFF2A1F05), modifier = Modifier.size(18.dp))
            }
        }
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tries.forEach { t ->
                Text(t, Modifier.clip(Pill).background(Color(0xFF14241D)).clickable { onOpen(t) }.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Color(0xFF9FE3C4), fontFamily = Mono, fontSize = 12.sp)
            }
        }
    }
}

/** The lab-notebook dot grid behind code cards. */
private fun Modifier.dotGrid() = drawBehind {
    val step = 18.dp.toPx(); val r = 0.9.dp.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) { drawCircle(Color(0x14FFFFFF), r, Offset(x, y)); x += step }
        y += step
    }
}

@Composable
private fun QuickTile(icon: ImageVector, title: String, caption: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(ShapeL).background(CardBg).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(ShapeM).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            Text(caption, color = Faint, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** Runs per day for the last week, with today highlighted. */
@Composable
private fun ActivityCard(prefs: Prefs) {
    val days = prefs.runsPerDay(7)
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val total = days.sumOf { it.second }
    val last = prefs.lastRun.split("|")
    Column(Modifier.fillMaxWidth().clip(ShapeL).background(CardBg).padding(18.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$total", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(" run${if (total == 1) "" else "s"} this week", color = Label, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp).weight(1f))
            last.getOrNull(2)?.toLongOrNull()?.let { t ->
                val ok = last.getOrNull(0) == "true"
                Text((if (ok) "✓ " else "✗ ") + ago(t), color = if (ok) Good else Bad, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(86.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { i, (day, n) ->
                val today = i == days.lastIndex
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    val h = if (n == 0) 6.dp else (8 + 52f * n / max).dp
                    Box(Modifier.fillMaxWidth().height(h).clip(ShapeS).background(when { n == 0 -> Track; today -> Spark; else -> Accent }))
                    Text(day.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault()), color = if (today) Ink else Faint, fontSize = 11.sp,
                        fontWeight = if (today) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/** The next lesson on the Learn path. */
@Composable
private fun UpNext(prefs: Prefs, onOpen: (String) -> Unit) {
    val next = EXAMPLES.firstOrNull { it.title !in prefs.learned } ?: return
    SectionLabel("Up next in Learn")
    Row(Modifier.fillMaxWidth().clip(ShapeL).background(Accent.copy(alpha = 0.10f)).border(1.dp, Accent.copy(alpha = 0.3f), ShapeL)
        .clickable { onOpen(next.title) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        ProgressRing(prefs.learned.size.toFloat() / EXAMPLES.size, 46, "${prefs.learned.size}")
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(next.category.uppercase() + " · LESSON ${EXAMPLES.indexOf(next) + 1}", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Text(next.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(next.summary, color = Label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Accent)
    }
}

@Composable
fun ProgressRing(fraction: Float, size: Int, label: String, color: Color = Accent) {
    val track = Track
    Box(Modifier.size(size.dp).drawBehind {
        val stroke = 4.dp.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }, contentAlignment = Alignment.Center) {
        Text(label, color = Ink, fontWeight = FontWeight.Bold, fontSize = (size * 0.3f).sp)
    }
}

private class ContinueScan(val main: String?, val preview: String, val files: List<String>)

/** The most recent project with a live preview of its main file. */
@Composable
private fun ContinueCard(info: ProjectInfo, onOpen: () -> Unit, onRun: () -> Unit) {
    val p = info.project
    val scan by produceState<ContinueScan?>(null, p.dir.path, info.modified) {
        value = withContext(Dispatchers.IO) {
            val main = p.mainFile()
            val preview = main?.let { File(p.dir, it).takeIf { f -> f.isFile }?.useLines { ls -> ls.dropWhile { l -> l.isBlank() }.take(6).joinToString("\n") } }.orEmpty()
            ContinueScan(main, preview, p.files())
        }
    }
    Column(Modifier.fillMaxWidth().clip(ShapeXL).background(CardBg).clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${info.files} file${if (info.files == 1) "" else "s"} · edited ${ago(info.modified)}", color = Faint, fontSize = 12.sp)
            }
            Box(Modifier.clip(Pill).background(Spark).clickable(onClick = onRun).padding(horizontal = 16.dp, vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, null, tint = OnSpark, modifier = Modifier.size(18.dp))
                    Text("Run", color = OnSpark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
        val preview = scan?.preview.orEmpty()
        if (preview.isNotBlank()) Column(Modifier.padding(12.dp).fillMaxWidth().clip(ShapeL).background(CodeBg).padding(14.dp)) {
            Text(scan?.main.orEmpty(), color = CodeFaint, fontSize = 11.sp, fontFamily = Mono, modifier = Modifier.padding(bottom = 6.dp))
            Text(highlightPython(preview), color = CodeText, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = Mono, maxLines = 6,
                overflow = TextOverflow.Clip, softWrap = false)
        } else Spacer(Modifier.height(16.dp))
    }
}

// ---------- Projects tab ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectsTab(navigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var projects by remember { mutableStateOf<List<ProjectInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    var sortByName by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Project?>(null) }
    var renaming by remember { mutableStateOf<Project?>(null) }
    var deleting by remember { mutableStateOf<Project?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val pinStore = remember { context.getSharedPreferences("home", android.content.Context.MODE_PRIVATE) }
    var pins by remember { mutableStateOf(pinStore.getStringSet("pins", emptySet())!!.toSet()) }
    fun togglePin(p: Project) { pins = if (p.name in pins) pins - p.name else pins + p.name; pinStore.edit().putStringSet("pins", pins).apply() }
    LaunchedEffect(refresh) { projects = withContext(Dispatchers.IO) { loadProjects(context) } }
    var exporting by remember { mutableStateOf<Project?>(null) }
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val p = exporting; exporting = null
        if (uri != null && p != null) scope.launch(Dispatchers.IO) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)!!.use { p.exportZip(it) } }.isSuccess
            withContext(Dispatchers.Main) { Toast.makeText(context, if (ok) "Exported ${p.name}.zip" else "Couldn't export ${p.name}", Toast.LENGTH_SHORT).show() }
        }
    }
    val importZip = rememberImportZip(navigate)
    val list = projects?.filter { query.isBlank() || it.project.name.contains(query, true) }
        ?.sortedWith(compareByDescending<ProjectInfo> { it.project.name in pins }.let { c -> if (sortByName) c.thenBy { it.project.name.lowercase() } else c.thenByDescending { it.modified } })

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = DockClearance),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Projects", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton({ importZip.launch(arrayOf("application/zip", "application/octet-stream")) }) { Icon(Icons.Filled.FolderZip, "Import .zip", tint = Ink) }
                Box(Modifier.clip(Pill).background(Accent).clickable { showNew = true }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                        Text(" New", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchPill(query, "Search projects", Modifier.weight(1f)) { query = it }
                Spacer(Modifier.width(8.dp))
                Row(Modifier.clip(Pill).background(ChipBg).clickable { sortByName = !sortByName }.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(16.dp), tint = Label)
                    Text(if (sortByName) " A–Z" else " Recent", color = Label, fontSize = 13.sp)
                }
            }
        }
        when {
            list == null -> item { Text("Loading…", color = Faint, modifier = Modifier.padding(8.dp)) }
            list.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(">>>", color = Spark, fontFamily = Mono, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(if (query.isBlank()) "No projects yet" else "No projects match “$query”", color = Ink, fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
                    if (query.isBlank()) TextButton({ showNew = true }) { Text("Create your first project", color = Accent) }
                }
            }
            else -> items(list, key = { it.project.dir.path }) { info ->
                Box {
                    ProjectRow(info, info.project.name in pins,
                        Modifier.clip(ShapeL).background(CardBg).combinedClickable(onClick = { navigate("ide:" + info.project.dir.path) },
                            onLongClick = { menuFor = info.project })) { menuFor = info.project }
                    DropdownMenu(menuFor == info.project, { menuFor = null }) {
                        val p = info.project
                        DropdownMenuItem({ Text("Run") }, { menuFor = null; navigate("ide:" + p.dir.path + "|run") }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) })
                        DropdownMenuItem({ Text("Open in terminal") }, { menuFor = null; navigate("term:" + p.dir.path) }, leadingIcon = { Icon(Icons.Filled.Terminal, null) })
                        DropdownMenuItem({ Text("Local history") }, { menuFor = null; navigate("history:" + p.dir.path) }, leadingIcon = { Icon(Icons.Filled.History, null) })
                        DropdownMenuItem({ Text(if (p.name in pins) "Unpin" else "Pin to top") }, { menuFor = null; togglePin(p) }, leadingIcon = { Icon(Icons.Filled.PushPin, null) })
                        DropdownMenuItem({ Text("Rename") }, { menuFor = null; renaming = p }, leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) })
                        DropdownMenuItem({ Text("Duplicate") }, {
                            menuFor = null
                            scope.launch(Dispatchers.IO) { p.dir.copyRecursively(uniqueDir(projectsDir(context), "${p.name} copy")); withContext(Dispatchers.Main) { refresh++ } }
                        }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) })
                        DropdownMenuItem({ Text("Export .zip") }, { menuFor = null; exporting = p; exportZip.launch("${p.name}.zip") }, leadingIcon = { Icon(Icons.Filled.IosShare, null) })
                        DropdownMenuItem({ Text("Delete", color = Bad) }, { menuFor = null; deleting = p }, leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = Bad) })
                    }
                }
            }
        }
    }
    if (showNew) NewProjectDialog({ showNew = false }) { name, t -> showNew = false; navigate("ide:" + createProject(context, name, t).dir.path) }
    renaming?.let { p ->
        NameDialog("Rename project", p.name, { renaming = null }) { name ->
            val dest = File(projectsDir(context), safeName(name))
            when {
                dest.exists() -> Toast.makeText(context, "A project named $name already exists", Toast.LENGTH_SHORT).show()
                p.dir.renameTo(dest) -> {
                    if (p.name in pins) { pins = pins - p.name + dest.name; pinStore.edit().putStringSet("pins", pins).apply() }
                    renaming = null; refresh++
                }
                else -> Toast.makeText(context, "Couldn't rename", Toast.LENGTH_SHORT).show()
            }
        }
    }
    deleting?.let { p ->
        ConfirmDialog("Delete ${p.name}?", "All files in this project will be removed from this device.", "Delete", danger = true,
            onDismiss = { deleting = null }) { p.dir.deleteRecursively(); deleting = null; refresh++ }
    }
}

@Composable
fun SearchPill(value: String, hint: String, modifier: Modifier = Modifier, autoFocus: Boolean = false, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }
    Row(modifier.clip(Pill).background(ChipBg).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = Faint)
        BasicTextField(value, onChange, Modifier.weight(1f).padding(start = 10.dp).focusRequester(focus), singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 15.sp, fontFamily = org.softbuddy.pythonx.ui.Poppins), cursorBrush = SolidColor(Accent),
            decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = Faint, fontSize = 15.sp); inner() })
        if (value.isNotEmpty()) Icon(Icons.Filled.Close, "Clear", Modifier.size(18.dp).clickable { onChange("") }, tint = Faint)
    }
}

@Composable
fun ProjectRow(info: ProjectInfo, pinned: Boolean, modifier: Modifier, onMore: (() -> Unit)?) {
    val p = info.project
    val hue = listOf(Color(0xFF5FD4A0), Color(0xFFE9C46A), Color(0xFF7EE2A8), Color(0xFF7FC8C0), Color(0xFFE8A87C))[Math.floorMod(p.name.hashCode(), 5)]
    Row(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(ShapeM).background(hue.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(p.name.take(1).uppercase(), color = hue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pinned) Icon(Icons.Filled.PushPin, "Pinned", Modifier.size(14.dp).padding(end = 2.dp), tint = Spark)
                Text(p.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${info.files} file${if (info.files == 1) "" else "s"} · ${ago(info.modified)}", color = Faint, fontSize = 12.sp, maxLines = 1)
        }
        if (onMore != null) IconButton(onMore, Modifier.offset(x = 8.dp)) { Icon(Icons.Filled.MoreVert, "More actions for ${p.name}", tint = Faint) }
        else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Faint)
    }
}

fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show() }
}

fun formatMillis(ms: Long) = if (ms < 1000) "$ms ms" else "%.1f s".format(ms / 1000.0)

private fun greeting(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Late night coding"
}

/** Full-screen template gallery; picking a card asks for a name and creates the project. */
@Composable
fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String, Template) -> Unit) {
    var picked by remember { mutableStateOf<Template?>(null) }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(PageBg).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("New project", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Every template runs as-is. Pick one, then make it yours.", color = Label, fontSize = 14.sp)
                }
                IconButton(onDismiss) { Icon(Icons.Filled.Close, "Close", tint = Ink) }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(TEMPLATES) { t ->
                    Column(Modifier.fillMaxWidth().clip(ShapeL).background(CardBg).clickable { picked = t }.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Ink, modifier = Modifier.weight(1f))
                            Text(t.files.keys.joinToString(" · "), color = Faint, fontSize = 11.sp, fontFamily = Mono, maxLines = 1)
                        }
                        Text(t.description, color = Label, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { t.tags.forEach { TagChip(it) } }
                    }
                }
            }
        }
    }
    picked?.let { t ->
        NameDialog("Name your project", t.name, onDismiss = { picked = null }, confirm = "Create") { name -> picked = null; onCreate(name.ifBlank { t.name }, t) }
    }
}

/** Search everything: actions, projects (by name or file), and Learn lessons. */
@Composable
private fun CommandPalette(projects: List<ProjectInfo>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    data class Hit(val icon: ImageVector, val title: String, val detail: String, val route: String)
    val actions = remember {
        listOf(
            Hit(Icons.Filled.PlayArrow, "Quick Run", "Run a line of Python", "quickrun"),
            Hit(Icons.Filled.Add, "New project", "From a template", "new"),
            Hit(Icons.Filled.Bolt, "Python REPL", "Interactive prompt", "repl"),
            Hit(Icons.Filled.Terminal, "Terminal", "sh, python3, pip", "term:"),
            Hit(Icons.Filled.Inventory2, "Packages", "Install from PyPI", "tab:packages"),
            Hit(Icons.AutoMirrored.Filled.MenuBook, "Learn", "${EXAMPLES.size} lessons", "tab:learn"),
            Hit(Icons.Filled.Settings, "Settings", "Editor, run, terminal", "settings"),
        )
    }
    val context = LocalContext.current
    val hits = remember(q) {
        if (q.isBlank()) actions + projects.take(5).map { Hit(Icons.Filled.FolderOpen, it.project.name, "Project", "ide:" + it.project.dir.path) }
        else actions.filter { it.title.contains(q, true) || it.detail.contains(q, true) } +
            projects.flatMap { info ->
                val byName = if (info.project.name.contains(q, true)) listOf(Hit(Icons.Filled.FolderOpen, info.project.name, "Project", "ide:" + info.project.dir.path)) else emptyList()
                byName + info.project.files().filter { File(it).name.contains(q, true) }.take(3)
                    .map { Hit(Icons.Filled.Description, File(it).name, "${info.project.name}/$it", "ide:" + info.project.dir.path) }
            }.take(20) +
            EXAMPLES.filter { it.title.contains(q, true) || it.category.contains(q, true) }.map { Hit(Icons.AutoMirrored.Filled.MenuBook, it.title, "Lesson · ${it.category}", "learn:" + it.title) }
    }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 48.dp).clip(ShapeXL).background(PageBg).padding(12.dp)) {
            SearchPill(q, "Search projects, files, lessons, actions", Modifier.fillMaxWidth(), autoFocus = true) { q = it }
            LazyColumn(Modifier.padding(top = 8.dp).height(440.dp)) {
                items(hits) { h ->
                    Row(Modifier.fillMaxWidth().clip(ShapeM).clickable { onPick(if (h.route == "term:") "term:" + projectsDir(context).path else h.route) }
                        .padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(h.icon, null, Modifier.size(20.dp), tint = Accent)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(h.title, color = Ink, fontSize = 15.sp, maxLines = 1)
                            Text(h.detail, color = Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (hits.isEmpty()) item { Text("Nothing matches “$q”.", color = Faint, modifier = Modifier.padding(12.dp)) }
            }
        }
    }
}

/** Side panel: a profile card with real stats, recent projects, tools and help. */
@Composable
private fun Sidebar(prefs: Prefs, recent: List<Project>, packages: Int, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "3.0" }
    Column(Modifier.fillMaxSize().background(PanelBg).verticalScroll(rememberScrollState()).systemBarsPadding().padding(vertical = 14.dp)) {
        Column(Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(ShapeXL).background(CodeBg).dotGrid().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(prefs.name, 52) {}
                Column(Modifier.padding(start = 14.dp)) {
                    Text(prefs.name.ifBlank { "Pythonista" }, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = CodeText)
                    Text("${prefs.level} · ${prefs.goal}", color = CodeFaint, fontSize = 12.sp)
                }
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Streak" to "${prefs.streak()}d", "Runs" to "${prefs.runCount}", "Learned" to "${prefs.learned.size}").forEach { (k, v) ->
                    Column(Modifier.weight(1f).clip(ShapeM).background(Color(0xFF12201A)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(v, color = CodePrompt, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                        Text(k, color = CodeFaint, fontSize = 11.sp)
                    }
                }
            }
            Text(">>> python ${Python.VERSION} · app v$version", color = CodeFaint, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
        }
        PanelHeader("Create")
        PanelItem(Icons.Filled.Add, "New project") { onPick("new") }
        PanelItem(Icons.Filled.Edit, "Scratch file") { onPick("scratch") }
        PanelItem(Icons.Filled.FolderZip, "Import project", ".zip") { onPick("import") }
        if (recent.isNotEmpty()) {
            PanelHeader("Jump back in")
            recent.forEach { p -> PanelItem(Icons.Filled.FolderOpen, p.name) { onPick("ide:" + p.dir.path) } }
        }
        PanelHeader("Tools")
        PanelItem(Icons.Filled.Bolt, "Python REPL") { onPick("repl") }
        PanelItem(Icons.Filled.Terminal, "Terminal", "sh") { onPick("term:" + projectsDir(context).path) }
        PanelItem(Icons.Filled.Inventory2, "Packages", if (packages > 0) "$packages" else null) { onPick("tab:packages") }
        PanelItem(Icons.AutoMirrored.Filled.MenuBook, "Learn", "${prefs.learned.size}/${EXAMPLES.size}") { onPick("tab:learn") }
        PanelItem(Icons.Filled.Settings, "Settings") { onPick("settings") }
        PanelHeader("Help")
        PanelItem(Icons.Filled.Description, "Python documentation") { onPick("docs") }
        PanelItem(Icons.Filled.Info, "About PythonX") { onPick("about") }
        PanelItem(Icons.Filled.Mail, "Contact us") { onPick("contact") }
        PanelItem(Icons.Filled.Policy, "Privacy policy") { onPick("privacy") }
        PanelItem(Icons.Filled.StarRate, "Rate on Play Store") { onPick("rate") }
        PanelItem(Icons.Filled.Share, "Share PythonX") { onPick("share") }
        PanelItem(Icons.Filled.Description, "Open-source licenses") { onPick("licenses") }
    }
}

private val ABOUT_TEXT = "PythonX is a Python lab for Android. It runs the official CPython ${Python.VERSION} build on your device, " +
    "so your code, Quick Run, the terminal and pip all work without a server. Projects, history and settings stay on your phone."
private const val LICENSES_TEXT = "CPython — Python Software Foundation License\n\n" +
    "tzdata — Apache License 2.0\n\nOpenSSL — Apache License 2.0\n\nSQLite — public domain\n\n" +
    "Termux terminal-emulator & terminal-view — Apache License 2.0\n\n" +
    "Sora Editor — GNU LGPL 2.1\n\nTextMate grammars (tm4e) — Eclipse Public License 2.0\n\n" +
    "Jetpack Compose, AndroidX — Apache License 2.0\n\nPoppins font — SIL Open Font License 1.1\n\nFira Code font — SIL Open Font License 1.1"
