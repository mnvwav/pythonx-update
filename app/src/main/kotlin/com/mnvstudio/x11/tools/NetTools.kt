package com.mnvstudio.x11.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mnvstudio.x11.Launcher
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.linux.Linux
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

internal object NetToolDefs {
    private const val TEST = "Network tests"
    private const val WEB = "Web & HTTP"
    private const val INFO = "Network info"
    private const val REMOTE = "Remote & sharing"

    private fun host(default: String = "example.com") = Field("host", "Host", default)

    val all: List<ToolDef> = listOf(
        // ---------------------------------------------------------------- tests
        ToolDef("connectivity", "Internet check", TEST, Icons.Filled.Wifi, "Is the internet reachable, and how fast does it answer?", view = View.TABLE,
            run = native { ctx, _, out -> Net.connectivity(ctx).forEach { (k, v) -> out.row(k, v) } }),
        ToolDef("ping", "Ping (latency)", TEST, Icons.Filled.NetworkPing, "Round-trip time to a host, measured with TCP connects",
            listOf(host("1.1.1.1"), Field("port", "Port", "443"), Field("count", "Count", "10")), View.BARS,
            run = native { _, v, out -> Net.ping(v["host"]!!, v["port"]?.toIntOrNull() ?: 443, v["count"]?.toIntOrNull() ?: 10, out) }),
        ToolDef("speed", "Speed test", TEST, Icons.Filled.Speed, "Download and upload speed (Cloudflare)", listOf(Field("mb", "Download size (MB)", "25")), View.TABLE,
            run = native { _, v, out -> Net.speed(v["mb"]?.toIntOrNull() ?: 25, out) }),
        ToolDef("port", "Port check", TEST, Icons.Filled.Cable, "Can this phone open a connection to host:port?", listOf(host(), Field("ports", "Ports (comma separated)", "22,80,443")),
            View.TABLE, run = native { _, v, out ->
                v["ports"].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.forEach { p ->
                    val t0 = System.nanoTime()
                    val ok = runCatching { Socket().use { it.connect(InetSocketAddress(v["host"], p), 3000) } }
                    out.row("$p", if (ok.isSuccess) "open (${(System.nanoTime() - t0) / 1_000_000} ms)" else "closed: ${ok.exceptionOrNull()?.message}")
                }
            }),
        ToolDef("monitor", "Website monitor", TEST, Icons.Filled.MonitorHeart, "Checks a site repeatedly and reports outages",
            listOf(Field("url", "URL", "https://example.com"), Field("every", "Every (seconds)", "30"), Field("times", "Checks", "20")),
            run = native { _, v, out ->
                repeat(v["times"]?.toIntOrNull() ?: 20) { i ->
                    if (out.cancelled) return@native
                    val r = runCatching { Net.request("HEAD", v["url"]!!, emptyMap(), null, follow = true) }
                    val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                    out.line("$t  " + r.fold({ "UP  ${it.code}  ${it.ms} ms" }, { "DOWN  ${it.message}" }))
                    if (i < (v["times"]?.toIntOrNull() ?: 20) - 1) Thread.sleep((v["every"]?.toLongOrNull() ?: 30) * 1000)
                }
            }),
        ToolDef("troubleshoot", "Network troubleshooter", TEST, Icons.Filled.HealthAndSafety, "Step-by-step: link, DNS, internet, HTTPS, Linux network, X11 port",
            keywords = "assistant diagnose recovery", run = native { ctx, _, out -> Net.troubleshoot(ctx, out) }),

        // ---------------------------------------------------------------- web
        ToolDef("browser", "Web browser", WEB, Icons.Filled.Public, "Built-in browser: tabs, private mode, bookmarks, source, dev tools",
            route = "browser", keywords = "lightweight private bookmarks reading list cookies source screenshot offline devtools"),
        ToolDef("downloads", "Download manager", WEB, Icons.Filled.Download, "Pause/resume, queue, multi-connection, history and stats",
            route = "downloads", keywords = "accelerator queue"),
        ToolDef("http", "HTTP / REST client", WEB, Icons.Filled.Api, "Send any request: method, headers, body; see status, headers, timing",
            listOf(Field("method", "Method", "GET", choices = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")),
                Field("url", "URL", "https://httpbin.org/get"), Field("headers", "Headers (Name: value per line)", "Accept: application/json", multiline = true),
                Field("body", "Body", "", multiline = true)), keywords = "api test request",
            run = native { _, v, out ->
                val headers = v["headers"].orEmpty().lines().filter { ':' in it }.associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
                val r = Net.request(v["method"]!!, v["url"]!!, headers, v["body"].takeIf { !it.isNullOrBlank() }, follow = true)
                out.line("${r.code} ${r.message}   ·   ${r.ms} ms   ·   ${human(r.body.size.toLong())}")
                out.line(""); r.headers.forEach { (k, vs) -> out.line("$k: ${vs.joinToString()}") }; out.line("")
                val text = String(r.body, Charsets.UTF_8)
                (if (text.trimStart().startsWith("{") || text.trimStart().startsWith("[")) DataFormat.pretty(text).drop(2) else text.lines()).take(2000).forEach(out::line)
            }),
        ToolDef("headers", "HTTP headers", WEB, Icons.Filled.ListAlt, "Response headers of a URL", listOf(Field("url", "URL", "https://example.com")), View.TABLE,
            run = native { _, v, out -> val r = Net.request("GET", v["url"]!!, emptyMap(), null, follow = false); out.row("Status", "${r.code} ${r.message}"); r.headers.forEach { (k, vs) -> out.row(k, vs.joinToString()) } }),
        ToolDef("redirects", "Redirect inspector", WEB, Icons.Filled.Redo, "Follows every redirect step and shows where a link really goes",
            listOf(Field("url", "URL", "http://github.com")), run = native { _, v, out ->
                var url = v["url"]!!
                repeat(15) { i ->
                    val r = Net.request("HEAD", url, emptyMap(), null, follow = false)
                    out.line("${i + 1}. ${r.code}  $url  (${r.ms} ms)")
                    val next = r.headers.entries.firstOrNull { it.key.equals("Location", true) }?.value?.firstOrNull() ?: return@native
                    url = URL(URL(url), next).toString()
                }
                out.line("Stopped after 15 redirects")
            }),
        ToolDef("tls", "TLS certificate", WEB, Icons.Filled.VerifiedUser, "Who issued a site's certificate, validity, protocol and cipher", listOf(host("example.com")), View.TABLE,
            keywords = "ssl https security", run = native { _, v, out -> Net.tls(v["host"]!!).forEach { (k, x) -> out.row(k, x) } }),
        ToolDef("websocket", "WebSocket tester", WEB, Icons.Filled.SyncAlt, "Connect, send a message and read replies",
            listOf(Field("url", "URL", "wss://ws.postman-echo.com/raw"), Field("msg", "Message", "hello from X11")),
            run = native { _, v, out -> WebSocketTester.run(v["url"]!!, v["msg"].orEmpty(), out) }),

        // ---------------------------------------------------------------- info
        ToolDef("ipinfo", "IP addresses", INFO, Icons.Filled.Router, "Interfaces, local and public addresses", view = View.TABLE,
            keywords = "network interface information public ip", run = native { ctx, _, out -> Net.interfaces().forEach { (k, x) -> out.row(k, x) }; out.row("Public IPv4", Net.publicIp()) }),
        ToolDef("dns", "DNS lookup", INFO, Icons.Filled.Dns, "System resolver and DNS-over-HTTPS (Cloudflare), any record type",
            listOf(host("example.com"), Field("type", "Record", "A", choices = listOf("A", "AAAA", "MX", "TXT", "NS", "CNAME", "SOA"))), View.TABLE,
            keywords = "doh resolver", run = native { _, v, out -> Net.dns(v["host"]!!, v["type"] ?: "A").forEach { (k, x) -> out.row(k, x) } }),
        ToolDef("wifi", "Wi-Fi & connection", INFO, Icons.Filled.NetworkWifi, "Network type, speed, VPN, metered, signal", view = View.TABLE,
            keywords = "vpn compatibility", run = native { ctx, _, out -> Net.connection(ctx).forEach { (k, x) -> out.row(k, x) } }),
        ToolDef("traffic", "Live traffic", INFO, Icons.Filled.ShowChart, "Download/upload speed every second (all apps and X11)",
            listOf(Field("secs", "Seconds", "20")), View.BARS, keywords = "bandwidth graph monitor interface",
            run = native { _, v, out ->
                var rx = TrafficStats.getTotalRxBytes(); var tx = TrafficStats.getTotalTxBytes()
                repeat(v["secs"]?.toIntOrNull() ?: 20) { i ->
                    if (out.cancelled) return@native
                    Thread.sleep(1000)
                    val r = TrafficStats.getTotalRxBytes(); val t = TrafficStats.getTotalTxBytes()
                    out.line("${r - rx}\t${i + 1}s  ↓ ${human(r - rx)}/s  ↑ ${human(t - tx)}/s"); rx = r; tx = t
                }
            }),
        ToolDef("usage", "Data usage", INFO, Icons.Filled.DataUsage, "Data used since boot: whole phone vs X11 and its Linux apps", view = View.TABLE,
            keywords = "statistics dashboard per-app", run = native { ctx, _, out ->
                val uid = ctx.applicationInfo.uid
                out.row("Phone ↓ since boot", human(TrafficStats.getTotalRxBytes())); out.row("Phone ↑ since boot", human(TrafficStats.getTotalTxBytes()))
                out.row("X11 + Linux ↓", human(TrafficStats.getUidRxBytes(uid))); out.row("X11 + Linux ↑", human(TrafficStats.getUidTxBytes(uid)))
            }),
        ToolDef("lan", "Find devices on Wi-Fi", INFO, Icons.Filled.DevicesOther, "Scans your local network for devices with open services",
            listOf(Field("ports", "Ports", "22,80,443,445,5900,6000,8080")), View.TABLE, keywords = "discovery scan",
            run = native { _, v, out -> Net.lanScan(v["ports"].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }, out) }),
        ToolDef("proxy", "Proxy for Linux", INFO, Icons.Filled.VpnLock, "Set http(s)_proxy for every Linux app (empty = off)",
            listOf(Field("url", "Proxy URL", "", "http://192.168.1.10:3128")), run = native { ctx, v, out ->
                val env = Linux.userEnv(ctx).toMutableMap()
                val p = v["url"].orEmpty().trim()
                listOf("http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY").forEach { if (p.isEmpty()) env.remove(it) else env[it] = p }
                Linux.saveUserEnv(ctx, env)
                out.line(if (p.isEmpty()) "Proxy removed for Linux apps." else "Linux apps will use $p (newly started apps).")
            }),

        // ---------------------------------------------------------------- remote & sharing
        ToolDef("share", "Share files over Wi-Fi", REMOTE, Icons.Filled.Share, "Local web server: browse, download and upload Linux files from any browser",
            route = "share", keywords = "http server web server file transfer browser-based"),
        ToolDef("ssh", "SSH", REMOTE, Icons.Filled.Terminal, "Open a remote shell in a terminal on the display",
            listOf(Field("target", "user@host", "user@192.168.1.20"), Field("port", "Port", "22"), Field("x", "Forward X11 apps here", "No", choices = listOf("No", "Yes"))),
            keywords = "remote linux connection manager secure x11", run = sshLike("ssh") { v -> "ssh ${if (v["x"] == "Yes") "-Y " else ""}-p ${v["port"]} ${q(v["target"])}" }),
        ToolDef("sftp", "SFTP / SCP", REMOTE, Icons.Filled.DriveFileMove, "Transfer files to and from another computer securely",
            listOf(Field("target", "user@host", "user@192.168.1.20"), Field("port", "Port", "22")),
            keywords = "secure file transfer", run = sshLike("sftp") { v -> "sftp -P ${v["port"]} ${q(v["target"])}" }),
        ToolDef("remotex", "Remote X11 setup", REMOTE, Icons.Filled.Cast, "Show apps from another computer on this phone",
            run = native { ctx, _, out -> Net.remoteXGuide(ctx).forEach(out::line) }),
    )

