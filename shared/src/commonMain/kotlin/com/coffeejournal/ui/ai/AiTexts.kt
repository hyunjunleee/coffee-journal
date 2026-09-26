package com.coffeejournal.ui.ai

/** A numbered step of a key guide, with the pages it names as links. */
data class GuideStep(val text: String, val links: List<Pair<String, String>> = emptyList())

data class KeyGuide(val title: String, val steps: List<GuideStep>, val notes: List<GuideStep> = emptyList())

/** "키 받는 방법": how to get each key, step by step (every page and button name checked 2026-09-26). */
object AiGuides {
    const val AISTUDIO_KEYS = "https://aistudio.google.com/apikey"
    const val AISTUDIO_PROJECTS = "https://aistudio.google.com/projects"
    const val AISTUDIO_RATE_LIMIT = "https://aistudio.google.com/rate-limit"
    const val AISTUDIO_USAGE = "https://aistudio.google.com/usage"
    const val AISTUDIO_BILLING = "https://aistudio.google.com/billing"
    const val AISTUDIO_SPEND = "https://aistudio.google.com/spend"
    const val TAVILY_APP = "https://app.tavily.com"
    const val OPENAI_KEYS = "https://platform.openai.com/api-keys"
    const val OPENAI_BILLING = "https://platform.openai.com/settings/organization/billing"
    const val OPENAI_LIMITS = "https://platform.openai.com/settings/organization/limits"
    const val CLAUDE_CONSOLE = "https://platform.claude.com"
    const val CLAUDE_KEYS = "https://platform.claude.com/settings/keys"
    const val CLAUDE_BILLING = "https://platform.claude.com/settings/billing"
    const val CLAUDE_USAGE = "https://platform.claude.com/usage"
    const val CLAUDE_PRIVACY = "https://platform.claude.com/settings/privacy"

    val GEMINI_FREE = KeyGuide(
        "Gemini API 키 (무료)",
        listOf(
            GuideStep("aistudio.google.com/apikey를 열어요.", listOf("aistudio.google.com/apikey" to AISTUDIO_KEYS)),
            GuideStep("Google 계정으로 로그인해요. 만 18세 이상이어야 하고, 한국에서 쓸 수 있어요."),
            GuideStep(
                "\"Create API key\"를 눌러요. 처음 쓰는 사람에게는 기본 Google Cloud 프로젝트가 저절로 만들어져요. " +
                    "이미 Google Cloud를 쓰고 있다면 먼저 Dashboard → Projects에서 \"Import projects\"로 프로젝트를 가져와야 할 수 있어요.",
            ),
            GuideStep("만들어진 키를 복사해요. 2026년 5월부터 새 키는 \"AQ.\"로 시작하고 처음부터 Gemini API에만 쓰도록 묶여 있어서, 따로 설정할 것은 없어요."),
            GuideStep("아래 \"Gemini API 키\" 칸에 붙여 넣고 저장해요."),
        ),
        listOf(
            GuideStep("무료 등급에서는 보낸 질문과 받은 답이 Google의 제품 개선에 쓰이고, 사람이 읽어 볼 수 있어요."),
            GuideStep("무료 등급은 Google 검색을 쓸 수 없어서, 무료로는 \"Gemini 무료 + Tavily\"로 물어요."),
            GuideStep(
                "등급과 한도: Projects의 Billing Tier 칸이 \"Set up billing\"이면 무료 등급이에요. 한도와 사용량도 AI Studio에서 볼 수 있고, " +
                    "하루 한도는 태평양 시간 자정(한국 시간 오후 4~5시)에 다시 채워져요.",
                listOf("Projects" to AISTUDIO_PROJECTS, "한도" to AISTUDIO_RATE_LIMIT, "사용량" to AISTUDIO_USAGE),
            ),
        ),
    )

