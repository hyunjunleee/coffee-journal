package com.coffeejournal.ui.guide

import com.coffeejournal.ui.ai.GuideStep

/**
 * A long, step-by-step how-to for one key, opened as a full-screen sheet from 설정 (KeyGuideSheet): [intro] says what
 * the key is for and what it costs, [sections] go from getting the key to troubleshooting. [asOf] is when every page
 * and button name was last checked against the service's own site.
 */
data class KeyHowTo(
    val id: String,
    val title: String,
    val intro: String,
    val asOf: String,
    val sections: List<HowToSection>,
)

/** One part of a [KeyHowTo]: [numbered] steps to follow in order, else points to read. */
data class HowToSection(val heading: String, val items: List<GuideStep>, val numbered: Boolean = false)

/** The how-tos (every page and button name checked 2026-09-29). */
object KeyHowTos {
    private const val AS_OF = "2026년 9월 기준"

    // Google Gemini (AI Studio and ai.google.dev)
    private const val AISTUDIO_KEYS = "https://aistudio.google.com/apikey"
    private const val AISTUDIO_PROJECTS = "https://aistudio.google.com/projects"
    private const val AISTUDIO_RATE_LIMIT = "https://aistudio.google.com/rate-limit"
    private const val AISTUDIO_USAGE = "https://aistudio.google.com/usage"
    private const val AISTUDIO_BILLING = "https://aistudio.google.com/billing"
    private const val AISTUDIO_SPEND = "https://aistudio.google.com/spend"
    private const val GEMINI_PRICING = "https://ai.google.dev/gemini-api/docs/pricing"
    private const val GEMINI_TERMS = "https://ai.google.dev/gemini-api/terms"

    // Tavily
    private const val TAVILY_APP = "https://app.tavily.com"
    private const val TAVILY_CREDITS = "https://docs.tavily.com/documentation/api-credits"

    // OpenAI
    private const val OPENAI_KEYS = "https://platform.openai.com/api-keys"
    private const val OPENAI_BILLING = "https://platform.openai.com/settings/organization/billing"
    private const val OPENAI_LIMITS = "https://platform.openai.com/settings/organization/limits"
    private const val OPENAI_USAGE = "https://platform.openai.com/usage"
    private const val OPENAI_PRICING = "https://developers.openai.com/api/docs/pricing"
    private const val OPENAI_DATA = "https://developers.openai.com/api/docs/guides/your-data"

    // Anthropic (Claude Console and its docs)
    private const val CLAUDE_CONSOLE = "https://platform.claude.com"
    private const val CLAUDE_KEYS = "https://platform.claude.com/settings/keys"
    private const val CLAUDE_BILLING = "https://platform.claude.com/settings/billing"
    private const val CLAUDE_USAGE = "https://platform.claude.com/usage"
    private const val CLAUDE_PRIVACY = "https://platform.claude.com/settings/privacy"
    private const val CLAUDE_WEB_SEARCH = "https://platform.claude.com/docs/en/agents-and-tools/tool-use/web-search-tool"
    private const val CLAUDE_DATA = "https://platform.claude.com/docs/en/build-with-claude/api-and-data-retention"

    // Kakao Developers
    private const val KAKAO_CONSOLE = "https://developers.kakao.com/console/app"
    private const val KAKAO_MAP_POLICY = "https://developers.kakao.com/docs/ko/kakaomap/common"
    private const val KAKAO_QUOTA = "https://developers.kakao.com/docs/ko/getting-started/quota"

    private fun p(text: String, vararg links: Pair<String, String>) = GuideStep(text, links.toList())
    private fun points(heading: String, vararg items: GuideStep) = HowToSection(heading, items.toList())
    private fun steps(heading: String, vararg items: GuideStep) = HowToSection(heading, items.toList(), numbered = true)

