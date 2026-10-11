package com.mnvstudio.x11.server

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Software rasterizer for one drawing request: draws into [d] with the state of [gc]
 * and records the bounding box of what it touched, for damage tracking.
 */
class Paint(private val d: Drawable, private val gc: GContext) {
    var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
    val touched get() = minX <= maxX
    private val dmask = d.mask
    private val fast = gc.function == 3 && gc.planeMask and dmask == dmask && gc.fillStyle == 0 && gc.clipMask == null
    private val plainCopy = gc.function == 3 && gc.planeMask and dmask == dmask && gc.clipMask == null && gc.clipRects == null

    private fun touch(x0: Int, y: Int, x1: Int) {
        if (x0 < minX) minX = x0; if (x1 > maxX) maxX = x1; if (y < minY) minY = y; if (y > maxY) maxY = y
    }

    private fun op(src: Int, dst: Int): Int {
        val r = when (gc.function) {
            0 -> 0; 1 -> src and dst; 2 -> src and dst.inv(); 3 -> src; 4 -> src.inv() and dst; 5 -> dst
            6 -> src xor dst; 7 -> src or dst; 8 -> (src or dst).inv(); 9 -> (src xor dst).inv(); 10 -> dst.inv()
            11 -> src or dst.inv(); 12 -> src.inv(); 13 -> src.inv() or dst; 14 -> (src and dst).inv(); else -> -1
        }
        val pm = gc.planeMask
        return ((dst and pm.inv()) or (r and pm)) and dmask
    }

    private fun maskClipped(x: Int, y: Int): Boolean {
        val m = gc.clipMask ?: return false
        val mx = x - gc.clipX; val my = y - gc.clipY
        return mx !in 0 until m.width || my !in 0 until m.height || m.pixels[my * m.width + mx] == 0
    }

    private fun rectsClipped(x: Int, y: Int): Boolean {
        val r = gc.clipRects ?: return false
        var i = 0
        while (i < r.size) {
            val rx = r[i] + gc.clipX; val ry = r[i + 1] + gc.clipY
            if (x >= rx && y >= ry && x < rx + r[i + 2] && y < ry + r[i + 3]) return false
            i += 4
        }
        return true
    }

    /** Source pixel of the fill style at (x, y), or null where a stipple leaves the pixel alone. */
    private fun fillSource(x: Int, y: Int): Int? = when (gc.fillStyle) {
        1 -> gc.tile?.let { t -> if (t.width == 0 || t.height == 0) gc.fg else t.pixels[Math.floorMod(y - gc.tsY, t.height) * t.width + Math.floorMod(x - gc.tsX, t.width)] } ?: gc.fg
        2, 3 -> gc.stipple?.let { s ->
            if (s.width == 0 || s.height == 0) gc.fg
            else if (s.pixels[Math.floorMod(y - gc.tsY, s.height) * s.width + Math.floorMod(x - gc.tsX, s.width)] != 0) gc.fg
            else if (gc.fillStyle == 3) gc.bg else null
        } ?: gc.fg
        else -> gc.fg
    }

    /** Fills pixels x0..x1 (inclusive) of row y with the GC's fill. */
    fun span(y: Int, x0In: Int, x1In: Int) {
        if (y < 0 || y >= d.height) return
        val x0 = max(0, x0In); val x1 = min(d.width - 1, x1In)
        if (x0 > x1) return
        val rects = gc.clipRects
        if (rects == null) { rawSpan(y, x0, x1); return }
        // Intersect with each clip rectangle covering this row, merging overlaps so no pixel is drawn twice.
        val parts = ArrayList<IntArray>()
        var i = 0
        while (i < rects.size) {
            val rx = rects[i] + gc.clipX; val ry = rects[i + 1] + gc.clipY
            if (y >= ry && y < ry + rects[i + 3]) {
                val a = max(x0, rx); val b = min(x1, rx + rects[i + 2] - 1)
                if (a <= b) parts += intArrayOf(a, b)
            }
            i += 4
        }
        parts.sortBy { it[0] }
        var cur: IntArray? = null
        for (p in parts) {
            val c = cur
            if (c != null && p[0] <= c[1] + 1) c[1] = max(c[1], p[1])
            else { c?.let { rawSpan(y, it[0], it[1]) }; cur = p }
        }
        cur?.let { rawSpan(y, it[0], it[1]) }
    }

