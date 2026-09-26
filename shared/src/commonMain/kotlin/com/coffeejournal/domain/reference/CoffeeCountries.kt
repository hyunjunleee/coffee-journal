// Generated from the web app reference data by scratchpad/site/gen_refs.py. Do not edit by hand.
package com.coffeejournal.domain.reference

object CoffeeCountries {
    data class Region(val name: String, val x: Float, val y: Float)
    data class Country(val en: String, val ko: String, val flag: String, val regions: List<Region>)

    /** 45 producing countries in the web's insertion order (order matters for lookup). */
    val all: List<Country> = listOf(
        Country("Brazil", "브라질", "🇧🇷", listOf(Region("Cerrado", 354.7f, 316.7f), Region("Sul de Minas", 358.7f, 324.7f), Region("Mogiana", 354.7f, 323.4f))),
        Country("Colombia", "콜롬비아", "🇨🇴", listOf(Region("Huila", 278.7f, 260.7f), Region("Nariño", 273.9f, 264.2f), Region("Antioquia", 278.7f, 250f))),
        Country("Peru", "페루", "🇵🇪", listOf(Region("Cajamarca", 270.7f, 286f))),
        Country("Ecuador", "에콰도르", "🇪🇨", listOf(Region("Loja", 268.8f, 278f))),
        Country("Bolivia", "볼리비아", "🇧🇴", listOf(Region("Caranavi", 299.7f, 309.5f))),
        Country("Mexico", "멕시코", "🇲🇽", listOf(Region("Chiapas", 232f, 222.7f))),
        Country("Guatemala", "과테말라", "🇬🇹", listOf(Region("Antigua", 238.1f, 228.6f), Region("Huehuetenango", 236f, 226.6f))),
        Country("Honduras", "온두라스", "🇭🇳", listOf(Region("Marcala", 245.2f, 229.6f))),
        Country("El Salvador", "엘살바도르", "🇸🇻", listOf(Region("Santa Ana", 241.2f, 230f))),
        Country("Nicaragua", "니카라과", "🇳🇮", listOf(Region("Jinotega", 250.7f, 232.4f))),
        Country("Costa Rica", "코스타리카", "🇨🇷", listOf(Region("Tarrazú", 256f, 241.6f))),
        Country("Panama", "파나마", "🇵🇦", listOf(Region("Boquete", 260.2f, 244f))),
        Country("Jamaica", "자메이카", "🇯🇲", listOf(Region("Blue Mountains", 275.7f, 219.2f))),
        Country("Dominican Rep.", "도미니카공화국", "🇩🇴", listOf(Region("Jarabacoa", 291.7f, 216.4f))),
        Country("Cuba", "쿠바", "🇨🇺", listOf(Region("Sierra Maestra", 276f, 214f))),
        Country("Haiti", "아이티", "🇭🇹", listOf(Region("Massif de la Selle", 287.7f, 218.4f))),
        Country("Venezuela", "베네수엘라", "🇻🇪", listOf(Region("Táchira", 287.5f, 246.6f))),
        Country("Puerto Rico", "푸에르토리코", "🇵🇷", listOf(Region("Yauco", 301.7f, 219.3f))),
        Country("Ethiopia", "에티오피아", "🇪🇹", listOf(Region("Yirgacheffe", 580.5f, 252.8f), Region("Sidamo", 584.5f, 250.5f), Region("Guji", 586.5f, 254.5f), Region("Harrar", 594.5f, 242.6f))),
        Country("Kenya", "케냐", "🇰🇪", listOf(Region("Nyeri", 577f, 267.5f), Region("Kirinyaga", 581f, 270f))),
        Country("Rwanda", "르완다", "🇷🇼", listOf(Region("Huye", 559.3f, 274.3f))),
        Country("Burundi", "부룬디", "🇧🇮", listOf(Region("Kayanza", 559f, 275.2f))),
        Country("Tanzania", "탄자니아", "🇹🇿", listOf(Region("Kilimanjaro", 579.6f, 276.4f), Region("Mbeya", 569.2f, 291.1f))),
        Country("Uganda", "우간다", "🇺🇬", listOf(Region("Mount Elgon", 572f, 264.4f))),
        Country("Dem. Rep. Congo", "콩고민주공화국", "🇨🇩", listOf(Region("Kivu", 557.3f, 274f))),
        Country("Zambia", "잠비아", "🇿🇲", listOf(Region("Nyika Plateau", 569.6f, 295.4f))),
        Country("Malawi", "말라위", "🇲🇼", listOf(Region("Mzuzu", 570.7f, 297.9f))),
        Country("Zimbabwe", "짐바브웨", "🇿🇼", listOf(Region("Chipinge", 566.9f, 321.2f))),
        Country("Cameroon", "카메룬", "🇨🇲", listOf(Region("West Region", 507.7f, 252.7f))),
        Country("Côte d'Ivoire", "코트디부아르", "🇨🇮", listOf(Region("Man", 459.9f, 247.6f))),
        Country("Madagascar", "마다가스카르", "🇲🇬", listOf(Region("Antsirabe", 605.4f, 320.4f))),
        Country("Indonesia", "인도네시아", "🇮🇩", listOf(Region("Sumatra (Mandheling)", 744f, 260.7f), Region("Java", 773.3f, 287.4f), Region("Sulawesi (Toraja)", 800f, 275.4f), Region("Bali", 787.2f, 289.8f), Region("Flores", 803.5f, 290.3f))),
        Country("Vietnam", "베트남", "🇻🇳", listOf(Region("Dak Lak", 768.5f, 233.5f))),
        Country("India", "인도", "🇮🇳", listOf(Region("Chikmagalur", 682.1f, 231.9f), Region("Coorg", 682.1f, 234.6f))),
        Country("China", "중국", "🇨🇳", listOf(Region("Yunnan", 749.3f, 202f))),
        Country("Thailand", "태국", "🇹🇭", listOf(Region("Chiang Mai", 743.7f, 217.2f))),
        Country("Myanmar", "미얀마", "🇲🇲", listOf(Region("Shan State", 740f, 210.6f))),
        Country("Laos", "라오스", "🇱🇦", listOf(Region("Bolaven Plateau", 763.5f, 227.1f))),
        Country("Papua New Guinea", "파푸아뉴기니", "🇵🇬", listOf(Region("Eastern Highlands", 867.7f, 283.6f))),
        Country("Philippines", "필리핀", "🇵🇭", listOf(Region("Batangas", 802.7f, 230.3f))),
        Country("Taiwan", "대만", "🇹🇼", listOf(Region("Alishan", 802.1f, 204.7f))),
        Country("Timor-Leste", "동티모르", "🇹🇱", listOf(Region("Ermera", 814.4f, 290.7f))),
        Country("Yemen", "예멘", "🇾🇪", listOf(Region("Haraz", 596f, 227.4f))),
        Country("Guinea", "기니", "🇬🇳", listOf(Region("Nzérékoré", 456.5f, 246.7f))),
        Country("Guyana", "가이아나", "🇬🇾", listOf(Region("Rupununi", 321.3f, 258f))),
    )

