package com.mnvstudio.x11.display

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
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
import android.widget.FrameLayout
import com.mnvstudio.x11.server.XServer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Shows the X screen through OpenGL ES (only changed rows are uploaded each frame) and turns touch, mouse and
 * keyboard input into X input.
 *
 * Touchpad mode: drag moves the pointer, tap clicks, long-press then drag holds the left button.
 * Direct mode: the pointer follows the finger and touching presses.
 * Both: two-finger tap = right click, two-finger drag = scroll, pinch = zoom the view, three-finger tap = middle click.
 */
@SuppressLint("ViewConstructor")
class XDisplayView(context: Context) : FrameLayout(context) {
    var server: XServer? = null
        set(v) {
            field?.onDamage = null
            field = v
            v?.onDamage = { scheduleFrame() }
            renderer.fullUpload = true
            relayout()
            scheduleFrame()
        }
    var directTouch = false
    var pointerSpeed = 1.2f
    var smooth = true
        set(v) { field = v; renderer.fullUpload = true; scheduleFrame() }
    /** "fit" (letterbox), "stretch" (fill, may distort) or "pixel" (1:1, centered). */
    var fitMode = "fit"
        set(v) { field = v; relayout() }
    var maxFps = 60
    var cursorScale = 1f
        set(v) { field = v; cursorView.invalidate() }
    /** Sticky modifiers from the extra-keys bar (X keycodes), held for the next key and then released. */
    var stickyMods: () -> List<Int> = { emptyList() }
    /** Frames drawn in the last second, reported about once a second when set (performance overlay). */
    var onFps: ((Int) -> Unit)? = null
    /** Left button held by the extra keys bar's DRAG key, so one finger can drag in trackpad mode. */
    var dragLock = false
        set(v) { field = v; server?.button(1, v) }
    var onStickyUsed: () -> Unit = {}

    // View transform: screen pixel (x, y) is drawn at (offX + x * sx, offY + y * sy).
    @Volatile private var sx = 1f
    @Volatile private var sy = 1f
    @Volatile private var offX = 0f
    @Volatile private var offY = 0f
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f

