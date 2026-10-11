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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mnvstudio.x11.R

/*
 * Design language: "the workbench". The logo is a white tool mark on graphite, so the app is too:
 *  - Graphite surfaces in three steps (page, card, raised), hairline borders instead of shadows.
 *  - White for primary text and the brand mark; one accent (signal blue) for actions and selection.
 *  - Mint marks live things (running server, connected apps); coral marks problems.
 *  - Shapes: 12 (chips, keys) · 16 (rows, inputs) · 24 (cards) · pills for buttons.
 * Material You swaps the accent for wallpaper colors; high contrast brightens text and borders.
 */

@Immutable
data class Palette(
    val page: Color, val card: Color, val raised: Color, val line: Color,
    val ink: Color, val label: Color, val faint: Color,
    val accent: Color, val onAccent: Color, val accentSoft: Color,
    val live: Color, val bad: Color, val warn: Color, val code: Color,
)

private val Graphite = Palette(
    page = Color(0xFF0E0E10), card = Color(0xFF161619), raised = Color(0xFF1E1E22), line = Color(0xFF2A2A30),
    ink = Color(0xFFF3F3F5), label = Color(0xFFA4A4AD), faint = Color(0xFF6C6C76),
    accent = Color(0xFF5B8CFF), onAccent = Color.White, accentSoft = Color(0xFF1A2440),
    live = Color(0xFF3DDC97), bad = Color(0xFFFF6B6B), warn = Color(0xFFFFB547), code = Color(0xFF09090B),
)

private val HighContrast = Graphite.copy(
    page = Color.Black, card = Color(0xFF0C0C0C), raised = Color(0xFF161616), line = Color(0xFF8A8A8A),
    ink = Color.White, label = Color(0xFFE2E2E2), faint = Color(0xFFBDBDBD), accent = Color(0xFF8AB0FF), accentSoft = Color(0xFF1F2B4D),
)

private fun Palette.withDynamic(s: ColorScheme) = copy(accent = s.primary, onAccent = s.onPrimary, accentSoft = s.primaryContainer)

val LocalPalette = staticCompositionLocalOf { Graphite }

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

@Composable
fun AppTheme(dynamic: Boolean = false, highContrast: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var p = if (highContrast) HighContrast else Graphite
    if (dynamic && Build.VERSION.SDK_INT >= 31 && !highContrast) p = p.withDynamic(dynamicDarkColorScheme(context))
    val scheme = darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentSoft, onPrimaryContainer = p.ink,
        secondary = p.live, onSecondary = Color.Black, secondaryContainer = p.raised, onSecondaryContainer = p.ink,
        background = p.page, onBackground = p.ink, surface = p.page, onSurface = p.ink,
        surfaceVariant = p.card, onSurfaceVariant = p.label, outline = p.line, outlineVariant = p.line,
        surfaceContainerLowest = p.page, surfaceContainerLow = p.card, surfaceContainer = p.card,
        surfaceContainerHigh = p.raised, surfaceContainerHighest = p.raised, error = p.bad, onError = Color.White,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography,
            shapes = Shapes(small = ShapeS, medium = ShapeM, large = ShapeL, extraLarge = ShapeL), content = content)
    }
}
