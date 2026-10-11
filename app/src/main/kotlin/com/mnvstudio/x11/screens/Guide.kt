package com.mnvstudio.x11.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.ui.CommandBlock
import com.mnvstudio.x11.ui.Good
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.StepNumber
import com.mnvstudio.x11.ui.SurfaceCard
import com.mnvstudio.x11.ui.Warnish

/** How to connect apps, the touch gestures, and what works today. */
@Composable
fun GuideScreen(prefs: Prefs, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val d = prefs.display
    Column(Modifier.fillMaxSize().background(Page).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Text("Guide", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp)) {
                SectionTitle("Termux")
                SurfaceCard {
                    Step(1, "Install the X11 repository and an app")
                    CommandBlock("pkg install x11-repo && pkg install xterm")
                    Spacer(Modifier.height(16.dp))
                    Step(2, "Tell apps where the display is")
                    CommandBlock("export DISPLAY=:$d")
                    Spacer(Modifier.height(16.dp))
                    Step(3, "Start the server here, then launch")
                    CommandBlock("xterm &")
                }

                SectionTitle("proot-distro (Debian, Ubuntu…)")
                SurfaceCard {
                    CommandBlock("proot-distro install debian && proot-distro login debian", caption = "In Termux")
                    Spacer(Modifier.height(14.dp))
                    CommandBlock("apt update && apt install -y x11-apps xterm", caption = "Inside Debian")
                    Spacer(Modifier.height(14.dp))
                    CommandBlock("DISPLAY=:$d xcalc &")
                }

                SectionTitle("Touch gestures")
                SurfaceCard {
                    Gesture("Touchpad", "One-finger drag moves the pointer · tap = left click · long-press then drag = hold and drag · " +
                        "two-finger tap = right click · two-finger drag = scroll")
                    Spacer(Modifier.height(12.dp))
                    Gesture("Direct touch", "Touch to click and drag · two-finger tap = right click · two-finger drag = scroll")
                    Spacer(Modifier.height(12.dp))
                    Gesture("Mouse & keyboard", "Bluetooth or USB mice and keyboards work directly. Ctrl and Alt on the extra keys bar stay on for one key.")
                }

                SectionTitle("What works")
                SurfaceCard {
                    Support(true, "Classic X11 apps such as xterm, xeyes, xclock and xcalc")
                    Support(true, "Keyboard, mouse, touch, scrolling and multiple windows")
                    Support(false, "Full desktops (XFCE, LXDE) and GTK/Qt apps need X extensions (RENDER, SHM, XInput) that aren't built yet")
                    Support(false, "OpenGL and hardware acceleration")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        StepNumber(n)
        Text(text, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Gesture(title: String, body: String) {
    Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Text(body, color = Label, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun Support(ok: Boolean, text: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Text(if (ok) "✓" else "–", color = if (ok) Good else Warnish, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 12.dp))
        Text(text, color = Label, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
