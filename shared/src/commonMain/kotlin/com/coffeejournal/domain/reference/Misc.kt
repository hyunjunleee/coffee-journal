// Generated from the web app reference data by scratchpad/site/gen_refs.py. Do not edit by hand.
package com.coffeejournal.domain.reference

object BeanRangeColors {
    val hex: List<String> = listOf(
        "#b58968",
        "#8a6a52",
        "#c9a47e",
        "#9c7355",
        "#d9bd9c",
        "#6b543e",
    )
}

object EquipmentTypes {
    data class Label(val type: String, val title: String, val name: String, val placeholder: String, val notesPlaceholder: String)
    val order: List<String> = listOf(
        "dripper",
        "filter",
        "kettle",
        "thermometer",
        "scale",
        "water",
    )
    val labels: Map<String, Label> = listOf(
        Label("kettle", "케틀", "케틀", "예: 펠로우 스타그 EKG 케틀", "용도, 특징 등"),
        Label("thermometer", "온도계", "온도계", "예: 써모웍스 서모팝", "용도, 특징 등"),
        Label("dripper", "드리퍼", "드리퍼", "예: 오리가미 드리퍼", "재질, 구멍 수, 특징 등"),
        Label("filter", "필터", "필터", "예: 칼리타 웨이브 필터 155", "브랜드, 재질(무표백/표백) 등"),
        Label("scale", "저울", "저울", "예: 아카이아 펄", "용도, 특징 등"),
        Label("water", "물", "물", "예: 스파클 정수, 정수기 물", "특징, 어디서 구하는지 등"),
    ).associateBy { it.type }
    const val ALL_TITLE = "전체 장비"
    const val ALL_NAME = "장비"
}

object RoastLevels {
    val all: List<String> = listOf("라이트", "미디엄 라이트", "미디엄", "미디엄 다크", "다크")
}

object ScoreTiers {
    fun label(score: Double): String? = when {
        score >= 90 -> "최상급 경매급·명품급"
        score >= 87 -> "상당히 뛰어난 마이크로랏급"
        score >= 85 -> "고품질 스페셜티"
        score >= 80 -> "스페셜티 기준"
        else -> null
    }
}

object RoadmapDefaults {
    const val STARTER_ID = "starter"
    const val STARTER_TITLE = "나의 로드맵"
    const val STARTER_RANGE = "자유롭게 작성"
    const val STARTER_DAY_START = 0
    const val STARTER_DAY_END = 99999
}

/** Pin positions (percent of canvas) for the roastery "map" (web roasteryPoint). */
object RoasteryMapPoints {
    data class Point(val regex: Regex, val x: Float, val y: Float)
    val domestic: List<Point> = listOf(
        Point(Regex("서울|seoul"), 48f, 27f), Point(Regex("인천|incheon"), 38f, 30f), Point(Regex("경기|gyeonggi|수원"), 54f, 34f),
        Point(Regex("강원|gangwon|춘천"), 65f, 25f), Point(Regex("충북|충청북|chungbuk|cheongbuk"), 55f, 44f), Point(Regex("충남|충청남|대전|세종|daejeon"), 43f, 49f),
        Point(Regex("경북|경상북|대구|daegu"), 62f, 55f), Point(Regex("전북|전라북|jeonbuk"), 43f, 61f), Point(Regex("경남|경상남|부산|울산|busan"), 58f, 70f),
        Point(Regex("광주|전남|전라남|gwangju"), 40f, 73f), Point(Regex("제주|jeju"), 46f, 91f),
    )
    val domesticDefault = Point(Regex(""), 50f, 52f)
    val overseas: List<Point> = listOf(
        Point(Regex("미국|usa|united states|new york|portland|california"), 18f, 42f), Point(Regex("캐나다|canada"), 17f, 29f), Point(Regex("멕시코|mexico"), 22f, 57f),
        Point(Regex("스페인|spain|barcelona|madrid"), 45f, 47f), Point(Regex("영국|united kingdom|london"), 46f, 34f), Point(Regex("프랑스|france|paris"), 48f, 42f),
        Point(Regex("덴마크|denmark|copenhagen|노르웨이|norway|oslo"), 51f, 26f), Point(Regex("독일|germany|berlin"), 52f, 38f), Point(Regex("이탈리아|italy"), 53f, 48f),
        Point(Regex("일본|japan|tokyo|osaka|kyoto"), 82f, 44f), Point(Regex("대만|taiwan|taipei"), 78f, 54f), Point(Regex("중국|china|shanghai|beijing"), 73f, 43f),
        Point(Regex("호주|australia|melbourne|sydney"), 84f, 78f), Point(Regex("뉴질랜드|new zealand"), 92f, 84f), Point(Regex("태국|thailand|bangkok|싱가포르|singapore"), 74f, 66f),
    )
    val overseasDefault = Point(Regex(""), 50f, 54f)

    fun locate(location: String, domesticScope: Boolean, index: Int): Pair<Float, Float> {
        val key = location.lowercase()
        val table = if (domesticScope) domestic else overseas
        val hit = table.firstOrNull { it.regex.containsMatchIn(key) } ?: (if (domesticScope) domesticDefault else overseasDefault)
        val ring = index % 7
        return Pair(hit.x + ((ring % 3) - 1) * 3.5f, hit.y + ((ring / 3) - 1) * 5f)
    }
}
