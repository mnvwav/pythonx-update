package org.softbuddy.pythonx.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.softbuddy.pythonx.R

/*
 * PythonX design language: "a pocket Python lab".
 *  - Elegant green: Emerald for navigation, selection and links;
 *    Gold ("Spark") only for things that execute code (Run, Quick Run, the dock's play button).
 *  - Forest (dark, default) and Paper (light) themes; neutrals are tinted green so surfaces feel of a piece.
 *  - Shapes: S 10 (chips, keys) · M 14 (rows, inputs) · L 22 (cards) · XL 28 (hero cards, dock); buttons are pills.
 *  - The ">>>" prompt is the brand mark. Code surfaces are always dark.
 */

@Immutable
data class Tokens(
    val ink: Color, val label: Color, val faint: Color, val divider: Color,
    val inset: Color, val chip: Color, val track: Color,
    val good: Color, val bad: Color, val warn: Color, val goodBg: Color, val warnBg: Color,
    val spark: Color, val onSpark: Color, val glow: Color,
)

private val DarkTokens = Tokens(
    ink = Color(0xFFE8F1EC), label = Color(0xFFAFC2B8), faint = Color(0xFF7A8F84), divider = Color(0xFF1F2E27),
    inset = Color(0xFF070C0A), chip = Color(0xFF1A2721), track = Color(0xFF283A31),
    good = Color(0xFF7EE2A8), bad = Color(0xFFFF7A85), warn = Color(0xFFFFC857), goodBg = Color(0xFF15301F), warnBg = Color(0xFF3A2E10),
    spark = Color(0xFFE9C46A), onSpark = Color(0xFF2A1F05), glow = Color(0xFF5FD4A0),
)
private val LightTokens = Tokens(
    ink = Color(0xFF0F1E17), label = Color(0xFF46584F), faint = Color(0xFF87968E), divider = Color(0xFFDFE7E2),
    inset = Color(0xFFECF1EE), chip = Color(0xFFE5ECE8), track = Color(0xFFD1DBD5),
    good = Color(0xFF1C8A4C), bad = Color(0xFFD13A48), warn = Color(0xFFA66A00), goodBg = Color(0xFFDDF4E6), warnBg = Color(0xFFFCF0D5),
    spark = Color(0xFFD9A92E), onSpark = Color(0xFF2A1F05), glow = Color(0xFF1F7A55),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5FD4A0), onPrimary = Color(0xFF042117), primaryContainer = Color(0xFF15402F), onPrimaryContainer = Color(0xFFD3F2E3),
    secondary = Color(0xFF5FD4A0), secondaryContainer = Color(0xFF1A2721), onSecondaryContainer = Color(0xFFE8F1EC),
    background = Color(0xFF0A110E), onBackground = Color(0xFFE8F1EC), surface = Color(0xFF0A110E), onSurface = Color(0xFFE8F1EC),
    surfaceVariant = Color(0xFF101A16), onSurfaceVariant = Color(0xFFAFC2B8), outline = Color(0xFF3A4D44), outlineVariant = Color(0xFF1F2E27),
    surfaceContainerLowest = Color(0xFF070C0A), surfaceContainerLow = Color(0xFF0D1612), surfaceContainer = Color(0xFF101A16),
    surfaceContainerHigh = Color(0xFF16231D), surfaceContainerHighest = Color(0xFF1D2C25),
    error = Color(0xFFFF7A85), onError = Color(0xFF3B0A10),
)
private val LightScheme = lightColorScheme(
    primary = Color(0xFF1F7A55), onPrimary = Color.White, primaryContainer = Color(0xFFD6EFE3), onPrimaryContainer = Color(0xFF0A3323),
    secondary = Color(0xFF1F7A55), secondaryContainer = Color(0xFFE5ECE8), onSecondaryContainer = Color(0xFF0F1E17),
    background = Color(0xFFF6F8F6), onBackground = Color(0xFF0F1E17), surface = Color(0xFFF6F8F6), onSurface = Color(0xFF0F1E17),
    surfaceVariant = Color.White, onSurfaceVariant = Color(0xFF46584F), outline = Color(0xFF95A69C), outlineVariant = Color(0xFFDFE7E2),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F4F2), surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDF2EF), surfaceContainerHighest = Color(0xFFE3EAE6),
    error = Color(0xFFD13A48), onError = Color.White,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
)
val ShapeS = RoundedCornerShape(10.dp)
val ShapeM = RoundedCornerShape(14.dp)
val ShapeL = RoundedCornerShape(22.dp)
val ShapeXL = RoundedCornerShape(28.dp)
val Pill = CircleShape

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

/** Code surfaces use Fira Code. */
val Mono = FontFamily(Font(R.font.fira_code))

/** Material 3 type scale with every style set in Poppins (code stays monospace). */
private val PoppinsTypography = Typography().run {
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

private val LocalTokens = staticCompositionLocalOf { DarkTokens }

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTokens provides if (dark) DarkTokens else LightTokens) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = PoppinsTypography, shapes = AppShapes, content = content)
    }
}

// Short names used across screens.
val PageBg @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val CardBg @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainer
val PanelBg @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerLow
val Accent @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val OnAccent @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimary
val Ink @Composable @ReadOnlyComposable get() = LocalTokens.current.ink
val Label @Composable @ReadOnlyComposable get() = LocalTokens.current.label
val Faint @Composable @ReadOnlyComposable get() = LocalTokens.current.faint
val DividerColor @Composable @ReadOnlyComposable get() = LocalTokens.current.divider
val Inset @Composable @ReadOnlyComposable get() = LocalTokens.current.inset
val ChipBg @Composable @ReadOnlyComposable get() = LocalTokens.current.chip
val Track @Composable @ReadOnlyComposable get() = LocalTokens.current.track
val Good @Composable @ReadOnlyComposable get() = LocalTokens.current.good
val Bad @Composable @ReadOnlyComposable get() = LocalTokens.current.bad
val Warn @Composable @ReadOnlyComposable get() = LocalTokens.current.warn
val GoodBg @Composable @ReadOnlyComposable get() = LocalTokens.current.goodBg
val WarnBg @Composable @ReadOnlyComposable get() = LocalTokens.current.warnBg
/** Gold: reserved for actions that execute code. */
val Spark @Composable @ReadOnlyComposable get() = LocalTokens.current.spark
val OnSpark @Composable @ReadOnlyComposable get() = LocalTokens.current.onSpark
/** Bright emerald for small highlights (prompt glyphs, focus rings). */
val Glow @Composable @ReadOnlyComposable get() = LocalTokens.current.glow

/** Code surfaces are dark in both themes. */
val CodeBg = Color(0xFF08100D)
val CodeText = Color(0xFFE3ECE7)
val CodeFaint = Color(0xFF6B8278)
val CodePrompt = Color(0xFFE9C46A)

/** The launcher mark (Python logo on its background), for in-app branding. */
@Composable
fun AppLogo(size: Dp) {
    Box(Modifier.size(size).clip(ShapeL)) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.matchParentSize().scale(1.4f))
    }
}

/** Clickable that doesn't steal focus, so the keyboard stays open when tapping the key bar. */
fun Modifier.clickableNoFocus(onClick: () -> Unit) =
    this.clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick)
