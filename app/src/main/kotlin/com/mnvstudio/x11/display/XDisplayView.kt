package com.mnvstudio.x11.display

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.mnvstudio.x11.server.XServer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Shows the X screen scaled to fit and turns touch, mouse and keyboard input into X input.
 *
 * Touchpad mode: drag moves the pointer, tap clicks, two-finger tap right-clicks, two-finger drag scrolls,
 * long-press then drag holds the left button. Direct mode: the pointer follows the finger and touching presses.
 */
@SuppressLint("ViewConstructor")
class XDisplayView(context: Context) : View(context) {
    var server: XServer? = null
        set(v) {
            field?.onDamage = null
            field = v
            v?.onDamage = { postInvalidateOnAnimation() }
            bitmap = v?.let { Bitmap.createBitmap(it.width, it.height, Bitmap.Config.ARGB_8888) }
            v?.let { s -> synchronized(s) { s.damage(0, 0, s.width, s.height) } }
            updateMatrix()
            invalidate()
        }
    var directTouch = false
    var pointerSpeed = 1.2f
    var smooth = true
    /** Sticky modifiers from the extra-keys bar: read before typing, cleared after one use. */
    var stickyCtrl: () -> Boolean = { false }
    var stickyAlt: () -> Boolean = { false }
    var onStickyUsed: () -> Unit = {}

    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    private var scale = 1f
    private var offX = 0f
    private var offY = 0f
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val cursorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val cursorStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val cursorPath = Path().apply {
        moveTo(0f, 0f); lineTo(0f, 22f); lineTo(6f, 17f); lineTo(10f, 26f); lineTo(14f, 24f); lineTo(10f, 15f); lineTo(17f, 15f); close()
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.BLACK)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { updateMatrix() }

    private fun updateMatrix() {
        val s = server ?: return
        if (width == 0 || height == 0) return
        scale = min(width.toFloat() / s.width, height.toFloat() / s.height)
        offX = (width - s.width * scale) / 2
        offY = (height - s.height * scale) / 2
        matrix.setScale(scale, scale)
        matrix.postTranslate(offX, offY)
    }

    override fun onDraw(canvas: Canvas) {
        val s = server ?: return
        val bmp = bitmap ?: return
        s.render { fb, r -> bmp.setPixels(fb, r.y * s.width + r.x, s.width, r.x, r.y, r.w, r.h) }
        bmpPaint.isFilterBitmap = smooth && scale != 1f
        canvas.drawBitmap(bmp, matrix, bmpPaint)
        if (!directTouch) {
            canvas.save()
            canvas.translate(offX + s.pointerX * scale, offY + s.pointerY * scale)
            val cs = resources.displayMetrics.density * 0.9f
            canvas.scale(cs, cs)
            canvas.drawPath(cursorPath, cursorFill)
            canvas.drawPath(cursorPath, cursorStroke)
            canvas.restore()
        }
    }

    private fun toScreenX(x: Float) = ((x - offX) / scale).toInt()
    private fun toScreenY(y: Float) = ((y - offY) / scale).toInt()

