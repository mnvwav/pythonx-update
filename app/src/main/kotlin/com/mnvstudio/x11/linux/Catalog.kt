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
    )

    fun byId(id: String) = apps.firstOrNull { it.id == id }
}