    val GEMINI: KeyHowTo = KeyHowTo(
        id = "gemini",
        title = "Gemini API 키 받는 법",
        intro = "Gemini API 키는 AI 노트 도우미가 Google Gemini에 질문을 보낼 때 써요. 무료 등급으로 받으면 돈이 들지 않고, " +
            "Google 검색까지 쓰려면 키의 프로젝트에 결제를 켜야 해요.",
        asOf = AS_OF,
        sections = listOf(
            points(
                "이 키로 하는 일",
                p("설정 › AI 노트 도우미에서 방식을 \"Gemini 무료 + Tavily\"나 \"Gemini + Google 검색\"으로 고르면 이 키를 써요. 두 방식이 같은 키 칸을 함께 써요."),
                p(
                    "\"Gemini 무료 + Tavily\": Gemini가 질문을 영어 검색어로 바꾸고, Tavily가 찾은 글만 보고 답을 써요. " +
                        "무료 등급 키면 Gemini 쪽은 돈이 들지 않아요(Tavily 키도 필요해요).",
                ),
                p(
                    "\"Gemini + Google 검색\": Gemini가 Google 검색 결과에 기대어 답해요. 결제를 켠 프로젝트의 키에서만 되고, " +
                        "검색은 월 5,000회 뒤부터 돈이 들어요.",
                ),
            ),
            points(
                "준비물",
                p("Google 계정 하나. Gemini API 약관상 만 18세 이상이어야 해요. 한국은 무료 등급도 쓸 수 있는 지역이에요.", "약관" to GEMINI_TERMS),
                p(
                    "무료로만 쓸 때는 카드가 필요 없어요. \"Gemini + Google 검색\"을 쓰려면 결제 수단과 " +
                        "최소 \$5(또는 그만큼의 다른 통화) 선불 충전이 필요해요.",
                ),
                p("PC나 휴대폰 브라우저 어느 쪽이든 돼요. 휴대폰에서 받으면 복사한 키를 바로 이 앱에 붙여 넣기 쉬워요."),
            ),
            steps(
                "키 받기",
                p("브라우저에서 aistudio.google.com/apikey를 열고 Google 계정으로 로그인해요.", "aistudio.google.com/apikey" to AISTUDIO_KEYS),
                p("처음 들어가면 약관(Terms of Service)에 동의하라고 나와요. 동의하면 기본 Google Cloud 프로젝트와 API 키가 저절로 만들어져요."),
                p(
                    "API Keys 표에 키가 이미 있으면 그 키를 써도 돼요. 새로 만들려면 \"Create API key\"를 누르고, " +
                        "창에서 키를 둘 프로젝트를 골라 만들어요(화면에 따라 이름이 조금 다를 수 있어요).",
                ),
                p(
                    "Google Cloud를 이미 쓰던 계정이면 기본 프로젝트가 만들어지지 않아요. 왼쪽 Dashboard › Projects에서 " +
                        "\"Import projects\"를 눌러 쓸 프로젝트를 가져온 뒤 API Keys에서 키를 만들어요.",
                ),
                p(
                    "\"Create API key\"가 눌리지 않고 \"You do not have permission to create a key in this project\"가 보이면, " +
                        "회사·학교 조직의 프로젝트라 권한이 없는 거예요. 조직에 속하지 않은 새 프로젝트를 만들어 키를 받아요.",
                ),
                p(
                    "2026년 5월 28일부터 새 키는 인증 키(auth key)로 만들어지고 \"AQ.\"로 시작해요. " +
                        "처음부터 Gemini API에만 쓰이도록 묶여 있어 따로 제한을 걸 것은 없어요.",
                ),
                p("표에서 키를 열어 복사 버튼을 눌러요. 휴대폰이면 복사한 뒤 바로 이 앱으로 돌아와 붙여 넣어요. 메모 앱이나 메신저에 붙여 두지 마세요."),
                p("예전에 만든 \"AIza…\" 키는 표준 키예요. \"Unrestricted\" 표시가 있으면 Gemini API가 받지 않으니 새 키를 만드는 게 가장 쉬워요."),
                p(
                    "여기부터는 \"Gemini + Google 검색\"을 쓸 때만 해요. aistudio.google.com/projects에서 키의 프로젝트 줄, " +
                        "Billing Tier 칸의 \"Set up billing\"을 눌러요.",
                    "aistudio.google.com/projects" to AISTUDIO_PROJECTS,
                ),
                p(
                    "Google 결제 계정이 처음이면 나라를 고르고 약관에 동의한 뒤 연락처와 결제 수단을 넣어요. " +
                        "이미 있으면 기존 결제 계정을 고르거나 \"Add new billing account\"를 눌러요.",
                ),
                p(
                    "선불(Prepay)로 최소 \$5를 충전하라고 나오면 충전해요. 더 넣을 때는 Billing 페이지에서 \"Buy credits\"를 눌러요" +
                        "(한 번에 \$5~\$5,000).",
                    "Billing" to AISTUDIO_BILLING,
                ),
                p(
                    "Projects의 Billing Tier 칸에 등급(Tier 1 등)이 보이면 끝이에요. \"Set up Prepay\"나 \"No credits\"가 보이면 " +
                        "눌러서 선불 설정이나 충전을 마쳐요.",
                    "Projects" to AISTUDIO_PROJECTS,
                ),
            ),
            steps(
                "앱에 넣기",
                p("이 앱의 설정 › AI 노트 도우미로 가서, 방식에서 \"Gemini 무료 + Tavily\"나 \"Gemini + Google 검색\"을 골라요."),
                p("\"Gemini API 키\" 칸(\"AQ.… 붙여넣기\")을 길게 눌러 붙여 넣고 저장을 눌러요. 앞뒤 빈칸과 줄바꿈은 앱이 지워요."),
                p("칸이 \"저장됨 …\"과 키의 끝 네 글자로 바뀌어요. 저장한 키 전체는 다시 보여 주지 않아요."),
                p("키 확인을 눌러요. 설정의 모델로 검색 없이 아주 짧게 물어보고, \"정상\"이 나오면 키가 맞아요."),
                p("키 확인은 결제가 켜졌는지 알려 주지 않아요. \"Gemini + Google 검색\"은 첫 질문에서 결제 여부를 알 수 있어요."),
                p(
                    "\"Gemini 무료 + Tavily\"는 Tavily API 키도 넣어야 물을 수 있어요. 처음 물을 때 \"질문을 보내기 전에\" 창에서 " +
                        "보내는 곳을 읽고 \"보내기\"를 눌러요.",
                ),
            ),
            points(
                "요금과 한도",
                p(
                    "무료 등급은 토큰 요금이 없어요. 대신 모델마다 분당 요청·분당 토큰·하루 요청 수 한도가 있고, 숫자는 AI Studio의 " +
                        "Rate limit 페이지에서 봐요. 한도는 키가 아니라 프로젝트마다 세서, 키를 더 만들어도 늘지 않아요.",
                    "Rate limit" to AISTUDIO_RATE_LIMIT,
                ),
                p("하루 한도는 태평양 시간 자정에 다시 채워져요. 한국 시간으로 오후 4시(11월 초~3월 초에는 오후 5시)예요."),
                p("\"Gemini 무료 + Tavily\"는 질문 한 번에 Gemini를 두 번 불러요(검색어 만들기, 답 쓰기). 키 확인은 아주 짧은 요청 한 번이에요."),
                p(
                    "무료 등급에서는 Google 검색을 쓸 수 없어요. 결제를 켜면 Gemini 3.x 모델을 합쳐 월 5,000회까지 무료, " +
                        "그 뒤 1,000회당 \$14예요. 질문 한 번이 검색 여러 번이 될 수 있고 검색마다 세요(가격표).",
                    "가격표" to GEMINI_PRICING,
                ),
                p(
                    "결제를 켠 프로젝트는 토큰에도 요금이 붙어요. 100만 토큰당 gemini-3.5-flash는 입력 \$1.50 · 출력 \$9.00, " +
                        "gemini-3.5-flash-lite는 입력 \$0.30 · 출력 \$2.50이에요.",
                ),
                p(
                    "키 칸은 두 방식이 함께 써서, 결제를 켠 프로젝트의 키를 넣으면 \"Gemini 무료 + Tavily\"로 물어도 토큰 요금이 나가요. " +
                        "무료로만 쓰려면 결제하지 않은 프로젝트의 키를 넣어요.",
                ),
                p(
                    "선불 크레딧은 12개월 뒤 사라지고 환불되지 않아요. 잔액이 \$0이 되면 그 결제 계정에 묶인 모든 프로젝트의 키가 멈춰요" +
                        "(HTTP 402). Google Cloud 환영 크레딧(\$300)은 2026년 3월 2일 뒤에 연 계정이면 Gemini API에 쓸 수 없어요.",
                ),
                p(
                    "쓴 양은 Usage, 지출은 Spend에서 봐요. Spend의 Monthly spend cap › \"Edit spend cap\"에서 프로젝트의 월 상한을 정해요" +
                        "(실험 기능이고 10분쯤 늦게 반영돼 조금 넘을 수 있어요).",
                    "Usage" to AISTUDIO_USAGE,
                    "Spend" to AISTUDIO_SPEND,
                ),
                p("자동 충전(auto-reload)은 켜지 않아도 돼요. 켠다면 Billing의 \"Manage auto-reload\"에서 Monthly Limit으로 한 달 합계를 묶어요."),
            ),
            points(
                "문제가 생기면",
                p("‘Gemini API 키가 아직 없어요’: 키를 저장하지 않았어요. 설정 › AI 노트 도우미에서 붙여 넣고 저장해요."),
                p(
                    "‘Gemini 키가 맞지 않아요’(키 확인에서는 ‘키가 틀려요’): Google이 키를 알아보지 못했어요(HTTP 400). " +
                        "키가 잘렸거나 지운 키예요. 지우기를 누르고 다시 복사해 넣어요.",
                ),
                p(
                    "‘이 Gemini 키로는 쓸 수 없어요(권한 없음)’: HTTP 401·403이에요. 제한 없는 예전 \"AIza…\" 키이거나, " +
                        "새어 나간 것으로 감지돼 막힌 키(\"Blocked\" 표시)일 수 있어요. 새 키를 만들어 넣어요.",
                ),
                p(
                    "‘… 모델은 쓸 수 없어요’(‘이 모델은 쓸 수 없어요’): 모델 이름이 틀렸거나 새 키에 더는 열어 주지 않는 모델이에요" +
                        "(HTTP 404). 설정의 모델에서 gemini-3.5-flash-lite 같은 다른 모델을 골라요.",
                ),
                p(
                    "‘Gemini 사용 한도를 넘었어요’(‘한도를 넘었어요’): 분당이나 하루 한도에 닿았어요(HTTP 429). " +
                        "1분쯤 뒤에 다시 하거나, 하루 한도면 오후 4~5시 뒤에 해요. 다른 모델을 골라도 돼요.",
                ),
                p(
                    "‘Google 검색은 결제를 켠 프로젝트에서만 돼요…’: 무료 등급 키로 \"Gemini + Google 검색\"을 썼어요(HTTP 429). " +
                        "결제를 켜거나 \"Gemini 무료 + Tavily\"를 골라요. 결제를 이미 켰다면 한도라서 잠시 뒤 다시 해요.",
                ),
                p(
                    "‘Gemini 선불 크레딧이 떨어졌어요’(‘결제가 필요해요’): 잔액이 \$0이에요(HTTP 402). Projects에 \"No credits\"가 보여요. " +
                        "Billing에서 \"Buy credits\"로 충전해요.",
                ),
                p("‘지금 있는 지역에서는 이 Gemini 등급을 쓸 수 없어요.’: 지원하지 않는 곳으로 접속했어요(HTTP 400). VPN을 쓰고 있다면 끄고 다시 해요."),
                p(
                    "‘Gemini 서버가 붐벼요’: Google 쪽 문제예요(HTTP 500대). 몇 분 뒤 다시 해요. " +
                        "‘연결 실패: Gemini에 연결하지 못했어요’는 휴대폰의 인터넷 연결을 확인해요.",
                ),
                p("‘안전 필터가 답을 막았어요’, ‘출처를 찾지 못했어요’: 키 문제가 아니에요. 표현을 바꿔 다시 물어요."),
            ),
            points(
                "보안과 개인정보",
                p("키는 이 휴대폰에만 암호화해 저장돼요. 백업에는 들어가지 않아서, 휴대폰을 바꾸면 키를 다시 넣어야 해요."),
                p(
                    "질문하면 이 휴대폰에서 바로 Google로 키와 질문 글(노트 이름이나 적은 맛 묘사)을 보내요. " +
                        "\"Gemini 무료 + Tavily\"는 Tavily가 찾은 글 조각도 보내요. 기록·원두·장소·사진은 보내지 않아요.",
                ),
                p(
                    "무료 등급: Google이 보낸 내용과 답을 제품 개선에 쓰고, 계정·키와 떼어 낸 뒤 사람이 읽어 볼 수 있어요. " +
                        "결제를 켠 프로젝트는 제품 개선에 쓰지 않고, 금지된 사용을 막으려고 한동안만 기록해요(약관).",
                    "약관" to GEMINI_TERMS,
                ),
                p(
                    "키가 새었다고 생각되면 새 키를 만들어 앱에 넣고, 예전 키는 API Keys 페이지나 Google Cloud Console의 " +
                        "사용자 인증 정보(Credentials)에서 사용 중지하거나 지워요.",
                    "API Keys" to AISTUDIO_KEYS,
                ),
                p("앱에서 지우려면 \"Gemini API 키\" 옆 지우기를 누르고 \"키 지우기\" 창에서 지우기를 눌러요. 이 휴대폰에서만 지워지고 Google의 키는 그대로예요."),
            ),
        ),
    )

