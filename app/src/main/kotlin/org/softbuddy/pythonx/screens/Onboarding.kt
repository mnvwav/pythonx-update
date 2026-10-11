package org.softbuddy.pythonx.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.softbuddy.pythonx.Prefs
import org.softbuddy.pythonx.editor.CodeView
import org.softbuddy.pythonx.editor.EditorController
import org.softbuddy.pythonx.project.Project
import org.softbuddy.pythonx.project.createProject
import org.softbuddy.pythonx.project.templateByName
import org.softbuddy.pythonx.python.Problem
import org.softbuddy.pythonx.python.Python
import org.softbuddy.pythonx.ui.Accent
import org.softbuddy.pythonx.ui.AppLogo
import org.softbuddy.pythonx.ui.CardBg
import org.softbuddy.pythonx.ui.CodeBg
import org.softbuddy.pythonx.ui.CodeFaint
import org.softbuddy.pythonx.ui.CodeText
import org.softbuddy.pythonx.ui.DividerColor
import org.softbuddy.pythonx.ui.Faint
import org.softbuddy.pythonx.ui.Good
import org.softbuddy.pythonx.ui.Ink
import org.softbuddy.pythonx.ui.Inset
import org.softbuddy.pythonx.ui.Label
import org.softbuddy.pythonx.ui.Mono
import org.softbuddy.pythonx.ui.OnAccent
import org.softbuddy.pythonx.ui.OnSpark
import org.softbuddy.pythonx.ui.Spark
import org.softbuddy.pythonx.ui.PageBg
import org.softbuddy.pythonx.ui.Pill
import org.softbuddy.pythonx.ui.Segmented
import org.softbuddy.pythonx.ui.ShapeL
import org.softbuddy.pythonx.ui.ShapeM
import org.softbuddy.pythonx.ui.ShapeS
import org.softbuddy.pythonx.ui.ToggleRow
import org.softbuddy.pythonx.ui.Track
import java.io.File

private const val PAGES = 7

/** The first project, chosen by the goal picked during onboarding. */
fun starterFor(goal: String) = templateByName(when (goal) {
    Prefs.GOAL_AUTOMATE -> "Command-line tool"
    Prefs.GOAL_DATA -> "CSV analysis"
    Prefs.GOAL_WEB -> "Weather API client"
    else -> "Number guessing game"
})

/**
 * Welcome, features, profile, goal, live editor setup, a real Python run, then finish.
 * Python unpacks in the background from the first page, so it is ready by the "Try it" page.
 */
