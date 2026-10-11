package org.softbuddy.pythonx.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.softbuddy.pythonx.Prefs
import org.softbuddy.pythonx.project.EXAMPLES
import org.softbuddy.pythonx.project.EXAMPLE_CATEGORIES
import org.softbuddy.pythonx.project.Example
import org.softbuddy.pythonx.project.Project
import org.softbuddy.pythonx.project.Template
import org.softbuddy.pythonx.project.createProject
import org.softbuddy.pythonx.project.projectsDir
import org.softbuddy.pythonx.ui.Accent
import org.softbuddy.pythonx.ui.CardBg
import org.softbuddy.pythonx.ui.Faint
import org.softbuddy.pythonx.ui.Good
import org.softbuddy.pythonx.ui.Ink
import org.softbuddy.pythonx.ui.Label
import org.softbuddy.pythonx.ui.Mono
import org.softbuddy.pythonx.ui.OnSpark
import org.softbuddy.pythonx.ui.Pill
import org.softbuddy.pythonx.ui.ShapeL
import org.softbuddy.pythonx.ui.ShapeS
import org.softbuddy.pythonx.ui.ShapeXL
import org.softbuddy.pythonx.ui.Spark
import org.softbuddy.pythonx.ui.Track

/** The lesson's project, reused if it was opened before so progress and edits are kept. */
fun lessonProject(context: Context, title: String): Project? {
    val e = EXAMPLES.firstOrNull { it.title == title } ?: return null
    projectsDir(context).listFiles().orEmpty().filter { it.isDirectory }.map { Project(it) }.firstOrNull { it.example == e.title }?.let { return it }
    return createProject(context, e.title, Template(e.title, e.summary, mapOf("main.py" to e.code))).also { it.example = e.title }
}

/** The examples as a learning path: categories in order, each lesson checked off after a successful run. */
@Composable
fun LearnTab(prefs: Prefs, navigate: (String) -> Unit) {
    var filter by remember { mutableStateOf<String?>(null) }
    val done = prefs.learned
    val next = EXAMPLES.firstOrNull { it.title !in done }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = DockClearance),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Learn", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.padding(top = 8.dp).fillMaxWidth().clip(ShapeXL).background(CardBg).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(done.size.toFloat() / EXAMPLES.size, 72, "${done.size * 100 / EXAMPLES.size}%", Spark)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text("${done.size} of ${EXAMPLES.size} lessons", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("A lesson counts once its program runs without errors. Change the code, break it, fix it.",
                        color = Label, fontSize = 13.sp, lineHeight = 18.sp)
                    if (next != null) Row(Modifier.padding(top = 10.dp).clip(Pill).background(Spark).clickable { navigate("learn:" + next.title) }
                        .padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(16.dp), tint = OnSpark)
                        Text(" Continue: ${next.title}", color = OnSpark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        EXAMPLE_CATEGORIES.forEach { category ->
            val lessons = EXAMPLES.filter { it.category == category }
            val n = lessons.count { it.title in done }
            item(key = "h:$category") {
                Column(Modifier.padding(top = 18.dp, start = 4.dp, end = 4.dp).clickable { filter = if (filter == category) null else category }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(category.uppercase(), color = Faint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, modifier = Modifier.weight(1f))
                        Text("$n/${lessons.size}", color = if (n == lessons.size) Good else Faint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).clip(Pill).background(Track)) {
                        Box(Modifier.fillMaxWidth(n.toFloat() / lessons.size).height(4.dp).clip(Pill).background(if (n == lessons.size) Good else Accent))
                    }
                }
            }
            items(lessons, key = { it.title }) { e -> LessonRow(e, EXAMPLES.indexOf(e) + 1, e.title in done, e == next) { navigate("learn:" + e.title) } }
        }
    }
}

@Composable
private fun LessonRow(e: Example, number: Int, done: Boolean, isNext: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(ShapeL).background(CardBg)
        .then(if (isNext) Modifier.border(1.5.dp, Spark.copy(alpha = 0.7f), ShapeL) else Modifier)
        .clickable(onClick = onOpen).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(Pill).background(if (done) Good.copy(alpha = 0.18f) else Track.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Filled.Check, "Done", tint = Good, modifier = Modifier.size(20.dp))
            else Text("$number", color = if (isNext) Spark else Label, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = Mono)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(e.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(e.summary, color = Label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (e.interactive) Icon(Icons.Filled.Keyboard, "Asks for input", Modifier.size(16.dp), tint = Faint)
        Spacer(Modifier.width(4.dp))
        Text("${e.code.lines().size}", color = Faint, fontSize = 11.sp, fontFamily = Mono, modifier = Modifier.clip(ShapeS).padding(4.dp))
    }
}
