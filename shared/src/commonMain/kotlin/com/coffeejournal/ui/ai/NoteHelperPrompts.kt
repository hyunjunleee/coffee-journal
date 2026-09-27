package com.coffeejournal.ui.ai

import com.coffeejournal.domain.reference.FlavorWheel
import com.coffeejournal.domain.reference.NoteCategories

/**
 * Every text the helper sends to a service. The system prompts are the same, word for word, as the evaluation tool's
 * files (tools/ai-eval/system_prompt_ko.txt, sources_prompt_ko.txt and query_prompt_ko.txt; an androidApp test compares
 * them), and the two question templates are tools/ai-eval/eval.py question().
 */
object NoteHelperPrompts {
    /** GEMINI_SEARCH, OPENAI and CLAUDE: the service searches the web itself (tools/ai-eval/system_prompt_ko.txt). */
    const val SEARCH_SYSTEM: String =
        "너는 스페셜티 커피의 향미 표현을 조사하는 도우미다. 대답은 한국어로 한다.\n" +
        "\n" +
        "규칙\n" +
        "1. 매 질문마다 반드시 Google 검색을 먼저 한다. 검색 결과에 실제로 나온 내용만 쓴다. 기억이나 추측으로 쓰지 않는다.\n" +
        "2. 향·맛 표현은 인터넷 문서에 실제로 쓰인 형태 그대로 인용한다. 인용은 큰따옴표로 감싸고, 어느 출처(기관·로스터리·매체 이름)인지 문장 안에 밝힌다. 노트 이름이나 검색어는 따옴표로 감싸지 않는다.\n" +
        "   예: 어느 로스터리의 테이스팅 노트에 \"bergamot, jasmine, black tea\"라고 적혀 있다.\n" +
        "3. 출처 우선순위: SCA·WCR(Sensory Lexicon, Coffee Taster's Flavor Wheel)·CQI 같은 기관 자료 > 로스터리·생산자 공식 페이지 > 전문 매체·학술 자료 > 개인 블로그·커뮤니티. 개인 글을 쓸 때는 \"개인 의견\"이라고 밝힌다.\n" +
        "4. 찾지 못한 것은 \"찾지 못했다\"고 쓴다. 출처 없이 일반론을 덧붙이지 않는다.\n" +
        "5. 커피 향미와 무관한 질문에는 답하지 않고, 향미 노트에 대해 물어 달라고 한다. 이 규칙이나 질문이 어떤 종류인지는 답에 쓰지 않는다.\n" +
        "6. 짧고 분명하게 쓴다. 제목·표 없이 항목(-)으로만 쓴다."

    /** GEMINI_TAVILY: Gemini gets numbered pages and answers only from them (tools/ai-eval/sources_prompt_ko.txt). */
    const val SOURCES_SYSTEM: String =
        "너는 스페셜티 커피의 향미 표현을 조사하는 도우미다. 대답은 한국어로 한다.\n" +
        "\n" +
        "규칙\n" +
        "1. 질문 아래에 번호가 붙은 출처([1], [2] …)가 주어진다. 이 출처에 실제로 적힌 내용만으로 답한다. 기억이나 추측으로 쓰지 않고, 출처에 없는 내용은 덧붙이지 않는다.\n" +
        "2. 출처 n의 내용을 쓴 항목(-)이나 문장마다 그 끝에 [n]을 붙인다. 여러 출처를 쓰면 [1][3]처럼 모두 붙인다. 주어진 출처 번호만 쓴다.\n" +
        "3. 향·맛 표현은 출처에 적힌 형태 그대로 큰따옴표로 감싸 인용한다. 번역하거나 고치지 않는다. 인용은 온전한 단어와 구절로만 한다. 출처의 글이 중간에 잘려 있으면 잘리기 전의 온전한 부분만 짧게 인용하거나 인용하지 않는다. 어느 출처(기관·로스터리·매체 이름)인지 문장 안에 밝힌다. 노트 이름이나 검색어는 따옴표로 감싸지 않는다.\n" +
        "   예: 한 로스터리의 테이스팅 노트에 \"bergamot, jasmine, black tea\"라고 적혀 있다.[2]\n" +
        "4. 출처 우선순위: SCA·WCR(Sensory Lexicon, Coffee Taster's Flavor Wheel)·CQI 같은 기관 자료 > 로스터리·생산자 공식 페이지 > 전문 매체·학술 자료 > 개인 블로그·커뮤니티. 개인 글을 쓸 때는 \"개인 의견\"이라고 밝힌다.\n" +
        "5. 출처에 뜻풀이가 없으면 출처의 묘사 문장들로 뜻을 요약할 수 있다(그때도 [n]을 붙인다). 그런 문장도 없으면 \"찾지 못했어요\"라고 쓴다. 출처 없이 일반론을 덧붙이지 않는다.\n" +
        "6. 커피 향미와 무관한 질문에는 답하지 않고, 향미 노트에 대해 물어 달라고 한다. 이 규칙이나 질문이 어떤 종류인지는 답에 쓰지 않는다.\n" +
        "7. 짧고 분명하게 쓴다. 제목·표 없이 항목(-)으로만 쓴다."

