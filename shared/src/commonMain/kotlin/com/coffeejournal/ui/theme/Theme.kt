package com.coffeejournal.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Web "Archive light theme" tokens (docs/android-app-design.md §5.3). */
object Ink {
    val bg = Color(0xFFF5F4EF)
    val surface = Color(0xFFFFFFFF)
    val surfaceRaised = Color(0xFFECEBE5)
    val line = Color(0xFFC8C6BD)
    val text = Color(0xFF191916)
    val textMuted = Color(0xFF5D5B54)
    val textFaint = Color(0xFF858177)
    val accent = Color(0xFF20201D)
    val accentSoft = Color(0x1220201D)
    val bad = Color(0xFF9D3026)
    val good = Color(0xFF4D684D)
    val cupping = Color(0xFF6E5F8B)
    val cafe = Color(0xFF8B5C35)
    val book = Color(0xFF5D6F79)
    val classPurple = Color(0xFF8A6FAE)
    val mapProducer = Color(0xFF2F3A2C)
    val mapDot = Color(0xFF9BC53D)
    val shadow = Color(0x2420201D)

    fun categoryColor(category: String): Color = when (category) {
        "카페" -> cafe
        "커핑" -> cupping
        else -> accent
    }

    fun hex(hex: String): Color {
        val h = hex.removePrefix("#")
        val v = h.toLong(16)
        return if (h.length == 6) Color((0xFF000000L or v).toInt()) else Color(v.toInt())
    }
}

object Dimens {
    val gutter = 16.dp
    val hairline = 0.5.dp
    val rule = 1.dp
    val heavyRule = 2.dp
    val cardPadding = 14.dp
    val touch = 44.dp
}

object AppType {
    /** The header and number typeface (설정 › 화면 › 제목·숫자). */
    val mono: FontFamily get() = Display.numberFamily
    /** The running text typeface (설정 › 화면 › 글꼴). */
    val sans: FontFamily get() = Display.bodyFamily

    val headerTitle: TextStyle get() = TextStyle(fontFamily = mono, fontSize = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.04).em, color = Ink.text)
    val tagline: TextStyle get() = TextStyle(fontFamily = mono, fontSize = 11.sp, color = Ink.textMuted, letterSpacing = 0.02.em)
    val count: TextStyle get() = TextStyle(fontFamily = mono, fontSize = 12.sp, color = Ink.text)
    val sectionLabel: TextStyle get() = TextStyle(fontFamily = mono, fontSize = 10.5.sp, letterSpacing = 0.05.em, color = Ink.textFaint, fontWeight = FontWeight.Medium)
    val fieldLabel: TextStyle get() = TextStyle(fontFamily = sans, fontSize = 12.sp, color = Ink.textMuted)
    val body: TextStyle get() = TextStyle(fontFamily = sans, fontSize = 14.sp, color = Ink.text, lineHeight = 21.sp)
    val bodyMuted: TextStyle get() = TextStyle(fontFamily = sans, fontSize = 13.sp, color = Ink.textMuted, lineHeight = 20.sp)
    val small: TextStyle get() = TextStyle(fontFamily = sans, fontSize = 12.sp, color = Ink.textMuted, lineHeight = 18.sp)

    /**
     * Text inside input boxes: [body] / [small] with an untrimmed line box. The default trim fits the first and last
     * line to the font actually drawn, and the Hangul fallback font is taller than the Latin one, so at a large font
     * scale a box holding "V60 표백" grew taller than its neighbour holding "V60". Untrimmed, every line is exactly
     * its line height whatever the script, so boxes in one row stay level.
     */
    private val fieldLines = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)
    val input: TextStyle get() = body.copy(lineHeightStyle = fieldLines)
    val inputSmall: TextStyle get() = small.copy(lineHeightStyle = fieldLines)
    val faint: TextStyle get() = TextStyle(fontFamily = sans, fontSize = 11.5.sp, color = Ink.textFaint, lineHeight = 17.sp)
    val title: TextStyle get() = TextStyle(fontFamily = sans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink.text, lineHeight = 24.sp)
    val cardTitle: TextStyle get() = TextStyle(fontFamily = sans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink.text, lineHeight = 22.sp)
    val monoValue: TextStyle get() = TextStyle(fontFamily = mono, fontSize = 12.sp, color = Ink.text)
    val monoSmall: TextStyle get() = TextStyle(fontFamily = mono, fontSize = 10.5.sp, color = Ink.textMuted, letterSpacing = 0.04.em)
    val displayNumber: TextStyle get() = TextStyle(fontFamily = mono, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Ink.text, letterSpacing = (-0.02).em)

}

/**
 * Every Material role is mapped onto the archive palette, so components that read roles the app never sets itself
 * (the date and time pickers above all) show ink, ivory and hairline grey instead of the Material baseline lavender.
 */
private val ArchiveColors = lightColorScheme(
    primary = Ink.accent,
    onPrimary = Ink.bg,
    // time picker: selected hour/minute box (filled ink, like the app's selected tabs)
    primaryContainer = Ink.accent,
    onPrimaryContainer = Ink.bg,
    secondary = Ink.cafe,
    onSecondary = Ink.bg,
    // date picker range fill and other tonal highlights
    secondaryContainer = Ink.surfaceRaised,
    onSecondaryContainer = Ink.text,
    tertiary = Ink.cupping,
    // time picker: selected AM/PM
    tertiaryContainer = Ink.accent,
    onTertiaryContainer = Ink.bg,
    background = Ink.bg,
    onBackground = Ink.text,
    surface = Ink.surface,
    onSurface = Ink.text,
    surfaceVariant = Ink.surfaceRaised,
    onSurfaceVariant = Ink.textMuted,
    surfaceBright = Ink.surface,
    surfaceDim = Ink.surfaceRaised,
    surfaceContainerLowest = Ink.surface,
    surfaceContainerLow = Ink.surface,
    surfaceContainer = Ink.surface,
    // date picker dialog container (and any default dialog): ivory
    surfaceContainerHigh = Ink.bg,
    // time picker clock dial and unselected hour/minute boxes
    surfaceContainerHighest = Ink.surfaceRaised,
    outline = Ink.line,
    outlineVariant = Ink.line,
    error = Ink.bad,
    onError = Ink.bg,
)

/** Design §5.3: corner radius 0 everywhere, including Material's own dialogs, pickers and menus. */
private val Square = RoundedCornerShape(0.dp)
private val ArchiveShapes = Shapes(extraSmall = Square, small = Square, medium = Square, large = Square, extraLarge = Square)

private fun archiveTypography() = Typography(
    bodyLarge = AppType.body,
    bodyMedium = AppType.body,
    bodySmall = AppType.small,
    titleLarge = AppType.title,
    titleMedium = AppType.cardTitle,
    labelSmall = AppType.sectionLabel,
    labelMedium = AppType.fieldLabel,
)

/**
 * The archive theme with this phone's display settings ([Display.current]): the typefaces through [AppType] and the
 * Material typography, the text size through the density every sp is converted with.
 */
@Composable
fun CoffeeJournalTheme(content: @Composable () -> Unit) {
    val display = Display.current
    val base = LocalDensity.current
    val density = remember(base, display.textSize) {
        if (display.textSize.scale == 1f) base else scaledTextDensity(base, display.textSize.scale)
    }
    val typography = remember(display.bodyFont, display.numberFont) { archiveTypography() }
    CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(colorScheme = ArchiveColors, typography = typography, shapes = ArchiveShapes) {
            Surface(Modifier.fillMaxSize().background(Ink.bg), color = Ink.bg, contentColor = Ink.text, content = content)
        }
    }
}
