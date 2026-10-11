package com.mnvstudio.x11.server

/** Diagnostics views of the server: resources per client, request tracing, extensions, input recording. */
object Inspector {
    /** Core protocol request names by opcode, for the request inspector. */
    val OPCODES: Array<String> = arrayOfNulls<String>(128).also { a ->
        ("CreateWindow ChangeWindowAttributes GetWindowAttributes DestroyWindow DestroySubwindows ChangeSaveSet ReparentWindow " +
            "MapWindow MapSubwindows UnmapWindow UnmapSubwindows ConfigureWindow CirculateWindow GetGeometry QueryTree InternAtom " +
            "GetAtomName ChangeProperty DeleteProperty GetProperty ListProperties SetSelectionOwner GetSelectionOwner ConvertSelection " +
            "SendEvent GrabPointer UngrabPointer GrabButton UngrabButton ChangeActivePointerGrab GrabKeyboard UngrabKeyboard GrabKey " +
            "UngrabKey AllowEvents GrabServer UngrabServer QueryPointer GetMotionEvents TranslateCoordinates WarpPointer SetInputFocus " +
            "GetInputFocus QueryKeymap OpenFont CloseFont QueryFont QueryTextExtents ListFonts ListFontsWithInfo SetFontPath GetFontPath " +
            "CreatePixmap FreePixmap CreateGC ChangeGC CopyGC SetDashes SetClipRectangles FreeGC ClearArea CopyArea CopyPlane PolyPoint " +
            "PolyLine PolySegment PolyRectangle PolyArc FillPoly PolyFillRectangle PolyFillArc PutImage GetImage PolyText8 PolyText16 " +
            "ImageText8 ImageText16 CreateColormap FreeColormap CopyColormapAndFree InstallColormap UninstallColormap ListInstalledColormaps " +
            "AllocColor AllocNamedColor AllocColorCells AllocColorPlanes FreeColors StoreColors StoreNamedColor QueryColors LookupColor " +
            "CreateCursor CreateGlyphCursor FreeCursor RecolorCursor QueryBestSize QueryExtension ListExtensions ChangeKeyboardMapping " +
            "GetKeyboardMapping ChangeKeyboardControl GetKeyboardControl Bell ChangePointerControl GetPointerControl SetScreenSaver " +
            "GetScreenSaver ChangeHosts ListHosts SetAccessControl SetCloseDownMode KillClient RotateProperties ForceScreenSaver " +
            "SetPointerMapping GetPointerMapping SetModifierMapping GetModifierMapping").split(' ').forEachIndexed { i, n -> a[i + 1] = n }
        a[127] = "NoOperation"
    }.map { it ?: "?" }.toTypedArray()

    fun opcodeName(op: Int) = if (op in 1..127) OPCODES[op] else "Extension($op)"

    /** Common extensions and whether this server provides them; apps that need the missing ones won't start. */
    val EXTENSIONS = listOf(
        Triple("Core protocol", true, "Windows, drawing, fonts, input: everything classic apps use"),
        Triple("BIG-REQUESTS", false, "Very large drawing requests"),
        Triple("XC-MISC", false, "Resource id recycling for long-running apps"),
        Triple("SHAPE", false, "Non-rectangular windows (xeyes draws a square instead)"),
        Triple("MIT-SHM", false, "Shared-memory images: faster video and games"),
        Triple("RENDER", false, "Anti-aliased text and transparency: needed by GTK, Qt, Firefox"),
        Triple("XInputExtension", false, "Multi-touch, tablets, smooth scrolling: needed by GTK 3"),
        Triple("RANDR", false, "Changing resolution while running, multiple monitors"),
        Triple("XFIXES", false, "Cursor changes, clipboard notifications"),
        Triple("DAMAGE", false, "Compositing window managers"),
        Triple("Composite", false, "Compositing window managers"),
        Triple("GLX / DRI3", false, "OpenGL graphics"),
        Triple("XKEYBOARD", false, "Advanced keyboard layouts (core keymap is used instead)"),
        Triple("SYNC", false, "Frame timing for some window managers"),
    )

    data class ClientInfo(
        val index: Int, val peer: String, val windows: Int, val pixmaps: Int, val pixmapBytes: Long, val gcs: Int, val fonts: Int,
        val requests: Long, val errors: Long, val title: String,
    )