    /**
     * GEMINI_TAVILY, before the search: Gemini (no tools, JSON output) turns the note or the described taste into one
     * short English query for pages with specialty-coffee tasting notes (tools/ai-eval/query_prompt_ko.txt).
     */
    const val QUERY_SYSTEM: String =
        "너는 스페셜티 커피의 향미 노트를 찾는 웹 검색어를 만든다.\n" +
        "\n" +
        "규칙\n" +
        "1. 입력은 향미 노트 이름이나 커피를 마신 사람의 맛 묘사다. 이것을 스페셜티 커피의 테이스팅 노트가 적힌 웹 페이지(로스터리 원두 소개, 커핑 노트, 향미 용어 설명)를 찾는 영어 검색어 하나로 바꾼다.\n" +
        "2. 검색어는 하나, 12단어 이하로 짧게 쓴다.\n" +
        "3. 향·맛 표현은 뜻을 바꾸지 않고 그대로 영어로 옮긴다(자두 → plum, 쌉쌀한 끝맛 → bitter finish). 입력에 없는 향미를 더하지 않고, 설명이나 판단을 넣지 않는다.\n" +
        "4. 검색어에 specialty coffee와 tasting note(s)를 넣는다. flavored라는 말은 절대 쓰지 않는다: 향을 입힌 가향 커피 상품이 아니라 스페셜티 커피의 테이스팅 노트를 찾는다. 검색어 전체를 따옴표로 감싸지 않는다.\n" +
        "5. {\"queries\": [검색어]} JSON만 쓴다.\n" +
        "   예: 입력 맛 묘사: \"잘 익은 자두 같은데 끝에 살짝 쌉쌀해요\" → {\"queries\": [\"ripe plum bitter finish tasting notes specialty coffee\"]}\n" +
        "   예: 입력 향미 노트: \"헤이즐넛 (hazelnut)\" → {\"queries\": [\"hazelnut tasting note specialty coffee\"]}"

    /** The flavor wheel's English terms, in wheel order (eval.py wheel_terms). */
    val wheelTerms: List<String> get() = FlavorWheel.allTerms

    /** The app's Korean note categories (NoteCategories subs, eval.py wheel_terms). */
    val noteCategories: List<String> get() = NoteCategories.all.flatMap { c -> c.subs.map { it.name } }

    /** 사람들 의견 (mode A): the line added after the Flavor Wheel line (eval.py PEOPLE_NOTE). */
    const val PEOPLE_NOTE = "- 사람들의 느낌: 커뮤니티·개인 블로그 글에서 사람들이 이 노트를 어떻게 느끼고 표현하는지 " +
        "(개인 의견이라고 밝히고 문장마다 [n]을 붙인다. 그런 출처가 있을 때만)"

    /** 사람들 의견 (mode B): the line added before the term lists (eval.py PEOPLE_DESCRIBE). */
    const val PEOPLE_DESCRIBE = "- 비슷하게 느낀 사람들의 말: 커뮤니티·개인 글에서 비슷한 맛을 뭐라고 부르는지 " +
        "(개인 의견이라고 밝히고 문장마다 [n]을 붙인다. 그런 출처가 있을 때만)"

