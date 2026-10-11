package org.softbuddy.pythonx.python

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * The bundled CPython runtime: the official python.org Android build, packaged by tools/build-python.sh.
 * Native libraries live in nativeLibraryDir; the standard library is unpacked once into filesDir/PyEnv.
 */
class Python(private val ctx: Context) {
    val home = File(ctx.filesDir, HOME_DIR)
    val bin = File(home, "bin")
    val sitePackages = File(home, "lib/python$SHORT/site-packages")
    private val stamp = File(home, ".version")
    private val nativeDir = File(ctx.applicationInfo.nativeLibraryDir)
    /** The `python3` executable. */
    val launcher = File(nativeDir, LAUNCHER)
    val caBundle = File(home, "etc/ssl/cert.pem")

    val installed get() = File(home, "lib/python$SHORT/os.py").isFile && stamp.takeIf { it.isFile }?.readText()?.trim() == STAMP

    val pipInstalled get() = File(sitePackages, "pip").isDirectory

    /**
     * Unpacks the standard library if this version isn't installed yet. A patch update (3.14.x) keeps site-packages;
     * a different minor version, or a leftover pre-3.14 install, starts clean because compiled modules aren't compatible.
     */
    @Synchronized
    fun install() {
        if (!installed) {
            val previous = stamp.takeIf { it.isFile }?.readText()?.trim().orEmpty()
            if (previous.substringBeforeLast('.') == SHORT) File(home, "lib/python$SHORT").listFiles()
                ?.filter { it.name != "site-packages" }?.forEach { it.deleteRecursively() }
            else home.deleteRecursively()
            // Remove the Python home used by PythonX 1.x.
            File(ctx.filesDir, "PY_HOME").deleteRecursively()
            installProgress = 0f
            extract(File(nativeDir, STDLIB_ARCHIVE), home)
            stamp.writeText(STAMP)
        }
        prepare()
        installProgress = 1f
    }

    /** Must run on every start: the native library directory moves on each app update. */
    fun prepare() {
        bin.mkdirs()
        for (name in listOf("python", "python3", "python$SHORT")) {
            val link = File(bin, name)
            link.delete()
            try {
                Os.symlink(launcher.path, link.path)
            } catch (e: ErrnoException) {
                throw IOException("Unable to link $name", e)
            }
        }
        writeCaBundle()
    }

    /** Environment for every Python process and shell started by the app. */
    fun env(): Map<String, String> = mapOf(
        "PATH" to "${bin.path}:${System.getenv("PATH") ?: "/system/bin"}",
        "PYTHONHOME" to home.path,
        "LD_LIBRARY_PATH" to nativeDir.path,
        "HOME" to home.path,
        "TMPDIR" to ctx.cacheDir.path,
        // Keep __pycache__ out of the user's project folders.
        "PYTHONPYCACHEPREFIX" to File(ctx.cacheDir, "pycache").path,
        "SSL_CERT_FILE" to caBundle.path,
        "REQUESTS_CA_BUNDLE" to caBundle.path,
        "PIP_DISABLE_PIP_VERSION_CHECK" to "1",
        "PIP_NO_WARN_SCRIPT_LOCATION" to "1",
        "LANG" to "en_US.UTF-8",
        "PYTHONIOENCODING" to "utf-8",
    )

    /** Starts `python3 args…` with captured output (stderr merged into stdout). */
    fun process(args: List<String>, cwd: File = home): Process =
        ProcessBuilder(listOf(launcher.path) + args).directory(cwd).redirectErrorStream(true)
            .apply { environment().putAll(env()) }.start()

    /** Runs `python3 args…` to completion and returns its exit code and output. */
    fun run(args: List<String>, cwd: File = home, stdin: String? = null, timeoutSec: Long = 60): Pair<Int, String> {
        val p = process(args, cwd)
        Thread { runCatching { p.outputStream.use { o -> stdin?.let { o.write(it.toByteArray()) } } } }.start()
        val out = StringBuilder()
        val reader = Thread { runCatching { out.append(p.inputStream.bufferedReader().readText()) } }.apply { start() }
        val done = p.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
        if (!done) p.destroyForcibly()
        reader.join(2000)
        return (if (done) p.exitValue() else -1) to out.toString()
    }

    /** Installs pip into site-packages from the bundled wheel, if missing. */
    fun ensurePip(): Boolean = pipInstalled || run(listOf("-m", "ensurepip", "--default-pip"), timeoutSec = 300).first == 0 && pipInstalled

    /**
     * Android names its CA files with OpenSSL's legacy subject hash, which OpenSSL 3 cannot look up,
     * so concatenate them into a single PEM bundle instead.
     */
    private fun writeCaBundle() {
        val dirs = listOf("/apex/com.android.conscrypt/cacerts", "/system/etc/security/cacerts")
        val certs = dirs.map { File(it) }.firstOrNull { it.isDirectory }?.listFiles() ?: return
        caBundle.parentFile?.mkdirs()
        caBundle.bufferedWriter().use { out ->
            for (cert in certs.sortedBy { it.name }) {
                val text = runCatching { cert.readText() }.getOrNull() ?: continue
                val start = text.indexOf(BEGIN)
                val end = text.indexOf(END)
                if (start < 0 || end < 0) continue
                out.write(text, start, end + END.length - start)
                out.write("\n")
            }
        }
    }

    private fun extract(archive: File, destination: File) {
        val root = destination.canonicalPath + File.separator
        ZipFile(archive).use { zip ->
            val entries = zip.entries().toList()
            entries.forEachIndexed { i, entry ->
                val target = File(destination, entry.name)
                if (!target.canonicalPath.startsWith(root)) throw IOException("Blocked zip entry outside target: ${entry.name}")
                if (entry.isDirectory) target.mkdirs()
                else {
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                }
                if (i % 16 == 0) installProgress = (i + 1f) / entries.size * 0.98f
            }
        }
    }

    companion object {
        const val VERSION = "3.14.8"
        const val SHORT = "3.14"
        /** Bump when the packaged standard library changes (e.g. bundled tzdata) without a Python version change. */
        private const val REVISION = 2
        private const val STAMP = "$VERSION+$REVISION"
        const val HOME_DIR = "PyEnv"
        private const val LAUNCHER = "libpythonx.so"
        private const val STDLIB_ARCHIVE = "libpystdlib.so"
        private const val BEGIN = "-----BEGIN CERTIFICATE-----"
        private const val END = "-----END CERTIFICATE-----"

        /** 0..1 while the standard library unpacks; observed by onboarding and the editor. */
        var installProgress by mutableFloatStateOf(0f)
    }
}
