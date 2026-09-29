package com.coffeejournal.ui.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Sentences, [n] marks, quote checks and mode B's term matching. */
class AnswerTextTest {
    private fun sentences(text: String) = Sentences.split(text).filter { !it.gap }.map { text.substring(it.start, it.end) }

    @Test fun sentences_splitAtEnds_notInsideQuotesOrNumbers_bulletsAreGaps() {
        val text = "- 첫 문장이다. 둘째는 \"sweet. juicy\"라고 한다! 1.5배 진하다?\n1) 목록도 된다."
        assertEquals(listOf("첫 문장이다.", "둘째는 \"sweet. juicy\"라고 한다!", "1.5배 진하다?", "목록도 된다."), sentences(text))
        // every character belongs to exactly one span
        val spans = Sentences.split(text)
        assertEquals(text.length, spans.sumOf { it.end - it.start })
        assertEquals(listOf("- ", "1) "), spans.filter { it.gap && text.substring(it.start, it.end).isNotBlank() }.map { text.substring(it.start, it.end) })
    }

    @Test fun marks_stripped_inEveryForm_positionsKept() {
        val m = SourcedAnswer.stripMarks("가.[1] 나 [2]. 다[1, 3] 라 [4]마")
        assertEquals("가. 나. 다 라 마", m.text)
        assertEquals(listOf(2 to listOf(1), 4 to listOf(2), 7 to listOf(1, 3), 10 to listOf(4)), m.marks)
    }

    @Test fun compose_pointMarksGoToTheirSentence_rangesMarkExactly_uncitedStayGrey() {
        val text = "하나다. 둘이다. 셋이다."
        // a point just before the second sentence's period, a range over the third
        val ps = AnswerComposer.compose(text, listOf(Citation(8, 8, listOf(2)), Citation(10, 14, listOf(1), Citation.Anchor.EXACT)))
        val runs = ps.single().runs
        assertEquals(listOf("하나다.", " ", "둘이다.", " ", "셋이다."), runs.map { it.text })
        assertEquals(listOf(false, false, true, false, true), runs.map { it.cited })
        assertEquals(listOf(emptyList(), emptyList(), listOf(2), emptyList(), listOf(1)), runs.map { it.markers })
        assertEquals(text, ps.single().runs.joinToString("") { it.text }, "the text itself is never changed")
    }

    @Test fun compose_exactMarkInsideASentence_splitsTheRun_linesBecomeParagraphs() {
        val text = "- 앞부분은 인용이고 뒷부분은 아니다.\n\n- 둘째 줄."
        val ps = AnswerComposer.compose(text, listOf(Citation(2, 11, listOf(1), Citation.Anchor.EXACT)))
        assertEquals(2, ps.size, "the empty line is not a paragraph")
        assertEquals(listOf("- ", "앞부분은 인용이고", " 뒷부분은 아니다."), ps[0].runs.map { it.text })
        assertEquals(listOf(1), ps[0].runs[1].markers)
        assertTrue(ps[0].runs[2].cited, "the rest of a tied sentence is ink too")
        assertFalse(ps[1].runs.last().cited)
    }

    @Test fun quotes_normalized_nfcCaseSpacesCurlyQuotes_cutOffPrefixStillFound() {
        // "향" written as three combining jamo (NFD) equals the precomposed syllable
        assertEquals("향", QuoteChecks.normalize("향"))
        assertEquals("\"a\" 'b' c d", QuoteChecks.normalize("  “A”   ‘B’\n C\tD "))
        val page = listOf("Tasting notes: Lychee, white peach, red currant. Washed.")
        fun check(quote: String) = QuoteChecks.check("노트는 \"$quote\"이다.", listOf(Citation(0, 0, listOf(1), evidence = page)), QuoteStatus.NOT_FOUND).single()
        assertEquals(QuoteStatus.FOUND, check("LYCHEE,  white peach").status)
        // a snippet cut mid-word still counts: it is a verbatim part of the page
        assertEquals(QuoteStatus.FOUND, check("Lychee, white peach, red c").status)
        assertEquals(QuoteStatus.FOUND, check("white peach…").status)
        assertEquals(QuoteStatus.NOT_FOUND, check("yellow peach").status)
        // curly quotes around the expression in the answer
        assertEquals(listOf("red currant"), QuoteChecks.quotesIn("한 로스터리는 “red currant”라고 했다."))
        // no evidence (Gemini with Google Search, OpenAI): no check
        assertTrue(QuoteChecks.check("\"x y\"", listOf(Citation(0, 0, listOf(1))), QuoteStatus.NOT_FOUND).isEmpty())
    }

