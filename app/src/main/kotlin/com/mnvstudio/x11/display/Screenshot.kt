package com.mnvstudio.x11.display

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.mnvstudio.x11.AppLog
import com.mnvstudio.x11.server.XServer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves what's on the X display as a PNG in Pictures/X11; returns where it went. */
object Screenshot {
    fun save(context: Context, server: XServer): String {
        val w = server.width; val h = server.height
        val pixels = IntArray(w * h)
        server.render { _, _ -> }
        synchronized(server) { System.arraycopy(server.framebuffer, 0, pixels, 0, pixels.size) }
        val bmp = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        val name = "X11-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".png"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/X11")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no storage")
            context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        AppLog.i("Screenshot saved: $name")
        return "Pictures/X11/$name"
    }
}