    /** Runs an interactive network program in a terminal window on the display. */
    private fun sshLike(cmd: String, command: (Map<String, String>) -> String): (Context, Map<String, String>, Out) -> Unit = { ctx, v, out ->
        if (Linux.state != Linux.State.READY) { out.line("Set up Linux first."); out.done() }
        else Linux.capture(ctx, "command -v $cmd >/dev/null || apk add -q openssh-client xterm font-dejavu") { _, _ ->
            if (!XHolder.ensureStarted(ctx)) { out.line(XHolder.error ?: "Display didn't start"); out.done(); return@capture }
            Linux.run(ctx, "xterm -fa 'DejaVu Sans Mono' -fs 11 -e ${q(command(v))}", XHolder.display, jobName = cmd)
            out.line("Opened $cmd in a terminal on the display. Open the display to use it."); out.done()
        }
    }
}

/** Plain-JDK networking used by the tools. */
object Net {
    class Response(val code: Int, val message: String, val headers: Map<String, List<String>>, val body: ByteArray, val ms: Long)

    fun request(method: String, url: String, headers: Map<String, String>, body: String?, follow: Boolean, maxBody: Int = 2_000_000): Response {
        val t0 = System.nanoTime()
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = follow; c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.requestMethod = method
        c.setRequestProperty("User-Agent", "X11-Android/1.0")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null && method != "GET" && method != "HEAD") { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        val stream = runCatching { if (code >= 400) c.errorStream else c.inputStream }.getOrNull()
        val bytes = stream?.use { s -> val o = java.io.ByteArrayOutputStream(); val b = ByteArray(16384); var n = 0
            while (o.size() < maxBody && s.read(b).also { n = it } > 0) o.write(b, 0, n); o.toByteArray() } ?: ByteArray(0)
        val r = Response(code, c.responseMessage ?: "", c.headerFields.filterKeys { it != null }, bytes, (System.nanoTime() - t0) / 1_000_000)
        c.disconnect(); return r
    }

