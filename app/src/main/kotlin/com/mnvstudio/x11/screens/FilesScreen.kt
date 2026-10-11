package com.mnvstudio.x11.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnvstudio.x11.AppLog
import com.mnvstudio.x11.linux.Archive
import com.mnvstudio.x11.linux.Linux
import com.mnvstudio.x11.ui.Accent
import com.mnvstudio.x11.ui.Bad
import com.mnvstudio.x11.ui.Card
import com.mnvstudio.x11.ui.CardHigh
import com.mnvstudio.x11.ui.CodeBg
import com.mnvstudio.x11.ui.Confirm
import com.mnvstudio.x11.ui.Faint
import com.mnvstudio.x11.ui.Ink
import com.mnvstudio.x11.ui.Label
import com.mnvstudio.x11.ui.Mono
import com.mnvstudio.x11.ui.RoundIcon
import com.mnvstudio.x11.ui.ShapeS
import com.mnvstudio.x11.ui.TopBar
import java.io.File
import java.nio.file.Files

/** Linux path → file on Android storage, refusing paths outside the profile. */
private fun resolve(root: File, path: String): File? {
    val f = File(root, path.trimStart('/'))
    return f.takeIf { it.canonicalPath.startsWith(root.canonicalPath) || Files.isSymbolicLink(it.toPath()) }
}

private fun human(bytes: Long) = when {
    bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1048576f)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/** Browse the Linux files: open, rename, delete, create, and move files between Android and Linux. */
@Composable
fun FilesScreen(start: String, navigate: (String) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val root = Linux.rootfs(context)
    var path by rememberSaveable { mutableStateOf(start.ifEmpty { "/root" }) }
    var refresh by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<Pair<String, File?>?>(null) }   // kind to target
    var deleting by remember { mutableStateOf<File?>(null) }
    var exporting by remember { mutableStateOf<File?>(null) }
    val dir = resolve(root, path)
    val entries = remember(path, refresh, Linux.revision) {
        (dir?.listFiles()?.toList() ?: emptyList()).sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        val target = dir ?: return@rememberLauncherForActivityResult
        Thread {
            var n = 0
            for (u in uris) runCatching {
                val name = context.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "file"
                context.contentResolver.openInputStream(u)?.use { i -> File(target, name.replace('/', '_')).outputStream().use { i.copyTo(it) } }
                n++
            }.onFailure { AppLog.e("Import failed", it) }
            android.os.Handler(android.os.Looper.getMainLooper()).post { refresh++; Toast.makeText(context, "Imported $n file(s)", Toast.LENGTH_SHORT).show() }
        }.start()
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val f = exporting ?: return@rememberLauncherForActivityResult
        exporting = null
        if (uri == null) return@rememberLauncherForActivityResult
        Thread {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } } }.isSuccess
            android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context, if (ok) "Exported ${f.name}" else "Export failed", Toast.LENGTH_SHORT).show() }
        }.start()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Files", path, onBack = { if (path != "/" && path.isNotEmpty()) path = path.substringBeforeLast('/').ifEmpty { "/" } else back() }) {
            RoundIcon(Icons.Filled.Upload, "Import from Android") { importer.launch(arrayOf("*/*")) }
            RoundIcon(Icons.Filled.CreateNewFolder, "New folder") { dialog = "folder" to null }
            RoundIcon(Icons.Filled.NoteAdd, "New file") { dialog = "file" to null }
        }
        // Path crumbs.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val parts = listOf("/") + path.split('/').filter { it.isNotEmpty() }
            parts.forEachIndexed { i, p ->
                Text(if (i == 0) "/" else "$p/", color = if (i == parts.lastIndex) Ink else Accent, fontFamily = Mono, fontSize = 13.sp,
                    modifier = Modifier.clip(ShapeS).clickable { path = "/" + parts.drop(1).take(i).joinToString("/") }.padding(6.dp))
            }
        }
        if (Linux.state != Linux.State.READY) Text("Set up Linux first.", color = Label, modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (entries.isEmpty()) item { Text("Empty folder", color = Faint, modifier = Modifier.padding(20.dp)) }
            items(entries, key = { it.name }) { f ->
                var menu by remember { mutableStateOf(false) }
                val link = Files.isSymbolicLink(f.toPath())
                Row(Modifier.fillMaxWidth().clickable {
                    val p = (path.trimEnd('/') + "/" + f.name)
                    if (f.isDirectory) path = p else navigate("editor?$p")
                }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(when { link -> Icons.Filled.Link; f.isDirectory -> Icons.Filled.Folder; else -> Icons.AutoMirrored.Filled.InsertDriveFile }, null,
                        tint = if (f.isDirectory) Accent else Label)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(f.name, color = Ink, fontSize = 15.sp, maxLines = 1)
                        Text(if (link) "→ " + runCatching { Files.readSymbolicLink(f.toPath()).toString() }.getOrDefault("?")
                            else if (f.isDirectory) "folder" else human(f.length()), color = Faint, fontSize = 12.sp, fontFamily = Mono)
                    }
                    Box {
                        RoundIcon(Icons.Filled.MoreVert, "More for ${f.name}", tint = Label) { menu = true }
                        DropdownMenu(menu, { menu = false }, containerColor = Card) {
                            if (f.isFile) DropdownMenuItem(text = { Text("Export to Android", color = Ink) }, leadingIcon = { Icon(Icons.Filled.Download, null, tint = Label) },
                                onClick = { menu = false; exporting = f; exporter.launch(f.name) })
                            DropdownMenuItem(text = { Text("Rename", color = Ink) }, leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null, tint = Label) },
                                onClick = { menu = false; dialog = "rename" to f })
                            DropdownMenuItem(text = { Text("Delete", color = Bad) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Bad) },
                                onClick = { menu = false; deleting = f })
                        }
                    }
                }
            }
        }
    }
    dialog?.let { (kind, target) ->
        NameDialog(when (kind) { "folder" -> "New folder"; "file" -> "New file"; else -> "Rename" }, target?.name ?: "", onDismiss = { dialog = null }) { name ->
            val d = dir ?: return@NameDialog
            val ok = when (kind) {
                "folder" -> File(d, name).mkdirs()
                "file" -> runCatching { File(d, name).createNewFile() }.getOrDefault(false)
                else -> target?.renameTo(File(d, name)) == true
            }
            if (!ok) Toast.makeText(context, "Couldn't do that (name taken?)", Toast.LENGTH_SHORT).show()
            dialog = null; refresh++
        }
    }
    deleting?.let { f -> Confirm("Delete ${f.name}?", if (f.isDirectory) "The folder and everything in it is deleted." else "This can't be undone.", "Delete",
        danger = true, onDismiss = { deleting = null }) { Archive.deleteTree(f); refresh++ } }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val valid = name.isNotBlank() && '/' !in name && name != "." && name != ".."
    AlertDialog(onDismissRequest = onDismiss, containerColor = Card, title = { Text(title, color = Ink) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, textStyle = TextStyle(fontFamily = Mono, color = Ink)) },
        confirmButton = { TextButton({ onOk(name.trim()) }, enabled = valid) { Text("OK", color = Accent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Label) } })
}

