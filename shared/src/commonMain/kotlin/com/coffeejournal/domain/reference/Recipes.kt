// Generated from the web app reference data by scratchpad/site/gen_refs.py. CafeRecipes were then corrected by hand
// against the cafés' own published guides (each carries its sourceUrl); keep those corrections when regenerating.
package com.coffeejournal.domain.reference

import com.coffeejournal.domain.model.RecipeStep

object Champions {
    data class Champion(val year: Int, val name: String, val country: String, val dripper: String, val dose: Double, val water: Double, val temp: Int, val desc: String, val highlight: Boolean)

    /** World Brewers Cup champions, oldest first (web CHAMPIONS). */
    val all: List<Champion> = listOf(
        Champion(2016, "Tetsu Kasuya", "일본", "V60 (세라믹)", 20.0, 300.0, 92, "4:6 메소드의 창시자. 물을 40%/60%로 나눠 부어 앞쪽에서 산미, 뒤쪽에서 단맛과 농도를 조절하는 방식. 총 추출 3:00.", false),
        Champion(2017, "Chad Wang", "대만", "V60 (세라믹)", 15.0, 250.0, 92, "표준에 가까운 1:16.7 비율로 2:00에 짧게 추출을 마무리.", false),
        Champion(2018, "Emi Fukahori", "스위스", "GINA", 17.0, 220.0, 88, "80~95도 사이 온도를 오가며 침지(담가두기)와 드립을 섞은 하이브리드 방식. 2:55 추출.", false),
        Champion(2019, "Du Jianing", "중국", "오리가미", 16.0, 240.0, 94, "높은 온도로 짧게(1:40) 끊어서 밝고 클린한 산미를 강조.", false),
        Champion(2021, "Matt Winton", "스위스", "V60 (메탈)", 20.0, 300.0, 93, "93도와 88도 물을 섞어 쓰며 5회로 나눠 부음. 2:40 추출.", false),
        Champion(2022, "Sherry Hsu", "대만", "Orea V3 + Kalita 185", 14.0, 200.0, 70, "분쇄도를 두 가지(1000/800마이크론)로 섞어 사용, 낮은 온도(70도)로 첫 푸어 시작.", false),
        Champion(2023, "Carlos Medina", "칠레", "오리가미", 15.5, 250.0, 91, "내추럴 프로세스 콜롬비아 시드라 원두 사용. 50g씩 30초 간격으로 총 5회 나눠 부음. 오늘 마시는 브라질 내추럴과 가공방식이 같아서 참고하기 좋아요.", true),
        Champion(2024, "Martin Wölfl", "오스트리아", "Orea v4", 17.0, 270.0, 93, "WDT(교반 도구)로 분쇄 뭉침을 먼저 풀어준 뒤 뜸 60ml, 이후 여러 차례 나눠 부음.", false),
        Champion(2025, "George Peng", "중국", "SOLO 드리퍼", 15.0, 210.0, 96, "첫 푸어는 96도로 뜨겁게, 이후 80도로 낮춰서 후반부 떫은맛과 자극적인 느낌을 억제.", false),
    )
}

object CafeRecipes {
    data class Recipe(
        val id: String, val name: String, val place: String, val dripper: String, val filter: String?,
        val dose: String, val water: String, val temp: String?, val tempRange: String?, val time: String, val grind: String,
        val desc: String, val steps: List<RecipeStep>,
        /** The café's own page the numbers were checked against; null when the web template gave no source. */
        val sourceUrl: String? = null,
    )

