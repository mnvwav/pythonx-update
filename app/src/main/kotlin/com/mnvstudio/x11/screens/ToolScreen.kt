package com.mnvstudio.x11.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.ui.Label

/** Routes for pushed screens other than the display, terminal and diagnostics. */
@Composable
fun ToolScreen(route: String, arg: String, prefs: Prefs, navigate: (String) -> Unit, back: () -> Unit) {
    when (route) {
        "session" -> SessionEditor(arg, prefs, back)
        "session-history" -> SessionHistoryScreen(arg, back)
        else -> Text(route, color = Label, modifier = Modifier.fillMaxSize().wrapContentSize())
    }
}
