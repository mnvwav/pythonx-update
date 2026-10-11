package com.mnvstudio.x11.screens

import android.os.Build
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Launcher
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.XService
import com.mnvstudio.x11.ui.Accent
import com.mnvstudio.x11.ui.Chips
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.OptionCard
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.SecondaryButton
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.ShapeM
import com.mnvstudio.x11.ui.SliderRow
import com.mnvstudio.x11.ui.SurfaceCard
import com.mnvstudio.x11.ui.ToggleRow
import com.mnvstudio.x11.ui.TopBar
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(prefs: Prefs, onReplayOnboarding: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val running = XHolder.server != null
    Column(Modifier.fillMaxSize().background(Page).systemBarsPadding()) {
        TopBar("Settings", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp)) {
                if (running) {
                    Row(Modifier.fillMaxWidth().clip(ShapeM).background(Accent.copy(alpha = 0.1f)).clickable { XService.stop(context) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = Accent)
                        Text("Display, network and window settings apply the next time the display starts. Tap to stop it now.",
                            color = Ink, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }

                SectionTitle("Display")
                SurfaceCard {
                    FieldLabel("Resolution")
                    Chips(Prefs.RESOLUTIONS, prefs.resolution, label = { if (it == Prefs.RES_NATIVE) "Fit my screen" else it }) { prefs.resolution = it }
                    if (prefs.resolution == Prefs.RES_NATIVE) {
                        Spacer(Modifier.height(8.dp))
                        SliderRow("Detail", "${(prefs.scale * 100).roundToInt()}%", prefs.scale, 0.25f..1f, steps = 2) { prefs.scale = it }
                        Hint("Lower detail makes apps larger and faster.")
                    }
                    if (prefs.resolution == Prefs.RES_CUSTOM) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NumberField("Width", prefs.customW, Modifier.weight(1f)) { prefs.customW = it }
                            Text("×", color = Label, modifier = Modifier.padding(horizontal = 10.dp))
                            NumberField("Height", prefs.customH, Modifier.weight(1f)) { prefs.customH = it }
                        }
                    }
                    val (w, h) = prefs.screenSize()
                    Hint("Screen: $w × $h")
                    FieldLabel("Fit to phone")
                    Chips(listOf("fit", "stretch", "pixel"), prefs.fitMode, label = { when (it) { "fit" -> "Fit"; "stretch" -> "Stretch"; else -> "1:1 pixels" } }) { prefs.fitMode = it }
                    FieldLabel("Text size (DPI)")
                    Chips(listOf("0", "96", "120", "144", "192", "240"), "${prefs.dpiSetting}", label = { if (it == "0") "Auto (${prefs.dpi()})" else it }) { prefs.dpiSetting = it.toInt() }
                    Hint("Higher makes text bigger in apps that follow Xft.dpi.")
                    FieldLabel("Frame rate limit")
                    Chips(listOf("30", "60", "120"), "${prefs.maxFps}", label = { "$it fps" }) { prefs.maxFps = it.toInt() }
                    FieldLabel("Orientation")
                    Chips(listOf("landscape", "portrait", "auto"), prefs.orientation, label = { it.replaceFirstChar(Char::uppercase) }) { prefs.orientation = it }
                    FieldLabel("Display number")
                    Chips((0..3).map { "$it" }, "${prefs.display}", label = { ":$it" }) { prefs.display = it.toInt() }
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("Smooth scaling", "Filter the picture when it's scaled", prefs.smoothScaling) { prefs.smoothScaling = it }
                    ToggleRow("Use the notch area", "Draw behind the camera cutout", prefs.drawInCutout) { prefs.drawInCutout = it }
                    ToggleRow("Keep screen on", "While the display is open", prefs.keepScreenOn) { prefs.keepScreenOn = it }
                    ToggleRow("Performance overlay", "Show frames per second on the display", prefs.perfOverlay) { prefs.perfOverlay = it }
                }

                SectionTitle("Windows")
                SurfaceCard {
                    ToggleRow("Desktop mode", "Start a window manager so windows can be moved and resized (installs twm from Linux apps)", prefs.desktopMode) { prefs.desktopMode = it }
                    ToggleRow("Full-screen apps", "Without a window manager, each new window fills the screen", prefs.fitWindows, enabled = !prefs.desktopMode) { prefs.fitWindows = it }
                }

                SectionTitle("Input")
                SurfaceCard {
                    OptionCard(Icons.Filled.Mouse, "Trackpad", "Drag moves the pointer, tap clicks, two fingers scroll or right-click.",
                        prefs.touchMode == Prefs.MODE_TOUCHPAD) { prefs.touchMode = Prefs.MODE_TOUCHPAD }
                    Spacer(Modifier.height(10.dp))
                    OptionCard(Icons.Filled.TouchApp, "Direct touch", "Touch where you want to click; two fingers scroll.",
                        prefs.touchMode == Prefs.MODE_DIRECT) { prefs.touchMode = Prefs.MODE_DIRECT }
                    Spacer(Modifier.height(8.dp))
                    SliderRow("Pointer speed", "${"%.1f".format(prefs.pointerSpeed)}×", prefs.pointerSpeed, 0.5f..3f) { prefs.pointerSpeed = it }
                    SliderRow("Cursor size", "${"%.1f".format(prefs.cursorScale)}×", prefs.cursorScale, 0.6f..3f) { prefs.cursorScale = it }
                    ToggleRow("Extra keys bar", "Esc, Tab, Ctrl, Alt, Super, arrows and F1–F12 above the keyboard", prefs.extraKeys) { prefs.extraKeys = it }
                    FieldLabel("Extra keys layout")
                    OutlinedTextField(prefs.keyLayout, { prefs.keyLayout = it.uppercaseKeys() }, Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Ink), shape = ShapeM)
                    Hint("Keys: ESC TAB CTRL ALT SHIFT SUPER UP DOWN LEFT RIGHT HOME END PGUP PGDN INS DEL BKSP ENTER F1–F12 FN KBD RCLICK DRAG. " +
                        "Other text is typed as is. Tap a modifier for one key, double-tap to lock it.")
                    Text("Reset layout", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp).clickable { prefs.keyLayout = Prefs.DEFAULT_KEYS })
                }

                SectionTitle("Clipboard")
                SurfaceCard {
                    ToggleRow("Share clipboard", "Copy and paste between Android and Linux apps", prefs.clipboardSync) { prefs.clipboardSync = it }
                    ToggleRow("Include mouse selections", "Also copy text you select in Linux apps", prefs.syncPrimary, enabled = prefs.clipboardSync) { prefs.syncPrimary = it }
                }

                SectionTitle("Network")
                SurfaceCard {
                    ToggleRow("Allow other devices", "Accept X11 apps from computers on your network (port ${6000 + prefs.display})", prefs.lanAccess) { prefs.lanAccess = it }
                    if (prefs.lanAccess) {
                        FieldLabel("Allowed addresses")
                        OutlinedTextField(prefs.allowList, { prefs.allowList = it }, Modifier.fillMaxWidth(), shape = ShapeM,
                            placeholder = { Text("192.168.1.20, 192.168.1.*", color = Faint, fontFamily = Mono) },
                            textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Ink))
                        Hint("Only these can connect; others are refused and noted in Diagnostics. On the computer: DISPLAY=<phone IP>:${prefs.display}")
                    }
                }

                SectionTitle("Power")
                SurfaceCard {
                    var exempt by remember { mutableStateOf(Launcher.ignoringBatteryOptimizations(context)) }
                    ToggleRow("Run without battery limits", if (exempt) "Allowed: X11 keeps running in the background" else "Recommended so the display survives switching apps",
                        exempt, enabled = !exempt) { Launcher.requestBatteryExemption(context); exempt = true }
                    ToggleRow("Keep the CPU awake", "For long installs or builds with the screen off; uses more battery", prefs.wakeLock) { prefs.wakeLock = it }
                    FieldLabel("Stop when idle")
                    Chips(listOf("0", "10", "30", "60"), "${prefs.idleStopMinutes}", label = { if (it == "0") "Never" else "$it min" }) { prefs.idleStopMinutes = it.toInt() }
                    Hint("Stops the display after this long with no apps connected.")
                }

                SectionTitle("Look & accessibility")
                SurfaceCard {
                    ToggleRow("Wallpaper colors", if (Build.VERSION.SDK_INT >= 31) "Use Material You colors from your wallpaper" else "Needs Android 12 or newer",
                        prefs.dynamicColor, enabled = Build.VERSION.SDK_INT >= 31) { prefs.dynamicColor = it }
                    ToggleRow("High contrast", "Brighter text and borders", prefs.highContrast) { prefs.highContrast = it }
                    ToggleRow("Vibrate on taps", null, prefs.haptics) { prefs.haptics = it }
                }

                SectionTitle("App")
                SurfaceCard {
                    SecondaryButton("Replay setup", Modifier.fillMaxWidth()) { onReplayOnboarding() }
                    Spacer(Modifier.height(16.dp))
                    Text("X11 1.0", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("No tracking, no accounts. Works offline once Linux is set up. Bundled fonts: misc-fixed (public domain); proot (GPL-2.0).",
                        color = Faint, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ColumnScope.FieldLabel(text: String) {
    Text(text, color = Ink, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp, bottom = 10.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
}

/** Keeps known key names upper-case while leaving typed text (like | or ~) alone. */
private fun String.uppercaseKeys() = split(" ").joinToString(" ") { if (it.length > 1 && it.all(Char::isLetterOrDigit)) it.uppercase() else it }

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf("$value") }
    OutlinedTextField(
        text, { t -> text = t.filter(Char::isDigit).take(4); text.toIntOrNull()?.let(onChange) }, modifier,
        label = { Text(label) }, singleLine = true, shape = ShapeM,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(fontFamily = Mono, color = Ink),
    )
}
