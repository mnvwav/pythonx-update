package com.mnvstudio.x11.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.AppLog
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.ui.Bad
import com.mnvstudio.x11.ui.CodeBg
import com.mnvstudio.x11.ui.Confirm
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Line
import com.mnvstudio.x11.ui.Live
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.PrimaryButton
import com.mnvstudio.x11.ui.SecondaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.SurfaceCard
import com.mnvstudio.x11.ui.TopBar
import com.mnvstudio.x11.ui.copyText

/** Tests and repairs the built-in Linux, shows storage use and the app log. */
@Composable
fun DiagnosticsScreen(prefs: Prefs, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var checks by remember { mutableStateOf<List<Linux.Check>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var usage by remember { mutableLongStateOf(-1L) }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(Linux.revision) {
        Linux.check(context)
        Thread { val u = Linux.diskUsage(context); android.os.Handler(android.os.Looper.getMainLooper()).post { usage = u } }.start()
    }

    Column(Modifier.fillMaxSize().background(Page).systemBarsPadding()) {
        TopBar("Diagnostics", "Test, repair and logs", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp)) {
                SurfaceCard {
                    Text("Linux self-test", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Checks each step from proot to the package mirror and fixes what it can: DNS, HTTPS to the mirror, and proot's compatibility mode.",
                        color = Label, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                    checks.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                            Icon(when (c.ok) { true -> Icons.Filled.CheckCircle; false -> Icons.Filled.Error; null -> Icons.Filled.HourglassTop },
                                null, tint = when (c.ok) { true -> Live; false -> Bad; null -> Faint }, modifier = Modifier.size(20.dp))
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(c.name, color = Ink, fontSize = 14.sp)
                                if (c.detail.isNotEmpty()) Text(c.detail, color = if (c.ok == false) Bad else Label, fontSize = 12.sp, fontFamily = Mono)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(if (running) "Testing…" else "Run test & repair", Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow, enabled = !running) {
                        running = true
                        Linux.diagnose(context, onUpdate = { checks = it }) { running = false }
                    }
                    if (prefs.prootNoSeccomp) Text("Compatibility mode is on (PROOT_NO_SECCOMP).", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                }

                SectionTitle("Storage")
                SurfaceCard {
                    Text(when { Linux.state != Linux.State.READY -> "Linux isn't set up"; usage < 0 -> "Measuring…"; else -> "Linux uses ${formatSize(usage)}" },
                        color = Ink, fontSize = 15.sp)
                    Text("Installed apps, packages and files in /root.", color = Label, fontSize = 12.sp)
                    if (Linux.state == Linux.State.READY) {
                        Spacer(Modifier.height(14.dp))
                        SecondaryButton("Reset Linux", Modifier.fillMaxWidth(), icon = Icons.Filled.DeleteForever, color = Bad) { confirmReset = true }
                    }
                }

                SectionTitle("Log")
                val rev = AppLog.revision.intValue
                val lines = remember(rev) { AppLog.snapshot() }
                Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).clip(ShapeM).background(CodeBg).border(1.dp, Line, ShapeM)
                    .verticalScroll(rememberScrollState(), reverseScrolling = true).padding(12.dp)) {
                    if (lines.isEmpty()) Text("Nothing logged yet.", color = Faint, fontFamily = Mono, fontSize = 12.sp)
                    lines.forEach { Text(it, color = if (" E " in it) Bad else Label, fontFamily = Mono, fontSize = 11.sp, lineHeight = 15.sp) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Copy log", Modifier.weight(1f), icon = Icons.Filled.ContentCopy) { copyText(context, AppLog.fullText(), "Log copied") }
                    SecondaryButton("Clear", Modifier.weight(1f)) { AppLog.clear() }
                }
                Text("Copy the log and send it when reporting a problem.", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    if (confirmReset) Confirm("Reset Linux?", "This deletes the built-in Linux with every app and file in it. You can set it up again afterwards.",
        "Delete everything", danger = true, onDismiss = { confirmReset = false }) { Linux.remove(context) { usage = 0 } }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    else -> "${bytes / 1024} KB"
}