    private fun rawSpan(y: Int, x0: Int, x1: Int) {
        val row = y * d.width
        val px = d.pixels
        if (fast) java.util.Arrays.fill(px, row + x0, row + x1 + 1, gc.fg and dmask)
        else for (x in x0..x1) {
            if (maskClipped(x, y)) continue
            val src = fillSource(x, y) ?: continue
            px[row + x] = op(src, px[row + x])
        }
        touch(x0, y, x1)
    }

    /** Writes one source pixel with the GC function, plane mask and clip (no fill style), as copies and images do. */
    fun put(x: Int, y: Int, src: Int) {
        if (x < 0 || y < 0 || x >= d.width || y >= d.height) return
        if (maskClipped(x, y) || rectsClipped(x, y)) return
        val i = y * d.width + x
        d.pixels[i] = op(src, d.pixels[i])
        touch(x, y, x)
    }

    // ---- Shapes ----

    fun point(x: Int, y: Int) = span(y, x, x)

    fun fillRect(x: Int, y: Int, w: Int, h: Int) {
        for (yy in max(0, y) until min(d.height, y + h)) span(yy, x, x + w - 1)
    }

    /** A thin or wide line from (x0, y0) to (x1, y1). */
    fun line(x0: Int, y0: Int, x1: Int, y1: Int, cap: Boolean = true) {
        val lw = gc.lineWidth
        if (lw <= 1) { thin(x0, y0, x1, y1, cap); return }
        val dx = (x1 - x0).toDouble(); val dy = (y1 - y0).toDouble()
        val len = hypot(dx, dy)
        if (len == 0.0) { fillRect(x0 - lw / 2, y0 - lw / 2, lw, lw); return }
        val nx = -dy / len * lw / 2; val ny = dx / len * lw / 2
        // Projecting caps extend by half the width; butt caps (the default) stop at the end points.
        val ext = if (gc.capStyle == 3) lw / 2.0 / len else 0.0
        val ax = x0 - dx * ext; val ay = y0 - dy * ext; val bx = x1 + dx * ext; val by = y1 + dy * ext
        polygon(doubleArrayOf(ax + nx, bx + nx, bx - nx, ax - nx), doubleArrayOf(ay + ny, by + ny, by - ny, ay - ny), winding = true)
    }

    private fun thin(x0: Int, y0: Int, x1: Int, y1: Int, last: Boolean) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            if (x == x1 && y == y1) { if (last) point(x, y); return }
            point(x, y)
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    /** Connected lines; joints are drawn once so XOR lines stay clean. */
    fun polyline(xs: IntArray, ys: IntArray) {
        if (xs.size == 1) { point(xs[0], ys[0]); return }
        for (i in 0 until xs.size - 1) {
            line(xs[i], ys[i], xs[i + 1], ys[i + 1], cap = i == xs.size - 2)
            if (gc.lineWidth > 2 && i > 0) fillDisc(xs[i], ys[i], gc.lineWidth / 2.0)
        }
    }

    private fun fillDisc(cx: Int, cy: Int, r: Double) {
        val n = max(8, (r * 4).toInt())
        polygon(DoubleArray(n) { cx + r * cos(2 * PI * it / n) }, DoubleArray(n) { cy + r * sin(2 * PI * it / n) }, true)
    }

    fun rectOutline(x: Int, y: Int, w: Int, h: Int) {
        if (gc.lineWidth <= 1) {
            // Outline covers w+1 by h+1 pixels; each pixel once.
            span(y, x, x + w)
            if (h > 0) span(y + h, x, x + w)
            for (yy in y + 1 until y + h) { point(x, yy); if (w > 0) point(x + w, yy) }
        } else polyline(intArrayOf(x, x + w, x + w, x, x), intArrayOf(y, y, y + h, y + h, y))
    }

    /** Scanline polygon fill; pixels whose centers are inside are filled. */
    fun polygon(xs: DoubleArray, ys: DoubleArray, winding: Boolean = gc.fillRule == 1) {
        val n = xs.size
        if (n < 3) return
        val top = max(0, ceil(ys.min() - 0.5).toInt()); val bottom = min(d.height - 1, ceil(ys.max() - 0.5).toInt() - 1)
        val hits = ArrayList<Pair<Double, Int>>()
        for (y in top..bottom) {
            val yc = y + 0.5
            hits.clear()
            for (i in 0 until n) {
                val ax = xs[i]; val ay = ys[i]; val bx = xs[(i + 1) % n]; val by = ys[(i + 1) % n]
                if (ay == by) continue
                val lo = min(ay, by); val hi = max(ay, by)
                if (yc < lo || yc >= hi) continue
                hits += (ax + (yc - ay) * (bx - ax) / (by - ay)) to (if (by > ay) 1 else -1)
            }
            hits.sortBy { it.first }
            var wind = 0
            for (k in 0 until hits.size - 1) {
                wind = if (winding) wind + hits[k].second else wind xor 1
                if (wind != 0) {
                    val a = ceil(hits[k].first - 0.5).toInt(); val b = ceil(hits[k + 1].first - 0.5).toInt() - 1
                    if (a <= b) span(y, a, b)
                }
            }
        }
    }

