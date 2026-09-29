package com.coffeejournal.ui.guide

import com.coffeejournal.ui.ai.AiKeySlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The how-tos opened from 설정 and the picker: one per key, every part there, every link a real page named in its step. */
class KeyHowTosTest {
    @Test fun everyKeyHasItsHowTo_andTheIdsAreDistinct() {
        AiKeySlot.entries.forEach { slot -> assertNotNull(KeyHowTos.of(slot.guide), "${slot.label} has no how-to") }
        assertNotNull(KeyHowTos.of("kakao"))
        assertEquals(KeyHowTos.ALL.size, KeyHowTos.ALL.map { it.id }.toSet().size)
        assertEquals(listOf("gemini", "tavily", "openai", "claude", "kakao"), KeyHowTos.ALL.map { it.id })
    }

    @Test fun everyHowTo_goesFromGettingTheKeyToTroubleshooting() {
        KeyHowTos.ALL.forEach { h ->
            assertTrue(h.title.isNotBlank() && h.intro.isNotBlank(), h.id)
            assertTrue(h.asOf.startsWith("2026년"), "${h.id}: ${h.asOf}")
            val headings = h.sections.map { it.heading }
            listOf("키 받기", "앱에 넣기", "문제가 생기면").forEach { assertTrue(it in headings, "${h.id} lacks $it: $headings") }
            assertTrue(h.sections.single { it.heading == "키 받기" }.numbered, "${h.id}: the steps are numbered")
            assertTrue(h.sections.all { it.items.isNotEmpty() && it.items.all { i -> i.text.isNotBlank() } }, h.id)
            assertTrue(h.sections.sumOf { it.items.size } >= 15, "${h.id} is detailed: ${h.sections.sumOf { it.items.size }} items")
        }
    }

    @Test fun everyLink_isHttps_andNamedInItsStep() {
        KeyHowTos.ALL.forEach { h ->
            h.sections.flatMap { it.items }.forEach { step ->
                step.links.forEach { (words, url) ->
                    assertTrue(url.startsWith("https://"), "${h.id}: $url")
                    assertTrue(words.isNotBlank() && words in step.text, "${h.id}: '$words' is not in \"${step.text}\"")
                }
            }
        }
    }

    @Test fun theOpenLabel_namesTheKey() {
        assertEquals("Gemini API 키 받는 법 자세히 ›", GuideTexts.open(AiKeySlot.GEMINI.label))
    }
}
