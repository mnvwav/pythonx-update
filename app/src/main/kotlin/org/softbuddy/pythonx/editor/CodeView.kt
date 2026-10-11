package org.softbuddy.pythonx.editor

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.SymbolPairMatch.SymbolPair
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource
import org.softbuddy.pythonx.Prefs
import org.softbuddy.pythonx.R
import org.softbuddy.pythonx.python.Problem

private val PY_KEYWORDS = ("False None True and as assert async await break case class continue def del elif else except " +
    "finally for from global if import in is lambda match nonlocal not or pass raise return self try type while with yield").split(" ")
private val PY_BUILTINS = ("abs all any ascii bin bool breakpoint bytearray bytes callable chr classmethod compile complex " +
    "delattr dict dir divmod enumerate eval exec filter float format frozenset getattr globals hasattr hash help hex id input " +
    "int isinstance issubclass iter len list locals map max memoryview min next object oct open ord pow print property range " +
    "repr reversed round set setattr slice sorted staticmethod str sum super tuple vars zip __name__ __main__ __init__ " +
    "Exception ValueError TypeError KeyError IndexError RuntimeError FileNotFoundError ZeroDivisionError KeyboardInterrupt " +
    "NotImplementedError AttributeError StopIteration").split(" ")

/** Holds the live editor view so screens can drive it (undo, search, jump…) from Compose. */
class EditorController {
    var view: CodeEditor? = null
        internal set
    /** Bumped on every edit; lets Compose observe the text without copying it on each keystroke. */
    var version by mutableIntStateOf(0)
        internal set
    var cursorLine by mutableIntStateOf(1)
        internal set
    var canUndo by mutableStateOf(false)
        internal set
    var canRedo by mutableStateOf(false)
        internal set

    val text: String get() = view?.text?.toString().orEmpty()

    fun setText(text: String) {
        view?.setText(text)
    }

    fun insert(s: String) {
        val v = view ?: return
        // Insert at the cursor through the editor so undo, auto-indent and pairs behave like typing.
        v.commitText(s)
    }

    fun undo() { view?.undo() }
    fun redo() { view?.redo() }
    fun moveLeft() { view?.moveSelectionLeft() }
    fun moveRight() { view?.moveSelectionRight() }
    fun moveUp() { view?.moveSelectionUp() }
    fun moveDown() { view?.moveSelectionDown() }
    fun home() { view?.moveSelectionHome() }
    fun end() { view?.moveSelectionEnd() }

    fun goToLine(line: Int, column: Int = 0) {
        val v = view ?: return
        val l = (line - 1).coerceIn(0, v.text.lineCount - 1)
        v.setSelection(l, column.coerceIn(0, v.text.getColumnCount(l)))
        v.ensureSelectionVisible()
        v.requestFocus()
    }

    /** Toggles "# " on the cursor line or every selected line. */
    fun toggleComment() {
        val v = view ?: return
        val c = v.cursor
        val first = c.leftLine; val last = c.rightLine
        val text = v.text
        val lines = (first..last).map { text.getLineString(it) }
        val nonBlank = lines.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return
        val uncomment = nonBlank.all { it.trimStart().startsWith("#") }
        val indent = nonBlank.minOf { it.length - it.trimStart().length }
        val replaced = lines.joinToString("\n") { l ->
            when {
                l.isBlank() -> l
                uncomment -> l.replaceFirst(Regex("^(\\s*)# ?"), "$1")
                else -> l.take(indent) + "# " + l.drop(indent)
            }
        }
        text.replace(first, 0, last, text.getColumnCount(last), replaced)
    }

    /** Duplicates the cursor line below itself. */
    fun duplicateLine() {
        val v = view ?: return
        val line = v.cursor.leftLine
        val s = v.text.getLineString(line)
        v.text.insert(line, v.text.getColumnCount(line), "\n" + s)
    }