    /** Points along an elliptical arc; angles in 1/64 degree, counter-clockwise from 3 o'clock. */
    private fun arcPoints(x: Int, y: Int, w: Int, h: Int, a1: Int, a2: Int): Pair<DoubleArray, DoubleArray> {
        val cx = x + w / 2.0; val cy = y + h / 2.0
        val sweep = a2.coerceIn(-360 * 64, 360 * 64)
        val n = max(6, ((w + h) * abs(sweep) / (360.0 * 64) * 1.2).toInt() + 2)
        val xs = DoubleArray(n + 1); val ys = DoubleArray(n + 1)
        for (i in 0..n) {
            val a = (a1 + sweep.toDouble() * i / n) / 64.0 * PI / 180
            xs[i] = cx + w / 2.0 * cos(a); ys[i] = cy - h / 2.0 * sin(a)
        }
        return xs to ys
    }

    fun arc(x: Int, y: Int, w: Int, h: Int, a1: Int, a2: Int) {
        val (xs, ys) = arcPoints(x, y, w, h, a1, a2)
        polyline(IntArray(xs.size) { xs[it].roundToInt() }, IntArray(ys.size) { ys[it].roundToInt() })
    }

    fun fillArc(x: Int, y: Int, w: Int, h: Int, a1: Int, a2: Int) {
        val (xs, ys) = arcPoints(x, y, w, h, a1, a2)
        if (abs(a2) < 360 * 64 && gc.arcMode == 1) polygon(xs + (x + w / 2.0), ys + (y + h / 2.0), false)
        else polygon(xs, ys, false)
    }

    // ---- Text ----

    /** Draws [chars] with the GC font, baseline at y; returns the advance. ImageText fills the background box first. */
    fun text(x: Int, y: Int, chars: IntArray, image: Boolean): Int {
        val f = gc.font ?: return 0
        val total = chars.size * f.width
        if (image) {
            for (yy in y - f.ascent until y + f.descent) for (xx in x until x + total) put(xx, yy, gc.bg)
        }
        var cx = x
        for (ch in chars) {
            for (gy in 0 until f.height) for (gx in 0 until f.width) if (f.pixel(ch, gx, gy)) {
                if (image) put(cx + gx, y - f.ascent + gy, gc.fg) else point(cx + gx, y - f.ascent + gy)
            }
            cx += f.width
        }
        return total
    }

    // ---- Copies ----

    fun copyArea(src: Drawable, sx: Int, sy: Int, dx: Int, dy: Int, w: Int, h: Int) {
        // Clip to the source and destination bounds.
        val x0 = max(0, max(-sx, -dx)); val y0 = max(0, max(-sy, -dy))
        val x1 = min(w, min(src.width - sx, d.width - dx)); val y1 = min(h, min(src.height - sy, d.height - dy))
        if (x0 >= x1 || y0 >= y1) return
        val sp = if (src === d) d.pixels.copyOf() else src.pixels
        if (plainCopy) {
            for (yy in y0 until y1) System.arraycopy(sp, (sy + yy) * src.width + sx + x0, d.pixels, (dy + yy) * d.width + dx + x0, x1 - x0)
            minX = min(minX, dx + x0); maxX = max(maxX, dx + x1 - 1); minY = min(minY, dy + y0); maxY = max(maxY, dy + y1 - 1)
        } else for (yy in y0 until y1) for (xx in x0 until x1) put(dx + xx, dy + yy, sp[(sy + yy) * src.width + sx + xx])
    }

    fun copyPlane(src: Drawable, sx: Int, sy: Int, dx: Int, dy: Int, w: Int, h: Int, plane: Int) {
        val sp = if (src === d) d.pixels.copyOf() else src.pixels
        for (yy in 0 until h) for (xx in 0 until w) {
            val px = sx + xx; val py = sy + yy
            if (px < 0 || py < 0 || px >= src.width || py >= src.height) continue
            put(dx + xx, dy + yy, if (sp[py * src.width + px] and plane != 0) gc.fg else gc.bg)
        }
    }
}