/** A plain-text and code editor for files in Linux, with line numbers, search and save. */
@Composable
fun EditorScreen(path: String, back: () -> Unit) {
    val context = LocalContext.current
    val file = resolve(Linux.rootfs(context), path)
    val tooBig = (file?.length() ?: 0) > 2 * 1024 * 1024
    var text by remember(path) { mutableStateOf(if (file == null || tooBig) "" else runCatching { file.readText() }.getOrDefault("")) }
    var saved by remember(path) { mutableStateOf(text) }
    var find by remember { mutableStateOf("") }
    var confirmLeave by remember { mutableStateOf(false) }
    val dirty = text != saved
    fun save() {
        if (file == null) return
        runCatching { file.writeText(text) }.onSuccess { saved = text; Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "Couldn't save: ${it.message}", Toast.LENGTH_LONG).show() }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar((if (dirty) "• " else "") + path.substringAfterLast('/'), path, onBack = { if (dirty) confirmLeave = true else back() }) {
            RoundIcon(Icons.Filled.Save, "Save", tint = if (dirty) Accent else Label) { save() }
        }
        when {
            file == null -> Text("That file is outside Linux.", color = Bad, modifier = Modifier.padding(20.dp))
            tooBig -> Text("This file is larger than 2 MB, too big to edit here. Use the terminal (vi or nano).", color = Label, modifier = Modifier.padding(20.dp))
            else -> {
                OutlinedTextField(find, { find = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
                    placeholder = { Text("Find", color = Faint) }, textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Ink),
                    supportingText = { if (find.isNotEmpty()) Text("${Regex(Regex.escape(find), RegexOption.IGNORE_CASE).findAll(text).count()} matches", color = Faint) })
                val lines = text.count { it == '\n' } + 1
                Row(Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(ShapeS).background(CodeBg)
                    .verticalScroll(rememberScrollState())) {
                    Text((1..lines).joinToString("\n"), color = Faint, fontFamily = Mono, fontSize = 13.sp, lineHeight = 19.sp,
                        modifier = Modifier.background(CardHigh).padding(horizontal = 8.dp, vertical = 10.dp))
                    BasicTextField(text, { text = it }, Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(10.dp),
                        textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 19.sp, color = Ink, fontWeight = FontWeight.Normal),
                        cursorBrush = SolidColor(Accent))
                }
                Text("${text.length} characters · $lines lines" + if (dirty) " · unsaved" else "", color = Faint, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
            }
        }
    }
    if (confirmLeave) Confirm("Leave without saving?", "Your changes will be lost.", "Leave", danger = true, onDismiss = { confirmLeave = false }) { back() }
}
