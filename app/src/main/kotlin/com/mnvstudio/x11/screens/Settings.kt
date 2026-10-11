package com.mnvstudio.x11.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.XService
import com.mnvstudio.x11.ui.Chips
import com.mnvstudio.x11.ui.Cyan
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.OptionCard
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.SecondaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.SliderRow
import com.mnvstudio.x11.ui.SurfaceCard
import com.mnvstudio.x11.ui.ToggleRow
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(prefs: Prefs, onReplayOnboarding: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val running = XHolder.server != null
    Column(Modifier.fillMaxSize().background(Page).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Text("Settings", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp)) {
                if (running) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(ShapeM).background(Cyan.copy(alpha = 0.1f))
                        .clickable { XService.stop(context) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = Cyan)
                        Text("Display settings apply the next time the server starts. Tap to stop it now.",
                            color = Ink, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }

                SectionTitle("Display")
                SurfaceCard {
                    Text("Resolution", color = Ink, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Chips(Prefs.RESOLUTIONS, prefs.resolution, label = { if (it == Prefs.RES_NATIVE) "Fit my screen" else it }) { prefs.resolution = it }
                    if (prefs.resolution == Prefs.RES_NATIVE) {
                        Spacer(Modifier.height(10.dp))
                        SliderRow("Detail", "${(prefs.scale * 100).roundToInt()}%", prefs.scale, 0.25f..1f, steps = 2) { prefs.scale = it }
                        Text("Lower detail makes apps larger and faster.", color = Faint, fontSize = 12.sp)
                    }
                    val (w, h) = prefs.screenSize()
                    Text("Screen: $w × $h", color = Label, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Display number", color = Ink, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Chips((0..3).map { "$it" }, "${prefs.display}", label = { ":$it" }) { prefs.display = it.toInt() }
                    Spacer(Modifier.height(16.dp))
                    Text("Orientation", color = Ink, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Chips(listOf("landscape", "portrait", "auto"), prefs.orientation, label = { it.replaceFirstChar(Char::uppercase) }) { prefs.orientation = it }
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("Fill the screen with each app", "New windows open full screen when no window manager runs", prefs.fitWindows) { prefs.fitWindows = it }
                    ToggleRow("Smooth scaling", "Filter the picture when it's scaled to your screen", prefs.smoothScaling) { prefs.smoothScaling = it }
                    ToggleRow("Keep screen on", "While the display is open", prefs.keepScreenOn) { prefs.keepScreenOn = it }
                }

                SectionTitle("Input")
                Group {
                    OptionCard(Icons.Filled.Mouse, "Touchpad", "Drag moves the pointer, tap clicks, two fingers scroll or right-click.",
                        prefs.touchMode == Prefs.MODE_TOUCHPAD) { prefs.touchMode = Prefs.MODE_TOUCHPAD }
                    Spacer(Modifier.height(10.dp))
                    OptionCard(Icons.Filled.TouchApp, "Direct touch", "Touch where you want to click; two fingers scroll.",
                        prefs.touchMode == Prefs.MODE_DIRECT) { prefs.touchMode = Prefs.MODE_DIRECT }
                    Spacer(Modifier.height(8.dp))
                    SliderRow("Pointer speed", "${"%.1f".format(prefs.pointerSpeed)}×", prefs.pointerSpeed, 0.5f..3f) { prefs.pointerSpeed = it }
                    ToggleRow("Extra keys bar", "Esc, Tab, Ctrl, Alt and arrows above the keyboard", prefs.extraKeys) { prefs.extraKeys = it }
                }

                SectionTitle("App")
                SurfaceCard {
                    SecondaryButton("Replay setup", Modifier.fillMaxWidth()) { onReplayOnboarding() }
                    Spacer(Modifier.height(16.dp))
                    Text("MNV X11 1.0", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("X11 core protocol server · bundled misc-fixed fonts (public domain)", color = Faint, fontSize = 12.sp)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) = SurfaceCard(content = content)
