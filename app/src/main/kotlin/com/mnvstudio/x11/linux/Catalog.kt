package com.mnvstudio.x11.linux

/** An app the user can install and launch with one tap. */
data class LinuxApp(
    val id: String,
    val name: String,
    val description: String,
    /** Alpine packages to install. */
    val packages: List<String>,
    /** Binary whose presence means the app is installed. */
    val binary: String,
    /** Shell command that starts it on the X display. */
    val command: String,
    val category: String,
    /** Needs X extensions this server doesn't have yet (RENDER, SHM, XInput…): listed, but marked as not working. */
    val needsExtensions: Boolean = false,
    /** A terminal program: opened inside a terminal window. */
    val terminal: Boolean = false,
)

/** Apps known to work on this server's core X11 (no RENDER/GL extensions yet). */
object Catalog {
    val apps = listOf(
        LinuxApp("xterm", "Terminal", "A full Linux shell in a window", listOf("xterm"), "xterm", "xterm -fn 9x15 -geometry 100x32", "Tools"),
        LinuxApp("xedit", "Text editor", "Edit notes, code and config files", listOf("xedit"), "xedit", "xedit", "Tools"),
        LinuxApp("xcalc", "Calculator", "Scientific calculator", listOf("xcalc"), "xcalc", "xcalc", "Tools"),
        LinuxApp("twm", "Window manager", "Move, resize and stack windows like a desktop", listOf("twm"), "twm", "twm", "Desktop"),
        LinuxApp("xclock", "Clock", "Analog clock", listOf("xclock"), "xclock", "xclock -update 1", "Desktop"),
        LinuxApp("xload", "System load", "Live CPU load graph", listOf("xload"), "xload", "xload", "Desktop"),
        LinuxApp("xeyes", "Eyes", "Eyes that follow the pointer, a quick display test", listOf("xeyes"), "xeyes", "xeyes", "Fun"),
        LinuxApp("xlogo", "X logo", "The classic X logo, another quick test", listOf("xlogo"), "xlogo", "xlogo", "Fun"),
        LinuxApp("xmag", "Magnifier", "Zoom into part of the screen", listOf("xmag"), "xmag", "xmag", "Tools"),
        LinuxApp("xfontsel", "Font browser", "Look through the server's fonts", listOf("xfontsel"), "xfontsel", "xfontsel", "Tools"),
        LinuxApp("htop", "Task monitor", "htop: interactive process viewer", listOf("htop", "xterm"), "htop", "xterm -fn 9x15 -e htop", "Tools", terminal = true),
        LinuxApp("nano", "nano", "Simple terminal text editor", listOf("nano", "xterm"), "nano", "xterm -fn 9x15 -e nano", "Developer", terminal = true),
        LinuxApp("vim", "Vim", "Powerful terminal text editor", listOf("vim", "xterm"), "vim", "xterm -fn 9x15 -e vim", "Developer", terminal = true),
        LinuxApp("python3", "Python", "Python 3 interpreter", listOf("python3", "xterm"), "python3", "xterm -fn 9x15 -e python3", "Developer", terminal = true),
        LinuxApp("mc", "Midnight Commander", "Two-pane file manager in a terminal", listOf("mc", "xterm"), "mc", "xterm -fn 9x15 -e mc", "Developer", terminal = true),
        LinuxApp("xfce4", "XFCE desktop", "Full desktop environment", listOf("xfce4", "xfce4-terminal"), "startxfce4", "startxfce4", "Desktops", needsExtensions = true),
        LinuxApp("lxqt", "LXQt desktop", "Lightweight Qt desktop", listOf("lxqt"), "startlxqt", "startlxqt", "Desktops", needsExtensions = true),
        LinuxApp("firefox", "Firefox", "Web browser", listOf("firefox"), "firefox", "firefox", "Internet", needsExtensions = true),
        LinuxApp("gimp", "GIMP", "Image editor", listOf("gimp"), "gimp", "gimp", "Graphics", needsExtensions = true),
    )

    val categories: List<String> get() = apps.map { it.category }.distinct()

    fun byId(id: String) = apps.firstOrNull { it.id == id }
}
