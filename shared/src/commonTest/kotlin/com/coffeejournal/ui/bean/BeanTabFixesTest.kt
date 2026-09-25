package com.coffeejournal.ui.bean

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** beanA-7 (Korean collation) and beanA-3 / design-7 (the selected sub tab is scrolled into view). */
class BeanTabFixesTest {
    @Test fun koreanOrder_matchesTheWebsLocaleCompareKo() {
        // Node 22 + ICU: ['가나','자스민','apple','Banana','Bergamot','Stone fruit'].sort((a,b) => a.localeCompare(b,'ko'))
        val web = listOf("가나", "자스민", "apple", "Banana", "Bergamot", "Stone fruit")
        assertEquals(web, listOf("Stone fruit", "Bergamot", "자스민", "Banana", "apple", "가나").sortedWith(KoreanOrder))
        assertTrue(KoreanOrder.compare("10번", "가") < 0, "digits before Hangul")
        assertTrue(KoreanOrder.compare("홍차", "Honey") < 0, "Hangul before Latin")
        assertTrue(KoreanOrder.compare("apple", "Apple") < 0, "lower case first at an otherwise equal text")
        assertEquals(0, KoreanOrder.compare("레드 허니", "레드 허니"))
    }

    @Test fun subTabScroll_bringsAnOffscreenChipIntoView_andLeavesAVisibleOneAlone() {
        val viewport = 379; val peek = 28; val max = 600
        // the default 커피 지도 chip (8th) sits beyond the first screen: scroll so it ends inside, with the next chip peeking
        assertEquals(700 - 379 + 28, SubTabScroll.target(start = 560, end = 700, viewport = viewport, current = 0, max = max, peek = peek))
        // the last chip is clamped to the scroll range
        assertEquals(max, SubTabScroll.target(start = 850, end = 979, viewport = viewport, current = 0, max = max, peek = peek))
        // back to 커피 노트 from the end of the row
        assertEquals(0, SubTabScroll.target(start = 0, end = 80, viewport = viewport, current = 600, max = max, peek = peek))
        // a chip already fully on screen keeps the row where it is
        assertEquals(120, SubTabScroll.target(start = 200, end = 300, viewport = viewport, current = 120, max = max, peek = peek))
    }
}
