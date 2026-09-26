// Generated from the web app reference data by scratchpad/site/gen_refs.py. Do not edit by hand.
package com.coffeejournal.domain.reference

object Processes {
    data class Process(val name: String, val en: String?, val seg: String?)

    /** 4 main methods shown as cards. */
    val main4: List<Process> = listOf(
        Process("워시드", "Washed", "워시드"),
        Process("내추럴", "Natural", "내추럴"),
        Process("허니", "Honey", "허니"),
        Process("아나로빅", "Anaerobic", "아나로빅"),
    )

    /** Other methods shown in the "more" table. */
    val etc: List<Process> = listOf(
        Process("카보닉 마세레이션", "Carbonic Maceration", null),
        Process("웻헐드", "Giling Basah", null),
        Process("디카페인", "Decaf", null),
        Process("기타 (앱에서 \"기타\"로 기록한 것)", null, "기타"),
    )

    /** Segment options of the record form (web #f-process). */
    val formSegments: List<String> = listOf("내추럴", "워시드", "허니", "아나로빅", "기타")
    val mainSegments: List<String> = listOf("내추럴", "워시드", "허니", "아나로빅")

    data class HoneySub(val label: String, val regex: Regex)
    val honeySubtypes: List<HoneySub> = listOf(
        HoneySub("블랙 허니", Regex("블랙\\s*허니|black\\s*honey", RegexOption.IGNORE_CASE)),
        HoneySub("레드 허니", Regex("레드\\s*허니|red\\s*honey", RegexOption.IGNORE_CASE)),
        HoneySub("옐로 허니", Regex("옐로(?:우)?\\s*허니|yellow\\s*honey", RegexOption.IGNORE_CASE)),
        HoneySub("화이트 허니", Regex("화이트\\s*허니|white\\s*honey", RegexOption.IGNORE_CASE)),
        HoneySub("골든 허니", Regex("골든\\s*허니|golden\\s*honey", RegexOption.IGNORE_CASE)),
    )
}
