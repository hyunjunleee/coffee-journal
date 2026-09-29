package com.coffeejournal.ui.ai

/** A step or point of a key how-to (ui/guide/KeyHowTos), with the pages it names as links. */
data class GuideStep(val text: String, val links: List<Pair<String, String>> = emptyList())

/** The screen copy of the AI helper (also read by the tests). */
object AiTexts {
    const val SECTION = "AI 노트 도우미"
    const val INTRO = "노트의 뜻이나 맛 묘사에 맞는 노트를 웹에서 찾은 출처와 함께 알려줘요. 앱에는 키가 들어 있지 않아서, 쓰려면 아래에서 방식을 고르고 자기 키를 넣어야 해요."
    const val MODEL = "모델"
    const val SAVE = "저장"
    const val CLEAR = "지우기"
    const val CHECK = "키 확인"
    const val CHECKING = "확인 중…"
    const val STORE_UNSUPPORTED = "이 기기에서는 아직 키를 안전하게 저장할 수 없어서 AI 노트 도우미를 쓸 수 없어요."
    const val KEY_HINT = "키는 이 휴대폰에만 암호화해 저장돼요. 백업에는 들어가지 않아요."
    const val TAVILY_CHECK_HINT = "Tavily 키 확인은 검색 한 번(1크레딧)을 써요."
    const val GEMINI_CHECK_HINT = "Gemini 키 확인은 검색 없이 아주 짧게 물어봐요. 결제가 켜졌는지는 첫 질문에서 알 수 있어요."

    const val TITLE = "AI 노트 도우미"
    /** While asking; the screen adds three dots that come and go, or a still "…" when 화면 전환 is 끔. */
    const val ASKING = "출처를 찾아 답을 쓰고 있어요"
    const val STAGE_QUERY = "검색어 만들기"
    const val STAGE_SEARCH = "검색"
    const val STAGE_ANSWER = "답 쓰기"
    const val STAGE_SEARCH_AND_ANSWER = "검색하고 답 쓰기"

    /** What TalkBack reads after a step's name. */
    fun stageState(status: StageStatus): String = when (status) {
        StageStatus.DONE -> "끝남"
        StageStatus.CURRENT -> "진행 중"
        StageStatus.LATER -> "남음"
        StageStatus.SKIPPED -> "건너뜀, 고친 검색어로 찾아요"
    }
    const val DISCLAIMER = "AI 요약은 틀릴 수 있어요. 출처를 확인해 주세요."
    const val SOURCES = "출처"
    const val SUGGESTIONS = "Google 검색 제안"
    const val CANDIDATES = "노트 후보"
    const val CANDIDATES_HINT = "답에서 찾은 앱의 휠 용어와 노트 분류예요. 눌러서 고른 뒤 노트에 넣을 수 있어요."
    const val NO_CANDIDATES = "답에서 앱의 휠 용어나 노트 분류를 찾지 못했어요."
    const val ADD_TO_NOTES = "노트에 추가"
    const val ASK_AGAIN = "다시 묻기"
    const val INSTITUTION = "기관"
    const val PERSONAL = "개인 의견"
    const val FOUND = "✓ 원문 확인"
    const val NOT_FOUND = "원문에서 찾지 못함"
    const val DECLINED = "보내지 않았어요. 다시 물으면 무엇을 어디로 보내는지 한 번 더 보여드려요."
    const val QUERIES = "검색어"
    const val EDIT_QUERIES = "검색어 고치기"
    const val QUERIES_HINT = "영어 검색어 하나를 고쳐요(영어가 잘 찾아져요). 다시 물으면 설정대로 Tavily에서 다시 찾아요."
    const val SEARCH_DEPTH = "검색"
    const val SEARCH_DEPTH_HINT = "기본은 빠르고 크레딧이 적어요. 정밀은 그 향미를 다루는 페이지를 찾고 인용할 수 있는 긴 글 조각을 함께 가져와요. " +
        "정밀+기본은 같은 검색어를 두 방식으로 찾아 더 여러 사이트를 봐요."
    const val PEOPLE = "사람들 의견"
    const val PEOPLE_ON = "켬"
    const val PEOPLE_OFF = "끔"
    const val PEOPLE_HINT = "네이버 블로그·티스토리·브런치 후기를 한 번 더 찾아 \"사람들의 느낌\"으로 정리하고 개인 의견으로 표시해요."
    const val BLOGS_FAILED = "블로그 후기 검색이 되지 않아 사람들의 느낌 없이 답했어요."

