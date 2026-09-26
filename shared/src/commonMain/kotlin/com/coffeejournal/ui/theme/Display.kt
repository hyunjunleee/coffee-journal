package com.coffeejournal.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density

/** 설정 › 화면 › 글꼴: the typeface of the running text. Both are the phone's own fonts; nothing is bundled or downloaded. */
enum class BodyFont(val label: String) {
    SANS("고딕"),

    /** The phone's serif; Hangul falls back to its serif CJK font (Noto Serif CJK on most phones), else to the sans. */
    SERIF("명조"),
}

/** 설정 › 화면 › 제목·숫자: the archive's monospace for headers and numbers, or the running text's typeface. */
enum class NumberFont(val label: String) {
    MONO("고정폭"),
    BODY("본문과 같게"),
}

/** 설정 › 화면 › 글자 크기: multiplies the phone's own font size setting. */
enum class TextSize(val label: String, val scale: Float) {
    SMALL("작게", 0.9f),
    NORMAL("기본", 1f),
    LARGE("크게", 1.15f),
    LARGER("더 크게", 1.3f),
}

/**
 * 설정 › 화면 › 화면 전환: how long a screen fades into the next when one opens or closes. Navigation's own default is
 * 700 ms, which read as a slow blur; the app now uses 400 ms unless the user picks otherwise.
 */
enum class Motion(val label: String, val millis: Int) {
    OFF("끔", 0),
    FAST("빠르게", 200),
    NORMAL("기본", 400),
    SLOW("느리게", 700),
}

/** The display choices of this phone (설정 › 화면). They are device settings, so a backup neither carries nor restores them. */
data class DisplaySettings(
    val bodyFont: BodyFont = BodyFont.SANS,
    val numberFont: NumberFont = NumberFont.MONO,
    val textSize: TextSize = TextSize.NORMAL,
    val motion: Motion = Motion.NORMAL,
)

/**
 * The display settings in effect. [AppType]'s styles read [current] when they are used, so every text in the app follows
 * a change at once (a snapshot state: composables that read a style recompose). App sets it from the saved settings
 * before it shows anything.
 */
object Display {
    var current: DisplaySettings by mutableStateOf(DisplaySettings())

    fun reset() {
        current = DisplaySettings()
    }

    val bodyFamily: FontFamily
        get() = when (current.bodyFont) {
            BodyFont.SANS -> FontFamily.Default
            BodyFont.SERIF -> FontFamily.Serif
        }

    val numberFamily: FontFamily
        get() = when (current.numberFont) {
            NumberFont.MONO -> FontFamily.Monospace
            NumberFont.BODY -> bodyFamily
        }
}

/**
 * [base] as if the phone's font size setting were [scale] times larger: sp sizes grow, dp sizes do not. The conversions
 * are the platform's own for that font scale, so on Android 14+ large text grows less than small text (non-linear font
 * scaling), exactly as with the phone's setting; Compose lays text out through the same font scale.
 */
internal fun scaledTextDensity(base: Density, scale: Float): Density = Density(base.density, base.fontScale * scale)
