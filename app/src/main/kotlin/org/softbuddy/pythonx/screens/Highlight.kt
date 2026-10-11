package org.softbuddy.pythonx.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

private val KeywordColor = Color(0xFFCC7832)
private val StringColor = Color(0xFF6A8759)
private val NumberColor = Color(0xFF6897BB)
private val CommentColor = Color(0xFF7E7E7E)
private val BuiltinColor = Color(0xFF8888C6)
private val DefColor = Color(0xFFFFC66D)

private val KEYWORDS = ("False None True and as assert async await break class continue def del elif else except finally for " +
    "from global if import in is lambda nonlocal not or pass raise return try while with yield match case").split(" ").toSet()
private val BUILTINS = "print input len range enumerate zip open int str float list dict set tuple sorted sum min max abs type isinstance self".split(" ").toSet()

private val TOKENS = Regex(
    "(#[^\\n]*)" +                                                       // comments
        "|([rbfRBF]{0,2}(?:\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'))" + // strings
        "|(\\b\\d+(?:\\.\\d+)?\\b)" +                                      // numbers
        "|((?<=def |class )[A-Za-z_]\\w*)" +                               // definitions
        "|([A-Za-z_]\\w*)"                                                 // words
)

/** Lightweight Python colouring for read-only previews (the editor itself uses TextMate). */
fun highlightPython(code: String): AnnotatedString = buildAnnotatedString {
    append(code)
    for (m in TOKENS.findAll(code)) {
        val g = m.groups
        val style = when {
            g[1] != null -> SpanStyle(color = CommentColor)
            g[2] != null -> SpanStyle(color = StringColor)
            g[3] != null -> SpanStyle(color = NumberColor)
            g[4] != null -> SpanStyle(color = DefColor)
            g[5] != null && m.value in KEYWORDS -> SpanStyle(color = KeywordColor)
            g[5] != null && m.value in BUILTINS -> SpanStyle(color = BuiltinColor)
            else -> null
        }
        if (style != null) addStyle(style, m.range.first, m.range.last + 1)
    }
}