    val TAVILY: KeyHowTo = KeyHowTo(
        id = "tavily",
        title = "Tavily API 키 받는 법",
        intro = "Tavily API 키는 \"Gemini 무료 + Tavily\" 방식에서 웹 검색을 맡아요. 카드 없이 매달 1,000크레딧을 무료로 써요.",
        asOf = AS_OF,
        sections = listOf(
            points(
                "이 키로 하는 일",
                p(
                    "설정 › AI 노트 도우미의 \"Gemini 무료 + Tavily\"에서 써요. Gemini가 만든 영어 검색어로 Tavily가 웹을 찾고, " +
                        "찾은 글만 Gemini에 넘겨 답하게 해요.",
                ),
                p("사람들 의견을 켜면 네이버 블로그·티스토리·브런치 같은 한국 블로그 후기를 한국어로 한 번 더 찾아요."),
                p("무료예요. 매달 1,000크레딧이 채워지고, 질문 한 번에 설정에 따라 1–4크레딧을 써요."),
            ),
            points(
                "준비물",
                p("가입할 이메일 주소나 계정 하나. 무료 요금제는 카드가 필요 없어요."),
                p("Gemini API 키도 함께 있어야 물을 수 있어요(Gemini 안내 참고)."),
                p("PC나 휴대폰 브라우저 어느 쪽이든 돼요."),
            ),
            steps(
                "키 받기",
                p("브라우저에서 app.tavily.com을 열어요.", "app.tavily.com" to TAVILY_APP),
                p("화면에 나오는 방법 중 하나로 가입하거나, 계정이 있으면 로그인해요."),
                p("가입하면 무료 Researcher 요금제(월 1,000크레딧)로 시작해요. 카드는 묻지 않아요."),
                p("첫 화면(Home)의 API Keys에 키가 이미 하나 있어요. \"tvly-\"로 시작해요."),
                p("키 옆 복사 버튼으로 복사해요. 대시보드에서 언제든 다시 복사할 수 있어요. 휴대폰이면 복사한 뒤 바로 이 앱에 붙여 넣어요."),
                p("(선택) 이 앱 전용 키를 따로 만들려면 API Keys 옆 \"+\"를 눌러요. Key Name을 적고 Key Type은 Development를 골라요."),
                p("같은 창의 Monthly Limit에 한 달에 쓸 크레딧 한도를 정할 수 있어요. 비워 둬도 무료 요금제는 1,000크레딧에서 멈춰요."),
                p("Production 키는 유료 요금제나 종량제를 켠 계정만 만들 수 있어요. 이 앱에는 Development 키(분당 100번)로 충분해요."),
            ),
            steps(
                "앱에 넣기",
                p("이 앱의 설정 › AI 노트 도우미에서 방식 \"Gemini 무료 + Tavily\"를 골라요."),
                p("\"Tavily API 키\" 칸(\"tvly-… 붙여넣기\")을 길게 눌러 붙여 넣고 저장을 눌러요. 앞뒤 빈칸과 줄바꿈은 앱이 지워요."),
                p("칸이 \"저장됨 …\"과 키의 끝 네 글자로 바뀌어요."),
                p("키 확인을 눌러요. 검색 한 번(1크레딧)을 해 보고 \"정상\"이 나오면 끝이에요."),
                p(
                    "아래 검색(기본·정밀·정밀+기본)과 사람들 의견(켬·끔)이 질문 한 번에 쓰는 크레딧을 정해요. " +
                        "그 아래 \"질문 한 번에 약 N크레딧 · 무료 1,000크레딧이면 한 달 약 M번\"으로 보여요.",
                ),
            ),
            points(
                "요금과 한도",
                p(
                    "무료(Researcher) 요금제는 매달 1,000크레딧이에요. 결제일과 상관없이 매달 1일에 다시 채워져요(크레딧 안내).",
                    "크레딧 안내" to TAVILY_CREDITS,
                ),
                p("Tavily 검색은 basic이 1크레딧, advanced가 2크레딧이에요. 앱의 기본은 basic, 정밀은 advanced, 정밀+기본은 둘 다예요."),
                p(
                    "질문 한 번: 기본 1, 정밀 2, 정밀+기본 3크레딧에 사람들 의견을 켜면 1크레딧이 더해져요. " +
                        "처음 설정(정밀 + 사람들 의견)은 3크레딧으로 한 달 약 330번, 기본 + 끔이면 약 1,000번이에요.",
                ),
                p("키 확인은 1크레딧을 써요."),
                p(
                    "무료 요금제는 크레딧을 다 쓰면 다음 달 1일까지 멈출 뿐 돈이 나가지 않아요. " +
                        "종량제(Pay as you go, 크레딧당 \$0.008)를 켰을 때만 넘는 만큼 청구돼요.",
                ),
                p("종량제를 켠다면 대시보드에서 사용 한도를 꼭 정해요. 한도에 닿으면 요청이 멈춰요."),
                p("쓴 크레딧은 app.tavily.com 대시보드에서 봐요(화면에 따라 이름이 조금 다를 수 있어요)."),
            ),
            points(
                "문제가 생기면",
                p(
                    "‘Tavily 키가 맞지 않아요’(키 확인에서는 ‘키가 틀려요’): HTTP 401·403이에요. 키가 잘렸거나 지운 키예요. " +
                        "지우기를 누르고 대시보드에서 다시 복사해 넣어요.",
                ),
                p(
                    "‘Tavily 이번 달 크레딧을 다 썼어요.’(‘한도를 넘었어요’): HTTP 432예요. 다음 달 1일까지 기다리거나, " +
                        "검색을 기본으로, 사람들 의견을 끔으로 줄여요. 키에 Monthly Limit을 정했다면 그 한도일 수도 있어요.",
                ),
                p("‘Tavily 종량제 사용 한도를 넘었어요.’: HTTP 433이에요. 대시보드에서 한도를 올리거나 다음 달까지 기다려요."),
                p("‘Tavily 요청이 너무 잦아요.’: 분당 한도에 닿았어요(HTTP 429). 1분쯤 뒤에 다시 해요."),
                p("‘Tavily 서버가 붐벼요’: Tavily 쪽 문제예요(HTTP 500대). 몇 분 뒤 다시 해요. ‘연결 실패: Tavily에…’는 인터넷 연결을 확인해요."),
                p("‘블로그 후기 검색이 되지 않아 사람들의 느낌 없이 답했어요.’: 블로그 검색만 실패하고 답은 나왔어요. 자주 나오면 남은 크레딧을 확인해요."),
                p(
                    "‘출처를 찾지 못했어요’: Tavily가 쓸 만한 글을 찾지 못했어요. 답 화면의 \"검색어 고치기\"로 영어 검색어를 고쳐 " +
                        "\"이 검색어로 다시 묻기\"를 눌러 봐요.",
                ),
            ),
            points(
                "보안과 개인정보",
                p("키는 이 휴대폰에만 암호화해 저장돼요. 백업에는 들어가지 않아서, 휴대폰을 바꾸면 키를 다시 넣어야 해요."),
                p(
                    "Tavily로는 Gemini가 질문으로 만든 영어 검색어와, 사람들 의견을 켰다면 노트 이름이나 묘사로 만든 " +
                        "한국어 검색어만 가요. 기록·원두·장소·사진은 보내지 않아요.",
                ),
                p("Tavily는 문서에서 SOC 2 인증을 받았고 데이터를 보관하지 않는다(zero data retention)고 밝혀요."),
                p(
                    "키가 새었다고 생각되면 대시보드에서 새 키를 만들어 앱에 넣은 뒤, 예전 키는 Home의 API Keys에서 " +
                        "휴지통 아이콘으로 지워요. 지운 키는 되살릴 수 없어요.",
                ),
                p("앱에서 지우려면 \"Tavily API 키\" 옆 지우기를 누르고 \"키 지우기\" 창에서 지우기를 눌러요. Tavily의 키는 그대로예요."),
            ),
        ),
    )

