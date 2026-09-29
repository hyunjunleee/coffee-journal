package com.coffeejournal.ui.form

import com.coffeejournal.domain.reference.Varieties

/**
 * The 품종 list: the web's reference varieties ([Varieties.reference], "티피카 (Typica)") and a few more that specialty
 * bags often name, each written in English as bags write it with its Korean name beside it. Picking one adds it to
 * the varieties already typed; the Heirloom selection numbers have a list of their own.
 */
object VarietyOptions {
    val varieties: List<Preset> by lazy {
        val reference = Varieties.reference.map { r ->
            val m = Regex("^(.*?)\\s*\\((.*)\\)\\s*$").find(r)
            when {
                m == null -> Preset(r)
                // "Heirloom (에티오피아 재래종)": the English name comes first there
                m.groupValues[1].all { it.code < 128 } -> Preset(m.groupValues[1].trim(), m.groupValues[2].trim(), listOf("에어룸", "재래종"))
                else -> Preset(m.groupValues[2].trim(), m.groupValues[1].trim())
            }
        }
        val more = listOf(
            "Sidra" to "시드라", "Wush Wush" to "우쉬우쉬", "Sudan Rume" to "수단 루메", "Eugenioides" to "유게니오이데스",
            "Mokka" to "모카", "Ethiopian Landrace" to "에티오피아 재래종", "Kurume" to "쿠루메", "Dega" to "데가", "Wolisho" to "월리쇼",
            "SL14" to "SL14", "K7" to "K7", "Blue Mountain" to "블루마운틴", "Tabi" to "타비", "Marsellesa" to "마르세예사",
            "Parainema" to "파라이네마", "Obata" to "오바타", "Arara" to "아라라", "Catucai" to "카투카이", "Sarchimor" to "사치모르",
            "Lempira" to "렘피라", "IHCAFE 90" to "IHCAFE 90", "Anacafe 14" to "아나카페 14", "Centroamericano" to "센트로아메리카노",
            "Tekisic" to "테키식", "Bourbon Aruzi" to "버번 아루지", "Robusta" to "로부스타", "Liberica" to "리베리카", "Excelsa" to "엑셀사",
        ).map { (en, ko) -> Preset(en, ko) }
        (reference + more).distinctBy { it.value.lowercase() }
    }

    /**
     * Selection numbers Ethiopian bags name after Heirloom: the Jimma Agricultural Research Center's releases and
     * the regional selections they are sold as. Any other number can be typed.
     */
    val heirloomNumbers: List<Preset> by lazy {
        listOf("74110", "74112", "74140", "74148", "74158", "74165", "75227").map { Preset(it, "에티오피아 선발 번호") }
    }
}
