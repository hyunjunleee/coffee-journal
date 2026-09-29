package com.coffeejournal.ui.about

import com.coffeejournal.domain.reference.CafeRecipes
import com.coffeejournal.ui.map.detail.DetailMapPrivacy
import com.coffeejournal.ui.map.detail.OpenFreeMap

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
    const val AI_HELPER_TITLE = "AI 노트 도우미 · Gemini · Tavily · OpenAI · Anthropic"
    const val PLACE_SEARCH_TITLE = "위치 지정 검색 · 기기 지도 서비스 · OpenStreetMap · 카카오 로컬"

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
            "한국 지도",
            "로스터리·카페 지도와 위치 지정의 시·도, 시·군·구 경계는 통계청 통계지리정보서비스(SGIS)의 행정구역 경계(공공누리 제1유형: 출처표시)를 " +
                "바탕으로 한 vuski/admdongkor ver20260701 보정본(CC BY 4.0)의 행정동 경계를 시·군·구와 시·도로 병합하고 단순화해 앱에 넣은 것이에요. " +
                "지도의 경계는 단순화되어 실제 경계와 조금 다를 수 있어요.\n" +
                "\"네이버 지도에서 열기\"·\"카카오맵에서 열기\"·\"Google 지도에서 열기\"는 해당 지도 앱(없으면 웹 지도)을 이름과 위치가 담긴 링크로 여는 것뿐이고, " +
                "앱이 직접 어디로 데이터를 보내지는 않아요. 링크를 연 뒤에는 각 지도 서비스의 약관과 개인정보 처리방침을 따라요.",
            links = listOf(
                "통계청 SGIS" to "https://sgis.kostat.go.kr/",
                "vuski/admdongkor" to "https://github.com/vuski/admdongkor",
                "공공누리 제1유형" to "https://www.kogl.or.kr/info/licenseType1.do",
                "CC BY 4.0" to "https://creativecommons.org/licenses/by/4.0/",
            ),
        ),
        Credit(
            "상세 지도 · OpenStreetMap",
            "로스터리·카페 지도와 위치 지정의 \"상세 지도\"는 OpenStreetMap 데이터(© OpenStreetMap contributors, ODbL 1.0)로 만든 " +
                "OpenMapTiles 스키마의 벡터 지도 조각을 OpenFreeMap(openfreemap.org)에서 받아 앱의 색으로 그린 것이에요. " +
                "지도 위에는 항상 \"${OpenFreeMap.ATTRIBUTION}\"를 표시해요. OpenFreeMap은 키·계정 없이 쓸 수 있는 무료 공개 서비스예요(상업적 사용 허용, 출처 표시 필요).\n" +
                "지도는 MapLibre Native(BSD 2-Clause)가 그리고, 앱 화면에는 MapLibre Compose(BSD 3-Clause)로 붙였어요. 둘의 라이선스와 MapLibre Native에 함께 들어간 " +
                "라이브러리들의 고지문은 아래 \"라이선스 전문 보기\"에 원문 그대로 있어요.\n" +
                DetailMapPrivacy.NOTE + "\n" +
                "인터넷이 없으면 상세 지도는 열리지 않고, 시·도·시·군·구 경계 지도는 앱에 들어 있어 그대로 쓸 수 있어요.",
            links = listOf(
                "OpenStreetMap 저작권 · ODbL 1.0" to OpenFreeMap.OSM_COPYRIGHT_URL,
                "OpenMapTiles" to OpenFreeMap.OPENMAPTILES_URL,
                "OpenFreeMap" to OpenFreeMap.SITE_URL,
                "MapLibre" to "https://maplibre.org/",
            ),
            licenseText = LicenseTexts.MAPLIBRE,
        ),
        Credit(
            AI_HELPER_TITLE,
            "노트 설명과 맛 묘사 → 노트 찾기는 설정에서 고른 방식에 따라 Google Gemini API(Google 검색 포함), Tavily 검색 API, " +
                "OpenAI API(웹 검색), Anthropic API(Claude, 웹 검색)를 불러요. 앱에는 키가 없고, 사용자가 각 서비스에서 받은 자기 키를 넣어 써요. " +
                "키는 이 휴대폰에만 암호화해 저장되고 백업에 들어가지 않아요.\n" +
                "보내는 것은 질문 글(노트 이름이나 맛 묘사)과, Gemini 무료 + Tavily 방식에서는 Tavily가 찾은 글뿐이에요. 기록·원두·장소·사진은 보내지 않아요. " +
                "Gemini 무료 등급에서는 보낸 질문과 받은 답이 Google의 제품 개선에 쓰이고 사람이 읽어 볼 수 있어요. " +
                "Google 검색에 기댄 답은 Google 검색 제안과 함께 보여주고, 답과 출처는 화면을 닫으면 남기지 않아요. " +
                "각 서비스의 약관과 개인정보 처리방침을 따라요.",
            links = listOf(
                "Gemini API 약관" to "https://ai.google.dev/gemini-api/terms",
                "Tavily 약관" to "https://tavily.com/terms",
                "Tavily 개인정보" to "https://tavily.com/privacy",
                "OpenAI 이용약관" to "https://openai.com/policies/row-terms-of-use",
                "OpenAI 개인정보" to "https://openai.com/policies/row-privacy-policy",
                "Anthropic 상업 약관" to "https://www.anthropic.com/legal/commercial-terms",
                "Anthropic 개인정보" to "https://www.anthropic.com/legal/privacy",
            ),
        ),
        Credit(
            PLACE_SEARCH_TITLE,
            "로스터리·카페 위치를 이름이나 주소로 찾을 때 \"검색\"을 누르면, 적은 말을 휴대폰의 지도 서비스(Android는 휴대폰의 지오코더, 보통 Google; " +
                "iOS는 Apple 지도)와 OpenStreetMap 검색(komoot의 공개 서비스 Photon, photon.komoot.io)으로 함께 보내요. " +
                "OpenStreetMap에서 찾은 곳은 OpenStreetMap 기여자들이 만든 데이터(© OpenStreetMap contributors, ODbL 1.0)라서 결과 아래에 출처를 표시해요. " +
                "설정 › 장소 검색에 자기 카카오 REST API 키를 넣으면 국내 검색은 카카오 로컬 API로 보내요. 앱에는 키가 없어요. " +
                "\"현재 위치\"를 정한 뒤 찾거나 \"프릳츠 장충\"처럼 이름과 동네를 함께 적어 그 동네 둘레를 다시 찾을 때는 그 좌표도 보내 가까운 곳부터 보여줘요. " +
                "기록·원두·사진은 보내지 않아요.\n" +
                "\"현재 위치\"는 누를 때만 위치 권한을 묻고 위치를 한 번만 읽어요. 남기는 것은 저장한 좌표(와 검색으로 고른 곳의 주소)뿐이에요. " +
                "각 서비스의 약관과 개인정보 처리방침을 따라요.",
            links = listOf(
                "OpenStreetMap 저작권 · ODbL 1.0" to OpenFreeMap.OSM_COPYRIGHT_URL,
                "Photon" to "https://photon.komoot.io/",
                "카카오 개발자 운영정책" to "https://developers.kakao.com/terms/latest/ko/site-policies",
                "카카오 개인정보" to "https://www.kakao.com/policy/privacy",
                "Apple 지도 약관" to "https://www.apple.com/legal/internet-services/maps/terms-en.html",
                "Google 개인정보" to "https://policies.google.com/privacy",
            ),
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
