// Generated from the web app reference data by scratchpad/site/gen_refs.py. Do not edit by hand.
package com.coffeejournal.domain.reference

object NoteCategories {
    data class Sub(val name: String, val keywords: List<String>)
    data class Category(val name: String, val colorHex: String, val subs: List<Sub>)

    val all: List<Category> = listOf(
        Category("과일 Fruity", "#c94f4f", listOf(
            Sub("베리류", listOf("베리", "블루베리", "라즈베리", "딸기")),
            Sub("감귤류", listOf("감귤", "오렌지", "자몽", "레몬", "라임")),
            Sub("열대과일", listOf("열대과일", "파인애플", "망고", "패션프루트", "용과", "코코넛", "리치", "망고스틴", "칸탈로프", "멜론", "수박", "구아바", "석류", "청사과", "파파야", "키위", "바나나")),
            Sub("씨과일", listOf("체리", "자두", "복숭아", "살구")),
            Sub("건과일", listOf("건포도", "무화과", "건자두")),
        )),
        Category("꽃 Floral", "#d98a8a", listOf(
            Sub("자스민", listOf("자스민", "재스민")),
            Sub("장미", listOf("장미")),
            Sub("카모마일", listOf("카모마일")),
            Sub("홍차 같은", listOf("홍차")),
            Sub("라벤더", listOf("라벤더")),
        )),
        Category("단맛 Sweet", "#c17a3e", listOf(
            Sub("바닐라", listOf("바닐라")),
            Sub("꿀", listOf("꿀")),
            Sub("흑설탕·갈색설탕", listOf("흑설탕", "갈색설탕", "갈색 설탕")),
            Sub("메이플시럽", listOf("메이플")),
            Sub("캐러멜", listOf("캐러멜")),
        )),
        Category("견과·코코아 Nutty/Cocoa", "#a87c4f", listOf(
            Sub("아몬드", listOf("아몬드")),
            Sub("헤이즐넛", listOf("헤이즐넛")),
            Sub("땅콩", listOf("땅콩")),
            Sub("다크초콜릿", listOf("다크초콜릿", "다크 초콜릿")),
            Sub("밀크초콜릿", listOf("밀크초콜릿", "밀크 초콜릿")),
            Sub("코코아", listOf("코코아")),
            Sub("고소함·구운견과류", listOf("고소함", "구운 견과류", "구운견과류")),
        )),
        Category("향신료 Spices", "#b58e3f", listOf(
            Sub("시나몬", listOf("시나몬")),
            Sub("정향", listOf("정향")),
            Sub("육두구", listOf("육두구")),
            Sub("후추", listOf("후추")),
        )),
        Category("로스팅 Roasted", "#6b5b48", listOf(
            Sub("토스트", listOf("토스트")),
            Sub("곡물", listOf("곡물")),
            Sub("담배", listOf("담배")),
            Sub("스모키", listOf("스모키")),
        )),
        Category("발효·신맛 Sour/Fermented", "#7d9470", listOf(
            Sub("와인", listOf("와인")),
            Sub("요거트", listOf("요거트")),
            Sub("발효과일", listOf("발효")),
            Sub("식초 느낌", listOf("식초")),
            Sub("은은한 산미", listOf("산미", "신맛")),
        )),
        Category("풋내·식물 Green/Vegetative", "#5c7a5e", listOf(
            Sub("풀", listOf("풀")),
            Sub("콩깍지", listOf("콩깍지")),
            Sub("허브", listOf("허브")),
            Sub("덜 익은 과일", listOf("덜 익은")),
        )),
        Category("기타 Other", "#7a94ad", listOf(
            Sub("종이", listOf("종이")),
            Sub("케미컬", listOf("케미컬")),
            Sub("흙", listOf("흙")),
            Sub("고무", listOf("고무")),
        )),
    )
}

/** Key = note text with all whitespace removed (web NOTE_SYNONYMS). */
object NoteSynonyms {
    val map: Map<String, String> = mapOf(
        "갈색설탕" to "브라운슈거",
        "사탕수수" to "케인슈거",
        "재스민" to "자스민",
        "쟈스민" to "자스민",
        "로즈메리" to "로즈마리",
        "만다린오렌지" to "만다린",
        "카라멜" to "캐러멜",
        "붉은열매" to "레드베리",
        "크리미바디" to "크림",
        "실키" to "크림",
        "실키/크리미바디" to "크림",
        "홍차같은티라이크함" to "홍차",
        "트로피컬" to "열대과일",
        "매우깨끗한애프터" to "클린",
        "쥬시" to "쥬시함",
        "스무스" to "부드러움",
        "둥글고시럽같은질감" to "시럽",
        "플럼" to "자두",
        "스파이시한후미" to "스파이시",
        "acacia" to "아카시아",
        "apple" to "사과",
        "blueberry" to "블루베리",
        "brownsugar" to "브라운슈거",
        "caramel" to "캐러멜",
        "chocolate" to "초콜릿",
        "citrus" to "시트러스",
        "clean" to "클린",
        "darkcherry" to "다크체리",
        "darkchocolate" to "다크 초콜릿",
        "floral" to "플로럴",
        "greengrape" to "청포도",
        "honey" to "꿀",
        "jasmine" to "자스민",
        "lavender" to "라벤더",
        "lime" to "라임",
        "orange" to "오렌지",
        "orangepeel" to "오렌지필",
        "peach" to "복숭아",
        "pineapple" to "파인애플",
        "plum" to "자두",
        "raspberry" to "라즈베리",
        "roastednuts" to "구운 견과류",
        "sugarcane" to "케인슈거",
        "syrup" to "시럽",
        "tropicalfruit" to "열대과일",
    )
}
