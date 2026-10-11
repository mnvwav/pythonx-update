package com.mnvstudio.x11.tools

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mnvstudio.x11.Prefs
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.ui.*
import java.io.File

// ---------------------------------------------------------------- browser

private fun normalize(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") || t.startsWith("file://") -> t
        ' ' !in t && '.' in t -> "https://$t"
        else -> "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(t, "UTF-8")
    }
}

private fun entries(s: String) = s.lines().filter { '|' in it }.map { it.substringBefore('|') to it.substringAfter('|') }

/**
 * A light built-in browser: private mode, light mode (no images or scripts), desktop mode, bookmarks and a reading
 * list, page source, a console for developers, page screenshots, saving pages offline and clearing site data.
 * Downloads go to the download manager.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(prefs: Prefs, start: String, navigate: (String) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var url by remember { mutableStateOf(start.ifEmpty { prefs.browserHome }) }
    var field by remember { mutableStateOf(url) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<String?>(null) }  // bookmarks, reading, source, console
    var source by remember { mutableStateOf("") }
    val console = remember { mutableStateListOf<String>() }
    var toast by remember { mutableStateOf<String?>(null) }

    fun apply(w: WebView) {
        w.settings.apply {
            javaScriptEnabled = !prefs.browserLight
            loadsImagesAutomatically = !prefs.browserLight
            blockNetworkImage = prefs.browserLight
            domStorageEnabled = !prefs.browserPrivate
            cacheMode = if (prefs.browserPrivate) android.webkit.WebSettings.LOAD_NO_CACHE else android.webkit.WebSettings.LOAD_DEFAULT
            useWideViewPort = true; loadWithOverviewMode = true; builtInZoomControls = true; displayZoomControls = false
            userAgentString = if (prefs.browserDesktop) "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36" else null
        }
        CookieManager.getInstance().setAcceptCookie(!prefs.browserPrivate)
    }
    DisposableEffect(Unit) {
        WebView.setWebContentsDebuggingEnabled(true)
        onDispose {
            if (prefs.browserPrivate) { web?.clearHistory(); web?.clearCache(true); CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData() }
            web?.destroy()
        }
    }
    LaunchedEffect(prefs.browserLight, prefs.browserPrivate, prefs.browserDesktop) { web?.let { apply(it); it.reload() } }
    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(2500); toast = null } }

    Column(Modifier.fillMaxSize().background(Page)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.Filled.Close, "Close browser", tint = Ink) }
            OutlinedTextField(field, { field = it }, Modifier.weight(1f), singleLine = true, shape = CircleShape,
                leadingIcon = { Icon(if (prefs.browserPrivate) Icons.Filled.VisibilityOff else if (url.startsWith("https")) Icons.Filled.Lock else Icons.Filled.Public, null, Modifier.size(18.dp)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { web?.loadUrl(normalize(field)) }),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp))
            IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "Browser menu", tint = Ink) }
            DropdownMenu(menu, { menu = false }, containerColor = Card) {
                @Composable fun item(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, action: () -> Unit) =
                    DropdownMenuItem({ Text(text, color = Ink) }, { menu = false; action() }, leadingIcon = { Icon(icon, null, tint = Label) })
                item(Icons.Filled.BookmarkAdd, "Bookmark this page") { prefs.bookmarks = (prefs.bookmarks.lines() + "${title.ifBlank { url }.replace("|", " ")}|$url").filter { it.isNotBlank() }.distinct().joinToString("\n"); toast = "Bookmarked" }
                item(Icons.Filled.Bookmarks, "Bookmarks") { panel = "bookmarks" }
                item(Icons.Filled.PlaylistAdd, "Add to reading list") { prefs.readingList = (prefs.readingList.lines() + "${title.ifBlank { url }.replace("|", " ")}|$url").filter { it.isNotBlank() }.distinct().joinToString("\n"); toast = "Added to reading list" }
                item(Icons.Filled.ChromeReaderMode, "Reading list") { panel = "reading" }
                item(Icons.Filled.Code, "View page source") {
                    web?.evaluateJavascript("document.documentElement.outerHTML") { s -> source = runCatching { org.json.JSONTokener(s).nextValue() as String }.getOrDefault(s); panel = "source" }
                }
                item(Icons.Filled.BugReport, "Developer console") { panel = "console" }
                item(Icons.Filled.Screenshot, "Screenshot page") { web?.let { w -> toast = saveScreenshot(context, w) } }
                item(Icons.Filled.OfflinePin, "Save for offline") {
                    val dir = File(Linux.rootfs(context), prefs.downloadDir.trimStart('/')).apply { mkdirs() }
                    val f = File(dir, (title.ifBlank { "page" }).replace(Regex("[^A-Za-z0-9 ._-]"), "_").take(60) + ".mht")
                    web?.saveWebArchive(f.path); toast = "Saved to ${prefs.downloadDir}/${f.name}"
                }
                item(Icons.Filled.FolderOpen, "Open saved pages") { panel = "offline" }
                item(Icons.Filled.Download, "Downloads") { navigate("downloads") }
                HorizontalDivider(color = Line)
                DropdownMenuItem({ Text("Private mode", color = Ink) }, { prefs.browserPrivate = !prefs.browserPrivate },
                    trailingIcon = { Switch(prefs.browserPrivate, { prefs.browserPrivate = it }) })
                DropdownMenuItem({ Text("Light mode (no images/JS)", color = Ink) }, { prefs.browserLight = !prefs.browserLight },
                    trailingIcon = { Switch(prefs.browserLight, { prefs.browserLight = it }) })
                DropdownMenuItem({ Text("Desktop site", color = Ink) }, { prefs.browserDesktop = !prefs.browserDesktop },
                    trailingIcon = { Switch(prefs.browserDesktop, { prefs.browserDesktop = it }) })
                item(Icons.Filled.Home, "Set as home page") { prefs.browserHome = url; toast = "Home page set" }
                item(Icons.Filled.Cookie, "Clear cookies & site data") {
                    CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData(); web?.clearCache(true); toast = "Cookies and site data cleared"
                }
            }
        }
        if (progress in 1..99) LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth().height(2.dp), color = Accent, trackColor = Color.Transparent)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { ctx ->
                WebView(ctx).also { w ->
                    web = w; apply(w)
                    w.webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) { url = u; field = u }
                        override fun onPageFinished(view: WebView, u: String) { url = u; field = u; title = view.title.orEmpty() }
                    }
                    w.webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, p: Int) { progress = p }
                        override fun onConsoleMessage(m: ConsoleMessage): Boolean { console += "${m.messageLevel()}: ${m.message()} (${m.sourceId().substringAfterLast('/')}:${m.lineNumber()})"; return true }
                    }
                    w.setDownloadListener { u, _, disposition, mime, _ ->
                        val name = android.webkit.URLUtil.guessFileName(u, disposition, mime)
                        Downloads.add(ctx, u, name); toast = "Downloading $name"
                    }
                    w.loadUrl(normalize(url))
                }
            }, modifier = Modifier.fillMaxSize())
            toast?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(CircleShape).background(Color(0xE6222226)).padding(horizontal = 16.dp, vertical = 10.dp)) }
        }
        Row(Modifier.fillMaxWidth().background(Card).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton({ web?.goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
            IconButton({ web?.goForward() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", tint = Ink) }
            IconButton({ web?.reload() }) { Icon(Icons.Filled.Refresh, "Reload", tint = Ink) }
            IconButton({ web?.loadUrl(normalize(prefs.browserHome)) }) { Icon(Icons.Filled.Home, "Home", tint = Ink) }
            IconButton({ panel = "bookmarks" }) { Icon(Icons.Filled.Bookmarks, "Bookmarks", tint = Ink) }
        }
    }

    panel?.let { p ->
        ModalBottomSheet({ panel = null }, containerColor = Card) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                when (p) {
                    "bookmarks", "reading" -> {
                        val isB = p == "bookmarks"
                        Text(if (isB) "Bookmarks" else "Reading list", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        val list = entries(if (isB) prefs.bookmarks else prefs.readingList)
                        if (list.isEmpty()) Text("Nothing saved yet. Use the menu on a page.", color = Label, modifier = Modifier.padding(vertical = 16.dp))
                        list.forEach { (t, u) ->
                            Row(Modifier.fillMaxWidth().clickable { web?.loadUrl(u); panel = null }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(t, color = Ink, maxLines = 1); Text(u, color = Faint, fontSize = 12.sp, maxLines = 1) }
                                IconButton({
                                    val rest = (if (isB) prefs.bookmarks else prefs.readingList).lines().filter { it != "$t|$u" }.joinToString("\n")
                                    if (isB) prefs.bookmarks = rest else prefs.readingList = rest
                                }) { Icon(Icons.Filled.Delete, "Remove", tint = Faint) }
                            }
                        }
                    }
                    "source" -> {
                        Text("Page source", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        SelectionContainer { Text(source.take(200_000), color = Ink, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(vertical = 10.dp)) }
                    }
                    "console" -> {
                        var js by remember { mutableStateOf("document.title") }
                        Text("Developer console", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Also available in desktop Chrome at chrome://inspect", color = Faint, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(js, { js = it }, Modifier.weight(1f).padding(vertical = 8.dp), singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
                            IconButton({ web?.evaluateJavascript(js) { r -> console += "> $js\n< $r" } }) { Icon(Icons.Filled.PlayArrow, "Run", tint = Accent) }
                        }
                        console.takeLast(300).forEach { Text(it, color = if (it.startsWith("ERROR")) Bad else Ink, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    }
                    "offline" -> {
                        Text("Saved pages", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        val dir = File(Linux.rootfs(context), prefs.downloadDir.trimStart('/'))
                        val files = dir.listFiles { f -> f.name.endsWith(".mht") }?.sortedByDescending { it.lastModified() } ?: emptyList()
                        if (files.isEmpty()) Text("No saved pages yet.", color = Label, modifier = Modifier.padding(vertical = 16.dp))
                        files.forEach { f -> Text(f.nameWithoutExtension, color = Ink, modifier = Modifier.fillMaxWidth().clickable { web?.loadUrl("file://${f.path}"); panel = null }.padding(vertical = 12.dp)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun saveScreenshot(context: android.content.Context, w: WebView): String {
    if (w.width == 0) return "Nothing to capture"
    val bmp = Bitmap.createBitmap(w.width, w.height, Bitmap.Config.ARGB_8888)
    w.draw(android.graphics.Canvas(bmp))
    val name = "Web_${System.currentTimeMillis()}.png"
    val v = android.content.ContentValues().apply {
        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name); put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (android.os.Build.VERSION.SDK_INT >= 29) put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/X11")
    }
    val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return "Couldn't save"
    context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return "Saved Pictures/X11/$name"
}

// ---------------------------------------------------------------- downloads

@Composable
fun DownloadsScreen(prefs: Prefs, back: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Downloads.init(context) }
    @Suppress("UNUSED_VARIABLE") val tick = Downloads.tick.intValue
    var url by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Page)) {
        TopBar("Downloads", "Saved to ${prefs.downloadDir} in Linux", onBack = back) {
            if (Downloads.items.any { it.state == Downloads.State.DONE }) RoundIcon(Icons.Filled.ClearAll, "Clear finished") { Downloads.clearFinished() }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(url, { url = it }, Modifier.weight(1f), placeholder = { Text("Paste a link") }, singleLine = true, shape = CircleShape)
                    Spacer(Modifier.width(8.dp))
                    RoundIcon(Icons.Filled.Download, "Download") { if (url.startsWith("http")) { Downloads.add(context, url.trim()); url = "" } }
                }
                SectionTitle("Connections per file")
                Chips(listOf("1", "2", "4", "8"), "${prefs.downloadConnections}") { prefs.downloadConnections = it.toInt() }
                SectionTitle("Queue")
                if (Downloads.items.isEmpty()) Text("No downloads yet. Links you download in the browser show up here too.", color = Label, fontSize = 14.sp)
                Downloads.items.toList().forEach { d -> DownloadRow(d) }
                SectionTitle("Statistics")
                SurfaceCard { Downloads.stats().forEach { (k, v) -> Row(Modifier.padding(vertical = 4.dp)) { Text(k, color = Label, modifier = Modifier.weight(1f)); Text(v, color = Ink) } } }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DownloadRow(d: Downloads.Item) {
    val frac = if (d.total > 0) d.done.toFloat() / d.total else 0f
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(ShapeM).background(Card).border(1.dp, Line, ShapeM).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(when (d.state) {
                    Downloads.State.RUNNING -> "${human(d.done)} of ${if (d.total > 0) human(d.total) else "?"} · ${human(d.speed)}/s · ${d.parts.size} conn."
                    Downloads.State.DONE -> "Done · ${human(d.file.length())}"
                    Downloads.State.FAILED -> "Failed: ${d.error}"
                    Downloads.State.PAUSED -> "Paused · ${human(d.done)}" + if (d.total > 0) " of ${human(d.total)}" else ""
                    Downloads.State.QUEUED -> "Waiting in queue"
                }, color = if (d.state == Downloads.State.FAILED) Bad else Label, fontSize = 12.sp, maxLines = 2)
            }
            when (d.state) {
                Downloads.State.RUNNING, Downloads.State.QUEUED -> IconButton({ Downloads.pause(d) }) { Icon(Icons.Filled.Pause, "Pause", tint = Ink) }
                Downloads.State.PAUSED, Downloads.State.FAILED -> IconButton({ Downloads.resume(d) }) { Icon(Icons.Filled.PlayArrow, "Resume", tint = Accent) }
                else -> {}
            }
            IconButton({ Downloads.remove(d, deleteFile = d.state != Downloads.State.DONE) }) { Icon(Icons.Filled.Close, "Remove", tint = Faint) }
        }
        if (d.state != Downloads.State.DONE && d.total > 0) LinearProgressIndicator({ frac }, Modifier.fillMaxWidth().padding(top = 8.dp), color = Accent, trackColor = CardHigh)
    }
}

// ---------------------------------------------------------------- file sharing

@Composable
fun ShareScreen(back: () -> Unit) {
    val context = LocalContext.current
    var folder by remember { mutableStateOf("/root") }
    var readOnly by remember { mutableStateOf(false) }
    val ip = remember { Net.interfaces().firstOrNull { (n, a) -> !a.startsWith("127.") && '.' in a && (n.startsWith("wlan") || n.startsWith("eth") || n.startsWith("ap")) }?.second?.substringBefore('/') }
    Column(Modifier.fillMaxSize().background(Page)) {
        TopBar("Share files over Wi-Fi", "Browse, download and upload from any browser", onBack = back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp)) {
                if (!FileServer.running) {
                    OutlinedTextField(folder, { folder = it }, Modifier.fillMaxWidth(), label = { Text("Folder to share (inside Linux)") }, singleLine = true)
                    ToggleRow("Read-only", "Others can download but not upload", readOnly) { readOnly = it }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Start sharing", Modifier.fillMaxWidth(), icon = Icons.Filled.Share) {
                        FileServer.readOnly = readOnly
                        runCatching { FileServer.start(File(Linux.rootfs(context), folder.trimStart('/')).apply { mkdirs() }) }
                    }
                    Text("Only devices on the same Wi-Fi can reach it, and only with the link (it contains a random code).", color = Faint, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp))
                } else {
                    val link = "http://${ip ?: "PHONE-IP"}:${FileServer.port}/${FileServer.token}/"
                    Column(Modifier.fillMaxWidth().clip(ShapeL).background(accentGradient()).padding(20.dp)) {
                        Text("Sharing $folder", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Open this on the other device:", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 8.dp))
                        SelectionContainer { Text(link, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp)) }
                        Text("${FileServer.requests} requests so far" + if (FileServer.readOnly) " · read-only" else " · uploads allowed",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (ip == null) Text("Connect to Wi-Fi (or turn on hotspot) so other devices can reach this phone.", color = Warn, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(14.dp))
                    CommandBlock(link, caption = "Copy link")
                    Spacer(Modifier.height(14.dp))
                    SecondaryButton("Stop sharing", Modifier.fillMaxWidth(), icon = Icons.Filled.Stop, color = Bad) { FileServer.stop() }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- markdown

@Composable
fun MarkdownScreen(back: () -> Unit) {
    val context = LocalContext.current
    var path by remember { mutableStateOf("/root/README.md") }
    var text by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Page)) {
        TopBar("Markdown preview", onBack = back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 760.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(path, { path = it }, Modifier.weight(1f), label = { Text("File (inside Linux)") }, singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    RoundIcon(Icons.Filled.PlayArrow, "Open") { text = runCatching { File(Linux.rootfs(context), path.trimStart('/')).readText() }.getOrElse { "Couldn't read: ${it.message}" } }
                }
                Spacer(Modifier.height(16.dp))
                text?.let { Markdown(it) }
            }
        }
    }
}

/** Headings, lists, quotes, code blocks, **bold**, *italic* and `code`: enough for READMEs. */
@Composable
fun Markdown(md: String) {
    var code = false
    md.lines().forEach { raw ->
        if (raw.trimStart().startsWith("```")) { code = !code; return@forEach }
        if (code) { Text(raw, color = Ink, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().background(CardHigh).padding(horizontal = 10.dp)); return@forEach }
        val l = raw.trimEnd()
        when {
            l.startsWith("#") -> { val n = l.takeWhile { it == '#' }.length
                Text(inline(l.drop(n).trim()), color = Ink, fontWeight = FontWeight.Bold, fontSize = (26 - n * 3).coerceAtLeast(15).sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
            l.startsWith("> ") -> Text(inline(l.drop(2)), color = Label, modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
            Regex("^\\s*([-*+]|\\d+\\.) ").containsMatchIn(l) -> Row(Modifier.padding(start = (l.takeWhile { it == ' ' }.length * 6).dp, top = 2.dp)) {
                Text(if (l.trim().first().isDigit()) l.trim().substringBefore(' ') + " " else "•  ", color = Accent); Text(inline(l.trim().substringAfter(' ')), color = Ink) }
            l.matches(Regex("^\\s*([-*_]\\s*){3,}$")) -> HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Line)
            l.isBlank() -> Spacer(Modifier.height(8.dp))
            else -> Text(inline(l), color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

private fun inline(s: String) = buildAnnotatedString {
    val re = Regex("(\\*\\*[^*]+\\*\\*|\\*[^*]+\\*|`[^`]+`|\\[[^]]+]\\([^)]+\\))")
    var i = 0
    re.findAll(s).forEach { m ->
        append(s.substring(i, m.range.first)); val t = m.value
        when {
            t.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t.removeSurrounding("**")) }
            t.startsWith("`") -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22888888))) { append(t.removeSurrounding("`")) }
            t.startsWith("[") -> withStyle(SpanStyle(color = Color(0xFF5B8CFF))) { append(t.substringAfter('[').substringBefore(']')) }
            else -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(t.removeSurrounding("*")) }
        }
        i = m.range.last + 1
    }
    append(s.substring(i))
}