    /** Highlights the problems reported by the analyzer as squiggles. */
    fun showProblems(problems: List<Problem>) {
        val v = view ?: return
        val content = v.text
        val container = DiagnosticsContainer()
        problems.forEachIndexed { i, p ->
            val line = (p.line - 1).coerceIn(0, content.lineCount - 1)
            val endLine = (p.endLine - 1).coerceIn(line, content.lineCount - 1)
            val col = (p.col - 1).coerceIn(0, content.getColumnCount(line))
            var endCol = (p.endCol - 1).coerceIn(0, content.getColumnCount(endLine))
            if (endLine == line && endCol <= col) endCol = (col + 1).coerceAtMost(content.getColumnCount(line))
            var start = content.getCharIndex(line, col)
            var end = content.getCharIndex(endLine, endCol)
            if (end <= start) {
                // Zero-width (e.g. an error at the end of a line): underline the whole line instead.
                start = content.getCharIndex(line, 0); end = content.getCharIndex(line, content.getColumnCount(line))
                if (end <= start) return@forEachIndexed
            }
            container.addDiagnostic(DiagnosticRegion(start, end,
                if (p.error) DiagnosticRegion.SEVERITY_ERROR else DiagnosticRegion.SEVERITY_WARNING, i.toLong(),
                DiagnosticDetail(p.message, p.message, emptyList(), null)))
        }
        v.diagnostics = container
    }

    fun search(query: String, regex: Boolean, matchCase: Boolean) {
        val s = view?.searcher ?: return
        if (query.isEmpty()) { s.stopSearch(); return }
        runCatching {
            s.search(query, EditorSearcher.SearchOptions(if (regex) EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                else EditorSearcher.SearchOptions.TYPE_NORMAL, !matchCase))
        }
    }

    fun searchNext() = view?.searcher?.takeIf { it.hasQuery() }?.gotoNext()
    fun searchPrevious() = view?.searcher?.takeIf { it.hasQuery() }?.gotoPrevious()
    fun replace(with: String) = runCatching { view?.searcher?.takeIf { it.hasQuery() }?.replaceThis(with) }
    fun replaceAll(with: String) = runCatching { view?.searcher?.takeIf { it.hasQuery() }?.replaceAll(with) }
    fun stopSearch() = view?.searcher?.stopSearch()
    fun matchCount() = view?.searcher?.takeIf { it.hasQuery() }?.matchedPositionCount ?: 0
    fun matchIndex() = view?.searcher?.takeIf { it.hasQuery() }?.currentMatchedPositionIndex ?: -1
}

private object TextMate {
    @Volatile private var loaded = false

    /** Grammars and the theme load once per process. */
    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.applicationContext.assets))
            val themes = ThemeRegistry.getInstance()
            themes.loadTheme(ThemeModel(IThemeSource.fromInputStream(
                FileProviderRegistry.getInstance().tryGetInputStream("textmate/darcula.json"), "textmate/darcula.json", null),
                "darcula").apply { isDark = true })
            themes.setTheme("darcula")
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
            loaded = true
        }
    }
}

