package com.mnvstudio.x11.xorg

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Choreographer
import android.view.Display
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * External monitor (HDMI, USB-C, Miracast, DeX): the Linux desktop is shown full screen there while the phone
 * stays the trackpad and keyboard. Uses Android's Presentation API; follows monitors being plugged in and out.
 */
object ExternalDisplay {
    var active by mutableStateOf(false); private set
    var name by mutableStateOf(""); private set
    private var presentation: Presentation? = null
    private var listener: DisplayManager.DisplayListener? = null

    /** Called while the X.Org display screen is open. */
    fun attach(context: Context) {
        val dm = context.getSystemService(DisplayManager::class.java)
        fun refresh() {
            val d = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
            if (d == null) { dismiss(); return }
            if (presentation?.display?.displayId == d.displayId) return
            dismiss()
            runCatching { Mirror(context, d).also { it.show(); presentation = it; active = true; name = d.name } }
        }
        listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) = refresh()
            override fun onDisplayRemoved(id: Int) = refresh()
            override fun onDisplayChanged(id: Int) {}
        }.also { dm.registerDisplayListener(it, null) }
        refresh()
    }

    fun detach(context: Context) {
        listener?.let { context.getSystemService(DisplayManager::class.java).unregisterDisplayListener(it) }
        listener = null
        dismiss()
    }

    private fun dismiss() { runCatching { presentation?.dismiss() }; presentation = null; active = false; name = "" }

    private class Mirror(context: Context, display: Display) : Presentation(context, display) {
        override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(MirrorView(context)) }
    }

    /** Draws X.Org's framebuffer scaled to the monitor; only repaints when something changed. */
    private class MirrorView(context: Context) : View(context), Choreographer.FrameCallback {
        private var bmp: Bitmap? = null
        private var px = IntArray(0)
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val dst = RectF()
        private var on = false
        private var frames = 0

        override fun onAttachedToWindow() { super.onAttachedToWindow(); on = true; Choreographer.getInstance().postFrameCallback(this) }
        override fun onDetachedFromWindow() { on = false; super.onDetachedFromWindow() }

        override fun doFrame(nanos: Long) {
            if (!on) return
            val f = XorgEngine.frame
            // The phone's view consumes DAMAGE events, so the mirror refreshes the whole frame at ~30 fps instead.
            if (f != null && (frames++ % 2 == 0)) {
                if (bmp?.width != f.width || bmp?.height != f.height) { bmp = Bitmap.createBitmap(f.width, f.height, Bitmap.Config.ARGB_8888); px = IntArray(f.width * f.height) }
                f.copyTo(px); bmp!!.setPixels(px, 0, f.width, 0, 0, f.width, f.height); invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(android.graphics.Color.BLACK)
            val b = bmp ?: return
            val s = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
            val w = b.width * s; val h = b.height * s
            dst.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            canvas.drawBitmap(b, null, dst, paint)
        }
    }
}
