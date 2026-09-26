package com.coffeejournal.ui.ai

import com.coffeejournal.domain.reference.FlavorWheel
import com.coffeejournal.domain.reference.NoteCategories

/**
 * Every text the helper sends to a service. The system prompts are the same, word for word, as the evaluation tool's
 * files (tools/ai-eval/system_prompt_ko.txt and sources_prompt_ko.txt; an androidApp test compares them), and the two
 * question templates are tools/ai-eval/eval.py question().
 */
object NoteHelperPrompts {
    /** GEMINI_SEARCH, OPENAI and CLAUDE: the service searches the web itself (tools/ai-eval/system_prompt_ko.txt). */
    const val SEARCH_SYSTEM: String =
        "너는 스페셜티 커피의 향미 표현을 조사하는 도우미다. 대답은 한국어로 한다.\n" +
        "\n" +
        "규칙\n" +
        "1. 매 질문마다 반드시 Google 검색을 먼저 한다. 검색 결과에 실제로 나온 내용만 쓴다. 기억이나 추측으로 쓰지 않는다.\n" +
        "2. 향·맛 표현은 인터넷 문서에 실제로 쓰인 형태 그대로 인용한다. 인용은 큰따옴표로 감싸고, 어느 출처(기관·로스터리·매체 이름)인지 문장 안에 밝힌다.\n" +
        "   예: 어느 로스터리의 테이스팅 노트에 \"bergamot, jasmine, black tea\"라고 적혀 있다.\n" +
        "3. 출처 우선순위: SCA·WCR(Sensory Lexicon, Coffee Taster's Flavor Wheel)·CQI 같은 기관 자료 > 로스터리·생산자 공식 페이지 > 전문 매체·학술 자료 > 개인 블로그·커뮤니티. 개인 글을 쓸 때는 \"개인 의견\"이라고 밝힌다.\n" +
        "4. 찾지 못한 것은 \"찾지 못했다\"고 쓴다. 출처 없이 일반론을 덧붙이지 않는다.\n" +
        "5. 커피 향미와 무관한 질문에는 답하지 않고, 향미 노트에 대해 물어 달라고 한다.\n" +
        "6. 짧고 분명하게 쓴다. 제목·표 없이 항목(-)으로만 쓴다."

    /** GEMINI_TAVILY: Gemini gets numbered pages and answers only from them (tools/ai-eval/sources_prompt_ko.txt). */
    const val SOURCES_SYSTEM: String =
        "너는 스페셜티 커피의 향미 표현을 조사하는 도우미다. 대답은 한국어로 한다.\n" +
        "\n" +
        "규칙\n" +
        "1. 질문 아래에 번호가 붙은 출처([1], [2] …)가 주어진다. 이 출처에 실제로 적힌 내용만으로 답한다. 기억이나 추측으로 쓰지 않고, 출처에 없는 내용은 덧붙이지 않는다.\n" +
        "2. 출처 n의 내용을 쓴 항목(-)이나 문장마다 그 끝에 [n]을 붙인다. 여러 출처를 쓰면 [1][3]처럼 모두 붙인다. 주어진 출처 번호만 쓴다.\n" +
        "3. 향·맛 표현은 출처에 적힌 형태 그대로 큰따옴표로 감싸 인용한다. 번역하거나 고치지 않는다. 인용은 온전한 단어와 구절로만 한다. 출처의 글이 중간에 잘려 있으면 잘리기 전의 온전한 부분만 짧게 인용하거나 인용하지 않는다. 어느 출처(기관·로스터리·매체 이름)인지 문장 안에 밝힌다.\n" +
        "   예: 한 로스터리의 테이스팅 노트에 \"bergamot, jasmine, black tea\"라고 적혀 있다.[2]\n" +
        "4. 출처 우선순위: SCA·WCR(Sensory Lexicon, Coffee Taster's Flavor Wheel)·CQI 같은 기관 자료 > 로스터리·생산자 공식 페이지 > 전문 매체·학술 자료 > 개인 블로그·커뮤니티. 개인 글을 쓸 때는 \"개인 의견\"이라고 밝힌다.\n" +
        "5. 출처에 뜻풀이가 없으면 출처의 묘사 문장들로 뜻을 요약할 수 있다(그때도 [n]을 붙인다). 그런 문장도 없으면 \"찾지 못했어요\"라고 쓴다. 출처 없이 일반론을 덧붙이지 않는다.\n" +
        "6. 커피 향미와 무관한 질문에는 답하지 않고, 향미 노트에 대해 물어 달라고 한다.\n" +
        "7. 짧고 분명하게 쓴다. 제목·표 없이 항목(-)으로만 쓴다."

    /** The flavor wheel's English terms, in wheel order (eval.py wheel_terms). */
    val wheelTerms: List<String> get() = FlavorWheel.allTerms

    /** The app's Korean note categories (NoteCategories subs, eval.py wheel_terms). */
    val noteCategories: List<String> get() = NoteCategories.all.flatMap { c -> c.subs.map { it.name } }

    /** The question (eval.py question()): mode A explains [NoteQuestion.query]; mode B picks terms for a described taste. */
    fun question(q: NoteQuestion): String = when (q.mode) {
        NoteMode.NOTE ->
            "노트 설명. 향미 노트: \"${q.query}\"\n" +
                "형식:\n" +
                "- 한 줄 뜻: 커피에서 이 노트가 가리키는 향·맛\n" +
                "- 실제 쓰임 2~4개: 인용 + 어떤 원두·가공·로스팅에서 나왔는지\n" +
                "- 비슷한 표현·헷갈리는 표현\n" +
                "- Coffee Taster's Flavor Wheel에서의 위치(찾은 경우에만)"
        NoteMode.DESCRIBE ->
            "맛 묘사로 노트 찾기. 마신 사람의 묘사: \"${q.query}\"\n" +
                "아래 목록 안에서 어울리는 용어 3~5개를 고르고, 각각 그렇게 부르는 근거를 실제 문서의 인용으로 보여줘.\n" +
                "형식:\n" +
                "- 용어: 근거(인용과 출처)\n" +
                "- 후보를 구별하는 방법(출처가 있을 때만)\n" +
                "플레이버 휠 용어: ${wheelTerms.joinToString(", ")}\n" +
                "앱의 한국어 노트 분류: ${noteCategories.joinToString(", ")}"
    }

    /**
     * The one Tavily search for GEMINI_TAVILY. Mode A: the note's English name when its label has one in parentheses
     * ("베르가못 (bergamot)"), else the label, quoted, with words that bring definition pages; mode B: the description.
     */
    fun tavilyQuery(q: NoteQuestion): String = when (q.mode) {
        NoteMode.NOTE -> "\"${searchName(q.query)}\" coffee flavor note meaning tasting notes"
        NoteMode.DESCRIBE -> "coffee tasting notes ${q.query.trim()}"
    }

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