    val OPENAI: KeyHowTo = KeyHowTo(
        id = "openai",
        title = "OpenAI API 키 받는 법",
        intro = "OpenAI API 키로 GPT가 웹을 검색해 노트 질문에 답해요. 유료라서 먼저 크레딧을 충전해야 해요(최소 \$5).",
        asOf = AS_OF,
        sections = listOf(
            points(
                "이 키로 하는 일",
                p("설정 › AI 노트 도우미에서 방식을 \"GPT (OpenAI)\"로 고르면 이 키 하나로 써요. GPT가 매번 웹을 검색하고, 찾은 페이지를 출처로 달아 답해요."),
                p("유료예요. 검색 1,000회당 \$10에 토큰 요금이 더해져 키의 계정에서 빠져나가요."),
            ),
            points(
                "준비물",
                p("OpenAI 계정. ChatGPT 구독과 API 요금은 따로예요."),
                p("달러로 결제할 카드와 최소 \$5 선불 충전. 한국은 OpenAI API 지원 국가예요."),
                p("PC 브라우저가 편해요. 키는 만들 때 한 번만 보이니, 휴대폰으로 옮길 방법을 먼저 정해 두거나 휴대폰에서 만들어요."),
            ),
            steps(
                "키 받기",
                p("platform.openai.com/api-keys를 열고 OpenAI 계정으로 로그인해요.", "platform.openai.com/api-keys" to OPENAI_KEYS),
                p("먼저 Billing 페이지에서 결제 수단을 넣고 크레딧을 충전해요(최소 \$5). 크레딧이 없으면 키가 있어도 답하지 않아요.", "Billing" to OPENAI_BILLING),
                p("충전할 때 자동 충전(auto recharge)은 끈 채로 둬요. 그러면 충전한 만큼만 써요."),
                p("API keys 페이지로 돌아와 \"Create new secret key\"를 눌러요."),
                p("Owned by는 \"You\"로 둬요. Name에는 알아보기 쉬운 이름(예: coffee journal)을 적어요."),
                p("Project는 키를 둘 프로젝트를 골라요. 처음이면 기본 프로젝트 하나뿐이에요(화면에 따라 이름이 조금 다를 수 있어요)."),
                p("Permissions는 기본값 \"All\"로 둬요. \"Restricted\"나 \"Read only\"로 좁히면 질문이 권한 없음(HTTP 403)으로 실패할 수 있어요."),
                p("만료일을 고를 수 있으면 정해 두는 게 안전해요(OpenAI 권장). 만료되면 새 키를 만들어 다시 넣어요."),
                p("만들기를 누르면 \"sk-proj-\"로 시작하는 키가 나와요. 이 창을 닫으면 다시 볼 수 없어요."),
                p("복사 버튼으로 복사해 바로 이 앱에 붙여 넣어요. 잃어버렸다면 새 키를 만들어요."),
            ),
            steps(
                "앱에 넣기",
                p("이 앱의 설정 › AI 노트 도우미에서 방식 \"GPT (OpenAI)\"를 골라요."),
                p("\"OpenAI API 키\" 칸(\"sk-proj-… 붙여넣기\")을 길게 눌러 붙여 넣고 저장을 눌러요. 칸이 \"저장됨 …\"과 끝 네 글자로 바뀌어요."),
                p("키 확인을 눌러요. 모델 목록만 받아 오는 무료 요청이에요. \"정상\"이면 키가 맞고 고른 모델도 쓸 수 있어요."),
                p("모델 칸을 비워 두면 gpt-5-nano를 써요. gpt-5.5도 고를 수 있어요."),
                p("처음 물을 때 \"질문을 보내기 전에\" 창에서 보내는 곳과 요금을 읽고 \"보내기\"를 눌러요."),
            ),
            points(
                "요금과 한도",
                p("웹 검색은 1,000회당 \$10, 한 번에 \$0.01이에요. 검색으로 가져온 글도 토큰으로 세어 모델 요금에 더해져요(가격표).", "가격표" to OPENAI_PRICING),
                p("앱은 질문마다 검색을 꼭 하게 해요. 모델이 한 질문에 검색을 여러 번 할 수도 있어요."),
                p("100만 토큰당 gpt-5-nano는 입력 \$0.05 · 출력 \$0.40, gpt-5.5는 입력 \$5 · 출력 \$30이에요."),
                p("선불 크레딧은 최소 \$5부터 사고, 산 날부터 1년 뒤 사라져요."),
                p(
                    "월 상한: Limits의 Spend에서 \"Edit spend limit\"을 눌러 Monthly spend limit을 적고 \"Enforce a hard limit\"을 켠 뒤 " +
                        "Save를 눌러요. 반영이 조금 늦어 약간 넘을 수 있어요.",
                    "Limits" to OPENAI_LIMITS,
                ),
                p("상한에 닿으면 다음 달까지 요청이 멈춰요(HTTP 429). 알림만 받고 싶다면 같은 곳에 spend alert를 걸어요."),
                p("쓴 금액은 Usage에서 봐요. 조직마다 등급별 월 사용 한도도 있어요(처음 \$5를 내면 Tier 1, 월 \$100).", "Usage" to OPENAI_USAGE),
            ),
            points(
                "문제가 생기면",
                p(
                    "‘OpenAI 키가 맞지 않아요’(키 확인에서는 ‘키가 틀려요’): HTTP 401이에요. 키가 잘렸거나, 지운 키이거나, 만료됐어요. " +
                        "새 키를 만들어 다시 넣어요.",
                ),
                p("‘OpenAI 결제가 필요해요’(‘결제가 필요해요’): 크레딧이 없거나 결제를 설정하지 않았어요(HTTP 429 insufficient_quota). Billing에서 충전해요."),
                p("‘OpenAI 크레딧이 다 떨어졌어요’: 선불 잔액이 \$0이에요(credit_balance_exhausted). Billing에서 충전해요."),
                p("‘OpenAI 요청 한도를 넘었어요’(‘한도를 넘었어요’): 분당 요청이 너무 잦았어요(HTTP 429). 잠시 뒤 다시 해요."),
                p(
                    "‘직접 정한 OpenAI 월 지출 한도에 닿았어요’(‘지출 한도에 닿았어요’): Limits에서 켠 월 상한(프로젝트나 조직)에 닿았어요. " +
                        "기다려도 풀리지 않아요. Limits › Spend에서 한도를 올리거나 다음 달까지 기다려요.",
                ),
                p(
                    "‘OpenAI가 이 조직에 정한 사용 한도에 닿았어요’: 등급(Tier)의 월 사용 한도예요. 결제 금액이 쌓여 등급이 오르면 한도도 올라가요. " +
                        "‘요청이 갑자기 늘어 OpenAI가 잠시 막았어요’는 몇 분 뒤 다시 해요.",
                ),
                p("‘이 모델은 이 키로 쓸 수 없어요’(‘이 모델은 쓸 수 없어요’): 이 키로 열리지 않은 모델이에요. 설정의 모델에서 gpt-5-nano를 골라요."),
                p("‘이 OpenAI 키로는 쓸 수 없어요(권한 없음)’: HTTP 403이에요. 키의 Permissions가 좁아요. \"All\"로 된 키를 새로 만들어요."),
                p("‘지금 있는 지역에서는 OpenAI를 쓸 수 없어요.’: 지원하지 않는 곳으로 접속했어요. VPN을 쓰고 있다면 끄고 다시 해요."),
                p("‘OpenAI 서버가 붐벼요’: OpenAI 쪽 문제예요(HTTP 500대). 몇 분 뒤 다시 해요. ‘연결 실패’는 인터넷 연결을 확인해요."),
                p("‘출처를 찾지 못했어요’: 답에 출처와 이어진 문장이 없어 보여 주지 않았어요. 다른 말로 물어요."),
            ),
            points(
                "보안과 개인정보",
                p("키는 이 휴대폰에만 암호화해 저장돼요. 백업에는 들어가지 않아서, 휴대폰을 바꾸면 키를 다시 넣어야 해요."),
                p(
                    "질문하면 이 휴대폰에서 바로 OpenAI로 키와 질문 글만 보내요. 검색 결과를 한국에 맞추려고 나라(KR)만 함께 보내요. " +
                        "기록·원두·장소·사진은 보내지 않아요.",
                ),
                p(
                    "OpenAI는 따로 동의하지 않는 한 API로 받은 내용을 학습에 쓰지 않아요. 남용 감시 기록은 최대 30일, " +
                        "Responses API의 응답은 기본으로 30일 보관돼요(데이터 정책).",
                    "데이터 정책" to OPENAI_DATA,
                ),
                p("키가 새었다고 생각되면 새 키를 만들어 앱에 넣고, API keys 페이지에서 예전 키를 지워요(revoke).", "API keys" to OPENAI_KEYS),
                p("앱에서 지우려면 \"OpenAI API 키\" 옆 지우기를 누르고 \"키 지우기\" 창에서 지우기를 눌러요. OpenAI의 키는 그대로예요."),
            ),
        ),
    )

