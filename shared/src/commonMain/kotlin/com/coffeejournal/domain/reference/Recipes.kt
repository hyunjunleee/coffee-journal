// Generated from the web app reference data by scratchpad/site/gen_refs.py. Do not edit by hand.
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
    )

    val all: List<Recipe> = listOf(
        Recipe("yourhome", "유어홈 (Your Home)", "", "", null, "15", "240", null, null, "", "15", "유어홈 앱 레시피. 원두 15g에 총 240g을 사용하며, 1차 50g과 2차 190g으로 나눠 추출해요.", listOf(
            RecipeStep("0:00", "50", "10", "1차 푸어"),
            RecipeStep("0:10", "", "40", "스월, 대기"),
            RecipeStep("0:50", "190", "30", "2차 푸어"),
            RecipeStep("1:20", "", "70", "드로우다운"),
        )),
        Recipe("glitch", "글리치 커피 (Glitch Coffee & Roasters)", "도쿄, 일본", "오리가미 드리퍼", null, "15", "240", "86", "86~93°C", "2:25", "", "글리치 커피 공식 레시피(Our Recipe). 물 온도는 86~93도 범위로 안내되는데, 낮은 쪽 기준으로 넣어뒀어요. 총 물량 240g을 붓고 최종 190~200g을 받아내는 방식이고, 2:20~2:30 사이에 마무리돼요.", listOf(
            RecipeStep("0:00", "40", "10", "1차 푸어"),
            RecipeStep("0:10", "", "25", "뜸들이기"),
            RecipeStep("0:35", "100", "15", "2차 푸어 (누적 140g)"),
            RecipeStep("0:50", "", "30", "1차 추출"),
            RecipeStep("1:20", "100", "15", "3차 푸어 (누적 240g)"),
            RecipeStep("1:35", "", "50", "마무리 추출"),
        )),
        Recipe("kurasu", "큐라스 (Kurasu Kyoto)", "교토, 일본", "V60", null, "13", "200", "91", null, "2:00", "중간 굵기", "교토의 스페셜티 카페 큐라스가 공개한 기본 V60 레시피. 라이트 로스트에 맞춘 표준형 3단계 붓기로, 원두나 로스팅에 따라 비율을 조금씩 조정해서 쓰는 걸 추천해요.", listOf(
            RecipeStep("0:00", "30", "5", "1차 푸어"),
            RecipeStep("0:05", "", "25", "뜸들이기"),
            RecipeStep("0:30", "100", "15", "2차 푸어"),
            RecipeStep("0:45", "", "15", "추출 대기"),
            RecipeStep("1:00", "70", "10", "3차 푸어"),
            RecipeStep("1:10", "", "50", "드로우다운"),
        )),
        Recipe("kurasu-origami-wave-15g", "큐라스 오리가미 웨이브 15g", "교토, 일본", "오리가미 드리퍼", "웨이브 필터", "15", "250", null, null, "1:30", "15 (클릭 기준)", "Kurasu Origami Wave 15g 레시피. 원두 15g에 물 250ml를 사용하고, 40ml·110ml·100ml로 세 번 나눠 부어요. 최종 추출액은 약 200ml, 표기 비율은 16ml/g이에요.", listOf(
            RecipeStep("0:00", "40", "10", "1차 푸어"),
            RecipeStep("0:10", "", "30", "뜸들이기"),
            RecipeStep("0:40", "110", "10", "2차 푸어 (누적 150ml)"),
            RecipeStep("0:50", "", "10", "대기"),
            RecipeStep("1:00", "100", "10", "3차 푸어 (누적 250ml)"),
            RecipeStep("1:10", "", "20", "마무리 추출"),
        )),
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
