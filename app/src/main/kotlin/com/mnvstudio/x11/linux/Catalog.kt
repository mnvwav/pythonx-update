package com.mnvstudio.x11.linux

/** An app the user can install and launch from inside this app. */
data class LinuxApp(
    val name: String,
    val description: String,
    /** Alpine packages to install. */
    val packages: List<String>,
    /** Binary whose presence means the app is installed. */
    val binary: String,
    /** Shell command that starts it (runs in the background on the X display). */
    val command: String,
)

/** Apps known to run on this server's core X11 (no RENDER/GL extensions yet). */
object Catalog {
    val apps = listOf(
        LinuxApp("Terminal", "xterm: a Linux terminal with a full shell", listOf("xterm"), "xterm", "xterm -fn 9x15 -geometry 100x32"),
        LinuxApp("Calculator", "xcalc: scientific calculator", listOf("xcalc"), "xcalc", "xcalc"),
        LinuxApp("Clock", "xclock: analog clock", listOf("xclock"), "xclock", "xclock -update 1"),
        LinuxApp("Eyes", "xeyes: eyes that follow the pointer", listOf("xeyes"), "xeyes", "xeyes"),
        LinuxApp("Text editor", "xedit: simple text editor", listOf("xedit"), "xedit", "xedit"),
        LinuxApp("Window manager", "twm: move and resize windows like a desktop", listOf("twm"), "twm", "twm"),
    )
}