    val GEMINI_PAID = KeyGuide(
        "Gemini 결제 켜기 (Google 검색용)",
        listOf(
            GuideStep("위 순서로 Gemini 키를 받아요. 같은 키를 써요."),
            GuideStep("aistudio.google.com/projects에서 키의 프로젝트 줄, Billing Tier 칸의 \"Set up billing\"을 눌러요.", listOf("aistudio.google.com/projects" to AISTUDIO_PROJECTS)),
            GuideStep("나라를 고르고 약관에 동의한 뒤 결제 계정을 추가해요."),
            GuideStep(
                "최소 \$5를 선불로 충전해요: Billing → \"Buy credits\". 크레딧은 12개월 뒤 사라지고, \$0가 되면 요청이 실패해요(HTTP 402).",
                listOf("aistudio.google.com/billing" to AISTUDIO_BILLING),
            ),
            GuideStep("(선택) Spend → \"Monthly spend cap\"에서 월 지출 상한을 정해요(실험 기능).", listOf("aistudio.google.com/spend" to AISTUDIO_SPEND)),
        ),
        listOf(
            GuideStep("Google 검색은 3.x 모델을 합쳐 월 5,000회까지 무료, 그 뒤 1,000회당 \$14예요."),
            GuideStep("유료 등급에서는 Google이 질문을 제품 개선에 쓰지 않아요."),
        ),
    )

    val TAVILY = KeyGuide(
        "Tavily API 키 (무료)",
        listOf(
            GuideStep("app.tavily.com에서 가입해요. 카드는 필요 없어요.", listOf("app.tavily.com" to TAVILY_APP)),
            GuideStep("대시보드에 있는 API 키(\"tvly-…\")를 복사해요."),
            GuideStep("아래 \"Tavily API 키\" 칸에 붙여 넣고 저장해요."),
        ),
        listOf(
            GuideStep("무료 요금제는 매달 1,000크레딧이에요. 여기서는 질문 한 번에 2크레딧(advanced 검색)을 써요."),
            GuideStep("질문으로 만든 검색어가 Tavily로 가요."),
        ),
    )

    val OPENAI = KeyGuide(
        "OpenAI API 키 (유료)",
        listOf(
            GuideStep("platform.openai.com/api-keys를 열어요.", listOf("platform.openai.com/api-keys" to OPENAI_KEYS)),
            GuideStep("\"+ Create new secret key\"를 누르고 프로젝트를 골라요. \"sk-proj-\"로 시작하는 키는 한 번만 보이니 바로 복사해요."),
            GuideStep("결제 페이지에서 크레딧을 충전해요(최소 \$5).", listOf("Billing" to OPENAI_BILLING)),
            GuideStep("Limits → Spend → \"Edit spend limit\"에서 \"Enforce a hard limit\"를 켜 월 상한을 정해요.", listOf("Limits" to OPENAI_LIMITS)),
            GuideStep("아래 \"OpenAI API 키\" 칸에 붙여 넣고 저장해요."),
        ),
        listOf(GuideStep("웹 검색 1,000회당 \$10에 토큰 요금이 더해져요.")),
    )

    val CLAUDE = KeyGuide(
        "Anthropic API 키 (유료)",
        listOf(
            GuideStep("Claude 콘솔 platform.claude.com에 로그인해요(console.anthropic.com도 여기로 와요). 한국에서 쓸 수 있어요.", listOf("platform.claude.com" to CLAUDE_CONSOLE)),
            GuideStep("Settings → Keys에서 \"Create key\"를 눌러 이름, 만료, 연결할 계정·워크스페이스를 정해요.", listOf("Keys" to CLAUDE_KEYS)),
            GuideStep("\"sk-ant-\"로 시작하는 키는 한 번만 보이니 바로 복사해요."),
            GuideStep(
                "Billing → \"Buy credits\"로 크레딧을 사요(Admin 또는 Billing 역할, 처음에는 조직과 쓰임새를 물어요). " +
                    "크레딧이 없으면 API가 동작하지 않고, 산 크레딧은 1년 뒤 사라져요.",
                listOf("Billing" to CLAUDE_BILLING),
            ),
            GuideStep("(선택) 같은 페이지의 \"Spend limits\" → \"Adjust limit\"에서 지출 한도를 정해요. 사용량은 Usage에서 봐요.", listOf("Usage" to CLAUDE_USAGE)),
            GuideStep("아래 \"Anthropic API 키\" 칸에 붙여 넣고 저장해요."),
        ),
        listOf(
            GuideStep("웹 검색은 기본으로 켜져 있어요. 조직 관리자가 Privacy 설정에서 껐다면 다시 켜야 해요.", listOf("Privacy" to CLAUDE_PRIVACY)),
            GuideStep("웹 검색 1,000회당 \$10에 토큰 요금이 더해져요(검색 결과도 입력 토큰으로 세요)."),
        ),
    )

