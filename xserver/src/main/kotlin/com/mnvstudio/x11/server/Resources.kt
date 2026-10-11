package com.mnvstudio.x11.server

/** Anything with an X resource id, owned by a client (null = the server). */
open class Resource(val id: Int, val owner: XClient?)

/**
 * Something that can be drawn on. Pixels are stored as plain pixel values:
 * 0x00RRGGBB for depth 24 (TrueColor), 0 or 1 for depth 1.
 */
abstract class Drawable(id: Int, owner: XClient?, var width: Int, var height: Int, val depth: Int) : Resource(id, owner) {
    var pixels = IntArray(maxOf(0, width * height))
    val mask get() = if (depth == 1) 1 else 0xFFFFFF
}

class Pixmap(id: Int, owner: XClient?, width: Int, height: Int, depth: Int) : Drawable(id, owner, width, height, depth)

class Property(var type: Int, var format: Int, var data: ByteArray)

class Window(
    id: Int, owner: XClient?, var parent: Window?,
    var x: Int, var y: Int, width: Int, height: Int, var border: Int,
    /** 1 = InputOutput, 2 = InputOnly. */
    val cls: Int, depth: Int,
) : Drawable(id, owner, width, height, depth) {
    /** Children, bottom-most first. */
    val children = ArrayList<Window>()
    var mapped = false
    var overrideRedirect = false
    /** Background: -1 = None, otherwise a pixel. [bgPixmap] wins when set; [bgParentRelative] uses the parent's. */
    var bgPixel = -1
    var bgPixmap: Pixmap? = null
    var bgParentRelative = false
    var borderPixel = 0
    var bitGravity = 0
    var winGravity = 1
    var colormap = 0
    var cursor = 0
    var doNotPropagate = 0
    val masks = LinkedHashMap<XClient, Int>()
    val props = LinkedHashMap<Int, Property>()

    init { if (cls == 2) pixels = IntArray(0) }

    val viewable: Boolean get() { var p: Window? = this; while (p != null) { if (!p.mapped) return false; p = p.parent }; return true }
    /** Absolute position of the window's interior (inside its border) on the screen. */
    val absX: Int get() = parent?.let { it.absX + x + border } ?: 0
    val absY: Int get() = parent?.let { it.absY + y + border } ?: 0
    fun allMasks() = masks.values.fold(0) { a, b -> a or b }
    fun isAncestorOf(w: Window?): Boolean { var p = w?.parent; while (p != null) { if (p === this) return true; p = p.parent }; return false }

    /** Reallocates the pixel buffer after a resize; content is kept top-left unless bit gravity is Forget. */
    fun resize(nw: Int, nh: Int) {
        if (nw == width && nh == height) return
        val oldW = width; val oldH = height; val old = pixels
        width = nw; height = nh
        if (cls == 2) return
        val np = IntArray(nw * nh)
        if (bitGravity != 0) for (yy in 0 until minOf(oldH, nh)) System.arraycopy(old, yy * oldW, np, yy * nw, minOf(oldW, nw))
        pixels = np
    }
}

class GContext(id: Int, owner: XClient?, val depth: Int) : Resource(id, owner) {
    var function = 3          // GXcopy
    var planeMask = -1
    var fg = 0
    var bg = 1
    var lineWidth = 0
    var lineStyle = 0
    var capStyle = 1
    var joinStyle = 0
    var fillStyle = 0         // Solid, Tiled, Stippled, OpaqueStippled
    var fillRule = 0          // EvenOdd, Winding
    var tile: Pixmap? = null
    var stipple: Pixmap? = null
    var tsX = 0; var tsY = 0
    var font: XFont? = null
    var subwindowMode = 0
    var exposures = true
    var clipX = 0; var clipY = 0
    /** Either a depth-1 mask or a list of rectangles (x, y, w, h quads), or neither (no clipping). */
    var clipMask: Pixmap? = null
    var clipRects: IntArray? = null
    var arcMode = 1           // PieSlice

    fun copyFrom(o: GContext, mask: Int) {
        if (mask and 0x1 != 0) function = o.function
        if (mask and 0x2 != 0) planeMask = o.planeMask
        if (mask and 0x4 != 0) fg = o.fg
        if (mask and 0x8 != 0) bg = o.bg
        if (mask and 0x10 != 0) lineWidth = o.lineWidth
        if (mask and 0x20 != 0) lineStyle = o.lineStyle
        if (mask and 0x40 != 0) capStyle = o.capStyle
        if (mask and 0x80 != 0) joinStyle = o.joinStyle
        if (mask and 0x100 != 0) fillStyle = o.fillStyle
        if (mask and 0x200 != 0) fillRule = o.fillRule
        if (mask and 0x400 != 0) tile = o.tile
        if (mask and 0x800 != 0) stipple = o.stipple
        if (mask and 0x1000 != 0) tsX = o.tsX
        if (mask and 0x2000 != 0) tsY = o.tsY
        if (mask and 0x4000 != 0) font = o.font
        if (mask and 0x8000 != 0) subwindowMode = o.subwindowMode
        if (mask and 0x10000 != 0) exposures = o.exposures
        if (mask and 0x20000 != 0) clipX = o.clipX
        if (mask and 0x40000 != 0) clipY = o.clipY
        if (mask and 0x80000 != 0) { clipMask = o.clipMask; clipRects = o.clipRects }
        if (mask and 0x400000 != 0) arcMode = o.arcMode
    }
}

class Colormap(id: Int, owner: XClient?) : Resource(id, owner)
class Cursor(id: Int, owner: XClient?) : Resource(id, owner)
class FontRef(id: Int, owner: XClient?, val font: XFont) : Resource(id, owner)
