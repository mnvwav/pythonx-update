package com.mnvstudio.x11

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.LaunchedEffect
import com.mnvstudio.x11.screens.DisplayScreen
import com.mnvstudio.x11.screens.GuideScreen
import com.mnvstudio.x11.screens.HomeScreen
import com.mnvstudio.x11.screens.Onboarding
import com.mnvstudio.x11.screens.SettingsScreen
import com.mnvstudio.x11.ui.AppTheme

class MainActivity : ComponentActivity() {
    /** Screen to open from the notification. */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) intent?.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute = it }
        val dark = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(dark, dark)
        val prefs = Prefs(this)
        setContent {
            AppTheme {
                // Routes: home, display, settings, guide.
                val stack = rememberSaveable(saver = listSaver({ it.toList() }, { it.toMutableStateList() })) { mutableStateListOf("home") }
                var onboarding by remember { mutableStateOf(!prefs.onboarded) }
                val go: (String) -> Unit = { if (stack.last() != it) stack += it }
                val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish() }
                LaunchedEffect(pendingRoute) { pendingRoute?.let { r -> pendingRoute = null; if (!onboarding) go(r) } }
                BackHandler(enabled = stack.size > 1 && !onboarding) { back() }

                if (onboarding) {
                    Onboarding(prefs) {
                        onboarding = false
                        XService.start(this)
                    }
                } else {
                    AnimatedContent(stack.last(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "route") { route ->
                        when (route) {
                            "display" -> DisplayScreen(prefs, back)
                            "settings" -> SettingsScreen(prefs, onReplayOnboarding = { stack.clear(); stack += "home"; onboarding = true }, onBack = back)
                            "guide" -> GuideScreen(prefs, back)
                            else -> HomeScreen(prefs, go)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
