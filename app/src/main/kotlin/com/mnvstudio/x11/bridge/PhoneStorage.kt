package com.mnvstudio.x11.bridge

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/** The phone's shared storage (Downloads, DCIM, Documents…) mounted in Linux at ~/Phone once the user allows it. */
object PhoneStorage {
    const val MOUNT = "/root/Phone"

    fun granted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Opens Android's "All files access" page (or asks for the storage permission on Android 10 and older). */
    fun request(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${activity.packageName}"))
            runCatching { activity.startActivity(i) }.onFailure { activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else activity.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 41)
    }

    /** proot bind arguments, empty when not allowed. */
    fun binds(context: Context, root: java.io.File): List<String> {
        if (!granted(context)) return emptyList()
        java.io.File(root, MOUNT.trimStart('/')).mkdirs(); java.io.File(root, "sdcard").mkdirs()
        @Suppress("DEPRECATION") val ext = Environment.getExternalStorageDirectory().path
        return listOf("-b", "$ext:$MOUNT", "-b", "$ext:/sdcard")
    }
}