    val CLAUDE: KeyHowTo = KeyHowTo(
        id = "claude",
        title = "Anthropic API 키 받는 법",
        intro = "Anthropic API 키로 Claude가 웹을 검색해 노트 질문에 답해요. 유료라서 Claude 콘솔에서 먼저 크레딧을 사야 해요.",
        asOf = AS_OF,
        sections = listOf(
            points(
                "이 키로 하는 일",
                p(
                    "설정 › AI 노트 도우미에서 방식을 \"Claude (Anthropic)\"로 고르면 이 키 하나로 써요. Claude가 웹을 검색하고, " +
                        "인용마다 원문 조각이 함께 와서 앱이 \"✓ 원문 확인\"을 붙여요.",
                ),
                p("유료예요. 검색 1,000회당 \$10에 토큰 요금(검색 결과 포함)이 더해져 크레딧에서 빠져나가요."),
            ),
            points(
                "준비물",
                p("Claude 콘솔 계정. claude.ai 구독(Pro·Max)과 API 크레딧은 따로 결제해요."),
                p("달러로 결제할 카드. 한국은 Claude API 지원 지역이에요."),
                p("PC 브라우저가 편해요. 키는 만들 때 한 번만 보이니, 휴대폰으로 옮길 방법을 먼저 정해 두거나 휴대폰에서 만들어요."),
            ),
            steps(
                "키 받기",
                p("platform.claude.com을 열어 로그인하거나 계정을 만들어요. console.anthropic.com도 여기로 와요.", "platform.claude.com" to CLAUDE_CONSOLE),
                p("Settings › Billing에서 \"Buy credits\"를 눌러요. 조직의 Admin이나 Billing 역할이 있어야 살 수 있어요.", "Billing" to CLAUDE_BILLING),
                p("처음에는 조직과 쓰임새를 물어요. 금액을 넣고 확인하면 크레딧을 바로 쓸 수 있어요."),
                p("같은 페이지의 Auto-reload는 꺼 둬요. 그러면 산 만큼만 써요."),
                p("Settings › API keys로 가서 \"Create key\"를 눌러요.", "API keys" to CLAUDE_KEYS),
                p("키 이름을 적어요(예: coffee journal). Linked account는 나 자신으로 둬요(개인 키)."),
                p(
                    "만료(expiration)를 골라요: 3 hours, 1 day, 7 days, 30 days, 직접 정하기, Never. " +
                        "만료되면 앱이 ‘키가 맞지 않아요’를 보이니 그때 새 키를 넣어요.",
                ),
                p(
                    "워크스페이스는 꼭 하나를 골라요(예: Default Workspace). 이 앱은 워크스페이스 ID를 따로 보내지 않아서, " +
                        "여러 워크스페이스에 쓰는 키는 HTTP 400으로 실패해요.",
                ),
                p("만들면 \"sk-ant-\"로 시작하는 키가 이때 한 번만 보여요. 복사 버튼으로 복사해요. 잃어버리면 다시 볼 수 없어 새로 만들어야 해요."),
                p("휴대폰이면 복사한 뒤 바로 이 앱으로 돌아와 붙여 넣어요. 메모 앱이나 메신저에 붙여 두지 마세요."),
            ),
            steps(
                "앱에 넣기",
                p("이 앱의 설정 › AI 노트 도우미에서 방식 \"Claude (Anthropic)\"를 골라요."),
                p("\"Anthropic API 키\" 칸(\"sk-ant-… 붙여넣기\")을 길게 눌러 붙여 넣고 저장을 눌러요. 칸이 \"저장됨 …\"과 끝 네 글자로 바뀌어요."),
                p("키 확인을 눌러요. 모델 목록만 받아 오는 무료 요청이에요. \"정상\"이면 키가 맞아요."),
                p("모델 칸을 비워 두면 claude-opus-5를 써요. 비용을 줄이려면 claude-sonnet-5나 claude-haiku-4-5를 골라요."),
                p("처음 물을 때 \"질문을 보내기 전에\" 창에서 보내는 곳과 요금을 읽고 \"보내기\"를 눌러요."),
            ),
            points(
                "요금과 한도",
                p(
                    "웹 검색은 1,000회당 \$10, 한 번에 \$0.01이고, 실패한 검색은 청구되지 않아요. 가져온 검색 결과는 입력 토큰으로 세요(웹 검색 요금).",
                    "웹 검색 요금" to CLAUDE_WEB_SEARCH,
                ),
                p("앱은 요청 한 번에 검색을 최대 3번으로 정해 둬요. 검색 결과가 입력 토큰이 되어 모델 요금이 검색 요금보다 커질 수 있어요."),
                p("100만 토큰당 입력 · 출력: Opus 5 \$5 · \$25, Sonnet 5 \$2 · \$10, Haiku 4.5 \$1 · \$5예요."),
                p("산 크레딧은 1년 뒤 사라지고 환불되지 않아요. 크레딧이 없으면 API가 멈춰요."),
                p(
                    "내 지출 한도: Settings › Billing의 Spend limits에서 \"Adjust limit\"(없으면 \"Set limit\")을 눌러 정해요. " +
                        "닿으면 요청이 멈추고, 한도를 올리거나 풀면 다시 돼요.",
                ),
                p("조직 등급마다 월 상한도 있어요(Start 등급 \$500). 닿으면 다음 달 1일 00:00 UTC, 한국 시간 오전 9시까지 멈춰요."),
                p("새 조직은 한도가 낮은 Evaluation 등급으로 시작할 수 있어요. 쓴 금액은 Usage에서 봐요.", "Usage" to CLAUDE_USAGE),
            ),
            points(
                "문제가 생기면",
                p(
                    "‘Anthropic 키가 맞지 않아요’(키 확인에서는 ‘키가 틀려요’): HTTP 401이에요. 키가 잘렸거나, 꺼지거나 지운 키이거나, " +
                        "만료됐어요. API keys에서 새 키를 만들어 넣어요.",
                ),
                p("‘Anthropic 크레딧이 부족해요’(‘결제가 필요해요’): 크레딧 잔액이 모자라요. Billing에서 \"Buy credits\"로 사요."),
                p("‘Anthropic 결제에 문제가 있어요’: 결제 정보에 문제가 있어요(HTTP 402). Billing에서 결제 수단을 확인해요."),
                p(
                    "‘직접 정한 Anthropic 지출 한도에 닿았어요’(‘지출 한도에 닿았어요’): Billing의 Spend limits에 정한 한도예요(HTTP 400). " +
                        "\"Adjust limit\"로 올리면 바로 다시 돼요. 워크스페이스 한도면 ‘워크스페이스에 정한 … 지출 한도’라고 나와요.",
                ),
                p(
                    "같은 HTTP 400에 \"anthropic-workspace-id is required\"가 보이면 워크스페이스를 하나로 정하지 않은 키예요. " +
                        "워크스페이스 하나를 골라 키를 새로 만들어요.",
                ),
                p("‘이 조직에서 웹 검색을 쓸 수 없어요.’: 조직 관리자가 웹 검색을 꺼 뒀어요. 콘솔의 Privacy 설정에서 켜요.", "Privacy" to CLAUDE_PRIVACY),
                p("‘이 Anthropic 키로는 쓸 수 없어요(권한 없음)’: HTTP 403이에요. 키의 워크스페이스와 역할을 확인해요."),
                p("‘이 모델은 이 키로 쓸 수 없어요’(‘이 모델은 쓸 수 없어요’): 설정의 모델에서 claude-sonnet-5 같은 다른 모델을 골라요."),
                p("‘Anthropic 요청 한도를 넘었어요’: 분당 한도에 닿았어요(HTTP 429). 잠시 뒤 다시 해요."),
                p(
                    "‘이번 달 Anthropic 사용 상한(조직 등급의 월 상한)에 닿았어요’: 등급의 월 상한이에요. 다음 달 1일 오전 9시(한국 시간)까지 " +
                        "풀리지 않아요. Limits의 \"Request rate limit increase\"로 올려 달라고 할 수 있어요.",
                ),
                p("‘Anthropic 서버가 붐벼요’: HTTP 500·529예요. 몇 분 뒤 다시 해요. ‘요청이 거절됐어요’는 다른 말로 물어요."),
            ),
            points(
                "보안과 개인정보",
                p("키는 이 휴대폰에만 암호화해 저장돼요. 백업에는 들어가지 않아서, 휴대폰을 바꾸면 키를 다시 넣어야 해요."),
                p(
                    "질문하면 이 휴대폰에서 바로 Anthropic으로 키와 질문 글만 보내요. 검색 결과를 한국에 맞추려고 나라(KR)만 함께 보내요. " +
                        "기록·원두·장소·사진은 보내지 않아요.",
                ),
                p("Anthropic은 명시적으로 허락받지 않는 한 API로 받은 내용을 모델 학습에 쓰지 않아요(데이터 보관).", "데이터 보관" to CLAUDE_DATA),
                p(
                    "키가 새었다고 생각되면 새 키를 넣고, API keys 페이지에서 예전 키를 Disable(다시 켤 수 있음)하거나 " +
                        "Delete(영구 삭제)해요.",
                ),
                p("앱에서 지우려면 \"Anthropic API 키\" 옆 지우기를 누르고 \"키 지우기\" 창에서 지우기를 눌러요. 콘솔의 키는 그대로예요."),
            ),
        ),
    )