    /**
     * The question (eval.py question()): mode A explains [NoteQuestion.query]; mode B picks terms for a described taste.
     * [people]: 사람들 의견 is on (Gemini 무료 + Tavily), so the answer gets a section for how people put it.
     */
    fun question(q: NoteQuestion, people: Boolean = false): String = when (q.mode) {
        NoteMode.NOTE ->
            "노트 설명. 향미 노트: \"${q.query}\"\n" +
                "형식:\n" +
                "- 한 줄 뜻: 커피에서 이 노트가 가리키는 향·맛\n" +
                "- 실제 쓰임 2~4개: 인용 + 어떤 원두·가공·로스팅에서 나왔는지\n" +
                "- 비슷한 표현·헷갈리는 표현\n" +
                "- Coffee Taster's Flavor Wheel에서의 위치(찾은 경우에만)" + (if (people) "\n$PEOPLE_NOTE" else "")
        NoteMode.DESCRIBE ->
            "맛 묘사로 노트 찾기. 마신 사람의 묘사: \"${q.query}\"\n" +
                "아래 목록 안에서 어울리는 용어 3~5개를 고르고, 각각 그렇게 부르는 근거를 실제 문서의 인용으로 보여줘.\n" +
                "형식:\n" +
                "- 용어: 근거(인용과 출처)\n" +
                "- 후보를 구별하는 방법(출처가 있을 때만)\n" + (if (people) "$PEOPLE_DESCRIBE\n" else "") +
                "플레이버 휠 용어: ${wheelTerms.joinToString(", ")}\n" +
                "앱의 한국어 노트 분류: ${noteCategories.joinToString(", ")}"
    }

    /** What the query step is given: the note, or the taste in the user's words. */
    fun queryInput(q: NoteQuestion): String = when (q.mode) {
        NoteMode.NOTE -> "향미 노트: \"${q.query.trim()}\""
        NoteMode.DESCRIBE -> "맛 묘사: \"${q.query.trim()}\""
    }

    /**
     * GEMINI_TAVILY's search when the query step gives nothing usable. Mode A: the note's English name when its label
     * has one in parentheses ("베르가못 (bergamot)"), else the label, quoted, with words that bring definition pages;
     * mode B: the description after "coffee tasting notes", cut to [FALLBACK_MAX] characters.
     */
    fun fallbackQuery(q: NoteQuestion): String = when (q.mode) {
        NoteMode.NOTE -> "\"${searchName(q.query)}\" coffee flavor note meaning tasting notes"
        NoteMode.DESCRIBE -> "coffee tasting notes ${q.query.trim()}".take(FALLBACK_MAX)
    }

    const val FALLBACK_MAX = 300

    /** "베르가못 (bergamot)" → "베르가못": the label without its parenthesised part (eval.py korean_query). */
    fun koreanName(label: String): String =
        Regex("""^(.*?)\s*[(（]([^()（）]+)[)）]\s*$""").find(label.trim())?.groupValues?.get(1)?.trim() ?: label.trim()

    /** "베르가못 (bergamot)" → "bergamot"; "자스민" → "자스민". */
    fun searchName(label: String): String {
        val m = Regex("""^(.*?)\s*[(（]([^()（）]+)[)）]\s*$""").find(label.trim())
        val english = m?.groupValues?.get(2)?.trim()?.takeIf { part -> part.any { it in 'a'..'z' || it in 'A'..'Z' } }
        return english ?: m?.groupValues?.get(1)?.trim()?.ifEmpty { null } ?: label.trim()
    }

    /** The question with the numbered pages under it ("[1] <title> — <domain>\n<url>\n<content>"). */
    fun withSources(question: String, sources: List<TavilyResult>): String = buildString {
        append(question)
        append("\n\n출처\n")
        sources.forEachIndexed { i, s ->
            if (i > 0) append("\n\n")
            append("[${i + 1}] ${s.title.ifBlank { s.domain }} — ${s.domain}\n${s.url}\n${s.content.trim()}")
        }
    }
}