    val byEn: Map<String, Country> = all.associateBy { it.en }

    /** Korean region name -> canonical English region (web REGION_SYNONYMS). */
    val regionSynonyms: Map<String, String> = mapOf(
        "예가체프" to "Yirgacheffe",
        "이르가체프" to "Yirgacheffe",
        "시다모" to "Sidamo",
        "시다마" to "Sidamo",
        "구지" to "Guji",
        "하라" to "Harrar",
        "하레르" to "Harrar",
        "하라르" to "Harrar",
        "니에리" to "Nyeri",
        "키리냐가" to "Kirinyaga",
        "우일라" to "Huila",
        "후일라" to "Huila",
        "나리뇨" to "Nariño",
        "안티오키아" to "Antioquia",
        "세하도" to "Cerrado",
        "술데미나스" to "Sul de Minas",
        "모지아나" to "Mogiana",
        "안티구아" to "Antigua",
        "우에우에테낭고" to "Huehuetenango",
        "타라주" to "Tarrazú",
        "보케테" to "Boquete",
        "블루마운틴" to "Blue Mountains",
        "치아파스" to "Chiapas",
        "킬리만자로" to "Kilimanjaro",
        "음베야" to "Mbeya",
    )

    /** lowercase alias -> lowercase canonical country (web COUNTRY_SYNONYMS). */
    val countrySynonyms: Map<String, String> = mapOf(
        "columbia" to "colombia",
        "tanzania, united republic of" to "tanzania",
        "lao pdr" to "laos",
        "lao people's democratic republic" to "laos",
        "burma" to "myanmar",
        "ivory coast" to "côte d'ivoire",
        "cote d'ivoire" to "côte d'ivoire",
        "congo-kinshasa" to "dem. rep. congo",
        "dr congo" to "dem. rep. congo",
        "drc" to "dem. rep. congo",
    )

