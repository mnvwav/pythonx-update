package com.mnvstudio.x11

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Picture-in-picture: the Linux display keeps running in a small floating window over other Android apps.
 * The display screen hides its controls while [active].
 */
object Pip {
    var active by mutableStateOf(false)
    /** True while the display screen is showing; leaving the app from there floats it when the setting is on. */
    @Volatile var displayOpen = false

    fun supported(activity: Activity) = activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun enter(activity: Activity): Boolean {
        if (!supported(activity)) return false
        val ratio = if (XHolder.width <= 0) Rational(16, 9) else Rational(XHolder.width, XHolder.height).let { r ->
            // Android only accepts ratios between 1:2.39 and 2.39:1.
            when { r.toFloat() > 2.39f -> Rational(239, 100); r.toFloat() < 1 / 2.39f -> Rational(100, 239); else -> r }
        }
        val b = PictureInPictureParams.Builder().setAspectRatio(ratio)
        if (Build.VERSION.SDK_INT >= 31) b.setSeamlessResizeEnabled(true)
        return runCatching { activity.enterPictureInPictureMode(b.build()) }.getOrDefault(false)
    }
}