    /** Resource usage per connected client. */
    fun clients(s: XServer): List<ClientInfo> = synchronized(s) {
        s.clients.map { c ->
            val owned = s.resources.values.filter { it.owner === c }
            val windows = owned.filterIsInstance<Window>()
            val pixmaps = owned.filterIsInstance<Pixmap>()
            val top = windows.firstOrNull { it.parent === s.root && it.mapped }
            ClientInfo(c.index, c.peer, windows.size, pixmaps.size, pixmaps.sumOf { it.pixels.size * 4L }, owned.count { it is GContext },
                owned.count { it is FontRef }, c.requestCount, c.errorCount, top?.let { s.wm?.title(it) ?: s.windowTitle(it) } ?: "")
        }
    }

    // ---------------------------------------------------------------- request tracing

    data class Traced(val time: Long, val client: Int, val seq: Int, val opcode: Int, val bytes: Int, val error: Int)

    @Volatile var tracing = false
    private val trace = ArrayDeque<Traced>()
    private val counts = LongArray(256)

    internal fun record(client: Int, seq: Int, opcode: Int, bytes: Int, error: Int) {
        counts[opcode and 0xFF]++
        if (!tracing) return
        synchronized(trace) { trace.addLast(Traced(System.currentTimeMillis(), client, seq, opcode, bytes, error)); while (trace.size > 500) trace.removeFirst() }
    }

    fun recent(): List<Traced> = synchronized(trace) { trace.toList() }
    fun clearTrace() = synchronized(trace) { trace.clear() }

    /** Requests seen since start, most frequent first: name to count. */
    fun requestCounts(): List<Pair<String, Long>> = counts.withIndex().filter { it.value > 0 }.map { opcodeName(it.index) to it.value }.sortedByDescending { it.second }
}

/**
 * Records injected input (pointer and keys) with timing, and plays it back: for reproducing bugs and testing apps.
 */
class InputRecorder(private val s: XServer) {
    sealed class Ev(val at: Long) {
        class Motion(at: Long, val x: Int, val y: Int) : Ev(at)
        class Button(at: Long, val b: Int, val down: Boolean) : Ev(at)
        class Key(at: Long, val code: Int, val down: Boolean) : Ev(at)
    }

    @Volatile var recording = false; private set
    @Volatile var playing = false; private set
    private var start = 0L
    val events = ArrayList<Ev>()

    fun startRecording() { synchronized(events) { events.clear() }; start = System.currentTimeMillis(); recording = true }
    fun stopRecording() { recording = false }

    internal fun motion(x: Int, y: Int) { if (recording) synchronized(events) { events += Ev.Motion(System.currentTimeMillis() - start, x, y) } }
    internal fun button(b: Int, down: Boolean) { if (recording) synchronized(events) { events += Ev.Button(System.currentTimeMillis() - start, b, down) } }
    internal fun key(code: Int, down: Boolean) { if (recording) synchronized(events) { events += Ev.Key(System.currentTimeMillis() - start, code, down) } }

    /** Plays the recording back with its original timing on a background thread. */
    fun play(speed: Float = 1f, done: () -> Unit = {}) {
        if (playing || recording) return
        val list = synchronized(events) { events.toList() }
        playing = true
        Thread {
            val t0 = System.currentTimeMillis()
            for (e in list) {
                if (!playing) break
                val wait = (e.at / speed).toLong() - (System.currentTimeMillis() - t0)
                if (wait > 0) Thread.sleep(wait)
                when (e) { is Ev.Motion -> s.motion(e.x, e.y); is Ev.Button -> s.button(e.b, e.down); is Ev.Key -> s.key(e.code, e.down) }
            }
            playing = false
            done()
        }.apply { isDaemon = true }.start()
    }

    fun stop() { playing = false }

    /** The recording as text (one event per line), for saving and sharing. */
    fun export(): String = synchronized(events) {
        events.joinToString("\n") { e -> when (e) {
            is Ev.Motion -> "${e.at} motion ${e.x} ${e.y}"; is Ev.Button -> "${e.at} button ${e.b} ${if (e.down) 1 else 0}"
            is Ev.Key -> "${e.at} key ${e.code} ${if (e.down) 1 else 0}"
        } }
    }

    fun import(text: String): Int = synchronized(events) {
        events.clear()
        text.lines().mapNotNull { l ->
            val p = l.trim().split(' ')
            val at = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            when (p.getOrNull(1)) {
                "motion" -> Ev.Motion(at, p[2].toInt(), p[3].toInt())
                "button" -> Ev.Button(at, p[2].toInt(), p[3] == "1")
                "key" -> Ev.Key(at, p[2].toInt(), p[3] == "1")
                else -> null
            }
        }.also { events.addAll(it) }.size
    }
}