    /** "질문 한 번에 약 3크레딧 · 무료 1,000크레딧이면 한 달 약 330번" for the settings' search choices. */
    fun credits(n: Int): String {
        val m = TavilyPlan.questionsPerMonth(n)
        val times = if (m >= 1000) "${m / 1000},${(m % 1000).toString().padStart(3, '0')}" else "$m"
        return "질문 한 번에 약 ${n}크레딧 · 무료 1,000크레딧이면 한 달 약 ${times}번"
    }
    const val ASK_WITH_QUERIES = "이 검색어로 다시 묻기"
    const val CLOSE = "닫기"

    const val ASK_FROM_NOTE = "✦ 출처로 알아보기"
    const val ASK_FROM_FORM = "✦ AI에게 묻기"
    const val DESCRIBE_TITLE = "맛 묘사로 노트 찾기"
    const val DESCRIBE_PLACEHOLDER = "예: 잘 익은 자두 같은데 끝이 살짝 쌉쌀해요"
    const val DESCRIBE_HINT = "적은 문장만 AI 서비스로 보내요. 기록의 다른 내용은 보내지 않아요."
    const val DESCRIBE_SEND = "묻기"

    const val CONSENT_TITLE = "질문을 보내기 전에"
    const val CONSENT_SEND = "보내기"

    /** "what is sent where" before the first question with [p]. */
    fun consent(p: AiProvider): List<String> = listOf(
        "질문하면 이 휴대폰에서 바로 아래 서비스로 질문 글만 보내요. 기록·원두·장소·사진은 보내지 않아요.",
        "보내는 곳: " + when (p) {
            AiProvider.GEMINI_TAVILY -> "Google Gemini API(질문 → 영어 검색어) → Tavily(검색어; 사람들 의견을 켰다면 노트 이름이나 묘사로 만든 한국어 검색어도) → Google Gemini API(질문과 Tavily가 찾은 글)"
            AiProvider.GEMINI_SEARCH -> "Google Gemini API(Google 검색 포함)"
            AiProvider.OPENAI -> "OpenAI API(웹 검색 포함)"
            AiProvider.CLAUDE -> "Anthropic API(Claude, 웹 검색 포함)"
        },
        when (p) {
            AiProvider.GEMINI_TAVILY ->
                "Gemini 무료 등급에서는 보낸 질문과 받은 답이 Google의 제품 개선에 쓰이고, 사람이 읽어 볼 수 있어요. Tavily 무료 크레딧은 질문 한 번에 설정에 따라 1–4크레딧을 써요."
            AiProvider.GEMINI_SEARCH -> "Google 검색은 월 5,000회 뒤 1,000회당 \$14가 결제 계정에 청구돼요. 유료 등급에서는 질문이 제품 개선에 쓰이지 않아요."
            AiProvider.OPENAI -> "웹 검색 1,000회당 \$10과 토큰 요금이 키의 계정에 청구돼요."
            AiProvider.CLAUDE -> "웹 검색 1,000회당 \$10과 토큰 요금(검색 결과 포함)이 키의 계정에 청구돼요."
        },
        "키는 이 휴대폰에만 암호화해 저장되고, 요청할 때 그 서비스에만 보내요. 한 번 확인하면 이 방식으로는 다시 묻지 않아요.",
    )
}