    @Test fun terms_wheelWordsWholeWordAnyCase_koreanLongestFirst_inOrder_once() {
        val answer = "- Jasmine: 한 로스터리는 \"jasmine, black tea\"라고 했다.\n" +
            "- 블루베리와 다크 초콜릿, 풀바디 느낌. Blackberry 같은 베리 향과 꿀처럼 단맛, 흑설탕.\n" +
            "- Moldy/Damp 는 아니고 hay like 도 아니다. JASMINE 다시."
        assertEquals(
            listOf("Jasmine", "Black Tea", "블루베리", "다크 초콜릿", "Blackberry", "베리", "꿀", "흑설탕", "Moldy / Damp", "Hay-like"),
            NoteTerms.find(answer),
        )
        // "풀" (grass) is not taken out of "풀바디"; "Tea" alone and "berry" inside "Blackberry" are not terms
        assertFalse("풀" in NoteTerms.find("풀바디에 부드러운 질감"))
        assertEquals(listOf("풀"), NoteTerms.find("풀 냄새가 난다"))
        assertEquals(emptyList(), NoteTerms.find("teacup, blackberries"))
    }

    @Test fun terms_includeTheWheelsUnofficialNotes_andItsMiddleTier_notItsInnerLabels() {
        // extras (FlavorWheelExtras) are candidates too; "Olive Oil" and "Green Pepper" win over "Olive" and "Pepper"
        assertEquals(
            listOf("Apricot", "Tartaric Acid", "Brown Sugar", "Olive Oil", "Green Pepper", "Pink Peppercorn"),
            NoteTerms.find("\"apricot, tartaric acid, brown sugar\" 그리고 olive oil, green pepper, pink peppercorn"),
        )
        // the wheel's inner labels are no candidates: "other" and "sweet" are everyday words
        assertEquals(emptyList(), NoteTerms.find("other notes, sweet finish"))
    }

    @Test fun formNotes_mergedAfterTheOldOnes_withoutDuplicates() {
        assertEquals(listOf("자스민", "Bergamot", "꿀"), NoteHelperResult.merge(listOf("자스민", "Bergamot"), listOf("bergamot", " 꿀 ", "꿀", "")))
        assertEquals(listOf("a", "b"), NoteHelperResult.decode(NoteHelperResult.encode(listOf("a", "b"))))
        assertEquals(null, NoteHelperResult.decode("{"))
    }

    @Test fun sourceKinds_andDomains() {
        assertEquals(SourceKind.INSTITUTION, SourceKinds.of("www.sca.coffee"))
        assertEquals(SourceKind.INSTITUTION, SourceKinds.of("store.sca.coffee"))
        assertEquals(SourceKind.PERSONAL, SourceKinds.of("someone.tistory.com"))
        for (d in listOf("blog.naver.com", "m.blog.naver.com", "cafe.naver.com", "m.cafe.naver.com", "brunch.co.kr", "tistory.com")) {
            assertEquals(SourceKind.PERSONAL, SourceKinds.of(d), d)
        }
        assertEquals(SourceKind.OTHER, SourceKinds.of("notsca.coffee.example.com"))
        assertEquals(SourceKind.OTHER, SourceKinds.of("fox.com"), "x.com must not match the end of another name")
        assertEquals("example.com", SourceKinds.domainOf("https://www.Example.com:443/a?b#c"))
        assertEquals("blog.naver.com", SourceKinds.domainOfTitleOrUrl("blog.naver.com", "https://vertexaisearch.cloud.google.com/x"))
        assertEquals("vertexaisearch.cloud.google.com", SourceKinds.domainOfTitleOrUrl("Some Title", "https://vertexaisearch.cloud.google.com/x"))
    }
}