    /** English country -> zone label (아메리카 / 아프리카 / 아시아·오세아니아). */
    val zones: Map<String, String> = mapOf(
        "Brazil" to "아메리카",
        "Colombia" to "아메리카",
        "Peru" to "아메리카",
        "Ecuador" to "아메리카",
        "Bolivia" to "아메리카",
        "Mexico" to "아메리카",
        "Guatemala" to "아메리카",
        "Honduras" to "아메리카",
        "El Salvador" to "아메리카",
        "Nicaragua" to "아메리카",
        "Costa Rica" to "아메리카",
        "Panama" to "아메리카",
        "Jamaica" to "아메리카",
        "Dominican Rep." to "아메리카",
        "Cuba" to "아메리카",
        "Haiti" to "아메리카",
        "Venezuela" to "아메리카",
        "Puerto Rico" to "아메리카",
        "Guyana" to "아메리카",
        "Ethiopia" to "아프리카",
        "Kenya" to "아프리카",
        "Rwanda" to "아프리카",
        "Burundi" to "아프리카",
        "Tanzania" to "아프리카",
        "Uganda" to "아프리카",
        "Dem. Rep. Congo" to "아프리카",
        "Zambia" to "아프리카",
        "Malawi" to "아프리카",
        "Zimbabwe" to "아프리카",
        "Cameroon" to "아프리카",
        "Côte d'Ivoire" to "아프리카",
        "Madagascar" to "아프리카",
        "Guinea" to "아프리카",
        "Indonesia" to "아시아·오세아니아",
        "Vietnam" to "아시아·오세아니아",
        "India" to "아시아·오세아니아",
        "China" to "아시아·오세아니아",
        "Thailand" to "아시아·오세아니아",
        "Myanmar" to "아시아·오세아니아",
        "Laos" to "아시아·오세아니아",
        "Papua New Guinea" to "아시아·오세아니아",
        "Philippines" to "아시아·오세아니아",
        "Taiwan" to "아시아·오세아니아",
        "Timor-Leste" to "아시아·오세아니아",
        "Yemen" to "아시아·오세아니아",
    )

    val zoneOrder: List<String> = listOf("아프리카", "아메리카", "아시아·오세아니아")
    val zoneEnglish: Map<String, String> = mapOf("아시아·오세아니아" to "Asia · Oceania", "아프리카" to "Africa", "아메리카" to "Americas", "기타" to "Other")

    data class ZoneProducers(val zone: String, val countries: List<String>)
    val majorProducersByZone: List<ZoneProducers> = listOf(
        ZoneProducers("아메리카", listOf("Colombia", "Panama", "Costa Rica", "Guatemala", "Honduras", "El Salvador", "Brazil", "Peru")),
        ZoneProducers("아프리카", listOf("Ethiopia", "Kenya", "Rwanda", "Burundi")),
        ZoneProducers("아시아·오세아니아", listOf("Yemen", "Indonesia", "Papua New Guinea")),
    )
}