@Composable
fun Onboarding(prefs: Prefs, onDone: (Project) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { PAGES }
    val python = remember { Python(context.applicationContext) }
    var ready by remember { mutableStateOf(python.installed) }
    var pipReady by remember { mutableStateOf(python.pipInstalled) }
    var setupError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var nameError by remember { mutableStateOf(false) }
    val nameMissing = pager.currentPage == 2 && prefs.name.isBlank()
    val last = PAGES - 1
    BackHandler(pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }

    LaunchedEffect(attempt) {
        setupError = null
        runCatching { withContext(Dispatchers.IO) { python.install() } }
            .onSuccess { ready = python.installed }.onFailure { setupError = it.message ?: it.toString() }
        // pip comes from the bundled wheel; installing it now makes the first package install instant.
        if (ready && !pipReady) pipReady = withContext(Dispatchers.IO) { runCatching { python.ensurePip() }.getOrDefault(false) }
    }

    fun finish() {
        if (prefs.name.isBlank()) prefs.name = "Coder"
        prefs.completeOnboarding()
        onDone(createProject(context, "My first project", starterFor(prefs.goal)))
    }

    Box(Modifier.fillMaxSize().background(PageBg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Thin segmented progress, one segment per page.
                Row(Modifier.weight(1f).padding(end = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(PAGES) { i ->
                        val c by animateColorAsState(if (i <= pager.currentPage) Ink else Track, tween(250), label = "seg")
                        Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(c))
                    }
                }
                SetupPill(ready, setupError) { attempt++ }
                if (pager.currentPage < 2) TextButton({ scope.launch { pager.animateScrollToPage(2) } }) { Text("Skip") }
            }

            HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = !nameMissing && pager.currentPage != 4,
                beyondViewportPageCount = 1) { page ->
                val offset = kotlin.math.abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                Column(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - offset * 0.7f }
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    when (page) {
                        0 -> WelcomePage(active = pager.currentPage == 0)
                        1 -> FeaturesPage(active = pager.currentPage == 1)
                        2 -> ProfilePage(prefs, nameError) { nameError = false }
                        3 -> GoalPage(prefs)
                        4 -> EditorPage(prefs, active = pager.currentPage == 4)
                        5 -> TryPage(prefs, python, ready, active = pager.currentPage == 5)
                        else -> FinishPage(prefs, ready, pipReady, setupError)
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pager.currentPage in 1..last) OutlinedButton({ scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    Modifier.height(54.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Button(
                    onClick = {
                        when {
                            nameMissing -> nameError = true
                            pager.currentPage < last -> { if (pager.currentPage in 2..3) prefs.applyProfile(); scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                            setupError != null -> attempt++
                            ready -> finish()
                        }
                    },
                    enabled = pager.currentPage < last || ready || setupError != null,
                    modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                ) {
                    Text(when {
                        pager.currentPage == 0 -> "Get started"
                        pager.currentPage == 5 -> "Looks good"
                        pager.currentPage < last -> "Continue"
                        setupError != null -> "Retry setup"
                        ready -> "Start coding"
                        else -> "Finishing setup… ${(Python.installProgress * 100).toInt()}%"
                    }, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (pager.currentPage < last || ready) { Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
                }
            }
        }
    }
}

/** Compact status of the background Python setup. */
@Composable
private fun SetupPill(ready: Boolean, error: String?, onRetry: () -> Unit) {
    val p = Python.installProgress
    Row(Modifier.padding(end = 4.dp).clip(Pill).background(CardBg).clickable(enabled = error != null, onClick = onRetry)
        .padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            error != null -> { Icon(Icons.Filled.ErrorOutline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error); Text(" Retry", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            ready -> { Icon(Icons.Filled.CheckCircle, null, Modifier.size(14.dp), tint = Good); Text(" Python ready", fontSize = 12.sp, color = Label) }
            else -> { CircularProgressIndicator({ p }, Modifier.size(12.dp), strokeWidth = 2.dp, color = Accent, trackColor = Track); Text(" Setting up ${(p * 100).toInt()}%", fontSize = 12.sp, color = Label) }
        }
    }
}

// ---------- page 1: a live "type and run" demo ----------

private const val DEMO = """names = ["Ada", "Grace", "Linus"]

for i, name in enumerate(names, 1):
    print(f"{i}. Hello, {name}!")"""

private val DEMO_OUTPUT = listOf("1. Hello, Ada!", "2. Hello, Grace!", "3. Hello, Linus!")

@Composable
private fun WelcomePage(active: Boolean) {
    var typed by remember { mutableIntStateOf(0) }
    var phase by remember { mutableIntStateOf(0) } // 0 typing, 1 running, 2 output
    LaunchedEffect(active) {
        while (active) {
            phase = 0; typed = 0
            while (typed < DEMO.length) { typed++; delay(if (DEMO[typed - 1] == '\n') 120 else 28) }
            delay(400); phase = 1; delay(500); phase = 2; delay(3200)
        }
    }
    val cursorOn by rememberInfiniteTransition(label = "cursor").animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "c")

    Spacer(Modifier.height(12.dp))
    Row(Modifier.padding(bottom = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        AppLogo(48.dp); Spacer(Modifier.width(14.dp)); Wordmark(24)
    }
    Text(buildAnnotatedString {
        append("Write. Run.\n")
        withStyle(SpanStyle(color = Label)) { append("Python anywhere.") }
    }, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, color = Ink)
    Text("Python ${Python.VERSION} runs right on your phone. No server, and no internet needed to run your code.",
        color = Label, modifier = Modifier.padding(top = 10.dp, bottom = 22.dp))

    Column(Modifier.fillMaxWidth().clip(ShapeL).background(CardBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("main.py", fontFamily = Mono, fontSize = 12.sp, color = Label, modifier = Modifier.weight(1f))
            Row(Modifier.clip(Pill).background(if (phase >= 1) Spark else Inset)
                .padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(14.dp), tint = if (phase >= 1) OnSpark else Spark)
                Text(" Run", fontSize = 12.sp, color = if (phase >= 1) OnSpark else Spark, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.fillMaxWidth().height(116.dp).background(CodeBg).padding(12.dp)) {
            val shown = DEMO.take(typed) + if (phase == 0 && cursorOn < 0.5f) "▌" else ""
            Text(highlightPython(shown), fontFamily = Mono, fontSize = 12.sp, lineHeight = 18.sp, color = CodeText)
        }
        Column(Modifier.fillMaxWidth().background(Color.Black).padding(12.dp).heightIn(min = 76.dp)) {
            AnimatedVisibility(phase >= 1, enter = fadeIn() + expandVertically()) {
                Text("$ python3 main.py", fontFamily = Mono, fontSize = 11.sp, color = Color(0xFF7DDBB3))
            }
            AnimatedVisibility(phase == 2, enter = fadeIn() + slideInVertically { it / 2 }) {
                Column(Modifier.padding(top = 4.dp)) {
                    DEMO_OUTPUT.forEach { Text(it, fontFamily = Mono, fontSize = 12.sp, color = CodeText) }
                }
            }
        }
    }
}

// ---------- page 2: what's inside ----------

private data class Feature(val icon: ImageVector, val title: String, val body: String)

private val FEATURES = listOf(
    Feature(Icons.Filled.Memory, "Python ${Python.SHORT}", "The official CPython build, fully offline"),
    Feature(Icons.Filled.Inventory2, "pip packages", "requests, rich, sympy and thousands more"),
    Feature(Icons.Filled.AutoAwesome, "Live error checking", "Typos and syntax errors as you type"),
    Feature(Icons.Filled.Terminal, "Real terminal", "input(), colours, Ctrl-C, a Python REPL"),
    Feature(Icons.Filled.FolderOpen, "Projects", "Folders, many files, zip import and export"),
    Feature(Icons.Filled.History, "Local history", "Every version saved; restore in one tap"),
)

@Composable
private fun FeaturesPage(active: Boolean) {
    Spacer(Modifier.height(16.dp))
    Text(buildAnnotatedString {
        append("A full IDE,\n"); withStyle(SpanStyle(color = Label)) { append("in your pocket.") }
    }, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, color = Ink)
    Spacer(Modifier.height(22.dp))
    FEATURES.chunked(2).forEachIndexed { row, pair ->
        Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEachIndexed { col, f ->
                val idx = row * 2 + col
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(active) { if (active) { delay(80L * idx); shown = true } else shown = false }
                val a by animateFloatAsState(if (shown) 1f else 0f, tween(350), label = "a")
                Column(Modifier.weight(1f).alpha(a).clip(ShapeL).background(CardBg).border(1.dp, DividerColor, ShapeL).padding(14.dp)) {
                    Box(Modifier.size(36.dp).clip(ShapeS).background(Inset), contentAlignment = Alignment.Center) {
                        Icon(f.icon, null, tint = Ink, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(f.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
                    Text(f.body, color = Faint, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

// ---------- pages 3-5: about you ----------

@Composable
private fun PageTitle(title: String, sub: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, color = Ink)
    Text(sub, color = Label, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
}

private val NAME_IDEAS = listOf("Ada", "Guido", "Grace", "Linus", "Alan", "Coder", "Byte")

@Composable
private fun ProfilePage(prefs: Prefs, nameError: Boolean, onTyped: () -> Unit) {
    PageTitle("What should we\ncall you?", "Just for greetings. It stays on this device.")
    val shake = remember { Animatable(0f) }
    LaunchedEffect(nameError) {
        if (nameError) for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, tween(55))
    }
    OutlinedTextField(prefs.name, { prefs.name = it.take(30); onTyped() }, Modifier.fillMaxWidth().offset(x = shake.value.dp),
        label = { Text("First name") }, singleLine = true, isError = nameError,
        leadingIcon = { Icon(Icons.Filled.Person, null) },
        supportingText = { if (nameError) Text("Please tell us your name, or tap a suggestion below.") })
    AnimatedVisibility(prefs.name.isBlank()) {
        Column {
            Text(if (nameError) "Need an idea? Pick one:" else "Or pick one:", color = if (nameError) Accent else Faint, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NAME_IDEAS.forEach { n ->
                    Text(n, Modifier.clip(Pill).background(if (nameError) Accent.copy(alpha = 0.16f) else CardBg)
                        .border(1.dp, if (nameError) Accent else DividerColor, Pill)
                        .clickable { prefs.name = n; onTyped() }.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = if (nameError) Accent else Ink, fontSize = 14.sp)
                }
            }
        }
    }
    Spacer(Modifier.height(28.dp))
    Text("Your experience with Python", fontWeight = FontWeight.SemiBold, color = Ink)
    Spacer(Modifier.height(10.dp))
    val levels = listOf("Beginner" to "Just starting", "Intermediate" to "Know the basics", "Advanced" to "Decorators are friends")
    Row(Modifier.fillMaxWidth().clip(ShapeM).background(CardBg).padding(4.dp)) {
        levels.forEach { (l, _) ->
            val sel = prefs.level == l
            Box(Modifier.weight(1f).clip(ShapeM).background(if (sel) Accent else Color.Transparent)
                .clickable { prefs.level = l; prefs.applyProfile() }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(l, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (sel) OnAccent else Ink)
            }
        }
    }
    Text(levels.first { it.first == prefs.level }.second + when (prefs.level) {
        "Beginner" -> ". Errors are explained in plain words, and typos are caught as you type."
        "Intermediate" -> ". Live checks for typos, unused imports and syntax warnings."
        else -> ". Programs also run in development mode (-X dev) for extra runtime warnings."
    }, color = Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun GoalPage(prefs: Prefs) {
    PageTitle("What are you\nhere to do?", "We'll pick your first project and tune the home screen for it.")
    val goals = listOf(
        Triple(Prefs.GOAL_LEARN, Icons.Filled.School, "basics, games, exercises"),
        Triple(Prefs.GOAL_AUTOMATE, Icons.Filled.Construction, "scripts and command-line tools"),
        Triple(Prefs.GOAL_DATA, Icons.Filled.Insights, "CSV, statistics, SQLite"),
        Triple(Prefs.GOAL_WEB, Icons.Filled.Language, "APIs, scraping, servers"),
    )
    goals.chunked(2).forEach { pair ->
        Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { (g, icon, body) ->
                val sel = prefs.goal == g
                Column(Modifier.weight(1f).height(150.dp).clip(ShapeL).background(CardBg)
                    .border(if (sel) 2.dp else 1.dp, if (sel) Ink else DividerColor, ShapeL).clickable { prefs.goal = g }.padding(14.dp)) {
                    Row {
                        Box(Modifier.size(36.dp).clip(ShapeS).background(if (sel) Ink else Inset), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = if (sel) PageBg else Ink, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        if (sel) Icon(Icons.Filled.CheckCircle, null, tint = Ink, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(g, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp, color = Ink)
                    Text(body, color = Faint, fontSize = 12.sp)
                }
            }
        }
    }
    Text("First project: ${starterFor(prefs.goal).name}", color = Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
}

private const val EDITOR_SAMPLE = """import math


def area(radius):
    return math.pi * radius ** 2


for r in range(1, 4):
    pritn(f"r={r}  area={area(r):.2f}")
"""

@Composable
private fun EditorPage(prefs: Prefs, active: Boolean) {
    PageTitle("Make the editor\nyours.", "This is the real editor. Change a setting and watch it update.")
    val controller = remember { EditorController() }
    // The typo on the last line shows what live error checking looks like.
    LaunchedEffect(active, prefs.liveErrors, controller.version) {
        delay(150)
        controller.showProblems(if (prefs.liveErrors) listOf(Problem(9, 5, 9, 10, "'pritn' is not defined", true)) else emptyList())
    }
    CodeView(controller, "onboarding-sample", EDITOR_SAMPLE, true, prefs, Modifier.fillMaxWidth().height(230.dp).clip(ShapeL))
    if (prefs.liveErrors) Text("'pritn' is not defined. Did you mean print?", color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
        fontFamily = Mono, modifier = Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(16.dp))
    Column(Modifier.fillMaxWidth().clip(ShapeL).background(CardBg).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Font size", Modifier.weight(1f), color = Ink); Text("${prefs.fontSize}sp", color = Accent, fontWeight = FontWeight.SemiBold)
        }
        Slider(prefs.fontSize.toFloat(), { prefs.fontSize = it.toInt() }, valueRange = 10f..22f, steps = 11,
            colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent))
        Segmented("Indent", listOf("2", "4"), prefs.tabSize.toString()) { prefs.tabSize = it.toInt() }
        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(top = 8.dp))
        ToggleRow("Live error checking", prefs.liveErrors) { prefs.liveErrors = it }
        ToggleRow("Line numbers", prefs.lineNumbers) { prefs.lineNumbers = it }
        ToggleRow("Highlight current line", prefs.lineHighlight) { prefs.lineHighlight = it }
        ToggleRow("Word wrap", prefs.wordWrap) { prefs.wordWrap = it }
    }
    Spacer(Modifier.height(16.dp))
}

// ---------- page 6: run real Python ----------

private fun trySample(prefs: Prefs) = """import platform

name = "${prefs.name.ifBlank { "Coder" }}"
for i in range(1, 4):
    print(f"{i}. Hello, {name}!")

print("Python", platform.python_version(), "on", platform.machine())
"""

@Composable
private fun TryPage(prefs: Prefs, python: Python, ready: Boolean, active: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { EditorController() }
    var output by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var millis by remember { mutableLongStateOf(0L) }
    val sample = remember(active) { trySample(prefs) }
    fun run() = scope.launch {
        running = true; output = null
        val code = controller.text
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "onboarding-try").apply { deleteRecursively(); mkdirs() }
                File(dir, "main.py").writeText(code)
                val start = System.currentTimeMillis()
                val (exit, text) = python.run(listOf("main.py"), dir, stdin = "", timeoutSec = 10)
                Triple(exit == 0, text, System.currentTimeMillis() - start)
            }.getOrElse { Triple(false, it.message ?: it.toString(), 0L) }
        }
        ok = result.first; output = result.second.trimEnd().ifBlank { "(no output)" }; millis = result.third; running = false
    }
    PageTitle("Try it.\nFor real.", "Edit the code and press Run. Python runs right here on your device.")
    Column(Modifier.fillMaxWidth().clip(ShapeL).background(CardBg)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("main.py", fontFamily = Mono, fontSize = 12.sp, color = Label, modifier = Modifier.weight(1f))
            Button(::run, enabled = ready && !running, contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Spark, contentColor = OnSpark)) {
                if (running) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = OnSpark)
                else Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                Text(if (ready) " Run" else " Preparing…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        CodeView(controller, "onboarding-try-$active", sample, true, prefs, Modifier.fillMaxWidth().height(200.dp))
        Column(Modifier.fillMaxWidth().background(Color.Black).padding(14.dp).heightIn(min = 90.dp)) {
            when {
                running -> Text("$ python3 main.py", fontFamily = Mono, fontSize = 12.sp, color = Color(0xFF7DDBB3))
                output == null -> Text(if (ready) "Output appears here." else "Python is finishing setup (${(Python.installProgress * 100).toInt()}%)…",
                    fontFamily = Mono, fontSize = 12.sp, color = CodeFaint)
                else -> {
                    Text(output!!, fontFamily = Mono, fontSize = 12.sp, color = if (ok) CodeText else Color(0xFFFF8A8A))
                    Text(if (ok) "✓ Ran in $millis ms" else "✗ Fix the error above and run again", fontFamily = Mono, fontSize = 11.sp,
                        color = if (ok) Color(0xFF8FDCA0) else Color(0xFFFF8A8A), modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
    Text("Tip: change the name or the range and run it again.", color = Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 16.dp))
}

// ---------- page 7: finish ----------

@Composable
private fun FinishPage(prefs: Prefs, ready: Boolean, pipReady: Boolean, error: String?) {
    val context = LocalContext.current
    var notifications by remember {
        mutableStateOf(android.os.Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifications = it }
    Spacer(Modifier.height(16.dp))
    Text("You're all set${prefs.name.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}.", fontSize = 26.sp, lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, color = Ink)
    Text("Your first project opens next.", color = Label, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))

    Column(Modifier.fillMaxWidth().clip(ShapeL).background(CardBg).padding(16.dp)) {
        Text("${prefs.goal} · ${prefs.level}", fontWeight = FontWeight.SemiBold, color = Ink)
        Text(prefs.profileSummary(), color = Label, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
    Spacer(Modifier.height(12.dp))
    @Composable fun step(icon: ImageVector, title: String, body: String, done: Boolean, action: (@Composable () -> Unit)? = null) =
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(ShapeL).background(CardBg).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(if (done) Good.copy(alpha = 0.18f) else Inset), contentAlignment = Alignment.Center) {
                Icon(if (done) Icons.Filled.Check else icon, null, Modifier.size(20.dp), tint = if (done) Good else Accent)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink); Text(body, color = Faint, fontSize = 12.sp)
            }
            action?.invoke()
        }
    step(Icons.Filled.Memory, "Python ${Python.VERSION}", when {
        error != null -> "Setup failed: $error"; ready -> "Standard library, SSL and SQLite installed"
        else -> "Unpacking… ${(Python.installProgress * 100).toInt()}%"
    }, ready) { if (!ready && error == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
    step(Icons.Filled.Inventory2, "pip", if (pipReady) "Ready to install packages" else "Setting up in the background", pipReady) {
        if (!pipReady && ready) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
    step(Icons.Filled.Notifications, "Background programs", "A notification while a program or server keeps running", notifications) {
        if (!notifications && android.os.Build.VERSION.SDK_INT >= 33) TextButton({ ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow") }
    }
    step(Icons.Filled.Code, "First project", "\"My first project\" from the ${starterFor(prefs.goal).name} starter", ready)
    Spacer(Modifier.height(16.dp))
}
