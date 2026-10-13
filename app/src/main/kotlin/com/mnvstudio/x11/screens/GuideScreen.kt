package com.mnvstudio.x11.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.ui.CommandBlock
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Live
import com.mnvstudio.x11.ui.Page
import com.mnvstudio.x11.ui.SectionTitle
import com.mnvstudio.x11.ui.StepNumber
import com.mnvstudio.x11.ui.SurfaceCard
import com.mnvstudio.x11.ui.TopBar
import com.mnvstudio.x11.ui.Warn

/** How to run apps, the gestures, and what works today. */
@Composable
fun GuideScreen(prefs: Prefs, onBack: () -> Unit) {
    val d = prefs.display
    Column(Modifier.fillMaxSize().background(Page).systemBarsPadding()) {
        TopBar("Guide", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp)) {
                SectionTitle("Built-in Linux")
                SurfaceCard {
                    Step(1, "Home → Linux apps → Download & set up")
                    Step(2, "Tap an app to install it, tap again to open it")
                    Step(3, "Use Console for anything else, e.g. apk add nano")
                    Text("It's Alpine Linux running in proot: no root, and everything stays inside X11. If installs fail, run Diagnostics.",
                        color = Label, fontSize = 13.sp, lineHeight = 19.sp)
                }

                SectionTitle("Gestures")
                SurfaceCard {
                    Gesture("Trackpad", "Drag to move the pointer · tap = click · long-press then drag = hold and drag · two-finger tap = right click · two-finger drag = scroll")
                    Spacer(Modifier.height(12.dp))
                    Gesture("Direct touch", "Touch to click and drag · two-finger tap = right click · two-finger drag = scroll")
                    Spacer(Modifier.height(12.dp))
                    Gesture("Both", "Pinch to zoom · three-finger tap = middle click (pastes in xterm) · DRAG key holds the left button")
                    Spacer(Modifier.height(12.dp))
                    Gesture("Keyboard & mouse", "Bluetooth and USB keyboards and mice work directly. Ctrl, Alt, Shift and Super on the extra keys bar " +
                        "stay on for one key; double-tap to lock.")
                }

                SectionTitle("Files")
                SurfaceCard {
                    Text("Share any file to X11 from another app (Share → X11). It's copied to /root/Shared in Linux.", color = Label, fontSize = 14.sp, lineHeight = 20.sp)
                }

                SectionTitle("From another computer")
                SurfaceCard {
                    Text("Turn on Settings → Network and add the computer's address. Then on the computer:", color = Label, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    CommandBlock("DISPLAY=<phone-ip>:$d xterm &")
                    Spacer(Modifier.height(10.dp))
                    Text("Over SSH from the phone's Linux: ssh -X user@computer, then run the app.", color = Label, fontSize = 13.sp)
                }

                SectionTitle("What works")
                SurfaceCard {
                    Support(true, "Classic X11 apps: xterm, xedit, xcalc, xclock, xload, twm and similar")
                    Support(true, "Keyboard in any script, mouse, touch, scrolling, multiple windows, shared clipboard")
                    Support(false, "Full desktops (XFCE, LXQt) and GTK/Qt apps like Firefox or GIMP: they need X extensions (RENDER, SHM, XInput) that aren't built yet")
                    Support(false, "Hardware-accelerated OpenGL")
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
        Text(if (ok) "✓" else "–", color = if (ok) Live else Warn, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
        Text(text, color = Label, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