    // ---------------------------------------------------------------- touch

    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f; private var downY = 0f; private var lastX = 0f; private var lastY = 0f
    private var downTime = 0L
    private var moved = false
    private var maxPointers = 0
    private var dragging = false
    private var scrollAcc = 0f
    private var fracX = 0f; private var fracY = 0f
    private val longPress = Runnable {
        if (!moved && maxPointers == 1 && !directTouch) {
            dragging = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            server?.button(1, true)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = server ?: return false
        if (e.isFromSource(InputDevice.SOURCE_MOUSE)) return mouse(e, s)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                downTime = SystemClock.uptimeMillis()
                moved = false; maxPointers = 1; dragging = false; scrollAcc = 0f
                if (directTouch) { s.motion(toScreenX(e.x), toScreenY(e.y)); s.button(1, true) }
                else postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                removeCallbacks(longPress)
                // A second finger turns a direct press into a scroll / right-click gesture.
                if (directTouch && !moved) s.button(1, false)
                lastY = avgY(e)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(e.x - downX, e.y - downY) > slop) { moved = true; removeCallbacks(longPress) }
                if (e.pointerCount >= 2) {
                    val y = avgY(e)
                    scrollAcc += y - lastY; lastY = y
                    val step = 28 * resources.displayMetrics.density
                    while (abs(scrollAcc) >= step) {
                        // Fingers moving up scroll the content down, like a phone.
                        s.click(if (scrollAcc < 0) 5 else 4)
                        scrollAcc += if (scrollAcc < 0) step else -step
                        moved = true
                    }
                } else if (maxPointers == 1) {
                    if (directTouch) s.motion(toScreenX(e.x), toScreenY(e.y))
                    else if (moved) {
                        fracX += (e.x - lastX) * pointerSpeed / scale; fracY += (e.y - lastY) * pointerSpeed / scale
                        val dx = fracX.toInt(); val dy = fracY.toInt()
                        fracX -= dx; fracY -= dy
                        if (dx != 0 || dy != 0) s.motion(s.pointerX + dx, s.pointerY + dy)
                        invalidate()
                    }
                    lastX = e.x; lastY = e.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                val quick = SystemClock.uptimeMillis() - downTime < 300
                when {
                    e.actionMasked == MotionEvent.ACTION_CANCEL -> { s.button(1, false) }
                    maxPointers >= 2 && !moved && quick -> { s.button(1, false); s.click(3) }
                    directTouch -> s.button(1, false)
                    dragging -> s.button(1, false)
                    !moved && quick && maxPointers == 1 -> s.click(1)
                }
                dragging = false
            }
        }
        return true
    }

    private fun avgY(e: MotionEvent): Float { var y = 0f; for (i in 0 until e.pointerCount) y += e.getY(i); return y / e.pointerCount }

    /** A real mouse moves the X pointer directly. */
    private fun mouse(e: MotionEvent, s: XServer): Boolean {
        s.motion(toScreenX(e.x), toScreenY(e.y))
        when (e.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> {
                val b = when (e.actionButton) { MotionEvent.BUTTON_SECONDARY -> 3; MotionEvent.BUTTON_TERTIARY -> 2; else -> 1 }
                s.button(b, e.actionMasked == MotionEvent.ACTION_BUTTON_PRESS)
            }
        }
        invalidate()
        return true
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        val s = server ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE -> { s.motion(toScreenX(e.x), toScreenY(e.y)); invalidate(); return true }
            MotionEvent.ACTION_SCROLL -> {
                val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (v != 0f) s.click(if (v > 0) 4 else 5)
                return true
            }
        }
        return super.onGenericMotionEvent(e)
    }

    // ---------------------------------------------------------------- keyboard

    /** Runs [block] with sticky Ctrl/Alt held down, then releases them and clears the stickies. */
    private fun withSticky(block: (XServer) -> Unit) {
        val s = server ?: return
        val ctrl = stickyCtrl(); val alt = stickyAlt()
        if (ctrl) s.key(KeyMap.CTRL, true)
        if (alt) s.key(KeyMap.ALT, true)
        block(s)
        if (alt) s.key(KeyMap.ALT, false)
        if (ctrl) s.key(KeyMap.CTRL, false)
        if (ctrl || alt) onStickyUsed()
    }

    fun typeText(text: CharSequence) {
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            withSticky { it.type(cp) }
            i += Character.charCount(cp)
        }
    }

    fun tapKey(xKeycode: Int) = withSticky { it.key(xKeycode, true); it.key(xKeycode, false) }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = key(keyCode, event, true) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = key(keyCode, event, false) || super.onKeyUp(keyCode, event)

    private fun key(keyCode: Int, event: KeyEvent, down: Boolean): Boolean {
        val s = server ?: return false
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val code = KeyMap.x(keyCode)
        if (code != null) {
            if (down) withSticky { it.key(code, true) } else s.key(code, false)
            return true
        }
        // Keys outside the US map (accented letters on other layouts): type the character they produce.
        if (down) {
            val ch = event.unicodeChar
            if (ch != 0) { typeText(String(Character.toChars(ch))); return true }
        }
        return false
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean { typeText(text); return true }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) { tapKey(KeyMap.BACKSPACE) }
                return true
            }
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) onKeyDown(event.keyCode, event) else if (event.action == KeyEvent.ACTION_UP) onKeyUp(event.keyCode, event)
                return true
            }
            override fun setComposingText(text: CharSequence, newCursorPosition: Int) = true
            override fun finishComposingText() = true
        }
    }
}
