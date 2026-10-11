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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.ui.Bad
import com.mnvstudio.x11.TermuxCommand
import com.mnvstudio.x11.ui.BrandGradient
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.CardHigh
import com.mnvstudio.x11.ui.Chips
import com.mnvstudio.x11.ui.CommandBlock
import com.mnvstudio.x11.ui.Cyan
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.Good
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Line
import com.mnvstudio.x11.ui.Logo
import com.mnvstudio.x11.ui.OptionCard
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.PrimaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.StepNumber
import com.mnvstudio.x11.ui.SurfaceCard
import com.mnvstudio.x11.ui.ToggleRow
import com.mnvstudio.x11.ui.Violet
import kotlinx.coroutines.launch

private const val PAGES = 6

/** First-run setup: what the app does, how to connect apps, display and input choices, permissions. */
@Composable
fun Onboarding(prefs: Prefs, onDone: () -> Unit) {
    val pager = rememberPagerState(pageCount = { PAGES })
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    Box(Modifier.fillMaxSize().background(Page)) {
        // A soft glow behind the content.
        Box(Modifier.fillMaxWidth().height(320.dp).background(Brush.verticalGradient(listOf(Color(0xFF1C1645), Page))))
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Step ${page + 1} of $PAGES", color = Faint, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (page < PAGES - 1) Text("Skip", color = Label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(CircleShape).clickable { scope.launch { pager.animateScrollToPage(PAGES - 1) } }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { p ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = 560.dp)) {
                        when (p) {
                            0 -> Welcome()
                            1 -> HowItWorks()
                            2 -> SetupApps(prefs)
                            3 -> DisplaySetup(prefs)
                            4 -> Permissions()
                            else -> Ready(prefs)
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
                } else PrimaryButton("Start X server", icon = Icons.Filled.PlayArrow) { prefs.onboarded = true; onDone() }
            }
        }
    }
}

@Composable
private fun Dots(page: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGES) { i ->
            val w by animateDpAsState(if (i == page) 22.dp else 8.dp, label = "dot")
            val c by animateColorAsState(if (i == page) Violet else Line, label = "dotColor")
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
    Spacer(Modifier.height(40.dp))
    Logo(112.dp, Modifier.align(Alignment.CenterHorizontally))
    Spacer(Modifier.height(32.dp))
    Text("MNV X11", color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
    Text("A pocket X11 display server", color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
    Text(
        "Run Linux desktop apps from Termux or a proot distro and use them right here, with touch, mouse and keyboard.",
        color = Label, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 20.dp).align(Alignment.CenterHorizontally),
    )
    Spacer(Modifier.height(32.dp))
    Feature(Icons.Filled.Tv, "Built-in display", "Your phone becomes the screen for X11 programs.")
    Feature(Icons.Filled.TouchApp, "Touch friendly", "Trackpad gestures, direct touch, or a real mouse.")
    Feature(Icons.Filled.Terminal, "Works with Termux", "Point DISPLAY at :0 and launch apps as usual.")
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(ShapeM).background(Card).border(1.dp, Line, ShapeM), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Violet, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(body, color = Label, fontSize = 13.sp)
        }
    }
}

