package com.mnvstudio.x11.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mnvstudio.x11.AppLog
import com.mnvstudio.x11.XHolder
import com.mnvstudio.x11.linux.Linux
import java.io.File

/** Tools that work on files and the Linux system; most run a short command inside Alpine. */
internal object LinuxToolDefs {
    private const val FILES = "Files"
    private const val SYSTEM = "System"
    private const val PACKAGES = "Packages"
    private const val TEXT = "Text & data"
    private const val DISPLAY = "Display & input"
    private const val HISTORY = "History & help"

    private val path = Field("path", "Folder or file (inside Linux)", "/root")

    val all: List<ToolDef> = listOf(
        // ---------------------------------------------------------------- files
        ToolDef("extract", "Extract archive", FILES, Icons.Filled.Unarchive, "Unpack zip, tar, tar.gz, tar.xz, 7z",
            listOf(Field("file", "Archive", "/root/archive.zip"), Field("to", "Extract to", "/root")),
            run = linux("p7zip:7z") { v ->
                val f = q(v["file"]); val to = q(v["to"])
                "mkdir -p $to && case ${f} in *.zip) unzip -o $f -d $to;; *.7z) 7z x -y -o$to $f;; *) tar -xvf $f -C $to;; esac && echo Done"
            }),
        ToolDef("compress", "Create archive", FILES, Icons.Filled.Archive, "Pack a folder into tar.gz, tar.xz or zip",
            listOf(Field("src", "Folder or file", "/root"), Field("out", "Archive to create", "/root/backup.tar.gz"),
                Field("fmt", "Format", "tar.gz", choices = listOf("tar.gz", "tar.xz", "zip"))),
            run = linux("zip:zip") { v ->
                val src = File(v["src"] ?: "/root"); val out = q(v["out"])
                when (v["fmt"]) {
                    "zip" -> "cd ${q(src.parent ?: "/")} && zip -r $out ${q(src.name)}"
                    "tar.xz" -> "tar -cJvf $out -C ${q(src.parent ?: "/")} ${q(src.name)}"
                    else -> "tar -czvf $out -C ${q(src.parent ?: "/")} ${q(src.name)}"
                } + " && ls -la $out"
            }),
        ToolDef("disk", "Disk space map", FILES, Icons.Filled.PieChart, "What uses space, as bars; tap deeper with another folder",
            listOf(path.let { Field(it.key, it.label, "/") }), View.BARS, keywords = "storage analyzer usage du",
            run = linux { v -> "du -k -d 1 ${q(v["path"])} 2>/dev/null | sort -rn | head -25 | awk -F'\\t' '{printf \"%d\\t%s\\n\", \$1*1024, \$2}'" }),
        ToolDef("large", "Large files", FILES, Icons.Filled.FilePresent, "Biggest files under a folder", listOf(path, Field("min", "Larger than (MB)", "10")), View.BARS,
            run = linux { v -> "find ${q(v["path"])} -xdev -type f -size +${(v["min"] ?: "10").toIntOrNull() ?: 10}M -exec du -k {} + 2>/dev/null | sort -rn | head -50 | awk -F'\\t' '{printf \"%d\\t%s\\n\", \$1*1024, \$2}'" }),
        ToolDef("dupes", "Duplicate files", FILES, Icons.Filled.FileCopy, "Files with identical contents (by MD5)", listOf(path),
            run = linux { v -> "find ${q(v["path"])} -xdev -type f -size +0 -exec md5sum {} + 2>/dev/null | sort | awk '{h=\$1; \$1=\"\"; if (h==p) {if (!s) print \"\\n\" pf; print \$0; s=1} else s=0; p=h; pf=\$0}'; echo; echo Done" }),
        ToolDef("hash", "Checksums", FILES, Icons.Filled.Fingerprint, "MD5, SHA-1, SHA-256 and SHA-512 of a file", listOf(Field("file", "File", "/root/file")), View.TABLE,
            keywords = "hash verify", run = linux { v -> val f = q(v["file"]); listOf("md5", "sha1", "sha256", "sha512").joinToString("; ") { "printf '$it\\t'; ${it}sum $f | cut -d' ' -f1" } }),
        ToolDef("perms", "Permissions", FILES, Icons.Filled.Lock, "See and change owner and rwx bits (e.g. 755)",
            listOf(Field("file", "File or folder", "/root"), Field("mode", "New mode (empty = just show)", "", "755"), Field("owner", "New owner (empty = keep)", "", "root:root")),
            run = linux { v ->
                val f = q(v["file"])
                (if (!v["mode"].isNullOrBlank()) "chmod ${q(v["mode"])} $f && " else "") + (if (!v["owner"].isNullOrBlank()) "chown ${q(v["owner"])} $f && " else "") +
                    "stat -c '%A  %a  %U:%G  %n' $f && echo && echo 'r=4 w=2 x=1 · owner/group/others, e.g. 755 = rwxr-xr-x'"
            }),
        ToolDef("links", "Symbolic links", FILES, Icons.Filled.Link, "List links under a folder, or create one",
            listOf(path, Field("target", "Create: link points to (empty = list)", ""), Field("name", "Create: link name", "")),
            run = linux { v ->
                if (!v["target"].isNullOrBlank()) "ln -sfn ${q(v["target"])} ${q(v["name"])} && ls -la ${q(v["name"])}"
                else "find ${q(v["path"])} -xdev -type l 2>/dev/null | head -200 | while read l; do echo \"\$l -> \$(readlink \"\$l\")\$( [ -e \"\$l\" ] || echo '  (broken)')\"; done"
            }),
        ToolDef("search", "Find files", FILES, Icons.Filled.ManageSearch, "By name, or by text inside files",
            listOf(path, Field("name", "Name contains", ""), Field("text", "Text inside (optional)", "")), keywords = "grep content index",
            run = linux { v ->
                val p = q(v["path"])
                if (!v["text"].isNullOrBlank()) "grep -rIl -- ${q(v["text"])} $p 2>/dev/null | head -300"
                else "find $p -xdev -iname ${q("*" + (v["name"] ?: "") + "*")} 2>/dev/null | head -300"
            }),
        ToolDef("rename", "Batch rename", FILES, Icons.Filled.DriveFileRenameOutline, "Rename many files with find/replace (dry run first)",
            listOf(path, Field("from", "Replace", "IMG_"), Field("to", "With", "photo_"), Field("apply", "Mode", "Preview", choices = listOf("Preview", "Rename"))),
            run = linux { v ->
                val mv = if (v["apply"] == "Rename") "mv -n \"\$f\" \"\$d/\$n\"; " else ""
                "cd ${q(v["path"])} && for f in *; do case \"\$f\" in *${(v["from"] ?: "").replace("'", "")}*) d=.; n=\$(echo \"\$f\" | sed \"s/${(v["from"] ?: "").replace("/", "\\/")}/${(v["to"] ?: "").replace("/", "\\/")}/g\"); echo \"\$f  ->  \$n\"; $mv;; esac; done"
            }),
        ToolDef("diff", "Compare files", FILES, Icons.Filled.Difference, "Line-by-line differences (unified diff)",
            listOf(Field("a", "First file", "/root/a.txt"), Field("b", "Second file", "/root/b.txt")),
            run = linux { v -> "diff -u ${q(v["a"])} ${q(v["b"])} && echo 'The files are identical'" }),
        ToolDef("open-android", "Open with Android", FILES, Icons.Filled.OpenInNew, "View images, PDFs and documents in your phone's apps",
            route = "files", keywords = "image viewer pdf document"),

        // ---------------------------------------------------------------- text & data
        ToolDef("format", "JSON / XML / YAML viewer", TEXT, Icons.Filled.DataObject, "Pretty-print and validate a file or pasted text",
            listOf(Field("file", "File (or leave empty and paste below)", ""), Field("text", "Text", "", multiline = true)), keywords = "config syntax checker",
            run = native { ctx, v, out ->
                val text = v["file"].takeIf { !it.isNullOrBlank() }?.let { File(Linux.rootfs(ctx), it.trimStart('/')).readText() } ?: v["text"].orEmpty()
                DataFormat.pretty(text).forEach(out::line)
            }),
        ToolDef("syntax", "Config syntax check", TEXT, Icons.Filled.Rule, "Checks JSON, shell scripts, Python and YAML/INI files",
            listOf(Field("file", "File", "/etc/profile")),
            run = linux("python3:python3") { v ->
                val f = q(v["file"])
                "case $f in *.json) python3 -m json.tool $f >/dev/null && echo 'Valid JSON';; *.py) python3 -m py_compile $f && echo 'Valid Python';; " +
                    "*.yml|*.yaml) python3 -c \"import sys;exec('try:\\n import yaml\\nexcept ImportError:\\n print(\\'install py3-yaml for full checks\\');sys.exit(0)');yaml.safe_load(open(sys.argv[1]));print('Valid YAML')\" $f;; " +
                    "*.ini|*.conf|*.cfg) python3 -c \"import configparser,sys;c=configparser.ConfigParser(strict=False);c.read(sys.argv[1]);print('Readable INI with',len(c.sections()),'sections')\" $f;; " +
                    "*) sh -n $f && echo 'Valid shell syntax';; esac"
            }),
        ToolDef("markdown", "Markdown preview", TEXT, Icons.Filled.Article, "Render a Markdown file", route = "markdown", keywords = "md readme"),

        // ---------------------------------------------------------------- system
        ToolDef("sysinfo", "System information", SYSTEM, Icons.Filled.PhoneAndroid, "Phone, CPU, memory, Android and Linux versions", view = View.TABLE,
            keywords = "hardware device capability", run = native { ctx, _, out -> SystemInfo.report(ctx).forEach { (k, v) -> out.row(k, v) } }),
        ToolDef("kernel", "Kernel & architecture", SYSTEM, Icons.Filled.Memory, "uname, CPU flags, page size, ABI",
            run = linux { "uname -a; echo; echo \"Arch: \$(uname -m)\"; echo \"Page size: \$(getconf PAGESIZE 2>/dev/null)\"; echo; grep -m1 -iE 'features|flags' /proc/cpuinfo; cat /proc/version" }),
        ToolDef("env", "Environment variables", SYSTEM, Icons.Filled.Tune, "Everything Linux programs see", view = View.TABLE,
            run = linux { "env | sort | sed 's/=/\\t/'" }),
        ToolDef("envdiff", "Compare environments", SYSTEM, Icons.Filled.CompareArrows, "Differences in variables and packages between two profiles",
            listOf(Field("a", "Profile A", "default"), Field("b", "Profile B", "")),
            run = native { ctx, v, out -> ProfileCompare.compare(ctx, v["a"].orEmpty(), v["b"].orEmpty()).forEach(out::line) }),
        ToolDef("services", "Services", SYSTEM, Icons.Filled.SettingsSuggest, "OpenRC services: list, start, stop, restart",
            listOf(Field("name", "Service (empty = list)", ""), Field("act", "Action", "status", choices = listOf("status", "start", "stop", "restart"))),
            keywords = "daemon openrc", run = linux("openrc:rc-service") { v ->
                if (v["name"].isNullOrBlank()) "rc-status -a 2>/dev/null || ls /etc/init.d" else "rc-service ${q(v["name"])} ${v["act"]}"
            }),
        ToolDef("cron", "Scheduled tasks (cron)", SYSTEM, Icons.Filled.Schedule, "See and edit cron jobs; runs while Linux is running",
            listOf(Field("jobs", "Crontab (empty = show current)", "", "*/30 * * * * /root/backup.sh", multiline = true)),
            keywords = "timer schedule", run = linux { v ->
                if (v["jobs"].isNullOrBlank()) "crontab -l 2>/dev/null || echo 'No jobs yet'; echo; echo 'min hour day month weekday command'; echo 'Start the scheduler with: crond'"
                else "printf '%s\\n' ${q(v["jobs"])} | crontab - && echo Saved && (pgrep crond >/dev/null || crond) && crontab -l"
            }),
        ToolDef("ldd", "Library dependencies", SYSTEM, Icons.Filled.AccountTree, "Which shared libraries a program needs (and which are missing)",
            listOf(Field("file", "Program", "/usr/bin/xterm")),
            run = linux { v -> "f=\$(command -v ${q(v["file"])} || echo ${q(v["file"])}); ldd \"\$f\" 2>&1 | sed 's/(0x[0-9a-f]*)//'" }),
        ToolDef("elf", "ELF inspector", SYSTEM, Icons.Filled.Memory, "Header, architecture, sections and symbols of a binary",
            listOf(Field("file", "Program or library", "/bin/busybox")),
            run = linux("binutils:readelf", "file:file") { v -> "f=\$(command -v ${q(v["file"])} || echo ${q(v["file"])}); file \"\$f\"; echo; readelf -h \"\$f\"; echo; readelf -d \"\$f\" 2>/dev/null | head -40" }),
        ToolDef("ports", "Listening ports", SYSTEM, Icons.Filled.Lan, "Services listening for connections (X11 is 6000+N)",
            keywords = "netstat sockets", run = linux { "netstat -tlnu 2>/dev/null || cat /proc/net/tcp" }),

        // ---------------------------------------------------------------- packages
        ToolDef("repo", "Repository browser", PACKAGES, Icons.Filled.Inventory2, "Search every Alpine package with descriptions",
            listOf(Field("q", "Search", "editor")), run = linux { v -> "apk search -v -d ${q(v["q"])} | head -200" }),
        ToolDef("cache", "Package cache", PACKAGES, Icons.Filled.Cached, "See and clear downloaded package files",
            listOf(Field("act", "Action", "Show", choices = listOf("Show", "Clean"))),
            run = linux { v -> (if (v["act"] == "Clean") "rm -rf /var/cache/apk/* && echo Cleared; " else "") + "du -sh /var/cache/apk 2>/dev/null; ls /var/cache/apk 2>/dev/null | head -50" }),

        // ---------------------------------------------------------------- display & input
        ToolDef("screenshot", "Screenshot", DISPLAY, Icons.Filled.Screenshot, "Save the X11 screen as a PNG in Pictures/X11", keywords = "capture",
            run = native { ctx, _, out -> out.line(DisplayTools.screenshot(ctx)) }),
        ToolDef("color", "Color picker", DISPLAY, Icons.Filled.Colorize, "Color of any pixel on the X11 screen",
            listOf(Field("x", "X", "100"), Field("y", "Y", "100")), View.TABLE, keywords = "inspector eyedropper",
            run = native { _, v, out -> DisplayTools.pixel(v["x"]?.toIntOrNull() ?: 0, v["y"]?.toIntOrNull() ?: 0).forEach { (k, c) -> out.row(k, c) } }),
        ToolDef("caps", "Display capabilities", DISPLAY, Icons.Filled.Monitor, "Resolution, depth, DPI, extensions, fonts of this server", view = View.TABLE,
            keywords = "x11 extension information", run = native { _, _, out -> DisplayTools.capabilities().forEach { (k, c) -> out.row(k, c) } }),
        ToolDef("fonts", "Font test", DISPLAY, Icons.Filled.TextFields, "Fonts installed in Linux, with a sample in each",
            run = linux("fontconfig:fc-list") { "fc-list : family style | sort -u | head -150" }),
        ToolDef("clip", "Clipboard inspector", DISPLAY, Icons.Filled.ContentPaste, "What's on the Android and Linux clipboards", view = View.TABLE,
            run = native { ctx, _, out -> DisplayTools.clipboard(ctx).forEach { (k, c) -> out.row(k, c) } }),
        ToolDef("keys", "Keyboard & touch tester", DISPLAY, Icons.Filled.Keyboard, "See key codes, touches and latency", route = "input"),
        ToolDef("xtree", "Window tree & properties", DISPLAY, Icons.Filled.AccountTree, "Every X11 window, its properties and the client", route = "inspector"),
        ToolDef("audio", "Audio & microphone", DISPLAY, Icons.Filled.VolumeUp, "Sound from Linux apps",
            run = native { _, _, out -> out.line("Sound from Linux apps isn't supported yet: X11 doesn't carry audio and there's no PulseAudio bridge in this version.") }),

        // ---------------------------------------------------------------- history & help
        ToolDef("launches", "Launch history", HISTORY, Icons.Filled.History, "Apps you opened and how they exited",
            run = native { _, _, out -> AppLog.snapshot().filter { "Launching" in it || "exited with code" in it }.takeLast(300).forEach(out::line) }),
        ToolDef("errors", "Error history", HISTORY, Icons.Filled.ErrorOutline, "Recent errors, searchable", listOf(Field("q", "Search", "")),
            run = native { _, v, out -> AppLog.snapshot().filter { it.contains(" E ") && it.contains(v["q"].orEmpty(), true) }.takeLast(300).forEach(out::line) }),
        ToolDef("verify", "Backup integrity", HISTORY, Icons.Filled.VerifiedUser, "Check that a .tar.gz backup reads completely",
            listOf(Field("file", "Backup (inside Linux, or /sdcard path)", "/root/backup.tar.gz")),
            run = linux { v -> "f=${q(v["file"])}; if gzip -t \"\$f\" && tar -tzf \"\$f\" >/dev/null; then echo \"OK: \$(tar -tzf \"\$f\" | wc -l) entries, \$(du -h \"\$f\" | cut -f1)\"; else echo 'Damaged or not a tar.gz'; fi" }),
        ToolDef("manpages", "Command reference", HISTORY, Icons.Filled.MenuBook, "Offline guide to common Linux commands", listOf(Field("q", "Search", "")), View.TABLE,
            keywords = "documentation help", run = native { _, v, out -> CommandReference.search(v["q"].orEmpty()).forEach { (k, d) -> out.row(k, d) } }),
        ToolDef("docs", "Local documentation", HISTORY, Icons.Filled.LibraryBooks, "Help pages of an installed command (--help or man)",
            listOf(Field("cmd", "Command", "tar")), run = linux { v -> "c=${q(v["cmd"])}; (man \"\$c\" 2>/dev/null | col -b) || \$c --help 2>&1 | head -200" }),
        ToolDef("shortcuts", "Keyboard shortcuts", HISTORY, Icons.Filled.KeyboardCommandKey, "All shortcuts in the display", view = View.TABLE,
            run = native { _, _, out -> (com.mnvstudio.x11.display.Shortcuts.APP + com.mnvstudio.x11.display.Shortcuts.LINUX).forEach { out.row(it.keys, "${it.what} · ${it.owner}") } }),
    )
}
