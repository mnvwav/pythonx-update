package com.mnvstudio.x11.bridge

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnvstudio.x11.AppLog
import com.mnvstudio.x11.linux.Linux
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Sound for Linux apps, like XSDL's built-in PulseAudio: PulseAudio runs inside Linux and plays into a silent
 * "Android" sink; its monitor is streamed as raw 48 kHz stereo PCM over a local socket into an AudioTrack.
 * PulseAudio apps find it through PULSE_SERVER, ALSA apps through /etc/asound.conf.
 */
object Sound {
    const val PORT = 4714
    const val SOCKET = "/tmp/x11-pulse.sock"
    val PACKAGES = listOf("pulseaudio", "pulseaudio-utils", "alsa-plugins-pulse")

    var state by mutableStateOf("off"); private set
    @Volatile private var running = false
    private var daemon: Process? = null
    private var reader: Thread? = null

    fun installed(context: Context) = File(Linux.rootfs(context), "usr/bin/pulseaudio").exists()

    /** Variables for every Linux program so it plays through us. */
    fun env(context: Context): Map<String, String> = if (running || installed(context)) mapOf("PULSE_SERVER" to "unix:$SOCKET") else emptyMap()

    @Synchronized
    fun start(context: Context) {
        if (running) return
        val app = context.applicationContext
        if (Linux.state != Linux.State.READY) return
        running = true
        Thread {
            try {
                if (!installed(app)) {
                    state = "installing"
                    val done = java.util.concurrent.CountDownLatch(1)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Linux.capture(app, "apk update -q >/dev/null 2>&1; for p in ${PACKAGES.joinToString(" ")}; do apk add \$p >/dev/null 2>&1; done") { _, _ -> done.countDown() }
                    }
                    done.await(10, java.util.concurrent.TimeUnit.MINUTES)
                    if (!installed(app)) { state = "unavailable"; running = false; return@Thread }
                }
                writeConfig(app)
                state = "starting"
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    daemon = Linux.run(app, "rm -f $SOCKET; exec pulseaudio -n -F /etc/x11-pulse.pa --exit-idle-time=-1 --daemonize=no " +
                        "--disallow-exit --disable-shm=yes --log-level=error", 0) { code -> if (running) AppLog.e("PulseAudio exited ($code)") }
                }
                stream()
            } catch (e: Exception) { AppLog.e("Sound", e) }
        }.also { reader = it }.start()
    }

    private fun writeConfig(context: Context) {
        val root = Linux.rootfs(context)
        File(root, "etc/x11-pulse.pa").writeText("""
            # Written by the X11 app: sound from Linux apps goes to Android.
            load-module module-native-protocol-unix auth-anonymous=1 socket=$SOCKET
            load-module module-null-sink sink_name=android sink_properties=device.description=Android rate=48000
            set-default-sink android
            load-module module-simple-protocol-tcp listen=127.0.0.1 port=$PORT source=android.monitor record=true playback=false rate=48000 format=s16le channels=2
        """.trimIndent() + "\n")
        File(root, "etc/asound.conf").writeText("pcm.!default { type pulse }\nctl.!default { type pulse }\n")
        File(root, "etc/pulse").mkdirs()
        File(root, "etc/pulse/client.conf").writeText("default-server = unix:$SOCKET\nenable-shm = no\nautospawn = no\n")
    }

    /** Connects to PulseAudio's PCM stream and plays it; reconnects if PulseAudio restarts. */
    private fun stream() {
        val rate = 48000
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min * 2, 16384)).setTransferMode(AudioTrack.MODE_STREAM)
            .apply { if (android.os.Build.VERSION.SDK_INT >= 26) setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) }
            .build()
        track.play()
        val buf = ByteArray(8192)
        try {
            while (running) {
                val s = runCatching { Socket().apply { connect(InetSocketAddress("127.0.0.1", PORT), 1000); tcpNoDelay = true } }.getOrNull()
                if (s == null) { Thread.sleep(500); continue }
                state = "on"
                AppLog.i("Sound connected")
                s.use { sock ->
                    val inp = sock.getInputStream()
                    while (running) { val n = inp.read(buf); if (n < 0) break; track.write(buf, 0, n and 3.inv()) }
                }
                if (running) state = "reconnecting"
            }
        } finally { track.stop(); track.release() }
    }

    fun stop() {
        running = false
        daemon?.destroy(); daemon = null
        state = "off"
    }
}