    fun ping(host: String, port: Int, count: Int, out: Out) {
        val addr = InetAddress.getByName(host)
        out.line("0\tPinging ${addr.hostAddress}:$port")
        val times = ArrayList<Long>()
        repeat(count) { i ->
            if (out.cancelled) return
            val t0 = System.nanoTime()
            val ok = runCatching { Socket().use { it.connect(InetSocketAddress(addr, port), 3000) } }.isSuccess
            val ms = (System.nanoTime() - t0) / 1_000_000
            if (ok) { times += ms; out.line("$ms\t#${i + 1}: $ms ms") } else out.line("0\t#${i + 1}: timeout")
            Thread.sleep(500)
        }
        if (times.isNotEmpty()) out.line("0\tmin ${times.min()} / avg ${times.average().toLong()} / max ${times.max()} ms, ${count - times.size} lost")
    }

    fun speed(mb: Int, out: Out) {
        val bytes = mb * 1_000_000L
        out.row("Server", "speed.cloudflare.com")
        val lat = (1..3).map { val t = System.nanoTime(); request("GET", "https://speed.cloudflare.com/__down?bytes=0", emptyMap(), null, true); (System.nanoTime() - t) / 1_000_000 }.min()
        out.row("Latency", "$lat ms")
        val t0 = System.nanoTime()
        val c = URL("https://speed.cloudflare.com/__down?bytes=$bytes").openConnection() as HttpURLConnection
        var got = 0L
        c.inputStream.use { s -> val b = ByteArray(65536); var n: Int; while (s.read(b).also { n = it } > 0 && !out.cancelled) got += n }
        val secs = (System.nanoTime() - t0) / 1e9
        out.row("Download", "%.1f Mbit/s (%s in %.1f s)".format(got * 8 / secs / 1e6, human(got), secs))
        val up = ByteArray(5_000_000)
        val t1 = System.nanoTime()
        runCatching { request("POST", "https://speed.cloudflare.com/__up", mapOf("Content-Type" to "application/octet-stream"), String(up, Charsets.ISO_8859_1), true) }
        val s2 = (System.nanoTime() - t1) / 1e9
        out.row("Upload", "%.1f Mbit/s (5 MB in %.1f s)".format(up.size * 8 / s2 / 1e6, s2))
    }