/** The code editor: Sora with TextMate highlighting, styled to the app's dark code surface. */
@Composable
fun CodeView(
    controller: EditorController, fileKey: String, initialText: String, isPython: Boolean, prefs: Prefs,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            TextMate.load(ctx)
            CodeEditor(ctx).apply {
                typefaceText = ResourcesCompat.getFont(ctx, R.font.fira_code)
                typefaceLineNumber = ResourcesCompat.getFont(ctx, R.font.fira_code)
                setPinLineNumber(true)
                setLineSpacing(2f, 1.1f)
                props.stickyScroll = true
                props.useICULibToSelectWords = false
                isStickyTextSelection = true
                setDividerWidth(0f)
                colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                applyColors(colorScheme)
                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    controller.version++
                    controller.canUndo = canUndo(); controller.canRedo = canRedo()
                }
                subscribeEvent(SelectionChangeEvent::class.java) { e, _ -> controller.cursorLine = e.left.line + 1 }
                controller.view = this
            }
        },
        update = { editor ->
            if (editor.tag != fileKey) {
                editor.tag = fileKey
                editor.setEditorLanguage(if (isPython) pythonLanguage() else EmptyLanguage())
                editor.setText(initialText)
                editor.diagnostics = DiagnosticsContainer()
                controller.version++
                controller.canUndo = false; controller.canRedo = false
            }
            editor.setTextSize(prefs.fontSize.toFloat())
            editor.tabWidth = prefs.tabSize
            editor.isWordwrap = prefs.wordWrap
            editor.isLineNumberEnabled = prefs.lineNumbers
            editor.isHighlightCurrentLine = prefs.lineHighlight
            editor.props.symbolPairAutoCompletion = prefs.autoClose
            editor.getComponent(io.github.rosemoe.sora.widget.component.EditorAutoCompletion::class.java).isEnabled = prefs.autocomplete
            editor.nonPrintablePaintingFlags = if (prefs.showWhitespace)
                CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_WHITESPACE_INNER or CodeEditor.FLAG_DRAW_LINE_SEPARATOR
            else 0
        },
        onRelease = { if (controller.view === it) controller.view = null; it.release() },
        modifier = modifier,
    )
}

private fun pythonLanguage(): TextMateLanguage =
    TextMateLanguage.create("source.python", true).apply {
        setCompleterKeywords((PY_KEYWORDS + PY_BUILTINS).distinct().toTypedArray())
        symbolPairs.apply {
            putPair('(', SymbolPair("(", ")"))
            putPair('[', SymbolPair("[", "]"))
            putPair('{', SymbolPair("{", "}"))
            val onlyWhenSelected = object : SymbolPair.SymbolPairEx {
                override fun shouldDoAutoSurround(content: Content) = content.cursor.isSelected
            }
            putPair('"', SymbolPair("\"", "\"", onlyWhenSelected))
            putPair('\'', SymbolPair("'", "'", onlyWhenSelected))
        }
    }

/** Repaints Sora's surfaces in the app's code colours; token colours come from the TextMate theme. */
private fun CodeEditor.applyColors(scheme: EditorColorScheme) {
    val bg = 0xFF08100D.toInt()
    scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, bg)
    scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, bg)
    scheme.setColor(EditorColorScheme.LINE_NUMBER, 0xFF46594F.toInt())
    scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, 0xFFE3E3E3.toInt())
    scheme.setColor(EditorColorScheme.CURRENT_LINE, 0xFF112019.toInt())
    scheme.setColor(EditorColorScheme.LINE_DIVIDER, Color.TRANSPARENT)
    scheme.setColor(EditorColorScheme.SELECTION_HANDLE, 0xFF5FD4A0.toInt())
    scheme.setColor(EditorColorScheme.SELECTION_INSERT, 0xFFE9C46A.toInt())
    scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, 0x555FD4A0)
    scheme.setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, 0x55F2C66D)
    scheme.setColor(EditorColorScheme.BLOCK_LINE, 0xFF2C3038.toInt())
    scheme.setColor(EditorColorScheme.BLOCK_LINE_CURRENT, 0xFF4A505C.toInt())
    scheme.setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, 0xFF16231D.toInt())
    scheme.setColor(EditorColorScheme.COMPLETION_WND_CORNER, 0xFF2C3038.toInt())
    scheme.setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, 0xFFEEF0F4.toInt())
    scheme.setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, 0xFF8A909C.toInt())
    scheme.setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, 0xFF15402F.toInt())
    scheme.setColor(EditorColorScheme.PROBLEM_ERROR, 0xFFFF6B6B.toInt())
    scheme.setColor(EditorColorScheme.PROBLEM_WARNING, 0xFFF2C66D.toInt())
    scheme.setColor(EditorColorScheme.NON_PRINTABLE_CHAR, 0xFF3C414B.toInt())
    setLineInfoTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics))
}
