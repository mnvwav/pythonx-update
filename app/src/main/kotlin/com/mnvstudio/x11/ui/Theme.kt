package com.mnvstudio.x11.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mnvstudio.x11.R

/*
 * Design language: a calm, dark "night desk".
 *  - Deep ink surfaces, tinted slightly blue so cards feel layered rather than flat.
 *  - Violet is the primary accent (actions, selection); cyan marks live things (running server, connections).
 *  - The brand gradient (violet to cyan) is used sparingly: the primary button, the logo and progress.
 *  - Shapes: 12 (chips, keys) · 16 (rows, inputs) · 24 (cards) · pills for buttons.
 */

val Page = Color(0xFF0B0D14)
val Card = Color(0xFF141826)
val CardHigh = Color(0xFF1B2032)
val Line = Color(0xFF252B40)
val Ink = Color(0xFFEDEFF7)
val Label = Color(0xFFA3A9C2)
val Faint = Color(0xFF6E7591)
val Violet = Color(0xFF8B6CFF)
val VioletDeep = Color(0xFF5B3FE0)
val Cyan = Color(0xFF2DD4EE)
val Good = Color(0xFF4ADE9A)
val Bad = Color(0xFFFF6B81)
val CodeBg = Color(0xFF080A11)

val BrandGradient = Brush.linearGradient(listOf(Violet, Cyan))
val BrandGradientDeep = Brush.linearGradient(listOf(VioletDeep, Color(0xFF1A9FC0)))

val ShapeS = RoundedCornerShape(12.dp)
val ShapeM = RoundedCornerShape(16.dp)
val ShapeL = RoundedCornerShape(24.dp)

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

/** Commands and addresses use Fira Code. */
val Mono = FontFamily(Font(R.font.fira_code))

private val Scheme = darkColorScheme(
    primary = Violet, onPrimary = Color.White, primaryContainer = Color(0xFF2A2160), onPrimaryContainer = Color(0xFFE4DDFF),
    secondary = Cyan, onSecondary = Color(0xFF00222A), secondaryContainer = CardHigh, onSecondaryContainer = Ink,
    background = Page, onBackground = Ink, surface = Page, onSurface = Ink,
    surfaceVariant = Card, onSurfaceVariant = Label, outline = Line, outlineVariant = Line,
    surfaceContainerLowest = Page, surfaceContainerLow = Card, surfaceContainer = Card,
    surfaceContainerHigh = CardHigh, surfaceContainerHighest = CardHigh,
    error = Bad, onError = Color.White,
)

/** Material 3 type scale with every style set in Poppins. */
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
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = AppTypography,
        shapes = Shapes(small = ShapeS, medium = ShapeM, large = ShapeL, extraLarge = ShapeL),
        content = content,
    )
}
