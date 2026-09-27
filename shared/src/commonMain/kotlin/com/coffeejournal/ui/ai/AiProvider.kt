package com.coffeejournal.ui.ai

/**
 * A key the user pastes in 설정 › AI 노트 도우미. The app never ships a key: every user brings their own. The Gemini key
 * serves both Gemini options.
 */
enum class AiKeySlot(val label: String, val service: String, val placeholder: String) {
    GEMINI("Gemini API 키", "Google Gemini", "AQ.… 붙여넣기"),
    TAVILY("Tavily API 키", "Tavily", "tvly-… 붙여넣기"),
    OPENAI("OpenAI API 키", "OpenAI", "sk-proj-… 붙여넣기"),
    CLAUDE("Anthropic API 키", "Anthropic", "sk-ant-… 붙여넣기"),
}

/**
 * How the helper finds web sources and writes the answer (docs/ai-note-helper-plan.md, 구현). Every option answers only
 * from real pages and shows them as numbered sources.
 */
enum class AiProvider(
    val label: String,
    val summary: String,
    val keys: List<AiKeySlot>,
    val defaultModel: String,
    val presets: List<String>,
) {
    /**
     * Gemini writes English queries, Tavily searches, Gemini (no tools) answers from the numbered pages; the app checks
     * every quote against them.
     */
    GEMINI_TAVILY(
        "Gemini 무료 + Tavily",
        "무료로 쓸 수 있어요. Gemini 키와 Tavily 키가 둘 다 필요해요. Gemini가 질문을 영어 검색어로 바꾸면 Tavily가 웹에서 찾고, " +
            "찾은 글만 Gemini에 넘겨 답하게 해요. 답에 인용된 표현이 그 글에 정말 있는지 앱이 확인해요. " +
            "질문 한 번에 Tavily 2크레딧(정밀) 또는 1크레딧(기본)을 써요(무료 월 1,000크레딧).",
        listOf(AiKeySlot.GEMINI, AiKeySlot.TAVILY),
        "gemini-3.5-flash-lite",
        listOf("gemini-3.5-flash-lite", "gemini-3.5-flash", "gemini-3.1-flash-lite"),
    ),

    /** Gemini with its built-in Google Search grounding: needs billing on the key's project. */
    GEMINI_SEARCH(
        "Gemini + Google 검색",
        "결제를 켠 Google 프로젝트에서만 돼요(무료 등급은 Google 검색을 쓸 수 없어요). Gemini 키 하나로 Google 검색 결과에 기대어 답해요. " +
            "검색은 월 5,000회까지 무료, 그 뒤 1,000회당 \$14예요.",
        listOf(AiKeySlot.GEMINI),
        "gemini-3.5-flash",
        listOf("gemini-3.5-flash", "gemini-3.8-flash", "gemini-3.5-flash-lite"),
    ),

    /** OpenAI Responses API with the web_search tool. */
    OPENAI(
        "GPT (OpenAI)",
        "유료예요. OpenAI 키 하나로 GPT가 웹을 검색해 답해요. 검색 1,000회당 \$10에 토큰 요금이 더해져요" +
            "(gpt-5-nano는 100만 토큰당 입력 \$0.05 · 출력 \$0.40).",
        listOf(AiKeySlot.OPENAI),
        "gpt-5-nano",
        listOf("gpt-5-nano", "gpt-5.5"),
    ),

    /** Anthropic Messages API with the server-side web_search tool; citations carry the cited text. */
    CLAUDE(
        "Claude (Anthropic)",
        "유료예요. Anthropic 키 하나로 Claude가 웹을 검색해 답해요. 검색 1,000회당 \$10에 토큰 요금이 더해져요(검색 결과도 입력 토큰). " +
            "100만 토큰당 Opus 5 \$5 · \$25, Sonnet 5 \$2 · \$10, Haiku 4.5 \$1 · \$5(입력 · 출력).",
        listOf(AiKeySlot.CLAUDE),
        "claude-opus-5",
        listOf("claude-opus-5", "claude-sonnet-5", "claude-haiku-4-5"),
    ),
    ;

    /** The service names in the order the question travels ("Tavily → Google Gemini"). */
    val route: String
        get() = when (this) {
            GEMINI_TAVILY -> "Gemini 검색어 → Tavily 검색 → Google Gemini"
            GEMINI_SEARCH -> "Google Gemini (Google 검색)"
            OPENAI -> "OpenAI (웹 검색)"
            CLAUDE -> "Anthropic Claude (웹 검색)"
        }

    companion object {
        val DEFAULT = GEMINI_TAVILY

        fun of(name: String?): AiProvider? = entries.firstOrNull { it.name == name }
    }
}

/**
 * 설정 › AI 노트 도우미 › 검색 (Gemini 무료 + Tavily only): how deep Tavily searches the one query. The list is open
 * for a third option.
 */
enum class SearchDepth(val label: String, val tavily: String, val credits: Int) {
    /** advanced: pages about that very flavor, with longer passages around the term to quote. The default. */
    PRECISE("정밀", "advanced", 2),

    /** basic: faster, half the credits. */
    BASIC("기본", "basic", 1),
    ;

    companion object {
        val DEFAULT = PRECISE

        /** A saved value; anything unknown (or from a later version) reads as the default. */
        fun of(name: String?): SearchDepth = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** The helper's two questions (Route.NoteHelper.mode). */
enum class NoteMode(val key: String, val label: String) {
    /** A: what a flavor note means in coffee, with sources. */
    NOTE("note", "노트 설명"),

    /** B: which of the app's wheel terms and note categories fit a described taste. */
    DESCRIBE("describe", "맛 묘사 → 노트 찾기"),
    ;

    companion object {
        fun of(key: String): NoteMode = entries.firstOrNull { it.key == key } ?: NOTE
    }
}

data class NoteQuestion(val mode: NoteMode, val query: String)
