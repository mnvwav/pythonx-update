package org.softbuddy.pythonx.python

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Package(
    val name: String,
    val version: String = "",
    val summary: String = "",
    /** For PyPI results: true when every release file needs compiling (no pure-Python or Android wheel). */
    val needsCompiler: Boolean = false,
    val homepage: String = "",
)

/** Pure-Python packages that install and work on Android without a compiler. */
val POPULAR_PACKAGES = listOf(
    Package("requests", summary = "Simple, human-friendly HTTP requests."),
    Package("httpx", summary = "Modern HTTP client with sync and async APIs."),
    Package("beautifulsoup4", summary = "Parse and scrape HTML and XML documents."),
    Package("rich", summary = "Colours, tables, markdown and progress bars in the terminal."),
    Package("textual", summary = "Build full terminal user interfaces in Python."),
    Package("click", summary = "Composable command-line interfaces."),
    Package("typer", summary = "CLIs from type hints, built on click."),
    Package("tqdm", summary = "Fast, extensible progress bars for loops."),
    Package("tabulate", summary = "Pretty-print tabular data."),
    Package("python-dateutil", summary = "Powerful extensions to the datetime module."),
    Package("sympy", summary = "Symbolic mathematics: algebra, calculus and more."),
    Package("networkx", summary = "Create and analyse graphs and networks."),
    Package("pygments", summary = "Syntax highlighting for source code."),
    Package("faker", summary = "Generate fake names, addresses and other test data."),
    Package("pyfiglet", summary = "Render text as large ASCII-art letters."),
    Package("emoji", summary = "Emoji for Python strings."),
    Package("markdown", summary = "Convert Markdown text to HTML."),
    Package("bottle", summary = "Tiny single-file web framework (run a local server)."),
)

/** pip and PyPI. Commands are run in a terminal by the Packages screen so the user sees live output. */
class Pip(private val python: Python) {

    /** PEP 503 normalisation, so "Python_DateUtil" and "python-dateutil" match. */
    fun normalize(name: String) = name.lowercase().replace(Regex("[-_.]+"), "-")

    /** Reads the *.dist-info folders in site-packages; no Python start-up needed. */
    fun installed(): List<Package> {
        val children = python.sitePackages.listFiles() ?: return emptyList()
        return children.filter { it.isDirectory && it.name.endsWith(".dist-info") }.mapNotNull { info ->
            val metadata = File(info, "METADATA").takeIf { it.isFile } ?: return@mapNotNull null
            var name: String? = null; var version = ""; var summary = ""; var home = ""
            metadata.useLines { lines ->
                for (line in lines) {
                    when {
                        line.isEmpty() -> break
                        line.startsWith("Name: ") -> name = line.removePrefix("Name: ").trim()
                        line.startsWith("Version: ") -> version = line.removePrefix("Version: ").trim()
                        line.startsWith("Summary: ") -> summary = line.removePrefix("Summary: ").trim()
                        line.startsWith("Home-page: ") -> home = line.removePrefix("Home-page: ").trim()
                    }
                }
            }
            name?.let { Package(it, version, summary, homepage = home) }
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** Looks up a package by its exact name on PyPI; null when it doesn't exist. */
    fun lookup(name: String): Package? {
        val url = URL("https://pypi.org/pypi/${URLEncoder.encode(name.trim(), "UTF-8")}/json")
        val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 10000; readTimeout = 15000 }
        try {
            if (conn.responseCode == 404) return null
            if (conn.responseCode != 200) throw IOException("PyPI returned ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val info = json.getJSONObject("info")
            val files = json.optJSONArray("urls") ?: JSONArray()
            val wheels = (0 until files.length()).map { files.getJSONObject(it).getString("filename") }.filter { it.endsWith(".whl") }
            // A pure wheel (-none-any) or an Android wheel installs without building anything.
            val easy = wheels.any { it.endsWith("-none-any.whl") || it.contains("android") }
            val sdistOnly = wheels.isEmpty()
            return Package(
                info.getString("name"), info.optString("version"), info.optString("summary"),
                needsCompiler = !easy && !sdistOnly,
                homepage = info.optJSONObject("project_urls")?.let { u -> u.optString("Homepage").ifBlank { u.optString("Source") } }
                    ?: info.optString("home_page"),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Packages with a newer release, as name to (installed, latest). Needs network. */
    fun outdated(): Map<String, Pair<String, String>> {
        val (code, out) = python.run(listOf("-m", "pip", "list", "--outdated", "--format=json"), timeoutSec = 120)
        if (code != 0) throw IOException(out.lines().lastOrNull { it.isNotBlank() } ?: "pip failed")
        val json = JSONArray(out.lines().first { it.trimStart().startsWith("[") })
        return (0 until json.length()).associate {
            val o = json.getJSONObject(it)
            o.getString("name") to (o.getString("version") to o.getString("latest_version"))
        }
    }

    fun installCommand(vararg specs: String) = "python3 -m pip install ${specs.joinToString(" ") { quote(it) }}"
    fun upgradeCommand(vararg specs: String) = "python3 -m pip install --upgrade ${specs.joinToString(" ") { quote(it) }}"
    fun uninstallCommand(name: String) = "python3 -m pip uninstall -y ${quote(name)}"
    fun requirementsCommand(file: File) = "python3 -m pip install -r ${quote(file.path)}"

    companion object {
        fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"
    }
}