    val KAKAO: KeyHowTo = KeyHowTo(
        id = "kakao",
        title = "카카오 REST API 키 받는 법",
        intro = "카카오 REST API 키를 넣으면 위치를 정할 때 한국 카페·로스터리를 이름으로 훨씬 잘 찾아요. " +
            "계정에서 카카오맵을 처음 켠 앱의 키면 무료예요.",
        asOf = AS_OF,
        sections = listOf(
            points(
                "이 키로 하는 일",
                p(
                    "설정 › 장소 검색의 \"카카오 REST API 키 (선택)\"에 넣어요. 로스터리·카페의 \"지도에서 위치 지정\"에서 " +
                        "\"주소나 이름으로 찾기\"로 국내 장소를 찾으면 카카오 로컬(키워드 검색과 주소 검색)로 찾아요.",
                ),
                p(
                    "키가 없으면 휴대폰의 지도 서비스와 오픈스트리트맵으로 함께 찾는데, 한국 카페는 이름으로 훨씬 적게 나와요. " +
                        "카카오 키가 있으면 \"프릳츠\"처럼 지점이 여러 곳인 가게도 지점마다 나와요.",
                ),
                p("해외 로스터리는 키가 있어도 계속 키 없이 찾아요."),
                p("무료예요. 계정에서 카카오맵을 처음 켠 앱은 키워드 검색을 하루 100,000건까지 무료로 써요. 카드는 필요 없어요."),
            ),
            points(
                "준비물",
                p("카카오계정 하나. 그 계정으로 카카오디벨로퍼스에 개발자 등록을 해요."),
                p("2026년 7월 21일부터 카카오맵은 신청이나 심사 없이 켜기만 하면 돼요. 무료 쿼터 안에서는 비즈월렛도 필요 없어요."),
                p("PC 브라우저가 편해요. 휴대폰 브라우저나 카카오디벨로퍼스 모바일 앱으로도 할 수 있어요."),
            ),
            steps(
                "키 받기",
                p("developers.kakao.com/console/app을 열고 카카오계정으로 로그인해요.", "developers.kakao.com/console/app" to KAKAO_CONSOLE),
                p("처음이면 [회원가입]을 눌러 개발자 계정을 등록해요."),
                p(
                    "이미 카카오맵을 켠 앱이 있다면 새로 만들지 말고 그 앱의 키를 써요. 무료 쿼터는 계정에서 카카오맵을 처음 켠 앱 하나에만 있어요" +
                        "(카카오맵 이용 정책).",
                    "카카오맵 이용 정책" to KAKAO_MAP_POLICY,
                ),
                p("전체 앱 목록에서 [앱 생성]을 눌러요. 앱 아이콘은 건너뛰어도 돼요."),
                p("앱 이름(필수)에 알아보기 쉬운 이름(예: 내 커피 기록)을 적어요. 회사명(필수)은 개인이면 자기 이름이나 별명을 적어도 돼요."),
                p("카테고리(필수)에서 가까운 분류를 골라요. 앱 대표 도메인은 비워 둬도 돼요."),
                p("운영 정책에서 제한하는 사항에 해당하지 않는지 확인하는 항목을 선택하고 [저장]을 눌러요."),
                p(
                    "만든 앱에서 [앱] › [제품 설정] › [카카오맵]으로 가서 [사용 설정]의 [상태]를 [ON]으로 바꿔요. " +
                        "이걸 하지 않으면 검색이 막혀요(HTTP 403).",
                ),
                p("앱 정보에 \"카카오맵 무료 쿼터\" 뱃지가 보이면 무료로 쓸 수 있는 앱이에요. 뱃지가 없으면 이 계정의 다른 앱이 먼저 켠 거예요."),
                p("[앱] › [플랫폼 키] › [REST API 키]로 가요. 앱을 만들 때 함께 생긴 REST API 키가 있어요."),
                p("키 값 옆 복사 버튼으로 복사해요. 영문과 숫자로 된 긴 문자열이고, 이 페이지에서 언제든 다시 복사할 수 있어요."),
                p(
                    "같은 화면의 호출 허용 IP 주소는 비워 둬요. 휴대폰의 IP는 자주 바뀌어서 넣으면 검색이 막혀요. " +
                        "리다이렉트 URI와 클라이언트 시크릿은 카카오 로그인용이라 건드리지 않아도 돼요.",
                ),
                p("JavaScript 키, 네이티브 앱 키, 어드민 키는 이 앱에 맞지 않아요. 꼭 REST API 키를 복사해요."),
            ),
            steps(
                "앱에 넣기",
                p("이 앱의 설정 › 장소 검색으로 가요."),
                p("\"카카오 REST API 키 (선택)\" 칸(\"REST API 키 붙여넣기\")을 길게 눌러 붙여 넣고 저장을 눌러요. 빈칸과 줄바꿈은 앱이 지워요."),
                p("칸이 \"저장됨 …\"과 키의 끝 네 글자로 바뀌어요."),
                p("키 확인을 눌러요. 카카오에 \"카페\"를 한 번 검색해 보고 \"✓ 키가 맞아요. 국내 검색은 카카오로 찾아요.\"가 나오면 끝이에요."),
                p(
                    "로스터리나 카페의 \"지도에서 위치 지정\"에서 \"주소나 이름으로 찾기\"에 \"프릳츠\"를 적고 검색해 봐요. " +
                        "결과 아래에 \"검색: 카카오\"가 보이면 카카오로 찾은 거예요.",
                ),
                p("지점이 많으면 결과 아래 \"더 보기\"로 다음 결과를 불러와요. 더 찾을 곳이 없으면 \"더 보기\"가 사라져요."),
            ),
            points(
                "요금과 한도",
                p(
                    "무료 쿼터는 하루 키워드로 장소 검색 100,000건, 주소로 좌표 변환 100,000건이에요. 한 달에는 전체 API 3,000,000건이에요(쿼터 표).",
                    "쿼터 표" to KAKAO_QUOTA,
                ),
                p(
                    "이 앱의 검색 한 번은 보통 2건(키워드 1, 주소 1)이에요. \"더 보기\"는 1건, 키 확인은 1건이에요. " +
                        "\"프릳츠 장충\"처럼 이름과 동네를 함께 적으면 동네 주변을 한 번 더 찾아 몇 건 더 써요.",
                ),
                p("혼자 쓰면 하루 한도에 닿을 일은 거의 없어요."),
                p(
                    "무료를 넘기거나 두 번째 앱부터 쓰려면 비즈월렛을 연결하고 [유료 API]에서 카카오맵을 \"사용 중\"으로 설정해야 해요. " +
                        "설정하지 않으면 한도에서 막힐 뿐(HTTP 429) 요금은 나가지 않아요.",
                ),
                p("유료로 설정했을 때만 키워드 검색 건당 2원, 주소 변환 건당 0.5원이 비즈월렛으로 청구돼요."),
                p(
                    "쓴 양은 앱 관리 페이지의 [통계] › [쿼터]에서 월간·일간으로 봐요(1시간 단위로 반영). " +
                        "무료 한도에 닿으면 메일과 알림이 와요.",
                ),
            ),
            points(
                "문제가 생기면",
                p(
                    "‘키가 맞지 않아요. REST API 키를 붙여 넣었는지 확인해 주세요.’: HTTP 401이에요. 다른 종류의 키를 넣었거나, " +
                        "일부만 복사했거나, 지운 키예요. [REST API 키]에서 다시 복사해 넣어요.",
                ),
                p("‘이 키의 앱에서 카카오맵이 꺼져 있어요…’: HTTP 403이에요. [앱] › [제품 설정] › [카카오맵]의 [사용 설정]에서 상태를 ON으로 켜요."),
                p(
                    "‘이 키의 카카오 무료 사용량을 다 썼어요…’: HTTP 429예요. 오늘 무료 쿼터를 다 썼거나, " +
                        "\"카카오맵 무료 쿼터\" 뱃지가 없는 앱의 키예요. 다음 날 다시 하거나 뱃지가 있는 앱의 키를 넣어요.",
                ),
                p("‘카카오에 연결하지 못했어요’, ‘검색하지 못했어요’: 카카오에 닿지 못했어요. 인터넷 연결을 확인해요."),
                p("‘지금은 확인하지 못했어요. 잠시 뒤 다시 해 보세요.’: 카카오가 다른 오류로 답했어요. 잠시 뒤 다시 해요."),
                p(
                    "지도 화면의 ‘… 키 없이 찾았어요’: 카카오가 거절해 휴대폰의 지도 서비스와 오픈스트리트맵으로 대신 찾았어요. " +
                        "함께 나온 이유(키, 꺼진 카카오맵, 사용량)대로 고쳐요.",
                ),
                p("‘더 찾지 못했어요. 다시 눌러 보세요.’: \"더 보기\"만 실패했어요. 다시 눌러요."),
                p(
                    "결과 아래가 \"검색: 기기 지도 서비스 · 오픈스트리트맵\"이면 키가 저장되지 않았거나(설정에서 \"저장됨\" 확인) " +
                        "해외 로스터리를 찾는 중이에요.",
                ),
            ),
            points(
                "보안과 개인정보",
                p("키는 이 휴대폰에만 암호화해 저장돼요. 백업에는 들어가지 않아서, 휴대폰을 바꾸면 키를 다시 넣어야 해요."),
                p("카카오로는 검색할 때 적은 말만 가요. 현재 위치에서 찾으면 그 좌표도 가요. 호출 수는 내 앱의 [통계]에 남아요."),
                p(
                    "키가 새었다고 생각되면 [REST API 키]의 키 관리 메뉴에서 [복제 키 생성]으로 새 키를 만들어 앱에 넣어요. " +
                        "새 키를 대표 키로 지정한 뒤 예전 키를 [삭제]해요(키가 하나뿐이거나 대표 키면 지울 수 없어요).",
                ),
                p("앱이 필요 없어지면 [앱] › [일반] › [앱 삭제]에서 [앱 영구 삭제]를 눌러요. 지운 앱은 되살릴 수 없어요."),
                p(
                    "이 앱에서 지우려면 설정 › 장소 검색의 지우기를 누르고 \"키 지우기\" 창에서 지우기를 눌러요. " +
                        "국내 검색은 키 없이 찾는 방식으로 돌아가요.",
                ),
            ),
        ),
    )

    val ALL = listOf(GEMINI, TAVILY, OPENAI, CLAUDE, KAKAO)

    fun of(id: String): KeyHowTo? = ALL.firstOrNull { it.id == id }
}
