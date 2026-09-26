package com.coffeejournal.ui.about

import com.coffeejournal.domain.reference.CafeRecipes

/** A source the app's content or look comes from, with its terms. */
data class Credit(
    val title: String,
    val body: String,
    val links: List<Pair<String, String>> = emptyList(),
    /** License text to show in full (icons); null when the terms are only linked. */
    val licenseText: String? = null,
)

/** Where the app's design, reference data and icons come from (checked 2026-09). */
object Credits {
    const val WEB_TEMPLATE_URL = "https://coffee-journal-empty-template.divine-pear-1472.chatgpt.site/"
    const val FLAVOR_WHEEL_ATTRIBUTION = "Coffee Taster's Flavor Wheel © 2016 SCA · WCR, CC BY-NC-ND 4.0"

    val all: List<Credit> = listOf(
        Credit(
            "웹 앱 coffee_journal 템플릿",
            "화면 구성과 문구, 색과 간격, 참고 데이터(플레이버 휠 용어, 품종·가공 방식·산지, 챔피언·카페 레시피, 배전도, 장비 유형)는 " +
                "이 웹 앱에서 가져왔어요. 공식 기관의 자료가 아니라 웹 앱 작성자가 정리한 자료라서, 공식 출처와 다른 부분은 아래에 따로 적었어요.",
            links = listOf("웹 앱" to WEB_TEMPLATE_URL),
        ),
        Credit(
            "Coffee Taster's Flavor Wheel",
            "The Coffee Taster's Flavor Wheel by SCA and WCR (©2016) is licensed under a Creative Commons " +
                "Attribution-NonCommercial-NoDerivatives 4.0 International License.\n" +
                "앱은 휠의 향미 용어와 분류를 쓰고, 휠을 앱 화면에 맞게 다시 그려 보여줘요. 비상업적 개인 기록용으로만 쓰고 있어요.",
            links = listOf(
                "SCA 플레이버 휠" to "https://sca.coffee/store/p/the-coffee-tasters-flavor-wheel-poster-english-dkx89",
                "CC BY-NC-ND 4.0" to "https://creativecommons.org/licenses/by-nc-nd/4.0/",
            ),
        ),
        Credit(
            "SCA 커핑 폼",
            "SCA 2004: 커핑 점수의 10개 항목과 점수 범위(6~10점, 0.25점 단위, 균일성·클린컵·단맛은 컵당 2점)는 SCA(구 SCAA)의 2004년 커핑 프로토콜과 양식을 따라요. " +
                "향·산미·바디의 강도 1~5점(0.5 단위)은 SCA 양식이 아니라 웹 앱이 정한 척도예요.\n" +
                "CVA: SCA는 2024년에 이 양식을 CVA(Coffee Value Assessment) 표준으로 대체했어요. 앱의 CVA 양식은 SCA-103(묘사 평가: 섹션별 강도 0~15, " +
                "향·맛 묘사 최대 5개, 주요 맛 최대 2개, 마우스필 최대 2개)과 SCA-104(정동 평가: 8개 섹션 품질 인상 1~9, 5컵 중 균일하지 않은 컵·결점 컵, " +
                "결점 종류 곰팡이·페놀·감자) 표준 문서와 SCA의 한국어 양식을 항목마다 대조해 만들었어요(2026-09 확인). " +
                "점수는 SCA-104 5.5의 식 S = 0.65625 × Σ(8개 섹션) + 52.75 − 2 × 균일하지 않은 컵 − 4 × 결점 컵을 0.25점 단위로 반올림한 값으로, " +
                "SCA 점수 계산기(sca.coffee/cuppingscore)와 같아요. 결점 컵은 결점 종류를 함께 골라야 계산돼요(SCA-104 5.4.1).",
            links = listOf(
                "SCA Coffee Value Assessment" to "https://sca.coffee/value-assessment",
                "SCA-103 묘사 평가" to "https://sca.coffee/s/AW_SCA-103_Descriptive-Assessment_Sept2024_Secured.pdf",
                "SCA-104 정동 평가" to "https://sca.coffee/s/AW_SCA-104_Affective-Assessment_Sept2024_Secured.pdf",
                "CVA 한국어 양식" to "https://sca.coffee/s/CVA-Cupping-Forms-Korean.pdf",
                "CVA 점수 계산기" to "https://sca.coffee/cuppingscore",
            ),
        ),
        Credit(
            "SCA 추출 조절 차트",
            "계산기의 추출수율(EY) = TDS × 추출액 무게 ÷ 원두량이고, 비교 기준은 SCA가 25 매거진 13호 \"Towards a New Brewing Chart\"(2020)에 " +
                "실은 고전 Coffee Brewing Control Chart의 IDEAL OPTIMUM BALANCE 구역(추출수율 18~22%, TDS 1.15~1.35%)이에요(2026-09 확인). " +
                "같은 글에서 SCA는 이 차트가 맛 묘사와 취향을 섞었다며 새 차트를 연구 중이라고 밝혀서, 앱은 참고 범위로만 보여줘요.",
            links = listOf("25 매거진 13호 (보관본)" to "https://web.archive.org/web/20210811223756/https://sca.coffee/sca-news/25/issue-13/towards-a-new-brewing-chart"),
        ),
        Credit(
            "카페 레시피",
            CafeRecipes.all.joinToString("\n") { r ->
                "· ${r.name}: " + if (r.sourceUrl != null) "카페가 공개한 가이드의 수치 그대로예요." else "웹 앱이 준 레시피로, 확인할 수 있는 공개 출처가 없어요."
            } + "\n가이드에 없는 값(붓는 시간, 분쇄도, 전체 추출시간)은 비워뒀어요.",
            links = CafeRecipes.all.mapNotNull { r -> r.sourceUrl?.let { r.name to it } },
        ),
        Credit(
            "월드 브루어스컵 챔피언 레시피",
            "우승 연도·이름·국가는 World Brewers Cup 대회 기록이에요. 레시피 수치(원두량·물량·온도·드리퍼)는 우승자 인터뷰와 영상 같은 공개 자료를 " +
                "웹 앱 작성자가 정리한 값이라 대회 공식 자료가 아니에요.",
            links = listOf("World Brewers Cup" to "https://wcc.coffee/world-brewers-cup"),
        ),
        Credit(
            "세계지도",
            "커피 지도의 나라 모양은 퍼블릭 도메인인 Natural Earth 지도 데이터를 단순화한 것이에요(웹 앱 원본 코드의 주석 기준).",
            links = listOf("Natural Earth" to "https://www.naturalearthdata.com/", "이용 조건" to "https://www.naturalearthdata.com/about/terms-of-use/"),
        ),
        Credit(
            "아이콘 · Lucide",
            "하단 탭과 화면의 선 아이콘 일부는 Lucide 아이콘의 경로를 그대로 쓰거나 줄여서 다시 그렸어요(ISC License).",
            links = listOf("lucide.dev" to "https://lucide.dev/license"),
            licenseText = LicenseTexts.LUCIDE,
        ),
        Credit(
            "아이콘 · Feather",
            "별 아이콘은 Feather 아이콘의 좌표를 썼어요(MIT License).",
            links = listOf("feathericons.com" to "https://feathericons.com/"),
            licenseText = LicenseTexts.FEATHER,
        ),
        Credit(
            "글꼴 · SQLite",
            "글꼴은 따로 넣지 않고 기기의 시스템 글꼴과 이모지를 그대로 써요. 기록은 앱에 함께 들어간 SQLite(퍼블릭 도메인)에 저장돼요.",
            links = listOf("SQLite 저작권" to "https://sqlite.org/copyright.html"),
        ),
    )
}
