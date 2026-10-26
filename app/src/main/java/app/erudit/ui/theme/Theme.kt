package app.erudit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.erudit.R
import app.erudit.data.prefs.ThemeMode

/** Дополнительные цвета, которых нет в Material-схеме. */
@Immutable
data class ExtraColors(
    val muted: Color,
    val faint: Color,
    val line: Color,
    val success: Color,
    val successSoft: Color,
    val error: Color,
    val errorSoft: Color,
    val warm: Color,
)

private val LightExtra = ExtraColors(
    muted = Color(0xFF6E6A63),
    faint = Color(0xFFA19D95),
    line = Color(0xFFE6E2DA),
    success = Color(0xFF2F7A52),
    successSoft = Color(0xFFE3F0E7),
    error = Color(0xFFB4443A),
    errorSoft = Color(0xFFF6E3E0),
    warm = Color(0xFFC9772B),
)

private val DarkExtra = ExtraColors(
    muted = Color(0xFF9E9A92),
    faint = Color(0xFF6C6962),
    line = Color(0xFF2B2A27),
    success = Color(0xFF6CC395),
    successSoft = Color(0xFF1C2E24),
    error = Color(0xFFE5867B),
    errorSoft = Color(0xFF3A2220),
    warm = Color(0xFFE4A05C),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1D1C1A),
    onPrimary = Color(0xFFFAF9F6),
    secondary = Color(0xFF6E6A63),
    onSecondary = Color.White,
    background = Color(0xFFF6F4EF),
    onBackground = Color(0xFF1D1C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1C1A),
    surfaceVariant = Color(0xFFEFECE5),
    onSurfaceVariant = Color(0xFF6E6A63),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1EEE8),
    surfaceContainerHighest = Color(0xFFEAE6DE),
    surfaceContainerLow = Color(0xFFFBFAF7),
    outline = Color(0xFFD9D4CA),
    outlineVariant = Color(0xFFE6E2DA),
    error = Color(0xFFB4443A),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFEDEAE4),
    onPrimary = Color(0xFF161615),
    secondary = Color(0xFF9E9A92),
    onSecondary = Color(0xFF161615),
    background = Color(0xFF121211),
    onBackground = Color(0xFFEDEAE4),
    surface = Color(0xFF1A1A18),
    onSurface = Color(0xFFEDEAE4),
    surfaceVariant = Color(0xFF242321),
    onSurfaceVariant = Color(0xFF9E9A92),
    surfaceContainer = Color(0xFF1A1A18),
    surfaceContainerHigh = Color(0xFF222220),
    surfaceContainerHighest = Color(0xFF2A2927),
    surfaceContainerLow = Color(0xFF161615),
    outline = Color(0xFF3A3936),
    outlineVariant = Color(0xFF2B2A27),
    error = Color(0xFFE5867B),
)

@OptIn(ExperimentalTextApi::class)
private fun literata(weight: Int, style: FontStyle = FontStyle.Normal) = Font(
    resId = if (style == FontStyle.Italic) R.font.literata_italic else R.font.literata,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Serif = FontFamily(
    literata(400),
    literata(500),
    literata(600),
    literata(700),
    literata(400, FontStyle.Italic),
    literata(600, FontStyle.Italic),
)

private val Sans = FontFamily.Default

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }
val LocalReadingScale = staticCompositionLocalOf { 1f }

object AppTheme {
    val extra: ExtraColors @Composable get() = LocalExtraColors.current
}

@Composable
fun EruditTheme(mode: ThemeMode = ThemeMode.SYSTEM, readingScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(
        LocalExtraColors provides if (dark) DarkExtra else LightExtra,
        LocalReadingScale provides readingScale,
    ) {
        val scheme = if (dark) DarkScheme else LightScheme
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes) {
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}

/** Цвет области, приглушённый для тёмной темы. */
@Composable
fun domainColor(argb: Long): Color {
    val base = Color(argb)
    return if (MaterialTheme.colorScheme.background.luminanceLow()) lighten(base, 0.28f) else base
}

private fun Color.luminanceLow(): Boolean = (red * 0.299f + green * 0.587f + blue * 0.114f) < 0.5f

private fun lighten(c: Color, amount: Float) = Color(
    red = c.red + (1 - c.red) * amount,
    green = c.green + (1 - c.green) * amount,
    blue = c.blue + (1 - c.blue) * amount,
    alpha = c.alpha,
)