    val all: List<Recipe> = listOf(
        Recipe("yourhome", "유어홈 (Your Home)", "", "", null, "15", "240", null, null, "", "15", "유어홈 앱 레시피. 원두 15g에 총 240g을 사용하며, 1차 50g과 2차 190g으로 나눠 추출해요.", listOf(
            RecipeStep("0:00", "50", "10", "1차 푸어"),
            RecipeStep("0:10", "", "40", "스월, 대기"),
            RecipeStep("0:50", "190", "30", "2차 푸어"),
            RecipeStep("1:20", "", "70", "드로우다운"),
        )),
        // shop.glitchcoffee.com/en/pages/brew-guide, ORIGAMI DRIP 【HOT DRIP】: Paper Karita Wave, 86-90℃, 14.5g, yield
        // 200-210g, ①0:00-0:05 30g ②0:20-0:25 30g ③0:50-1:05 100g ④1:20-1:35 100g (total 260g). No grind or total time.
        Recipe("glitch", "글리치 커피 (Glitch Coffee & Roasters)", "도쿄, 일본", "오리가미 드리퍼", "칼리타 웨이브 필터", "14.5", "260", "86", "86~90°C", "", "", "글리치 커피 공식 브루 가이드의 오리가미 핫 드립 레시피. 물 온도는 86~90도로 안내되는데, 낮은 쪽 기준으로 넣어뒀어요. 30·30·100·100g 네 번에 나눠 총 260g을 붓고 추출액 200~210g을 받아요. 가이드에 분쇄도와 전체 추출시간은 없어서, 단계는 마지막 붓기가 끝나는 1:35까지예요.", listOf(
            RecipeStep("0:00", "30", "5", "1차 푸어"),
            RecipeStep("0:05", "", "15", "대기"),
            RecipeStep("0:20", "30", "5", "2차 푸어 (누적 60g)"),
            RecipeStep("0:25", "", "25", "대기"),
            RecipeStep("0:50", "100", "15", "3차 푸어 (누적 160g)"),
            RecipeStep("1:05", "", "15", "대기"),
            RecipeStep("1:20", "100", "15", "4차 푸어 (누적 260g)"),
        ), sourceUrl = "https://shop.glitchcoffee.com/en/pages/brew-guide"),
        // kurasu.kyoto (2020): Coffee 13g, Water 200g, 90c~91c, "30g water 30sec bloom / Pour up to 100g / 60 sec /
        // Pour up to 200g". Pour speed, grind and total time are left to the beans.
        Recipe("kurasu", "큐라스 (Kurasu Kyoto)", "교토, 일본", "V60", null, "13", "200", "90", "90~91°C", "", "", "교토의 스페셜티 카페 큐라스가 매장에서 쓰는 기본 V60 레시피. 30g으로 30초 뜸을 들이고, 100g까지 부은 뒤 60초에 200g까지 채워요. 붓는 속도와 분쇄도, 전체 추출시간은 원두와 로스팅에 맞춰 조정한다고 안내돼요.", listOf(
            RecipeStep("0:00", "30", "30", "뜸 (30초)"),
            RecipeStep("0:30", "70", "30", "2차 푸어 (누적 100g)"),
            RecipeStep("1:00", "100", "", "3차 푸어 (누적 200g)"),
        ), sourceUrl = "https://kurasu.kyoto/blogs/kurasu-journal/how-to-brew-with-hario-v60-recipe-by-kurasu-kyoto"),
        // kurasu.kyoto (2024, Kurasu Fushimi Inari): Coffee 15g, Water 270g (88-90℃), 40g in the first 30 s, 130g slowly in
        // the next 30 s, all 270g poured before 80 s, then the dripper comes off. Produces 170-180g; fine grind (EK #6.5).
        Recipe("kurasu-origami", "큐라스 오리가미 (Kurasu)", "교토, 일본", "오리가미 드리퍼", null, "15", "270", "88", "88~90°C", "", "가늘게 (EK43 약 6.5)", "큐라스 후시미이나리점이 공개한 오리가미 레시피. 처음 30초 안에 40g, 다음 30초 동안 130g을 천천히 붓고, 1:20 전까지 270g을 모두 부은 뒤 물이 남아 있어도 드리퍼를 떼어내요. 추출액은 170~180g이고, V60보다 조금 가늘게 갈아요.", listOf(
            RecipeStep("0:00", "40", "30", "1차 푸어 (30초 안에)"),
            RecipeStep("0:30", "130", "30", "2차 푸어 · 천천히 (누적 170g)"),
            RecipeStep("1:00", "100", "20", "3차 푸어 (누적 270g, 1:20 전에 마무리)"),
            RecipeStep("1:20", "", "", "드리퍼 분리"),
        ), sourceUrl = "https://kurasu.kyoto/blogs/kurasu-journal/origami-dripper-brewing-recipe-how-we-do-it-at-kurasu"),
    )
}

object GenericSteps {
    /** Example pour schedule shown when no recipe is applied (web GENERIC_STEPS_EXAMPLE). */
    val example: List<RecipeStep> = listOf(
        RecipeStep("0:00", "30", "10", "1차 푸어"),
        RecipeStep("0:10", "", "30", "뜸들이기"),
        RecipeStep("0:40", "100", "15", "2차 푸어"),
        RecipeStep("0:55", "", "25", "1차 추출"),
        RecipeStep("1:20", "80", "15", "3차 푸어"),
        RecipeStep("1:35", "", "35", "마무리 추출"),
    )
}