    fun connectivity(context: Context): List<Pair<String, String>> {
        val r = ArrayList<Pair<String, String>>()
        r += connection(context).take(2)
        r += "DNS (example.com)" to runCatching { val t = System.nanoTime(); InetAddress.getByName("example.com").hostAddress + " in ${(System.nanoTime() - t) / 1_000_000} ms" }.getOrElse { "failed: ${it.message}" }
        r += "HTTPS (google 204)" to runCatching { request("GET", "https://www.google.com/generate_204", emptyMap(), null, false).let { "${it.code} in ${it.ms} ms" } }.getOrElse { "failed: ${it.message}" }
        r += "Alpine mirror" to runCatching { request("HEAD", "https://dl-cdn.alpinelinux.org/alpine/", emptyMap(), null, true).let { "${it.code} in ${it.ms} ms" } }.getOrElse { "failed: ${it.message}" }
        return r
    }

    fun connection(context: Context): List<Pair<String, String>> {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            ?: return listOf("Connected" to "no network")
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }
        val r = mutableListOf("Connected" to type, "Internet validated" to "${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}",
            "VPN active" to "${caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)} (Linux apps use it too)",
            "Metered" to "${!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)}",
            "Bandwidth estimate" to "↓ ${caps.linkDownstreamBandwidthKbps / 1000} Mbit/s · ↑ ${caps.linkUpstreamBandwidthKbps / 1000} Mbit/s")
        if (type == "Wi-Fi") runCatching {
            @Suppress("DEPRECATION") val info = context.applicationContext.getSystemService(WifiManager::class.java).connectionInfo
            r += "Wi-Fi signal" to "${info.rssi} dBm"; r += "Wi-Fi link speed" to "${info.linkSpeed} Mbit/s"; r += "Wi-Fi frequency" to "${info.frequency} MHz"
        }
        cm.getLinkProperties(cm.activeNetwork)?.let { lp ->
            r += "DNS servers" to lp.dnsServers.joinToString { it.hostAddress ?: "" }
            r += "Private DNS" to (if (android.os.Build.VERSION.SDK_INT >= 28) (lp.privateDnsServerName ?: if (lp.isPrivateDnsActive) "automatic" else "off") else "-")
        }
        return r
    }

    fun interfaces(): List<Pair<String, String>> = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp }.flatMap { ni ->
        ni.interfaceAddresses.map { a -> ni.displayName to "${a.address.hostAddress?.substringBefore('%')}/${a.networkPrefixLength}" }
    }

    fun publicIp(): String = runCatching {
        String(request("GET", "https://www.cloudflare.com/cdn-cgi/trace", emptyMap(), null, true).body).lines()
            .associate { it.substringBefore('=') to it.substringAfter('=') }.let { "${it["ip"]} (${it["loc"]})" }
    }.getOrElse { "unavailable: ${it.message}" }

    fun dns(host: String, type: String): List<Pair<String, String>> {
        val r = ArrayList<Pair<String, String>>()
        if (type == "A" || type == "AAAA") runCatching { InetAddress.getAllByName(host).forEach { r += "System" to it.hostAddress.orEmpty() } }
            .onFailure { r += "System" to "failed: ${it.message}" }
        runCatching {
            val res = request("GET", "https://cloudflare-dns.com/dns-query?name=${URLEncoder.encode(host, "UTF-8")}&type=$type",
                mapOf("Accept" to "application/dns-json"), null, true)
            val j = JSONObject(String(res.body))
            r += "DoH status" to (if (j.optInt("Status") == 0) "OK in ${res.ms} ms" else "error ${j.optInt("Status")}")
            j.optJSONArray("Answer")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { r += "DoH $type (ttl ${it.optInt("TTL")})" to it.optString("data") } }
        }.onFailure { r += "DoH" to "failed: ${it.message}" }
        return r
    }

    fun tls(host: String): List<Pair<String, String>> {
        val s = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(host.substringBefore(':'), host.substringAfter(':', "443").toInt()) as javax.net.ssl.SSLSocket
        s.use {
            it.soTimeout = 10_000; it.startHandshake()
            val sess = it.session
            val c = sess.peerCertificates[0] as X509Certificate
            val fp = MessageDigest.getInstance("SHA-256").digest(c.encoded).joinToString(":") { b -> "%02X".format(b) }
            val days = (c.notAfter.time - System.currentTimeMillis()) / 86_400_000
            return listOf("Protocol" to sess.protocol, "Cipher" to sess.cipherSuite, "Subject" to c.subjectX500Principal.name,
                "Issuer" to c.issuerX500Principal.name, "Valid from" to "${c.notBefore}", "Valid until" to "${c.notAfter} ($days days left)",
                "Names" to (c.subjectAlternativeNames?.joinToString { n -> n[1].toString() } ?: "-"),
                "Key" to "${c.publicKey.algorithm}", "Signature" to c.sigAlgName, "SHA-256" to fp, "Chain" to "${sess.peerCertificates.size} certificates")
        }
    }

    fun lanScan(ports: List<Int>, out: Out) {
        val me = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }.firstOrNull { it.address is Inet4Address && it.networkPrefixLength in 16..30 }
            ?: run { out.line("Not connected to a local network"); return }
        val base = me.address.hostAddress!!.substringBeforeLast('.')
        out.row("Scanning", "$base.1–254 on ${ports.joinToString()}")
        val pool = Executors.newFixedThreadPool(48)
        for (i in 1..254) pool.execute {
            if (out.cancelled) return@execute
            val ip = "$base.$i"
            val open = ports.filter { p -> runCatching { Socket().use { it.connect(InetSocketAddress(ip, p), 400) } }.isSuccess }
            if (open.isNotEmpty()) {
                val name = runCatching { InetAddress.getByName(ip).canonicalHostName }.getOrNull()?.takeIf { it != ip }
                out.row(ip + (if (ip == me.address.hostAddress) " (this phone)" else ""), (name?.let { "$it · " } ?: "") + "open: " + open.joinToString())
            }
        }
        pool.shutdown(); pool.awaitTermination(60, TimeUnit.SECONDS)
        out.row("Done", "")
    }

    fun troubleshoot(context: Context, out: Out) {
        fun step(name: String, block: () -> String) = out.line(runCatching { "✓ $name: ${block()}" }.getOrElse { "✗ $name: ${it.message}" })
        val conn = connection(context)
        out.line((if (conn.first().second == "no network") "✗" else "✓") + " Network: ${conn.first().second}")
        step("DNS") { InetAddress.getByName("dl-cdn.alpinelinux.org").hostAddress!! }
        step("Internet") { request("GET", "https://www.google.com/generate_204", emptyMap(), null, false).code.toString() }
        step("HTTPS to Alpine") { request("HEAD", "https://dl-cdn.alpinelinux.org/alpine/", emptyMap(), null, true).code.toString() }
        step("X11 display port") { val d = XHolder.display; Socket().use { it.connect(InetSocketAddress("127.0.0.1", 6000 + d), 1000) }; "127.0.0.1:${6000 + d} accepts connections" }
        if (Linux.state == Linux.State.READY) {
            val lock = Object(); var res = ""
            Linux.capture(context, "wget -q -T 8 -O /dev/null http://dl-cdn.alpinelinux.org/alpine/ && echo ok || echo failed") { _, o -> res = o.lastOrNull().orEmpty(); synchronized(lock) { lock.notify() } }
            synchronized(lock) { lock.wait(15_000) }
            out.line((if (res == "ok") "✓" else "✗") + " Network inside Linux: ${res.ifEmpty { "no answer" }}")
            if (res != "ok") out.line("  → Run Diagnostics → Run test & repair; it rewrites DNS and switches mirrors.")
        }
        out.line("Done.")
    }

    fun remoteXGuide(context: Context): List<String> {
        val ip = interfaces().firstOrNull { !it.second.startsWith("127.") && '.' in it.second }?.second?.substringBefore('/') ?: "PHONE-IP"
        val d = XHolder.display
        return listOf(
            "Safest: over SSH from this phone (Tools → SSH, \"Forward X11 apps here\" = Yes), then run any app on the other computer.",
            "", "Or directly over Wi-Fi (trusted networks only):",
            "1. Settings → Network → Allow other devices: on, and add the computer's IP to Allowed addresses.",
            "2. On the computer: DISPLAY=$ip:$d xterm",
            "", "Reverse SSH tunnel (works through firewalls):",
            "  ssh -R 6010:127.0.0.1:${6000 + d} user@computer   then on the computer: DISPLAY=127.0.0.1:10 xterm",
        )
    }
}

