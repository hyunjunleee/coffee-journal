package com.coffeejournal.android

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.domain.reference.CafeRecipes
import com.coffeejournal.ui.about.Credits
import com.coffeejournal.ui.about.LicenseTexts
import com.coffeejournal.ui.about.platformLibraries
import com.coffeejournal.ui.platform.installUrlOpener
import com.coffeejournal.ui.settings.SettingsTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The 출처 · 라이선스 page (not on the web): reached from 설정 (the gear in the tab header), it credits the content sources
 * with their terms and lists every library of the APK under its license, and the café recipes link their sources.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class AboutFlowTest : CoverageFlowBase() {

    /** The app installs this in CoffeeJournalApplication; the test application does not. */
    @Before
    fun installOpener() = installUrlOpener(ApplicationProvider.getApplicationContext<Application>())

    private fun lastOpenedUrl(): String? {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val intent = Shadows.shadowOf(app).nextStartedActivity ?: return null
        assertEquals(Intent.ACTION_VIEW, intent.action)
        return intent.dataString
    }

    /** Scrolls the lazy list tagged [listTag] until [target] is composed (the top anchor may be gone by then). */
    private fun scrollIn(listTag: String, target: SemanticsMatcher) {
        waitFor(hasTestTag(listTag))
        node(hasTestTag(listTag)).performScrollToNode(target)
        settle(1)
    }

    private fun about(target: SemanticsMatcher) = scrollIn("about-list", target)

    private fun openAbout() {
        launchApp()
        tap(hasTestTag("open-settings"))
        tap(button(SettingsTexts.SOURCES))
        waitForText("출처 · 라이선스")
    }

    @Test
    fun about_isReachedFromTheMiscTab_andCreditsEverySource() {
        openAbout()
        for (credit in Credits.all) {
            about(hasText(credit.title))
            assertTrue("credit ${credit.title}", has(hasText(credit.title)))
        }
        // the flavor wheel's CC BY-NC-ND attribution, word for word from sca.coffee
        about(hasText("The Coffee Taster's Flavor Wheel by SCA and WCR (©2016)", substring = true))
        // a link opens the source in the browser
        about(button("CC BY-NC-ND 4.0 ↗"))
        tap(button("CC BY-NC-ND 4.0 ↗"))
        assertEquals("https://creativecommons.org/licenses/by-nc-nd/4.0/", lastOpenedUrl())
        // the icon licenses open in full
        about(hasText("아이콘 · Lucide"))
        tap(button("라이선스 전문 보기") and hasAnyAncestor(hasAnyChild(hasText("아이콘 · Lucide"))))
        waitForText("Copyright (c) 2013-present Cole Bemis", substring = true)
        // the detail map's credit: MapLibre's notices in full, with the C++ libraries MapLibre Native bundles
        about(hasText("상세 지도 · OpenStreetMap"))
        tap(button("라이선스 전문 보기") and hasAnyAncestor(hasAnyChild(hasText("상세 지도 · OpenStreetMap"))))
        waitForText("Copyright (c) 2021 MapLibre contributors", substring = true)
        waitForText("kdbush.hpp", substring = true)
    }

    @Test
    fun about_listsEveryLibraryUnderItsLicense() {
        openAbout()
        val libraries = platformLibraries
        assertTrue("the generated list is not empty", libraries.size > 100)
        assertTrue("every library has a license the page can name", libraries.all { lib -> lib.licenses.isNotEmpty() && lib.licenses.all { LicenseTexts.url(it) != null || LicenseTexts.text(it) != null } })
        about(hasText("${libraries.size}개"))
        about(hasText("Apache License 2.0", substring = true))
        tap(button("전문 보기"))
        waitForText("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION", substring = true)
        tap(button("전문 접기"))
        waitGone(hasText("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION", substring = true))
        // a library row opens its project page
        val koin = libraries.first { it.group == "io.insert-koin" }
        // the row merges its two lines into one button
        val row = button("${koin.group}:${koin.artifact}:${koin.version}")
        about(row)
        tap(row)
        assertEquals(koin.url, lastOpenedUrl())
    }

    @Test
    fun cafeRecipes_showAndOpenTheirSources() {
        launchApp()
        openNewForm()
        clickText("☕ 카페 레시피")
        waitForText("유명 스페셜티 카페들이 공개한 브루 가이드예요.", substring = true)
        val sourced = CafeRecipes.all.filter { it.sourceUrl != null }
        assertEquals(listOf("glitch", "kurasu", "kurasu-origami"), sourced.map { it.id })
        assertEquals("no link where the web gave no source", 3, count(hasText("출처: ", substring = true) and hasClickAction()))
        tap(button("출처: kurasu.kyoto ↗"), 1)
        assertEquals(CafeRecipes.all.single { it.id == "kurasu-origami" }.sourceUrl, lastOpenedUrl())
        // the champions carry the note that their numbers are not WBrC material
        clickText("🏆 챔피언 레시피")
        waitForText("레시피 수치는 인터뷰·영상 등 공개 자료를 정리한 값이라 대회 공식 자료는 아니에요.", substring = true)
        assertFalse(has(hasText("출처: shop.glitchcoffee.com ↗")))
    }
}