@Composable
private fun HowItWorks() {
    Title("How it works", "X11 apps don't draw to the screen themselves: they talk to a display server. This app is that server.")
    val steps = listOf(
        Triple(Icons.Filled.PlayArrow, "Start the server", "It runs in the background with a small notification, ready for apps to connect."),
        Triple(Icons.Filled.Terminal, "Launch an app", "In Termux or a Linux distro, set DISPLAY=:0 and start any X11 program."),
        Triple(Icons.Filled.Computer, "Use it here", "Open the display to see the app. Tap, drag and type to control it."),
    )
    steps.forEachIndexed { i, (icon, title, body) ->
        SurfaceCard(Modifier.padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepNumber(i + 1)
                Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
                Icon(icon, null, tint = Faint)
            }
            Text(body, color = Label, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun SetupApps(prefs: Prefs) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Linux.check(context) }
    Title("Linux apps, built in", "This app carries its own small Linux. Download it once and install apps with a tap.")
    SurfaceCard(Modifier.padding(bottom = 12.dp)) {
        when (Linux.state) {
            Linux.State.READY -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Good)
                Text("Linux is ready", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
            }
            Linux.State.INSTALLING -> {
                Text(Linux.status, color = Label, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                LinearProgressIndicator(progress = { Linux.progress }, modifier = Modifier.fillMaxWidth().clip(CircleShape), color = Violet, trackColor = CardHigh)
            }
            else -> {
                Text("Alpine Linux · about 4 MB", color = Ink, fontWeight = FontWeight.SemiBold)
                Text("You can keep going while it downloads.", color = Label, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                if (Linux.state == Linux.State.FAILED) Text(Linux.status, color = Bad, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                PrimaryButton("Download Linux", Modifier.fillMaxWidth(), enabled = Linux.prootAvailable(context)) { Linux.install(context) }
            }
        }
    }
    SectionTitle("Already use Termux?")
    SurfaceCard {
        CommandBlock(TermuxCommand.install, caption = "Install the mnvx11 command")
        Spacer(Modifier.height(14.dp))
        CommandBlock("mnvx11 :${prefs.display} xterm", caption = "Then start apps from Termux")
    }
}

@Composable
private fun DisplaySetup(prefs: Prefs) {
    Title("Display & input", "You can change all of this later in Settings.")
    SectionTitle("Resolution")
    Chips(Prefs.RESOLUTIONS, prefs.resolution, label = { if (it == Prefs.RES_NATIVE) "Fit my screen" else it }) { prefs.resolution = it }
    val (w, h) = prefs.screenSize()
    Text("Screen will be $w × $h", color = Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
    Spacer(Modifier.height(20.dp))
    SectionTitle("Touch input")
    OptionCard(Icons.Filled.Mouse, "Trackpad", "Drag to move a pointer, tap to click, two fingers to scroll or right-click.",
        prefs.touchMode == Prefs.MODE_TOUCHPAD) { prefs.touchMode = Prefs.MODE_TOUCHPAD }
    Spacer(Modifier.height(10.dp))
    OptionCard(Icons.Filled.TouchApp, "Direct touch", "Touch exactly where you want to click, like a touchscreen laptop.",
        prefs.touchMode == Prefs.MODE_DIRECT) { prefs.touchMode = Prefs.MODE_DIRECT }
    Spacer(Modifier.height(12.dp))
    SurfaceCard(padding = 16.dp) {
        ToggleRow("Fill the screen with each app", "Without a window manager, new windows open full screen.", prefs.fitWindows) { prefs.fitWindows = it }
    }
}

@Composable
private fun Permissions() {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    Title("Stay running", "Android stops background apps to save power. A notification keeps the server alive while you switch to Termux.")
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(ShapeM).background(if (granted) Good.copy(alpha = 0.15f) else Violet.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center) {
                Icon(if (granted) Icons.Filled.CheckCircle else Icons.Filled.Notifications, null, tint = if (granted) Good else Violet)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("Notifications", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(if (granted) "Allowed. You're all set." else "Shows that the server is on, with a Stop button.", color = Label, fontSize = 13.sp)
            }
        }
        if (!granted) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Allow notifications", Modifier.fillMaxWidth()) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
    Text("Tip: if your phone has aggressive battery saving, set this app to \"Unrestricted\" battery use in system settings.",
        color = Faint, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun ColumnScope.Ready(prefs: Prefs) {
    Spacer(Modifier.height(48.dp))
    Box(Modifier.size(120.dp).clip(CircleShape).background(BrandGradient).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(56.dp))
    }
    Text("You're ready", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 28.dp))
    Text("Open Linux apps from the home screen, or launch from Termux with", color = Label, fontSize = 15.sp, textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
    Spacer(Modifier.height(16.dp))
    CommandBlock("DISPLAY=:${prefs.display} xterm &")
    Spacer(Modifier.height(24.dp))
    val (w, h) = prefs.screenSize()
    SurfaceCard(padding = 16.dp) {
        Summary("Display", ":${prefs.display}")
        Summary("Resolution", "$w × $h")
        Summary("Input", if (prefs.touchMode == Prefs.MODE_DIRECT) "Direct touch" else "Trackpad")
    }
}

@Composable
private fun Summary(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(k, color = Label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(v, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