    fun forProvider(p: AiProvider): List<KeyGuide> = when (p) {
        AiProvider.GEMINI_TAVILY -> listOf(GEMINI_FREE, TAVILY)
        AiProvider.GEMINI_SEARCH -> listOf(GEMINI_FREE, GEMINI_PAID)
        AiProvider.OPENAI -> listOf(OPENAI)
        AiProvider.CLAUDE -> listOf(CLAUDE)
    }
}

/** The screen copy of the AI helper (also read by the tests). */
object AiTexts {
    const val SECTION = "AI 노트 도우미"
    const val INTRO = "노트의 뜻이나 맛 묘사에 맞는 노트를 웹에서 찾은 출처와 함께 알려줘요. 앱에는 키가 들어 있지 않아서, 쓰려면 아래에서 방식을 고르고 자기 키를 넣어야 해요."
    const val GUIDE_OPEN = "키 받는 방법 ▾"
    const val GUIDE_CLOSE = "키 받는 방법 ▴"
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
    const val ASKING = "출처를 찾아 답을 쓰고 있어요…"
    const val DISCLAIMER = "AI 요약은 틀릴 수 있어요. 출처를 확인해 주세요."
    const val SOURCES = "출처"
    const val SUGGESTIONS = "Google 검색 제안"
    const val CANDIDATES = "노트 후보"
    const val CANDIDATES_HINT = "답에서 찾은 앱의 휠 용어와 노트 분류예요. 눌러서 고른 뒤 노트에 넣을 수 있어요."
    const val NO_CANDIDATES = "답에서 앱의 휠 용어나 노트 분류를 찾지 못했어요."
    const val ADD_TO_NOTES = "노트에 추가"
    const val ASK_AGAIN = "다시 묻기"
    const val INSTITUTION = "기관"
    const val PERSONAL = "개인 글"
    const val FOUND = "✓ 원문 확인"
    const val NOT_FOUND = "원문에서 찾지 못함"
    const val DECLINED = "보내지 않았어요. 다시 물으면 무엇을 어디로 보내는지 한 번 더 보여드려요."

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
            AiProvider.GEMINI_TAVILY -> "Tavily(질문으로 만든 검색어) → Google Gemini API(질문과 Tavily가 찾은 글)"
            AiProvider.GEMINI_SEARCH -> "Google Gemini API(Google 검색 포함)"
            AiProvider.OPENAI -> "OpenAI API(웹 검색 포함)"
            AiProvider.CLAUDE -> "Anthropic API(Claude, 웹 검색 포함)"
        },
        when (p) {
            AiProvider.GEMINI_TAVILY ->
                "Gemini 무료 등급에서는 보낸 질문과 받은 답이 Google의 제품 개선에 쓰이고, 사람이 읽어 볼 수 있어요. Tavily 무료 크레딧은 질문 한 번에 2크레딧을 써요."
            AiProvider.GEMINI_SEARCH -> "Google 검색은 월 5,000회 뒤 1,000회당 \$14가 결제 계정에 청구돼요. 유료 등급에서는 질문이 제품 개선에 쓰이지 않아요."
            AiProvider.OPENAI -> "웹 검색 1,000회당 \$10과 토큰 요금이 키의 계정에 청구돼요."
            AiProvider.CLAUDE -> "웹 검색 1,000회당 \$10과 토큰 요금(검색 결과 포함)이 키의 계정에 청구돼요."
        },
        "키는 이 휴대폰에만 암호화해 저장되고, 요청할 때 그 서비스에만 보내요. 한 번 확인하면 이 방식으로는 다시 묻지 않아요.",
    )
}
