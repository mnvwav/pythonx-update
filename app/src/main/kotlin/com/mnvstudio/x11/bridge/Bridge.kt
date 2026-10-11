package com.mnvstudio.x11.bridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.mnvstudio.x11.AppLog
import com.mnvstudio.x11.MainActivity
import com.mnvstudio.x11.R
import com.mnvstudio.x11.linux.Linux
import java.io.File
import java.util.concurrent.Executors

/**
 * Linux ⇄ Android bridge. Linux programs talk to the phone through small scripts this installs in /usr/local/bin:
 *  - `android <command> …`  toast, notify, vibrate, say, open, share, clip, battery, info
 *  - `notify-send`          Linux desktop notifications appear as Android notifications
 *  - `xdg-open` / `x-www-browser`  links and files open in Android apps (Chrome, the gallery, a PDF viewer…)
 * A request is a NUL-separated argument list dropped as a file into /run/x11-bridge; the app answers with a
 * file whose first line is the exit code. No sockets, no root, and it works inside proot.
 */
object Bridge {
    private var observer: FileObserver? = null
    private var dir: File? = null
    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private const val CHANNEL = "linux"
    private var notifyId = 1000

    /** Starts watching the active Linux profile's bridge folder (call whenever the profile may have changed). */
    @Synchronized
    fun ensure(context: Context) {
        val app = context.applicationContext
        val root = Linux.rootfs(app)
        if (!File(root, "bin").exists()) return
        val d = File(root, "run/x11-bridge")
        if (d == dir && observer != null) return
        observer?.stopWatching()
        d.mkdirs(); dir = d
        installShims(root)
        @Suppress("DEPRECATION")
        observer = object : FileObserver(d.path, MOVED_TO or CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) { if (path != null && path.startsWith("req.")) pool.execute { handle(app, File(d, path)) } }
        }.also { it.startWatching() }
        // Requests that arrived while the app wasn't running.
        d.listFiles { f -> f.name.startsWith("req.") }?.forEach { f -> pool.execute { handle(app, f) } }
    }

    /** The Linux side: shell scripts in /usr/local/bin (ahead of /usr/bin on PATH). */
    fun installShims(root: File) {
        val bin = File(root, "usr/local/bin").apply { mkdirs() }
        fun put(name: String, body: String) = File(bin, name).apply { writeText(body); setExecutable(true, false) }
        put("android", ANDROID_SH)
        put("notify-send", """#!/bin/sh
# Shows Linux notifications on Android (installed by the X11 app).
title=""; body=""
while [ ${'$'}# -gt 0 ]; do
  case "${'$'}1" in
    -u|-t|-i|-a|-c|-h|--urgency|--expire-time|--icon|--app-name|--category|--hint) shift ;;
    -*) ;;
    *) if [ -z "${'$'}title" ]; then title="${'$'}1"; elif [ -z "${'$'}body" ]; then body="${'$'}1"; fi ;;
  esac
  shift
done
exec android notify "${'$'}title" "${'$'}body"
""")
        val open = """#!/bin/sh
# Opens links and files in Android apps (installed by the X11 app).
[ -z "${'$'}1" ] && { echo "usage: ${'$'}0 URL|FILE" >&2; exit 1; }
case "${'$'}1" in
  *://*|mailto:*|tel:*) exec android open "${'$'}1" ;;
  *) exec android open "${'$'}(readlink -f "${'$'}1" 2>/dev/null || echo "${'$'}1")" ;;
esac
"""
        put("xdg-open", open); put("x-www-browser", open); put("sensible-browser", open)
    }

    private fun handle(context: Context, req: File) {
        val id = req.name.removePrefix("req.")
        val args = runCatching { req.readText().split('\u0000').dropLastWhile { it.isEmpty() } }.getOrNull() ?: return
        req.delete()
        val (code, out) = runCatching { run(context, args) }.getOrElse { 1 to "error: ${it.message}" }
        val res = File(req.parentFile, ".res.$id")
        res.writeText("$code\n$out" + if (out.isEmpty() || out.endsWith("\n")) "" else "\n")
        res.renameTo(File(req.parentFile, "res.$id"))
    }

    private fun ui(block: () -> Unit) = main.post { runCatching(block).onFailure { AppLog.e("Bridge", it) } }

    private fun run(context: Context, a: List<String>): Pair<Int, String> {
        val cmd = a.firstOrNull() ?: "help"
        val rest = a.drop(1)
        val text = rest.joinToString(" ")
        AppLog.i("Linux → Android: $cmd")
        return when (cmd) {
            "toast" -> { ui { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }; 0 to "" }
            "notify" -> { notify(context, rest.getOrNull(0)?.ifBlank { null } ?: "Linux", rest.getOrNull(1).orEmpty()); 0 to "" }
            "vibrate" -> {
                val ms = rest.firstOrNull()?.toLongOrNull() ?: 300
                val v = context.getSystemService(Vibrator::class.java)
                v?.vibrate(VibrationEffect.createOneShot(ms.coerceIn(1, 5000), VibrationEffect.DEFAULT_AMPLITUDE)); 0 to ""
            }
            "say" -> { ui { speak(context, text) }; 0 to "" }
            "open" -> open(context, rest.firstOrNull() ?: return 2 to "usage: android open URL|FILE", share = false)
            "share" -> open(context, rest.firstOrNull() ?: return 2 to "usage: android share TEXT|FILE", share = true)
            "clip" -> clip(context, rest)
            "battery" -> {
                val bm = context.getSystemService(BatteryManager::class.java)
                val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val charging = bm.isCharging
                0 to "level=$level\ncharging=$charging\n"
            }
            "info" -> 0 to "device=${Build.MANUFACTURER} ${Build.MODEL}\nandroid=${Build.VERSION.RELEASE}\nsdk=${Build.VERSION.SDK_INT}\nabi=${Build.SUPPORTED_ABIS.first()}\n"
            "help", "--help", "-h" -> 0 to HELP
            else -> 2 to "android: unknown command '$cmd'\n$HELP"
        }
    }

    private fun notify(context: Context, title: String, body: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Linux apps", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications sent by Linux programs (notify-send, android notify)" })
        val tap = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "display")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
            .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).setContentIntent(tap).build()
        runCatching { nm.notify(notifyId++, n) }
    }

    private fun speak(context: Context, text: String) {
        val t = tts
        if (t != null) { t.speak(text, TextToSpeech.QUEUE_ADD, null, "x11"); return }
        tts = TextToSpeech(context.applicationContext) { status -> if (status == TextToSpeech.SUCCESS) tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "x11") }
    }

    /** URLs open directly; Linux paths are shared with Android apps through the FileProvider. */
    private fun open(context: Context, target: String, share: Boolean): Pair<Int, String> {
        val isUrl = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(target) && !target.startsWith("/")
        val intent = if (isUrl && !share) Intent(Intent.ACTION_VIEW, Uri.parse(target))
        else if (!isUrl && (target.startsWith("/") || File(target).isAbsolute)) {
            val f = File(Linux.rootfs(context), target.trimStart('/'))
            if (!f.exists()) return 1 to "no such file: $target"
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
            val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            if (share) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        } else Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, target)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = (if (share) Intent.createChooser(intent, "Share from Linux") else intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ui {
            try { context.startActivity(chooser) }
            catch (e: Exception) {
                // Android may block opening from the background: offer it as a notification instead.
                notify(context, if (share) "Share from Linux" else "Open from Linux", target)
            }
        }
        return 0 to ""
    }

    private fun clip(context: Context, rest: List<String>): Pair<Int, String> {
        val cm = context.getSystemService(ClipboardManager::class.java)
        return when (rest.firstOrNull()) {
            "set" -> { val t = rest.drop(1).joinToString(" "); ui { cm.setPrimaryClip(ClipData.newPlainText("Linux", t)) }; 0 to "" }
            "get", null -> {
                // Android only lets the foreground app read the clipboard; fall back to what X11 last synced.
                val t = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
                    ?: com.mnvstudio.x11.XHolder.lastFromX
                if (t == null) 1 to "clipboard not readable right now (Android allows it only while X11 is open)" else 0 to t
            }
            else -> 2 to "usage: android clip [get|set TEXT]"
        }
    }

    private val HELP = """usage: android COMMAND [ARGS]
  toast TEXT            short message on screen
  notify TITLE [BODY]   Android notification (notify-send works too)
  vibrate [MS]          vibrate the phone
  say TEXT              speak text aloud
  open URL|FILE         open in an Android app (xdg-open works too)
  share TEXT|FILE       Android share sheet
  clip get | clip set TEXT   phone clipboard (also: echo hi | android clip set)
  battery               level and charging state
  info                  device and Android version
"""

    private val ANDROID_SH = """#!/bin/sh
# Talks to the X11 Android app (installed by it). Try: android help
d=/run/x11-bridge
mkdir -p "${'$'}d" 2>/dev/null
[ "${'$'}1" = clip ] && [ "${'$'}2" = set ] && [ ${'$'}# -eq 2 ] && [ ! -t 0 ] && set -- clip set "${'$'}(cat)"
[ ${'$'}# -eq 0 ] && set -- help
id="${'$'}${'$'}${'$'}{RANDOM:-0}"
for a in "${'$'}@"; do printf '%s\000' "${'$'}a"; done > "${'$'}d/.tmp.${'$'}id" && mv "${'$'}d/.tmp.${'$'}id" "${'$'}d/req.${'$'}id"
i=0
while [ ! -f "${'$'}d/res.${'$'}id" ]; do
  i=${'$'}((i + 1)); [ ${'$'}i -gt 150 ] && { echo "android: the X11 app didn't answer (is it running?)" >&2; rm -f "${'$'}d/req.${'$'}id"; exit 1; }
  sleep 0.1
done
code=${'$'}(head -n 1 "${'$'}d/res.${'$'}id")
tail -n +2 "${'$'}d/res.${'$'}id"
rm -f "${'$'}d/res.${'$'}id"
exit "${'$'}{code:-1}"
"""
}
