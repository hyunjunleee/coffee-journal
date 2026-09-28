package com.coffeejournal.ui.settings

import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.ui.theme.BodyFont
import com.coffeejournal.ui.theme.DerivationDispatcher
import com.coffeejournal.ui.theme.DisplaySettings
import com.coffeejournal.ui.theme.Motion
import com.coffeejournal.ui.theme.NumberFont
import com.coffeejournal.ui.theme.TextSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/**
 * 설정 › 화면, kept in [SettingsRepository] under device keys ([SettingsRepository.DEVICE_PREFIX]): typefaces, text size
 * and transition speed belong to this phone, so a backup neither carries nor restores them. A missing or unknown value
 * reads as the default.
 */
class DisplayPrefs(private val settings: SettingsRepository) {
    /**
     * The saved display settings, again only when they change: every write to the settings table re-reads them (the
     * record form writes its draft there while it is typed), and the app root collects this in its composition.
     */
    fun observe(): Flow<DisplaySettings> = combine(
        settings.observe(KEY_BODY_FONT),
        settings.observe(KEY_NUMBER_FONT),
        settings.observe(KEY_TEXT_SIZE),
        settings.observe(KEY_MOTION),
    ) { body, number, size, motion -> decode(body, number, size, motion) }.distinctUntilChanged().flowOn(DerivationDispatcher)

    suspend fun load(): DisplaySettings =
        decode(settings.get(KEY_BODY_FONT), settings.get(KEY_NUMBER_FONT), settings.get(KEY_TEXT_SIZE), settings.get(KEY_MOTION))

    suspend fun save(display: DisplaySettings) {
        settings.put(KEY_BODY_FONT, display.bodyFont.name)
        settings.put(KEY_NUMBER_FONT, display.numberFont.name)
        settings.put(KEY_TEXT_SIZE, display.textSize.name)
        settings.put(KEY_MOTION, display.motion.name)
    }

    companion object {
        const val KEY_BODY_FONT = SettingsRepository.DEVICE_PREFIX + "display.bodyFont"
        const val KEY_NUMBER_FONT = SettingsRepository.DEVICE_PREFIX + "display.numberFont"
        const val KEY_TEXT_SIZE = SettingsRepository.DEVICE_PREFIX + "display.textSize"
        const val KEY_MOTION = SettingsRepository.DEVICE_PREFIX + "display.motion"

        fun decode(body: String?, number: String?, size: String?, motion: String?): DisplaySettings {
            val d = DisplaySettings()
            return DisplaySettings(
                bodyFont = BodyFont.entries.firstOrNull { it.name == body } ?: d.bodyFont,
                numberFont = NumberFont.entries.firstOrNull { it.name == number } ?: d.numberFont,
                textSize = TextSize.entries.firstOrNull { it.name == size } ?: d.textSize,
                motion = Motion.entries.firstOrNull { it.name == motion } ?: d.motion,
            )
        }
    }
}
