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
    /** Big app not fully tested on this server yet: installable, but labelled experimental. */
    val experimental: Boolean = false,
    /** A terminal program: opened inside a terminal window. */
    val terminal: Boolean = false,
)

/**
 * Apps tested against this server: classic X apps, GTK 3 and Qt 5 apps and the XFCE desktop all draw through the
 * core protocol (cairo and Qt fall back to image uploads without RENDER).
 */
object Catalog {
    /** Fonts, icons and a session bus: GTK/Qt apps show empty boxes or wait on D-Bus without them. */
    private val GUI = listOf("font-dejavu", "adwaita-icon-theme", "dbus-x11")
    private fun gui(vararg p: String) = p.toList() + GUI
    private const val TERM = "xterm -fa 'DejaVu Sans Mono' -fs 11 -geometry 100x32"

    val apps = listOf(
        // Desktops
        LinuxApp("xfce4", "XFCE desktop", "Full desktop: panel, menu, window manager, file manager", gui("xfce4", "xfce4-terminal", "mousepad"),
            "startxfce4", "dbus-launch --exit-with-session startxfce4", "Desktops"),
        LinuxApp("twm", "Simple window manager", "Move, resize and stack windows", listOf("twm"), "twm", "twm", "Desktops"),
        // Tools
        LinuxApp("xterm", "Terminal", "A full Linux shell in a window", listOf("xterm", "font-dejavu"), "xterm", TERM, "Tools"),
        LinuxApp("xfce4-terminal", "XFCE Terminal", "Tabs, colours and copy/paste", gui("xfce4-terminal"), "xfce4-terminal", "xfce4-terminal", "Tools"),
        LinuxApp("thunar", "Thunar", "File manager", gui("thunar"), "thunar", "thunar", "Tools"),
        LinuxApp("pcmanfm", "PCManFM", "Light file manager", gui("pcmanfm"), "pcmanfm", "pcmanfm", "Tools"),
        LinuxApp("galculator", "Calculator", "Scientific calculator", gui("galculator"), "galculator", "galculator", "Tools"),
        LinuxApp("xarchiver", "Archive manager", "Open and create zip, tar and 7z files", gui("xarchiver"), "xarchiver", "xarchiver", "Tools"),
        LinuxApp("htop", "Task monitor", "htop: interactive process viewer", listOf("htop", "xterm", "font-dejavu"), "htop", "$TERM -e htop", "Tools", terminal = true),
        LinuxApp("mc", "Midnight Commander", "Two-pane file manager in a terminal", listOf("mc", "xterm", "font-dejavu"), "mc", "$TERM -e mc", "Tools", terminal = true),
        // Office & text
        LinuxApp("mousepad", "Mousepad", "Simple, fast text editor", gui("mousepad"), "mousepad", "mousepad", "Office"),
        LinuxApp("featherpad", "FeatherPad", "Qt text editor", listOf("featherpad", "font-dejavu", "dbus-x11"), "featherpad", "featherpad", "Office"),
        LinuxApp("abiword", "AbiWord", "Word processor", gui("abiword"), "abiword", "abiword", "Office"),
        LinuxApp("gnumeric", "Gnumeric", "Spreadsheet", gui("gnumeric"), "gnumeric", "gnumeric", "Office"),
        // Developer
        LinuxApp("geany", "Geany", "Code editor with syntax highlighting and build tools", gui("geany"), "geany", "geany", "Developer"),
        LinuxApp("nano", "nano", "Simple terminal text editor", listOf("nano", "xterm", "font-dejavu"), "nano", "$TERM -e nano", "Developer", terminal = true),
        LinuxApp("vim", "Vim", "Powerful terminal text editor", listOf("vim", "xterm", "font-dejavu"), "vim", "$TERM -e vim", "Developer", terminal = true),
        LinuxApp("python3", "Python", "Python 3 interpreter", listOf("python3", "xterm", "font-dejavu"), "python3", "$TERM -e python3", "Developer", terminal = true),
        LinuxApp("devkit", "Developer kit", "Python, Node.js, Go, Rust, C/C++ compilers, Git and make, ready in a terminal",
            listOf("python3", "py3-pip", "nodejs", "npm", "go", "rust", "cargo", "build-base", "git", "xterm", "font-dejavu"), "cargo", TERM, "Developer", terminal = true),
        LinuxApp("code-server", "VS Code (browser)", "VS Code in the built-in browser via code-server (installs with npm, needs ~1 GB)",
            listOf("nodejs", "npm", "build-base", "python3", "git", "bash", "libstdc++", "krb5-dev", "libsecret-dev"), "code-server",
            "command -v code-server >/dev/null || npm install -g --unsafe-perm code-server; code-server --auth none --bind-addr 127.0.0.1:8080 & sleep 4; android open http://127.0.0.1:8080; wait",
            "Developer", experimental = true),
        LinuxApp("git", "Git", "Version control, in a terminal", listOf("git", "xterm", "font-dejavu"), "git", "$TERM", "Developer", terminal = true),
        // Graphics
        LinuxApp("ristretto", "Ristretto", "Image viewer", gui("ristretto"), "ristretto", "ristretto", "Graphics"),
        LinuxApp("mtpaint", "mtPaint", "Painting and pixel art", gui("mtpaint"), "mtpaint", "mtpaint", "Graphics"),
        LinuxApp("gimp", "GIMP", "Advanced image editor", gui("gimp"), "gimp", "gimp", "Graphics", experimental = true),
        // Internet
        LinuxApp("netsurf", "NetSurf", "Light, fast web browser", gui("netsurf"), "netsurf-gtk3",
            "netsurf-gtk3 || netsurf-gtk || netsurf", "Internet"),
        LinuxApp("dillo", "Dillo", "Tiny web browser for simple pages", listOf("dillo", "font-dejavu"), "dillo", "dillo", "Internet"),
        LinuxApp("firefox", "Firefox", "Full web browser (heavy: needs 1 GB+ free RAM)", gui("firefox"), "firefox", "MOZ_ENABLE_WAYLAND=0 firefox", "Internet", experimental = true),
        LinuxApp("hexchat", "HexChat", "IRC chat", gui("hexchat"), "hexchat", "hexchat", "Internet"),
        // Classic X
        LinuxApp("xcalc", "xcalc", "Classic X calculator", listOf("xcalc"), "xcalc", "xcalc", "Classic"),
        LinuxApp("xclock", "Clock", "Analog clock", listOf("xclock"), "xclock", "xclock -update 1", "Classic"),
        LinuxApp("xeyes", "Eyes", "Eyes that follow the pointer, a quick display test", listOf("xeyes"), "xeyes", "xeyes", "Classic"),
        LinuxApp("xedit", "xedit", "Classic X text editor", listOf("xedit"), "xedit", "xedit", "Classic"),
        LinuxApp("xload", "System load", "Live CPU load graph", listOf("xload"), "xload", "xload", "Classic"),
    )

    val categories: List<String> get() = apps.map { it.category }.distinct()

    fun byId(id: String) = apps.firstOrNull { it.id == id }
}
