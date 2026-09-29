package com.coffeejournal.android

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 국가 · 지역 · 세부 지역 from their lists, and 재배 고도 as a number with its "m", through the real record form: the
 * record keeps the region as "시다모, 벤사, 코코세" and the altitude as "1900-2100m", the detail shows the region as a path,
 * and editing opens the fields as they were typed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class OriginFieldsFlowTest : CoverageFlowBase() {
    /** A choice in the list of the field labelled [label]. */
    private fun choice(label: String, value: String) = hasText(value) and hasAnyAncestor(hasTestTag("presets-$label"))

    private fun openList(label: String) = clickNode(hasContentDescription("$label 목록 열기"))

    @Test
    fun countryRegionAndSubRegion_fromTheLists_altitudeAsANumber_savedAsTheWebsHierarchy() {
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "에티오피아 벤사 코코세 테스트")

        // 국가: the list of producing countries, narrowed by typing (English works too)
        openList("국가")
        waitFor(choice("국가", "에티오피아"))
        assertTrue("every country, each with its English name", has(choice("국가", "Ethiopia")) && has(choice("국가", "케냐")))
        typeInto("에티오피아", "ethi")
        waitGone(choice("국가", "케냐"))
        clickNode(choice("국가", "에티오피아"))
        waitFor(hasSetTextAction() and hasText("에티오피아"))

        // 지역: Ethiopia's regions; 세부 지역: the places inside the chosen one, a place inside a place as a path
        openList("지역")
        waitFor(choice("지역", "시다모"))
        clickNode(choice("지역", "시다모"))
        openList("세부 지역")
        waitFor(choice("세부 지역", "벤사 › 코코세"))
        assertTrue(has(choice("세부 지역", "벤사")))
        clickNode(choice("세부 지역", "벤사 › 코코세"))
        waitFor(hasSetTextAction() and hasText("벤사 › 코코세"))

        // 재배 고도: the number keyboard's text only; the "m" is shown after it and saved with it
        typeInto("1900-2100", "1900-2100m 쯤")
        waitFor(hasSetTextAction() and hasText("1900-2100 "))
        saveForm("에티오피아 벤사 코코세 테스트")
        val saved = entries().single()
        assertEquals("시다모, 벤사, 코코세", saved.region)
        assertEquals("1900-2100m", saved.altitude)
        assertEquals("에티오피아", saved.country)
        waitForText("시다모 › 벤사 › 코코세", substring = true)
        assertTrue(has(hasText("1900-2100m")))

        // editing opens 지역 and 세부 지역 apart again, the altitude without its unit
        clickText("수정")
        waitForText("기록 수정")
        waitFor(hasSetTextAction() and hasText("시다모"))
        assertTrue(has(hasSetTextAction() and hasText("벤사 › 코코세")) && has(hasSetTextAction() and hasText("1900-2100")))
        assertFalse(has(hasSetTextAction() and hasText("1900-2100m")))
    }

    @Test
    fun aSubRegionTypedByHand_withAnySeparator_isKeptAsItsPlaces() {
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "케냐 손으로 적은 세부 지역")
        typeInto("에티오피아", "케냐")
        typeInto("시다모", "니에리")
        typeInto("벤사 › 코코세", "오타야 > 카가냐")
        saveForm("케냐 손으로 적은 세부 지역")
        assertEquals("니에리, 오타야, 카가냐", entries().single().region)
    }
}
