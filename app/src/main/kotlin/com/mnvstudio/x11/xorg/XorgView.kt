package com.mnvstudio.x11.xorg

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.InputType
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Shows the X.Org screen and sends input to it. The frame is copied from Xvfb's mapped framebuffer at up to
 * [maxFps] while visible and drawn scaled to fit; input runs on its own thread so a slow server never stalls the UI.
 *
 * Touch, in trackpad mode: one finger moves the pointer, tap = left click, double-tap-and-drag = drag,
 * two-finger tap = right click, two-finger drag = scroll, three-finger tap = middle click.
 * In direct mode the pointer jumps to the finger and touching presses the left button.
 */
@SuppressLint("ViewConstructor")
class XorgView(context: Context) : View(context), Choreographer.FrameCallback {
    var directTouch = false
    var pointerSpeed = 1.5f
    var smooth = true
    var maxFps = 60
    var onKeyboardRequest: () -> Unit = {}

    private val input = Executors.newSingleThreadExecutor()
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private var lastFrame = 0L
    private var running = false
    private var px = 0f; private var py = 0f   // pointer position in X coordinates

    init { isFocusable = true; isFocusableInTouchMode = true; keepScreenOn = true }

    private fun send(block: XorgClient.() -> Unit) { val c = XorgEngine.client ?: return; input.execute { runCatching { c.block() } } }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); running = true; Choreographer.getInstance().postFrameCallback(this) }
    override fun onDetachedFromWindow() { running = false; Choreographer.getInstance().removeFrameCallback(this); super.onDetachedFromWindow() }

    override fun doFrame(nanos: Long) {
        if (!running) return
        if (nanos - lastFrame >= 1_000_000_000L / maxFps.coerceIn(10, 120) - 2_000_000) {
            lastFrame = nanos
            val f = XorgEngine.frame
            if (f != null) {
                if (bitmap?.width != f.width || bitmap?.height != f.height) {
                    bitmap = Bitmap.createBitmap(f.width, f.height, Bitmap.Config.ARGB_8888); pixels = IntArray(f.width * f.height)
                    px = f.width / 2f; py = f.height / 2f
                }
                f.copyTo(pixels)
                bitmap!!.setPixels(pixels, 0, f.width, 0, 0, f.width, f.height)
                invalidate()
            }
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(android.graphics.Color.BLACK)
        val b = bitmap ?: return
        val s = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
        val w = b.width * s; val h = b.height * s
        dst.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        paint.isFilterBitmap = smooth
        canvas.drawBitmap(b, null, dst, paint)
        if (!directTouch) {
            // Our own pointer in trackpad mode (Xvfb draws no cursor into the framebuffer).
            val cx = dst.left + px * s; val cy = dst.top + py * s
            cursor.reset(); cursor.moveTo(cx, cy); cursor.lineTo(cx, cy + 22); cursor.lineTo(cx + 6, cy + 17); cursor.lineTo(cx + 10, cy + 26)
            cursor.lineTo(cx + 13, cy + 24); cursor.lineTo(cx + 9, cy + 16); cursor.lineTo(cx + 16, cy + 16); cursor.close()
            canvas.drawPath(cursor, cursorFill); canvas.drawPath(cursor, cursorEdge)
        }
    }
    private val cursor = android.graphics.Path()
    private val cursorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    private val cursorEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }

    private fun toX(x: Float, y: Float): Pair<Int, Int> {
        val b = bitmap ?: return 0 to 0
        val s = dst.width() / b.width
        return ((x - dst.left) / s).toInt().coerceIn(0, b.width - 1) to ((y - dst.top) / s).toInt().coerceIn(0, b.height - 1)
    }

    // ---------------------------------------------------------------- touch

    private var downTime = 0L; private var downX = 0f; private var downY = 0f; private var lastX = 0f; private var lastY = 0f
    private var maxPointers = 0; private var moved = false; private var dragging = false; private var lastTapUp = 0L
    private var scrollAcc = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val b = bitmap ?: return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                downTime = e.eventTime; downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; maxPointers = 1; moved = false; scrollAcc = 0f
                if (directTouch) { val (x, y) = toX(e.x, e.y); px = x.toFloat(); py = y.toFloat(); send { motion(x, y); button(1, true) } }
                else if (e.eventTime - lastTapUp < 280) { dragging = true; send { button(1, true) } }   // double-tap-and-drag
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                if (directTouch && e.pointerCount == 2) send { button(1, false) }   // two fingers: not a left drag after all
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastX; val dy = e.y - lastY
                if (hypot(e.x - downX, e.y - downY) > 12) moved = true
                if (e.pointerCount >= 2) {
                    scrollAcc += dy
                    val step = 40f
                    while (abs(scrollAcc) >= step) { val up = scrollAcc > 0; send { scroll(up) }; scrollAcc -= if (up) step else -step }
                } else if (directTouch) {
                    val (x, y) = toX(e.x, e.y); px = x.toFloat(); py = y.toFloat(); send { motion(x, y) }
                } else {
                    val s = b.width / dst.width() * pointerSpeed
                    px = (px + dx * s).coerceIn(0f, b.width - 1f); py = (py + dy * s).coerceIn(0f, b.height - 1f)
                    val x = px.toInt(); val y = py.toInt(); send { motion(x, y) }
                    invalidate()
                }
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_UP -> {
                val quick = e.eventTime - downTime < 250 && !moved
                when {
                    directTouch && maxPointers == 1 -> send { button(1, false) }
                    dragging -> { dragging = false; send { button(1, false) } }
                    quick && maxPointers == 1 -> { send { click(1) }; lastTapUp = e.eventTime }
                    quick && maxPointers == 2 -> send { click(3) }
                    quick && maxPointers >= 3 -> send { click(2) }
                }
                if (directTouch && quick && maxPointers == 2) send { click(3) }
                if (quick && maxPointers >= 4) onKeyboardRequest()
            }
        }
        return true
    }

    // ---------------------------------------------------------------- keys

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean { typeText(text.toString()); return true }
            override fun deleteSurroundingText(before: Int, after: Int): Boolean { repeat(maxOf(1, before)) { tap(0xff08) }; return true }
            override fun sendKeyEvent(event: KeyEvent): Boolean { dispatchKeyEvent(event); return true }
        }
    }

    /** Ctrl/Alt/Super tapped on the extra-keys bar: held for the next key only. */
    val pendingMods = LinkedHashSet<Int>()
    var onModsUsed: () -> Unit = {}

    private fun withMods(block: XorgClient.() -> Unit) {
        val mods = pendingMods.toList()
        if (mods.isNotEmpty()) { pendingMods.clear(); post(onModsUsed) }
        send { mods.forEach { keysym(it, true) }; block(); mods.reversed().forEach { keysym(it, false) } }
    }

    fun typeText(t: String) { t.codePoints().forEach { cp -> withMods { type(cp) } } }
    fun tap(keysym: Int) = withMods { keysym(keysym, true); keysym(keysym, false) }
    fun keysymDown(keysym: Int, down: Boolean) = send { keysym(keysym, down) }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = key(keyCode, event, true) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = key(keyCode, event, false) || super.onKeyUp(keyCode, event)

    private fun key(code: Int, e: KeyEvent, down: Boolean): Boolean {
        if (code == KeyEvent.KEYCODE_BACK) return false
        KEYS[code]?.let { sym -> send { keysym(sym, down) }; return true }
        val ch = e.getUnicodeChar(e.metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv())
        if (ch > 0) {
            if (!down) return true
            if (e.isCtrlPressed || e.isAltPressed) {
                // Shortcuts: hold the modifiers around the plain key so apps see Ctrl+C, Alt+F…
                val mods = listOfNotNull(0xffe3.takeIf { e.isCtrlPressed }, 0xffe9.takeIf { e.isAltPressed })
                send { mods.forEach { keysym(it, true) }; type(ch); mods.reversed().forEach { keysym(it, false) } }
            } else withMods { type(ch) }
            return true
        }
        return false
    }

    companion object {
        /** Android key codes → X keysyms for keys that don't type a character. */
        val KEYS = mapOf(
            KeyEvent.KEYCODE_ENTER to 0xff0d, KeyEvent.KEYCODE_NUMPAD_ENTER to 0xff8d, KeyEvent.KEYCODE_DEL to 0xff08, KeyEvent.KEYCODE_FORWARD_DEL to 0xffff,
            KeyEvent.KEYCODE_TAB to 0xff09, KeyEvent.KEYCODE_ESCAPE to 0xff1b, KeyEvent.KEYCODE_DPAD_LEFT to 0xff51, KeyEvent.KEYCODE_DPAD_UP to 0xff52,
            KeyEvent.KEYCODE_DPAD_RIGHT to 0xff53, KeyEvent.KEYCODE_DPAD_DOWN to 0xff54, KeyEvent.KEYCODE_MOVE_HOME to 0xff50, KeyEvent.KEYCODE_MOVE_END to 0xff57,
            KeyEvent.KEYCODE_PAGE_UP to 0xff55, KeyEvent.KEYCODE_PAGE_DOWN to 0xff56, KeyEvent.KEYCODE_INSERT to 0xff63,
            KeyEvent.KEYCODE_SHIFT_LEFT to 0xffe1, KeyEvent.KEYCODE_SHIFT_RIGHT to 0xffe2, KeyEvent.KEYCODE_CTRL_LEFT to 0xffe3, KeyEvent.KEYCODE_CTRL_RIGHT to 0xffe4,
            KeyEvent.KEYCODE_ALT_LEFT to 0xffe9, KeyEvent.KEYCODE_ALT_RIGHT to 0xffea, KeyEvent.KEYCODE_META_LEFT to 0xffeb, KeyEvent.KEYCODE_META_RIGHT to 0xffec,
        ) + (0..11).associate { (KeyEvent.KEYCODE_F1 + it) to (0xffbe + it) }
    }
}