/** A minimal RFC 6455 client: enough to test that a server answers. */
object WebSocketTester {
    fun run(url: String, msg: String, out: Out) {
        val u = java.net.URI(url)
        val tls = u.scheme == "wss"
        val port = if (u.port > 0) u.port else if (tls) 443 else 80
        val sock: Socket = if (tls) SSLSocketFactory.getDefault().createSocket(u.host, port) else Socket(u.host, port)
        sock.use { s ->
            s.soTimeout = 10_000
            val key = android.util.Base64.encodeToString(java.util.UUID.randomUUID().toString().toByteArray().copyOf(16), android.util.Base64.NO_WRAP)
            val path = (u.rawPath.ifEmpty { "/" }) + (u.rawQuery?.let { "?$it" } ?: "")
            s.getOutputStream().write(("GET $path HTTP/1.1\r\nHost: ${u.host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray())
            val inp = s.getInputStream()
            val status = generateSequence { readLine(inp) }.takeWhile { it.isNotEmpty() }.toList()
            out.line("< " + status.firstOrNull())
            if (status.firstOrNull()?.contains("101") != true) { status.drop(1).forEach { out.line("< $it") }; return }
            out.line("Connected. Sending: $msg")
            val data = msg.toByteArray(); val mask = ByteArray(4).also { java.util.Random().nextBytes(it) }
            val frame = java.io.ByteArrayOutputStream()
            frame.write(0x81)
            if (data.size < 126) frame.write(0x80 or data.size) else { frame.write(0x80 or 126); frame.write(data.size shr 8); frame.write(data.size and 0xFF) }
            frame.write(mask); data.forEachIndexed { i, b -> frame.write(b.toInt() xor mask[i % 4].toInt()) }
            s.getOutputStream().write(frame.toByteArray())
            repeat(5) {
                val b0 = inp.read(); if (b0 < 0) return
                var len = inp.read() and 0x7F
                if (len == 126) len = (inp.read() shl 8) or inp.read() else if (len == 127) { repeat(8) { _ -> inp.read() }; out.line("(large frame)"); return }
                val payload = ByteArray(len).also { var o = 0; while (o < len) o += inp.read(it, o, len - o) }
                out.line("> " + if (b0 and 0x0F == 1) String(payload) else "(opcode ${b0 and 0x0F}, $len bytes)")
                if (inp.available() == 0) { Thread.sleep(800); if (inp.available() == 0) return }
            }
        }
    }

    private fun readLine(i: java.io.InputStream): String {
        val sb = StringBuilder(); while (true) { val c = i.read(); if (c < 0 || c == '\n'.code) break; if (c != '\r'.code) sb.append(c.toChar()) }; return sb.toString()
    }
}
