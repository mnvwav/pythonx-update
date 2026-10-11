package com.mnvstudio.x11.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mnvstudio.x11.Launcher
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.ui.Accent
import com.mnvstudio.x11.ui.AccentSoft
import com.mnvstudio.x11.ui.Bad
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.CardHigh
import com.mnvstudio.x11.ui.Chips
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.HeaderGlow
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Line
import com.mnvstudio.x11.ui.Live
import com.mnvstudio.x11.ui.Logo
import com.mnvstudio.x11.ui.OptionCard
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.PrimaryButton
import com.mnvstudio.x11.ui.SecondaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.SurfaceCard
import kotlinx.coroutines.launch

private const val PAGES = 4

/** First run: what X11 does, set up Linux, display & input, permissions. */
@Composable
fun Onboarding(prefs: Prefs, onDone: () -> Unit) {
    val pager = rememberPagerState(pageCount = { PAGES })
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    Box(Modifier.fillMaxSize().background(Page)) {
        Box(Modifier.fillMaxWidth().height(300.dp).background(HeaderGlow))
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${page + 1} of $PAGES", color = Faint, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (page < PAGES - 1) Text("Skip", color = Label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(CircleShape).clickable { scope.launch { pager.animateScrollToPage(PAGES - 1) } }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { p ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = 560.dp)) {
                        when (p) {
                            0 -> Welcome()
                            1 -> LinuxSetup()
                            2 -> DisplaySetup(prefs)
                            else -> Permissions()
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Dots(page)
                Spacer(Modifier.weight(1f))
                if (page > 0) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(CardHigh).border(1.dp, Line, CircleShape)
                        .clickable { scope.launch { pager.animateScrollToPage(page - 1) } }, contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                if (page < PAGES - 1) PrimaryButton(if (page == 0) "Get started" else "Next", icon = Icons.AutoMirrored.Filled.ArrowForward) {
                    scope.launch { pager.animateScrollToPage(page + 1) }
                } else PrimaryButton("Start X11", icon = Icons.Filled.PlayArrow) { prefs.onboarded = true; onDone() }
            }
        }
    }
}

@Composable
private fun Dots(page: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGES) { i ->
            val w by animateDpAsState(if (i == page) 22.dp else 8.dp, label = "dot")
            val c by animateColorAsState(if (i == page) Accent else Line, label = "dotColor")
            Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(c))
        }
    }
}

@Composable
private fun Title(text: String, body: String) {
    Text(text, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp, modifier = Modifier.padding(top = 12.dp))
    Text(body, color = Label, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
}

@Composable
private fun ColumnScope.Welcome() {
    Spacer(Modifier.height(36.dp))
    Logo(120.dp, Modifier.align(Alignment.CenterHorizontally))
    Spacer(Modifier.height(28.dp))
    Text("X11", color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
    Text("Linux apps on your phone", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
    Text("A display server and a small Linux, built in. Install apps with a tap and use them with touch, mouse and keyboard.",
        color = Label, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 18.dp).align(Alignment.CenterHorizontally))
    Spacer(Modifier.height(28.dp))
    Feature(Icons.Filled.Apps, "Apps in one tap", "Terminal, editor, calculator and more, no other apps needed.")
    Feature(Icons.Filled.TouchApp, "Made for touch", "Trackpad gestures, direct touch, pinch to zoom, extra keys.")
    Feature(Icons.Filled.ContentPaste, "Works with Android", "Shared clipboard, file sharing, shortcuts on your home screen.")
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(ShapeM).background(Card).border(1.dp, Line, ShapeM), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(body, color = Label, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LinuxSetup() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Linux.check(context) }
    Title("Get Linux", "X11 carries its own small Linux. Download it once (about 4 MB); you can keep going while it loads.")
    SurfaceCard {
        when (Linux.state) {
            Linux.State.READY -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Live)
                Text("Linux is ready", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
            }
            Linux.State.INSTALLING -> {
                Text(Linux.status, color = Label, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                LinearProgressIndicator(progress = { Linux.progress }, modifier = Modifier.fillMaxWidth().clip(CircleShape), color = Accent, trackColor = CardHigh)
            }
            else -> {
                Text("Alpine Linux", color = Ink, fontWeight = FontWeight.SemiBold)
                Text("Runs without root, stays inside the app.", color = Label, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                if (Linux.state == Linux.State.FAILED) Text(Linux.status, color = Bad, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                if (!Linux.prootAvailable(context)) Text("This build doesn't include Linux support (proot). You can still use X11 with Termux.",
                    color = Bad, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                PrimaryButton("Download Linux", Modifier.fillMaxWidth(), enabled = Linux.prootAvailable(context)) { Linux.install(context) }
            }
        }
    }
    Text("Prefer Termux? Skip this; the guide shows how to connect Termux apps.", color = Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
}

@Composable
private fun DisplaySetup(prefs: Prefs) {
    Title("Display & input", "You can change these any time in Settings.")
    SectionTitle("Resolution")
    Chips(Prefs.RESOLUTIONS.filter { it != Prefs.RES_CUSTOM }, prefs.resolution, label = { if (it == Prefs.RES_NATIVE) "Fit my screen" else it }) { prefs.resolution = it }
    val (w, h) = prefs.screenSize()
    Text("Screen will be $w × $h", color = Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
    SectionTitle("Touch")
    OptionCard(Icons.Filled.Mouse, "Trackpad", "Drag to move a pointer, tap to click. Precise, like a laptop.", prefs.touchMode == Prefs.MODE_TOUCHPAD) { prefs.touchMode = Prefs.MODE_TOUCHPAD }
    Spacer(Modifier.height(10.dp))
    OptionCard(Icons.Filled.TouchApp, "Direct touch", "Touch exactly where you want to click.", prefs.touchMode == Prefs.MODE_DIRECT) { prefs.touchMode = Prefs.MODE_DIRECT }
}

@Composable
private fun Permissions() {
    val context = LocalContext.current
    var notif by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    var battery by remember { mutableStateOf(Launcher.ignoringBatteryOptimizations(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notif = it }
    Title("Keep it running", "So your apps stay open while you switch to other apps.")
    Permission(Icons.Filled.Notifications, "Notifications", "Shows that X11 is on, with Open and Stop buttons.", notif) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    Spacer(Modifier.height(12.dp))
    Permission(Icons.Filled.BatteryChargingFull, "No battery limits", "Stops Android from closing X11 in the background.", battery) {
        Launcher.requestBatteryExemption(context); battery = true
    }
}

@Composable
private fun Permission(icon: ImageVector, title: String, body: String, granted: Boolean, request: () -> Unit) {
    SurfaceCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(ShapeM).background(if (granted) Live.copy(alpha = 0.15f) else AccentSoft), contentAlignment = Alignment.Center) {
                Icon(if (granted) Icons.Filled.CheckCircle else icon, null, tint = if (granted) Live else Accent)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(if (granted) "Done" else body, color = Label, fontSize = 13.sp)
            }
        }
        if (!granted) { Spacer(Modifier.height(14.dp)); SecondaryButton("Allow", Modifier.fillMaxWidth(), onClick = request) }
    }
}
