package com.mnvstudio.x11.ui

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mnvstudio.x11.R

/*
 * Design language: "paper & ink", from the logo (a white page with a red and black X on a black tile).
 *  - Near-black surfaces in three steps (page, card, raised) with hairline borders instead of shadows.
 *  - Soft off-white text; one muted crimson for actions, selection and the brand; nothing oversaturated.
 *  - Status colors are desaturated too (sage green, sand, terracotta) so they inform without shouting.
 *  - Shapes: 12 (chips, keys) · 16 (rows, inputs) · 24 (cards) · pills for buttons.
 * Other accents and Material You stay available in Settings → Look.
 */

@Immutable
data class Palette(
    val page: Color, val card: Color, val raised: Color, val line: Color,
    val ink: Color, val label: Color, val faint: Color,
    val accent: Color, val onAccent: Color, val accentSoft: Color,
    val live: Color, val bad: Color, val warn: Color, val code: Color,
)

private val Graphite = Palette(
    page = Color(0xFF111112), card = Color(0xFF18181A), raised = Color(0xFF202023), line = Color(0xFF2C2C30),
    ink = Color(0xFFECEBE8), label = Color(0xFFA5A4A0), faint = Color(0xFF6F6E6B),
    accent = Color(0xFFD45D68), onAccent = Color.White, accentSoft = Color(0xFF2A1C1F),
    live = Color(0xFF7DBE95), bad = Color(0xFFE08A6E), warn = Color(0xFFD8AE6A), code = Color(0xFF0B0B0C),
)

private val Amoled = Graphite.copy(page = Color.Black, card = Color(0xFF0E0E0F), raised = Color(0xFF17171A), line = Color(0xFF242427), code = Color.Black)

private val Paper = Palette(
    page = Color(0xFFF6F5F2), card = Color.White, raised = Color(0xFFEDECE8), line = Color(0xFFDFDDD8),
    ink = Color(0xFF1A1A1B), label = Color(0xFF5C5B58), faint = Color(0xFF8F8D88),
    accent = Color(0xFFAE3F4A), onAccent = Color.White, accentSoft = Color(0xFFF4E4E5),
    live = Color(0xFF2E8A5A), bad = Color(0xFFB9532F), warn = Color(0xFF9A6A1E), code = Color(0xFF1A1A1B),
)

/** Accent choices in Settings, with the soft tint used behind selected items (dark, light). */
val ACCENTS = mapOf(
    "Red" to Color(0xFFD45D68), "Gold" to Color(0xFFD6B26A), "Blue" to Color(0xFF7C9BD6), "Violet" to Color(0xFF9E8CCB), "Teal" to Color(0xFF6BB3A8),
    "Orange" to Color(0xFFD99067), "Pink" to Color(0xFFCF7FA0), "Lime" to Color(0xFFA8BF6E),
)

private val HighContrast = Graphite.copy(
    page = Color.Black, card = Color(0xFF0C0C0C), raised = Color(0xFF161616), line = Color(0xFF8A8A8A),
    ink = Color.White, label = Color(0xFFE2E2E2), faint = Color(0xFFBDBDBD), accent = Color(0xFFF08A94), onAccent = Color.Black, accentSoft = Color(0xFF3A1E22),
)

private fun Palette.withDynamic(s: ColorScheme) = copy(accent = s.primary, onAccent = s.onPrimary, accentSoft = s.primaryContainer)

val LocalPalette = staticCompositionLocalOf { Graphite }
val LocalIsLight = staticCompositionLocalOf { false }

private fun Color.darker() = Color(red * 0.82f, green * 0.82f, blue * 0.82f, alpha)

val Page @Composable @ReadOnlyComposable get() = LocalPalette.current.page
val Card @Composable @ReadOnlyComposable get() = LocalPalette.current.card
val CardHigh @Composable @ReadOnlyComposable get() = LocalPalette.current.raised
val Line @Composable @ReadOnlyComposable get() = LocalPalette.current.line
val Ink @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
val Label @Composable @ReadOnlyComposable get() = LocalPalette.current.label
val Faint @Composable @ReadOnlyComposable get() = LocalPalette.current.faint
val Accent @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
val OnAccent @Composable @ReadOnlyComposable get() = LocalPalette.current.onAccent
val AccentSoft @Composable @ReadOnlyComposable get() = LocalPalette.current.accentSoft
val Live @Composable @ReadOnlyComposable get() = LocalPalette.current.live
val Bad @Composable @ReadOnlyComposable get() = LocalPalette.current.bad
val Warn @Composable @ReadOnlyComposable get() = LocalPalette.current.warn
val CodeBg @Composable @ReadOnlyComposable get() = LocalPalette.current.code

