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
        "files" -> FilesScreen(arg, navigate, back)
        "editor" -> EditorScreen(arg, back)
        "processes" -> ProcessesScreen(back)
        "packages" -> PackagesScreen(back)
        "profiles" -> ProfilesScreen(back)
        "backup" -> BackupScreen(back)
        "environment", "users" -> EnvironmentScreen(navigate, back)
        "maintenance" -> MaintenanceScreen(back)
        "dev" -> DevToolsScreen(navigate, back)
        "windows" -> WindowsScreen(back)
        "inspector" -> InspectorScreen(back)
        "input" -> InputScreen(back)
        "toolbox" -> com.mnvstudio.x11.tools.ToolboxScreen(prefs, navigate, back)
        "tool" -> com.mnvstudio.x11.tools.ToolRunScreen(prefs, arg, back)
        "browser" -> com.mnvstudio.x11.tools.BrowserScreen(prefs, arg, navigate, back)
        "downloads" -> com.mnvstudio.x11.tools.DownloadsScreen(prefs, back)
        "share" -> com.mnvstudio.x11.tools.ShareScreen(back)
        "markdown" -> com.mnvstudio.x11.tools.MarkdownScreen(back)
        else -> Text(route, color = Label, modifier = Modifier.fillMaxSize().wrapContentSize())
    }
}
