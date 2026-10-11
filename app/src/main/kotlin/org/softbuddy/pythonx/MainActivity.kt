package org.softbuddy.pythonx

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.softbuddy.pythonx.project.Project
import org.softbuddy.pythonx.project.RunConfig
import org.softbuddy.pythonx.project.migrateLooseScripts
import org.softbuddy.pythonx.project.projectsDir
import org.softbuddy.pythonx.project.safeName
import org.softbuddy.pythonx.project.uniqueDir
import org.softbuddy.pythonx.python.Python
import org.softbuddy.pythonx.screens.HistoryScreen
import org.softbuddy.pythonx.screens.Shell
import org.softbuddy.pythonx.screens.Tab
import org.softbuddy.pythonx.screens.lessonProject
import org.softbuddy.pythonx.screens.IdeScreen
import org.softbuddy.pythonx.screens.Onboarding
import org.softbuddy.pythonx.screens.PackagesScreen
import org.softbuddy.pythonx.screens.SettingsScreen
import org.softbuddy.pythonx.screens.TerminalScreen
import org.softbuddy.pythonx.screens.runCommand
import org.softbuddy.pythonx.term.Notify
import org.softbuddy.pythonx.term.Term
import org.softbuddy.pythonx.ui.AppTheme
import java.io.File

class MainActivity : ComponentActivity() {
    /** Screen to open from a notification or a shared file. */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute = it; return }
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            else -> null
        } ?: return
        lifecycleScope.launch {
            val dir = withContext(Dispatchers.IO) { runCatching { importSharedFile(uri) }.getOrNull() }
            if (dir != null) pendingRoute = "ide:" + dir.path
            else Toast.makeText(this@MainActivity, "Couldn't open that file", Toast.LENGTH_SHORT).show()
        }
    }

    /** Copies a file shared from another app into its own project, so it can be edited and run. */
    private fun importSharedFile(uri: Uri): File {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "script.py"
        val dir = uniqueDir(projectsDir(this), safeName(File(name).nameWithoutExtension))
        dir.mkdirs()
        val file = File(dir, safeName(name))
        contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        Project(dir).runConfig = RunConfig(entry = file.name)
        return dir
    }

    /**
     * Keeps the Android 12+ splash up briefly so the logo is seen; only the first frame waits.
     */
    private fun holdSplash() {
        val until = android.os.SystemClock.uptimeMillis() + SPLASH_MS
        val content = findViewById<android.view.View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (android.os.SystemClock.uptimeMillis() < until) { content.postDelayed({ content.invalidate() }, 16); return false }
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    override fun onStart() { super.onStart(); Notify.foreground = true }
    override fun onStop() { super.onStop(); Notify.foreground = false }

    /**
     * Hardware/IME key events go straight to a focused terminal. Compose otherwise sees them first and can
     * swallow Enter for focus handling, so the program never gets it.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val focused = currentFocus
        if (focused is TerminalView && event.keyCode != KeyEvent.KEYCODE_BACK) {
            val handled = when (event.action) {
                KeyEvent.ACTION_DOWN -> focused.onKeyDown(event.keyCode, event)
                KeyEvent.ACTION_UP -> focused.onKeyUp(event.keyCode, event)
                else -> false
            }
            if (handled) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notify.channels(this)
        if (savedInstanceState == null) handleIntent(intent)
        if (android.os.Build.VERSION.SDK_INT >= 31) holdSplash()
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember { Prefs(context.applicationContext) }
            // Unpack Python on first launch (and after an update), then prepare links and certificates on every start.
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    runCatching { migrateLooseScripts(context) }
                    runCatching { Python(context.applicationContext).install() }
                }
            }
            // Status and navigation bar icons follow the app theme, not the system's.
            LaunchedEffect(prefs.dark) {
                val style = if (prefs.dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            AppTheme(dark = prefs.dark) {
                Surface {
                    // Routes: home, settings, packages, examples, repl, ide:<dir>[|run], run:<dir>, term:<dir>, history:<dir>
                    val stack = rememberSaveable(saver = listSaver({ it.toList() }, { it.toMutableStateList() })) { mutableStateListOf("home") }
                    val go: (String) -> Unit = { stack += it }
                    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish() }
                    val route = stack.last()
                    val autoRuns = remember { mutableSetOf<String>() }
                    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
                    LaunchedEffect(pendingRoute) { pendingRoute?.let { r -> pendingRoute = null; if (stack.last() != r) go(r) } }
                    // Ask once for notification permission when something starts running in the background.
                    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                    LaunchedEffect(route) {
                        if ((route.startsWith("run:") || route == "repl") && android.os.Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    when {
                        !prefs.onboarded -> Onboarding(prefs) { project -> stack.clear(); stack += "home"; go("ide:" + project.dir.path) }
                        route == "settings" -> SettingsScreen(prefs, go, back)
                        route == "packages" -> PackagesScreen(prefs, go, back)
                        route == "examples" -> LaunchedEffect(route) { tab = Tab.LEARN; back() }
                        // A lesson opens its own project (reused on later visits) in the editor.
                        route.startsWith("learn:") -> LaunchedEffect(route) {
                            val project = withContext(Dispatchers.IO) { lessonProject(context, route.removePrefix("learn:")) }
                            stack.removeAt(stack.lastIndex)
                            if (project != null) go("ide:" + project.dir.path)
                        }
                        route == "repl" -> TerminalScreen(prefs, Term.REPL, "Python REPL", back,
                            start = { it.startRepl(projectsDir(context)) })
                        route.startsWith("ide:") -> key(route) {
                            val (dir, flag) = route.removePrefix("ide:").split("|").let { it[0] to it.getOrElse(1) { "" } }
                            // "|run" fires once: coming back to the editor must not start the program again.
                            IdeScreen(prefs, Project(File(dir)), go, back, autoRun = flag == "run" && autoRuns.add(route))
                        }
                        route.startsWith("run:") -> key(route) {
                            val project = Project(File(route.removePrefix("run:")))
                            TerminalScreen(prefs, "ide:" + project.dir.path, project.name, back,
                                start = { t -> runCommand(context, prefs, project)?.let { (cwd, cmd) -> t.startProgram(cwd, cmd, prefs.clearBeforeRun) } })
                        }
                        route.startsWith("term:") -> key(route) {
                            val dir = File(route.removePrefix("term:"))
                            TerminalScreen(prefs, route, if (route == "term:pip") "pip" else "Terminal", back,
                                start = { it.startShell(if (dir.isDirectory) dir else projectsDir(context)) })
                        }
                        route.startsWith("history:") -> key(route) { HistoryScreen(Project(File(route.removePrefix("history:"))), back) }
                        else -> Shell(prefs, tab, { tab = it }, go)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        private const val SPLASH_MS = 700L
    }
}