/** Soft top glow used behind screen headers. */
val HeaderGlow @Composable @ReadOnlyComposable get() = Brush.verticalGradient(listOf(LocalPalette.current.accentSoft, LocalPalette.current.page))

val ShapeS = RoundedCornerShape(12.dp)
val ShapeM = RoundedCornerShape(16.dp)
val ShapeL = RoundedCornerShape(24.dp)

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

/** Commands, addresses and key labels use Fira Code. */
val Mono = FontFamily(Font(R.font.fira_code))

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Poppins), displayMedium = displayMedium.copy(fontFamily = Poppins),
        displaySmall = displaySmall.copy(fontFamily = Poppins), headlineLarge = headlineLarge.copy(fontFamily = Poppins),
        headlineMedium = headlineMedium.copy(fontFamily = Poppins), headlineSmall = headlineSmall.copy(fontFamily = Poppins),
        titleLarge = titleLarge.copy(fontFamily = Poppins), titleMedium = titleMedium.copy(fontFamily = Poppins),
        titleSmall = titleSmall.copy(fontFamily = Poppins), bodyLarge = bodyLarge.copy(fontFamily = Poppins),
        bodyMedium = bodyMedium.copy(fontFamily = Poppins), bodySmall = bodySmall.copy(fontFamily = Poppins),
        labelLarge = labelLarge.copy(fontFamily = Poppins), labelMedium = labelMedium.copy(fontFamily = Poppins),
        labelSmall = labelSmall.copy(fontFamily = Poppins),
    )
}

/** [mode] is "dark", "light", "amoled" or "system"; [accent] a key of [ACCENTS]. */
@Composable
fun AppTheme(mode: String = "dark", accent: String = "Red", dynamic: Boolean = false, highContrast: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val light = mode == "light" || (mode == "system" && !androidx.compose.foundation.isSystemInDarkTheme())
    var p = when { highContrast -> HighContrast; light -> Paper; mode == "amoled" -> Amoled; else -> Graphite }
    val a = ACCENTS[accent]
    if (a != null && !highContrast) {
        val acc = if (light) a.darker().let { if (accent == "Red") Color(0xFFAE3F4A) else if (accent == "Gold") Color(0xFFB8860B) else it } else a
        // Text on the accent: dark on bright accents (gold, lime), white on deep ones.
        p = p.copy(accent = acc, onAccent = if (acc.luminance() > 0.45f) Color(0xFF111111) else Color.White,
            accentSoft = a.copy(alpha = if (light) 0.16f else 0.16f).compositeOver(p.page))
    }
    if (dynamic && Build.VERSION.SDK_INT >= 31 && !highContrast) {
        p = p.withDynamic(if (light) androidx.compose.material3.dynamicLightColorScheme(context) else dynamicDarkColorScheme(context))
    }
    val base = if (light) androidx.compose.material3.lightColorScheme() else darkColorScheme()
    val scheme = base.copy(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentSoft, onPrimaryContainer = p.ink,
        secondary = p.live, onSecondary = Color.Black, secondaryContainer = p.raised, onSecondaryContainer = p.ink,
        background = p.page, onBackground = p.ink, surface = p.page, onSurface = p.ink,
        surfaceVariant = p.card, onSurfaceVariant = p.label, outline = p.line, outlineVariant = p.line,
        surfaceContainerLowest = p.page, surfaceContainerLow = p.card, surfaceContainer = p.card,
        surfaceContainerHigh = p.raised, surfaceContainerHighest = p.raised, error = p.bad, onError = Color.White,
    )
    CompositionLocalProvider(LocalPalette provides p, LocalIsLight provides light) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography,
            shapes = Shapes(small = ShapeS, medium = ShapeM, large = ShapeL, extraLarge = ShapeL), content = content)
    }
}
