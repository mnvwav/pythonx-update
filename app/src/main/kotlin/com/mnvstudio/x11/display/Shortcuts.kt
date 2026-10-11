package com.mnvstudio.x11.display

/**
 * Keyboard shortcuts across the app, the built-in window manager and common Linux apps, so conflicts can be shown
 * before they surprise you.
 */
object Shortcuts {
    data class Shortcut(val keys: String, val what: String, val owner: String)

    val APP = listOf(
        Shortcut("Ctrl+Alt+K", "Show keyboard", "X11"), Shortcut("Ctrl+Alt+W", "Windows & desktops", "X11"),
        Shortcut("Ctrl+Alt+M", "Display menu", "X11"), Shortcut("Ctrl+Alt+Q", "Leave the display", "X11"),
        Shortcut("Ctrl+Alt+L", "Release the mouse lock", "X11"),
    )

    /** Shortcuts common Linux apps use, which the app or window manager would take away. */
    val LINUX = listOf(
        Shortcut("Alt+Tab", "Switch windows", "Desktops (XFCE, LXQt)"), Shortcut("Alt+F4", "Close window", "Most apps"),
        Shortcut("Ctrl+Alt+T", "Open terminal", "Desktops"), Shortcut("Ctrl+Alt+L", "Lock screen", "Desktops"),
        Shortcut("Ctrl+Alt+Del", "Log out", "Desktops"), Shortcut("Super+Left", "Snap left", "Desktops"),
        Shortcut("Ctrl+W", "Close tab", "Browsers, editors"), Shortcut("Ctrl+Alt+M", "Toggle menu bar", "Some editors"),
    )

    /** Keys used by more than one owner: the first owner (X11 or the window manager) wins. */
    fun conflicts(wm: List<Pair<String, String>>, appShortcutsOn: Boolean): List<Pair<Shortcut, List<Shortcut>>> {
        val ours = (if (appShortcutsOn) APP else emptyList()) + wm.map { Shortcut(it.first, it.second, "Window manager") }
        fun norm(k: String) = k.lowercase().replace(" ", "")
        return ours.mapNotNull { s ->
            val clash = LINUX.filter { norm(it.keys) == norm(s.keys) } + ours.filter { it !== s && norm(it.keys) == norm(s.keys) }
            if (clash.isEmpty()) null else s to clash
        }
    }
}