    private val renderer = Renderer()
    private val gl = GLSurfaceView(context).apply {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }
    private val cursorView = CursorView(context)
    private val main = Handler(Looper.getMainLooper())
    private var framePending = false
    private var lastFrame = 0L

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.BLACK)
        addView(gl, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(cursorView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Coalesces damage into at most [maxFps] frames a second; nothing is drawn while the view is hidden. */
    private fun scheduleFrame() {
        main.post {
            if (framePending || windowVisibility != VISIBLE) return@post
            framePending = true
            val wait = max(0L, lastFrame + 1000L / maxFps.coerceIn(10, 240) - SystemClock.uptimeMillis())
            main.postDelayed({
                framePending = false
                lastFrame = SystemClock.uptimeMillis()
                gl.requestRender()
                cursorView.invalidate()
            }, wait)
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) { renderer.fullUpload = true; scheduleFrame() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { relayout() }

    fun resetZoom() { zoom = 1f; panX = 0f; panY = 0f; relayout() }

    private fun relayout() {
        val s = server ?: return
        if (width == 0 || height == 0) return
        val fx = width.toFloat() / s.width; val fy = height.toFloat() / s.height
        var bx: Float; var by: Float
        when (fitMode) {
            "stretch" -> { bx = fx; by = fy }
            "pixel" -> { bx = 1f; by = 1f }
            else -> { bx = min(fx, fy); by = bx }
        }
        bx *= zoom; by *= zoom
        val cw = s.width * bx; val ch = s.height * by
        // Center content that fits; otherwise keep the pan inside the content.
        val maxPanX = max(0f, (cw - width) / 2); val maxPanY = max(0f, (ch - height) / 2)
        panX = panX.coerceIn(-maxPanX, maxPanX); panY = panY.coerceIn(-maxPanY, maxPanY)
        sx = bx; sy = by
        offX = (width - cw) / 2 + panX
        offY = (height - ch) / 2 + panY
        renderer.layoutChanged = true
        gl.requestRender()
        cursorView.invalidate()
    }

    private fun toScreenX(x: Float) = ((x - offX) / sx).toInt()
    private fun toScreenY(y: Float) = ((y - offY) / sy).toInt()

    // ---------------------------------------------------------------- GL

    private inner class Renderer : GLSurfaceView.Renderer {
        @Volatile var fullUpload = true
        @Volatile var layoutChanged = true
        private var program = 0
        private var tex = 0
        private var texW = 0
        private var texH = 0
        private var viewW = 1
        private var viewH = 1
        private var filterLinear = true
        private val quad: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
            program = linkProgram(
                "attribute vec2 aPos; attribute vec2 aTex; varying vec2 vTex;" +
                    "void main() { gl_Position = vec4(aPos, 0.0, 1.0); vTex = aTex; }",
                // Pixels are 0xAARRGGBB ints, little-endian: bytes B,G,R,A land in the r,g,b,a channels.
                "precision mediump float; varying vec2 vTex; uniform sampler2D uTex;" +
                    "void main() { vec4 c = texture2D(uTex, vTex); gl_FragColor = vec4(c.b, c.g, c.r, 1.0); }",
            )
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            tex = ids[0]
            texW = 0; texH = 0
            fullUpload = true
        }

        override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
            viewW = width; viewH = height
            GLES20.glViewport(0, 0, width, height)
            layoutChanged = true
        }

        private var frames = 0
        private var fpsSince = 0L

        override fun onDrawFrame(unused: GL10?) {
            frames++
            val now = SystemClock.uptimeMillis()
            if (now - fpsSince >= 1000) { val f = frames; frames = 0; fpsSince = now; onFps?.let { cb -> main.post { cb(f) } } }
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            val s = server ?: return
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            if (texW != s.width || texH != s.height || filterLinear != smooth) {
                texW = s.width; texH = s.height; filterLinear = smooth
                val f = if (smooth) GLES20.GL_LINEAR else GLES20.GL_NEAREST
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, f)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, f)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, texW, texH, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
                fullUpload = true
            }
            if (fullUpload) { fullUpload = false; synchronized(s) { s.damage(0, 0, s.width, s.height) } }
            // Upload only the changed rows, straight from the server's framebuffer.
            s.render { fb, r ->
                val buf = IntBuffer.wrap(fb, r.y * s.width, r.h * s.width).slice()
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, r.y, s.width, r.h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            }
            // Quad in normalized device coordinates for the current view transform.
            val x0 = offX / viewW * 2 - 1; val x1 = (offX + s.width * sx) / viewW * 2 - 1
            val y0 = 1 - offY / viewH * 2; val y1 = 1 - (offY + s.height * sy) / viewH * 2
            quad.clear()
            quad.put(floatArrayOf(x0, y0, 0f, 0f, x1, y0, 1f, 0f, x0, y1, 0f, 1f, x1, y1, 1f, 1f))
            GLES20.glUseProgram(program)
            val aPos = GLES20.glGetAttribLocation(program, "aPos")
            val aTex = GLES20.glGetAttribLocation(program, "aTex")
            quad.position(0)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(aPos)
            quad.position(2)
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(aTex)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        private fun linkProgram(vs: String, fs: String): Int {
            fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
            return GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, vs))
                GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, fs))
                GLES20.glLinkProgram(it)
            }
        }
    }

    /** The pointer arrow, drawn over the GL surface in touchpad mode. */
    private inner class CursorView(context: Context) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
        private val path = Path().apply {
            moveTo(0f, 0f); lineTo(0f, 22f); lineTo(6f, 17f); lineTo(10f, 26f); lineTo(14f, 24f); lineTo(10f, 15f); lineTo(17f, 15f); close()
        }

        override fun onDraw(canvas: Canvas) {
            val s = server ?: return
            if (directTouch) return
            canvas.save()
            canvas.translate(offX + s.pointerX * sx, offY + s.pointerY * sy)
            val c = resources.displayMetrics.density * 0.9f * cursorScale
            canvas.scale(c, c)
            canvas.drawPath(path, fill)
            canvas.drawPath(path, stroke)
            canvas.restore()
        }
    }

    // ---------------------------------------------------------------- touch

    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f; private var downY = 0f; private var lastX = 0f; private var lastY = 0f
    private var downTime = 0L
    private var moved = false
    private var maxPointers = 0
    private var dragging = false
    private var scrollAcc = 0f
    private var fracX = 0f; private var fracY = 0f
    // Two-finger gesture state: pinch zoom versus scroll is decided by how much the fingers spread.
    private var startSpan = 0f
    private var startZoom = 1f
    private var focusX = 0f; private var focusY = 0f
    private var pinching = false
    private val longPress = Runnable {
        if (!moved && maxPointers == 1 && !directTouch) {
            dragging = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            server?.button(1, true)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent) = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = server ?: return false
        if (e.isFromSource(InputDevice.SOURCE_MOUSE)) return mouse(e, s)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                downTime = SystemClock.uptimeMillis()
                moved = false; maxPointers = 1; dragging = false; scrollAcc = 0f; pinching = false
                if (directTouch) { s.motion(toScreenX(e.x), toScreenY(e.y)); s.button(1, true) }
                else postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = max(maxPointers, e.pointerCount)
                removeCallbacks(longPress)
                // A second finger turns a direct press into a gesture.
                if (directTouch && !moved) s.button(1, false)
                startSpan = span(e); startZoom = zoom
                focusX = avg(e, true); focusY = avg(e, false)
                lastY = focusY
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(e.x - downX, e.y - downY) > slop) { moved = true; removeCallbacks(longPress) }
                if (e.pointerCount == 2) twoFingers(e, s)
                else if (e.pointerCount == 1 && maxPointers == 1) {
                    if (directTouch) s.motion(toScreenX(e.x), toScreenY(e.y))
                    else if (moved) {
                        fracX += (e.x - lastX) * pointerSpeed / sx; fracY += (e.y - lastY) * pointerSpeed / sy
                        val dx = fracX.toInt(); val dy = fracY.toInt()
                        fracX -= dx; fracY -= dy
                        if (dx != 0 || dy != 0) s.motion(s.pointerX + dx, s.pointerY + dy)
                    }
                    lastX = e.x; lastY = e.y
                    cursorView.invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                val quick = SystemClock.uptimeMillis() - downTime < 300
                when {
                    e.actionMasked == MotionEvent.ACTION_CANCEL -> s.button(1, false)
                    maxPointers >= 3 && !moved && quick -> s.click(2)
                    maxPointers == 2 && !moved && quick -> { s.button(1, false); s.click(3) }
                    directTouch || dragging -> s.button(1, false)
                    !moved && quick && maxPointers == 1 -> s.click(1)
                }
                dragging = false
            }
        }
        return true
    }

    private fun twoFingers(e: MotionEvent, s: XServer) {
        val sp = span(e)
        val fx = avg(e, true); val fy = avg(e, false)
        if (!pinching && startSpan > 0 && abs(sp / startSpan - 1) > 0.15f) pinching = true
        if (pinching) {
            moved = true
            val newZoom = (startZoom * sp / startSpan).coerceIn(1f, 6f)
            // Keep the content under the fingers' midpoint while zooming and follow its movement.
            val k = newZoom / zoom
            panX = (panX - (focusX - width / 2f)) * k + (focusX - width / 2f) + (fx - focusX)
            panY = (panY - (focusY - height / 2f)) * k + (focusY - height / 2f) + (fy - focusY)
            zoom = if (newZoom < 1.03f) 1f else newZoom
            focusX = fx; focusY = fy
            relayout()
            return
        }
        scrollAcc += fy - lastY; lastY = fy
        val step = 28 * resources.displayMetrics.density
        while (abs(scrollAcc) >= step) {
            // Fingers moving up scroll the content down, like a phone.
            s.click(if (scrollAcc < 0) 5 else 4)
            scrollAcc += if (scrollAcc < 0) step else -step
            moved = true
        }
    }

    private fun avg(e: MotionEvent, x: Boolean): Float { var v = 0f; for (i in 0 until e.pointerCount) v += if (x) e.getX(i) else e.getY(i); return v / e.pointerCount }
    private fun span(e: MotionEvent): Float = if (e.pointerCount < 2) 0f else hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))

    /** A real mouse moves the X pointer directly. */
    private fun mouse(e: MotionEvent, s: XServer): Boolean {
        s.motion(toScreenX(e.x), toScreenY(e.y))
        when (e.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> {
                val b = when (e.actionButton) { MotionEvent.BUTTON_SECONDARY -> 3; MotionEvent.BUTTON_TERTIARY -> 2; else -> 1 }
                s.button(b, e.actionMasked == MotionEvent.ACTION_BUTTON_PRESS)
            }
        }
        cursorView.invalidate()
        return true
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        val s = server ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE -> { s.motion(toScreenX(e.x), toScreenY(e.y)); cursorView.invalidate(); return true }
            MotionEvent.ACTION_SCROLL -> {
                val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (v != 0f) s.click(if (v > 0) 4 else 5)
                return true
            }
        }
        return super.onGenericMotionEvent(e)
    }

    // ---------------------------------------------------------------- keyboard

    /** Runs [block] with the sticky modifiers held down, then releases them and clears the stickies. */
    private fun withSticky(block: (XServer) -> Unit) {
        val s = server ?: return
        val mods = stickyMods()
        mods.forEach { s.key(it, true) }
        block(s)
        mods.asReversed().forEach { s.key(it, false) }
        if (mods.isNotEmpty()) onStickyUsed()
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
        // Physical keyboards with a non-US layout: send the character the key produces, not the US key in that spot.
        val ch = event.unicodeChar
        val usLetterOrDigit = keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z || keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9
        val modified = event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
        if (down && ch != 0 && !modified && !Character.isISOControl(ch) && (!usLetterOrDigit || !matchesUs(keyCode, ch))) {
            typeText(String(Character.toChars(ch)))
            return true
        }
        val code = KeyMap.x(keyCode)
        if (code != null) {
            if (down) withSticky { it.key(code, true) } else s.key(code, false)
            return true
        }
        return false
    }

    /** Whether a letter or digit key produced what a US keyboard would (so it can be sent as that key, with modifiers). */
    private fun matchesUs(keyCode: Int, ch: Int): Boolean {
        val c = ch.toChar().lowercaseChar()
        return if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) c == 'a' + (keyCode - KeyEvent.KEYCODE_A)
        else c == '0' + (keyCode - KeyEvent.KEYCODE_0) || !c.isLetterOrDigit()
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, false) {
            // Scripts like Hindi and Punjabi are composed in the IME and arrive here as committed text.
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
