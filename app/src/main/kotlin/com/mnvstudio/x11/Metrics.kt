package com.mnvstudio.x11

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Resource monitor. Android hides the phone-wide CPU counters from apps, but an app can read its own processes,
 * which include the built-in Linux and everything it runs. So CPU is "X11 + Linux", RAM and storage are phone-wide.
 */
object Metrics {
    class Sample(val cpu: Float, val ramUsedPct: Float, val appRssMb: Long, val time: Long)

    /** A process belonging to this app (the app itself, proot, or a Linux program). */
    data class Proc(val pid: Int, val name: String, val cmdline: String, val cpu: Float, val rssMb: Float, val state: String)

    const val HISTORY = 90

    var latest by mutableStateOf<Sample?>(null); private set
    var history by mutableStateOf<List<Sample>>(emptyList()); private set
    var processes by mutableStateOf<List<Proc>>(emptyList()); private set
    var ramTotalMb by mutableStateOf(0L); private set
    var ramAvailMb by mutableStateOf(0L); private set
    var lowMemory by mutableStateOf(false); private set
    var storageFreeMb by mutableStateOf(0L); private set
    var storageTotalMb by mutableStateOf(0L); private set

    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var users = 0
    private var lastJiffies = HashMap<Int, Long>()
    private var lastTime = 0L
    private val tick = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(100L).coerceAtLeast(1)
    private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    private var warnedDisk = false
    private var warnedRam = false

    /** Screens showing metrics call start/stop so sampling only runs while someone looks. */
    fun start(context: Context) {
        users++
        if (running) return
        running = true
        val app = context.applicationContext
        val loop = object : Runnable {
            override fun run() {
                if (!running) return
                Thread { sample(app) }.start()
                main.postDelayed(this, 2000)
            }
        }
        main.post(loop)
    }

    fun stop() { users = (users - 1).coerceAtLeast(0); if (users == 0) running = false }

    private fun sample(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val st = runCatching { StatFs(context.filesDir.path) }.getOrNull()
        val procs = readProcesses()
        val now = SystemClock.elapsedRealtime()
        val dt = (now - lastTime).coerceAtLeast(1)
        val cpuOf = HashMap<Int, Float>()
        val jiffies = HashMap<Int, Long>()
        for ((pid, j) in procs.map { it.first to it.second }) {
            jiffies[pid] = j
            val prev = lastJiffies[pid]
            if (prev != null && lastTime > 0) cpuOf[pid] = ((j - prev) * 1000f / tick / dt * 100f / cores).coerceIn(0f, 100f)
        }
        lastJiffies = jiffies; lastTime = now
        val list = procs.map { (pid, _, p) -> p.copy(cpu = cpuOf[pid] ?: 0f) }.sortedByDescending { it.cpu }
        val totalCpu = list.sumOf { it.cpu.toDouble() }.toFloat().coerceAtMost(100f)
        val ramUsed = if (mi.totalMem > 0) (1f - mi.availMem.toFloat() / mi.totalMem) * 100f else 0f
        val appRss = list.sumOf { it.rssMb.toDouble() }.toLong()
        val s = Sample(totalCpu, ramUsed, appRss, System.currentTimeMillis())
        val free = st?.availableBytes?.div(1 shl 20) ?: 0
        val total = st?.totalBytes?.div(1 shl 20) ?: 0
        main.post {
            latest = s
            history = (history + s).takeLast(HISTORY)
            processes = list
            ramTotalMb = mi.totalMem / (1 shl 20); ramAvailMb = mi.availMem / (1 shl 20)
            lowMemory = mi.lowMemory || mi.availMem < mi.totalMem / 10
            storageFreeMb = free; storageTotalMb = total
            if (free in 1 until 500 && !warnedDisk) { warnedDisk = true; AppLog.e("Low storage: $free MB free") }
            if (lowMemory && !warnedRam) { warnedRam = true; AppLog.e("Low memory: ${mi.availMem / (1 shl 20)} MB available") }
        }
    }

    /** Our own processes from /proc (Android only shows an app its own). Triple of pid, CPU jiffies, details. */
    private fun readProcesses(): List<Triple<Int, Long, Proc>> {
        val me = android.os.Process.myUid()
        val out = ArrayList<Triple<Int, Long, Proc>>()
        val pageKb = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) / 1024 }.getOrDefault(4L)
        File("/proc").listFiles()?.forEach { dir ->
            val pid = dir.name.toIntOrNull() ?: return@forEach
            runCatching {
                if (Os.stat(dir.path).st_uid != me) return@forEach
                val stat = File(dir, "stat").readText()
                // Fields after the ")" that closes the command name: state is 3rd, utime 14th, stime 15th.
                val name = stat.substring(stat.indexOf('(') + 1, stat.lastIndexOf(')'))
                val f = stat.substring(stat.lastIndexOf(')') + 2).split(' ')
                val jiffies = f[11].toLong() + f[12].toLong()
                val rssPages = File(dir, "statm").readText().split(' ')[1].toLong()
                val cmd = runCatching { File(dir, "cmdline").readText().replace('\u0000', ' ').trim() }.getOrDefault("")
                out += Triple(pid, jiffies, Proc(pid, name, cmd, 0f, rssPages * pageKb / 1024f, f[0]))
            }
        }
        return out
    }

    /** Stops one of our processes (Linux programs or proot). */
    fun kill(pid: Int): Boolean = runCatching { Os.kill(pid, OsConstants.SIGTERM); true }.getOrDefault(false)

    /** Size of a directory tree in bytes; slow, run off the main thread. */
    fun sizeOf(dir: File): Long = dir.walkTopDown().onFail { _, _ -> }.filter { it.isFile }.sumOf { it.length() }
}
