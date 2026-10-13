package com.mnvstudio.x11

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Mono
import java.io.File

/** Crash reports: written when the app crashes, offered (copy or share) on the next launch, then deleted. */
object CrashReport {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")

    fun save(c: Context, thread: Thread, e: Throwable) {
        val text = buildString {
            appendLine("X11 crash report")
            appendLine("Time: ${java.util.Date()}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.SUPPORTED_ABIS.firstOrNull()}")
            appendLine("App: ${runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull()}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine(e.stackTraceToString())
            appendLine("Recent log:")
            AppLog.snapshot().takeLast(40).forEach { appendLine(it) }
        }
        file(c).writeText(text)
    }

    fun pending(c: Context): String? = file(c).takeIf { it.exists() }?.readText()
    fun clear(c: Context) { file(c).delete() }
}

/** Shown once after a crash. */
@Composable
fun CrashDialog() {
    val context = LocalContext.current
    var text by remember { mutableStateOf(CrashReport.pending(context)) }
    val t = text ?: return
    fun done() { CrashReport.clear(context); text = null }
    AlertDialog(onDismissRequest = { done() }, containerColor = Card,
        title = { Text("X11 crashed last time", color = Ink) },
        text = {
            Column {
                Text("Sorry about that. Sharing this report helps get it fixed.", color = Label, fontSize = 13.sp)
                Text(t.lines().take(14).joinToString("\n"), color = Label, fontSize = 10.sp, fontFamily = Mono,
                    modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
            }
        },
        confirmButton = {
            TextButton({
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Share crash report")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                done()
            }) { Text("Share") }
        },
        dismissButton = {
            TextButton({
                (context.getSystemService(android.content.ClipboardManager::class.java)).setPrimaryClip(android.content.ClipData.newPlainText("X11 crash", t))
                done()
            }) { Text("Copy") }
        })
}
