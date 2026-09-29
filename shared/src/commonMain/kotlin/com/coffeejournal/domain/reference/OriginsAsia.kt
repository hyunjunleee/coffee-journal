package com.coffeejournal.domain.reference

import com.coffeejournal.domain.reference.OriginRegions.Origin
import com.coffeejournal.domain.reference.OriginRegions.Place

/** [OriginRegions] of Asia, Oceania and Yemen: one private function per country, each with its sources above it. */
internal object OriginsAsia {
    val all: List<Origin> = listOf(
        indonesia(), vietnam(), india(), china(), thailand(), myanmar(), laos(), papuaNewGuinea(), philippines(),
        taiwan(), timorLeste(), yemen(),
    )

    // source: Cafe Imports "Sumatra"; Wikipedia "Coffee production in Indonesia", "Central Aceh Regency", "Takengon";
    // kopipohon.com Indonesian coffee origins (Gayo, Lintong, Sidikalang, Dolok Sanggul, Kerinci, Java Ijen, Java
    // Preanger, Toraja, Kalosi Enrekang, Bajawa Flores). Korean: common roaster usage (만델링, 가요, 토라자, 킨타마니).
    // coords: enwiki island articles (Sumatra, Java, Sulawesi, Bali, Flores).
    private fun indonesia() = Origin(
        "Indonesia",
        listOf(
            Place(
                "수마트라", "Sumatra", listOf("Sumatra (Mandheling)", "Sumatera", "수마트라 만델링"),
                listOf(
                    Place(
                        "아체", "Aceh", listOf("아쩨"),
                        listOf(
                            Place("가요", "Gayo", listOf("Gayo Highlands", "Gayo Mountain", "가요 마운틴")),
                            Place("타켕온", "Takengon", listOf("타케곤")),
                            Place("베네르 메리아", "Bener Meriah"),
                        ),
                    ),
                    Place(
                        "북수마트라", "North Sumatra", listOf("노스 수마트라", "Sumatera Utara"),
                        listOf(
                            Place("린통", "Lintong", listOf("Lintongnihuta", "린통니후타")),
                            Place("시디칼랑", "Sidikalang"),
                            Place("돌록 상굴", "Dolok Sanggul"),
                        ),
                    ),
                    Place("만델링", "Mandheling", listOf("Mandailing", "만데링")),
                    Place("케린치", "Kerinci", listOf("Kerinchi")),
                ),
                lat = 0.0, lng = 102.0,
            ),
            Place(
                "자바", "Java", listOf("Jawa"),
                listOf(
                    Place("이젠", "Ijen", listOf("Kawah Ijen", "Ijen Plateau")),
                    Place("웨스트 자바", "West Java", listOf("Jawa Barat", "Preanger", "Priangan", "프리앙안")),
                ),
                lat = -7.49, lng = 110.0,
            ),
            Place(
                "술라웨시", "Sulawesi", listOf("Sulawesi (Toraja)", "Celebes", "셀레베스"),
                listOf(
                    Place("토라자", "Toraja", listOf("Tana Toraja", "타나 토라자")),
                    Place("칼로시", "Kalosi", listOf("Enrekang", "엔레캉")),
                ),
                lat = -2.0, lng = 121.0,
            ),
            Place("발리", "Bali", subs = listOf(Place("킨타마니", "Kintamani")), lat = -8.34, lng = 115.09),
            Place(
                "플로레스", "Flores",
                subs = listOf(
                    Place("바자와", "Bajawa", listOf("Ngada")),
                    Place("망가라이", "Manggarai", listOf("Ruteng")),
                ),
                lat = -8.67, lng = 121.38,
            ),
        ),
    )

    // source: JayArr "Vietnam coffee origin guide"; Heritage (Vietnam Airlines) "Lam Dong and valleys of Arabica";
    // hello5coffee "Cau Dat coffee". Province names as before the 2025 mergers, as the trade still uses them.
    // coords: enwiki province articles.
    private fun vietnam() = Origin(
        "Vietnam",
        listOf(
            Place(
                "닥락", "Dak Lak", listOf("Đắk Lắk", "Daklak", "다크락"),
                listOf(Place("부온마투옷", "Buon Ma Thuot", listOf("Buôn Ma Thuột", "Ban Me Thuot", "반메투옷"))),
                lat = 12.67, lng = 108.05,
            ),
            Place(
                "람동", "Lam Dong", listOf("Lâm Đồng", "럼동"),
                listOf(
                    Place("달랏", "Da Lat", listOf("Đà Lạt", "Dalat")),
                    Place("까우닷", "Cau Dat", listOf("Cầu Đất", "꺼우덧")),
                ),
                lat = 11.95, lng = 108.43,
            ),
            Place("지아라이", "Gia Lai", listOf("잘라이"), lat = 13.75, lng = 108.25),
            Place("선라", "Son La", listOf("Sơn La", "손라"), lat = 21.17, lng = 104.0),
        ),
    )

    // source: Coffee Board of India, 13 regional coffees (coffeeboard.gov.in; PIB "India's Coffee Story from Farm to Cup").
    // coords: enwiki — Chikmagalur, Kodagu district, Baba Budangiri, Sakleshpur (Manjarabad), Biligiriranga Hills,
    // Wayanad district, Idukki district (Travancore), Nilgiris district, Palani Hills, Servarayan Hills, Anaimalai
    // Hills, Araku Valley, Karbi Anglong district (Brahmaputra).
    private fun india() = Origin(
        "India",
        listOf(
            Place("치크마갈루르", "Chikmagalur", listOf("치크마갈루루", "Chikkamagaluru"), lat = 13.33, lng = 75.79),
            Place("쿠르그", "Coorg", listOf("코다구", "Kodagu"), lat = 12.42, lng = 75.74),
            Place("바바부단기리", "Bababudangiris", listOf("Bababudangiri", "Baba Budan Giri", "바바 부단"), lat = 13.42, lng = 75.76),
            Place("만자라바드", "Manjarabad", lat = 12.89, lng = 75.72),
            Place("빌리기리", "Biligiris", listOf("Biligiri Rangan Hills", "BR Hills"), lat = 11.99, lng = 77.14),
            Place("와야나드", "Wayanad", listOf("Wayanaad"), lat = 11.63, lng = 76.09),
            Place("트라반코르", "Travancore", lat = 9.85, lng = 76.94),
            Place("닐기리", "Nilgiris", listOf("Nilgiri", "닐기리스"), lat = 11.4, lng = 76.7),
            Place("풀니", "Pulneys", listOf("Pulney Hills", "Palani Hills", "팔라니"), lat = 10.2, lng = 77.47),
            Place("셰바로이", "Shevaroys", listOf("Sheveroys", "Yercaud"), lat = 11.83, lng = 78.27),
            Place("아나말라이", "Anamalais", listOf("Anamalai"), lat = 10.17, lng = 77.06),
            Place("아라쿠 밸리", "Araku Valley", listOf("Araku", "아라쿠"), lat = 18.33, lng = 82.87),
            Place("브라마푸트라", "Brahmaputra", lat = 26.18, lng = 93.57),
        ),
    )

    // source: Sucafina "China"; GoKunming "Coffee trade in Yunnan" (Pu'er, Baoshan, Dehong, Lincang, Xishuangbanna).
    // coords: enwiki Pu'er City (about half of Yunnan's coffee).
    private fun china() = Origin(
        "China",
        listOf(
            Place(
                "윈난", "Yunnan", listOf("운남"),
                listOf(
                    Place("푸얼", "Pu'er", listOf("Puer", "보이", "Simao", "쓰마오")),
                    Place("바오산", "Baoshan", listOf("보산")),
                    Place("린창", "Lincang", listOf("임창")),
                    Place("더훙", "Dehong", listOf("덕굉")),
                    Place("시솽반나", "Xishuangbanna", listOf("서쌍판납")),
                ),
                lat = 22.79, lng = 100.98,
            ),
        ),
    )

    // source: Sprudge "Guide to coffee in Chiang Rai"; Wikipedia "Doi Tung"; Helena Coffee "Doi Chang"; Thai coffee
    // region notes (Mae Hong Son). Doi Wawee: common trade usage.
    // coords: enwiki province articles.
    private fun thailand() = Origin(
        "Thailand",
        listOf(
            Place("치앙마이", "Chiang Mai", listOf("Chiangmai"), lat = 18.84, lng = 98.97),
            Place(
                "치앙라이", "Chiang Rai", listOf("Chiangrai"),
                listOf(
                    Place("도이창", "Doi Chang", listOf("Doi Chaang", "도이 창")),
                    Place("도이뚱", "Doi Tung", listOf("도이 퉁")),
                    Place("도이 와위", "Doi Wawee"),
                ),
                lat = 19.9, lng = 99.82,
            ),
            Place("매홍손", "Mae Hong Son", lat = 19.29, lng = 97.96),
        ),
    )

    // source: Sucafina "Ywangan fully washed"; JayArr "Myanmar coffee: Shan State"; Wikipedia "Pyin Oo Lwin".
    // coords: enwiki — Ywangan Township (Shan State), Pyin Oo Lwin (Mandalay Region).
    private fun myanmar() = Origin(
        "Myanmar",
        listOf(
            Place("샨주", "Shan State", listOf("샨 주", "Shan"), listOf(Place("이왕안", "Ywangan")), lat = 21.16, lng = 96.44),
            Place("만달레이", "Mandalay Region", listOf("Mandalay"), listOf(Place("핀우른", "Pyin Oo Lwin", listOf("Pyin U Lwin", "Maymyo", "메이묘"))), lat = 22.03, lng = 96.46),
        ),
    )

    // source: Wikipedia "Coffee production in Laos", "Paksong"; laoscoffee.org "Bolaven Plateau".
    // coords: enwiki Bolaven Plateau.
    private fun laos() = Origin(
        "Laos",
        listOf(
            Place("볼라벤 고원", "Bolaven Plateau", listOf("볼라벤", "Bolaven"), listOf(Place("팍송", "Paksong", listOf("Pakxong"))), lat = 15.0, lng = 106.0),
        ),
    )

    // source: PNG Coffee Industry Corporation "coffee growing areas" (cic.org.pg); Wikipedia "Eastern Highlands Province"
    // (districts), "Jiwaka Province", "Western Highlands Province"; Perfect Daily Grind "A guide to Papua New Guinea's
    // coffee sector".
    // coords: enwiki province articles.
    private fun papuaNewGuinea() = Origin(
        "Papua New Guinea",
        listOf(
            Place(
                "이스턴 하이랜드", "Eastern Highlands", listOf("동부 고원주", "EHP"),
                listOf(
                    Place("고로카", "Goroka"),
                    Place("카이난투", "Kainantu"),
                    Place("오카파", "Okapa"),
                ),
                lat = -6.07, lng = 145.39,
            ),
            Place(
                "웨스턴 하이랜드", "Western Highlands", listOf("서부 고원주", "WHP"),
                listOf(
                    Place("마운트 하겐", "Mount Hagen", listOf("Mt. Hagen", "하겐")),
                    Place("와기 밸리", "Wahgi Valley", listOf("Waghi Valley", "Waghi")),
                ),
                lat = -5.67, lng = 144.5,
            ),
            Place("지와카", "Jiwaka", subs = listOf(Place("반즈", "Banz")), lat = -6.0, lng = 144.58),
            Place("심부", "Simbu", listOf("Chimbu", "침부"), lat = -6.43, lng = 145.0),
            Place("모로베", "Morobe", lat = -6.83, lng = 146.67),
        ),
    )

    // source: ECHOstore "Coffee Country: Philippine coffee regions"; Wikipedia "Benguet coffee", "Sagada coffee".
    // coords: enwiki — Batangas, La Trinidad (Benguet), Sagada, Mount Apo.
    private fun philippines() = Origin(
        "Philippines",
        listOf(
            Place("바탕가스", "Batangas", lat = 13.83, lng = 121.0),
            Place("벵겟", "Benguet", lat = 16.46, lng = 120.59),
            Place("사가다", "Sagada", listOf("Mountain Province"), lat = 17.08, lng = 120.9),
            Place("아포산", "Mount Apo", listOf("Mt. Apo", "마운트 아포"), lat = 6.99, lng = 125.27),
        ),
    )

    // source: Taiwan Agriculture Tourism "Exploring hidden coffee treasures" (Gukeng, Dongshan); TGC Yunlin Gukeng.
    // coords: enwiki — Alishan (Chiayi), Gukeng, Dongshan District (Tainan).
    private fun taiwan() = Origin(
        "Taiwan",
        listOf(
            Place("아리산", "Alishan", listOf("알리산", "Ali Mountain"), lat = 23.35, lng = 120.8),
            Place("구컹", "Gukeng", listOf("Yunlin", "윈린"), lat = 23.65, lng = 120.57),
            Place("둥산", "Dongshan", listOf("Tainan Dongshan"), lat = 23.28, lng = 120.44),
        ),
    )

    // source: Royal Coffee "Timor-Leste Ermera Letefoho"; Wikipedia "Ermera Municipality"; Tourism Timor-Leste coffee
    // (Maubisse, Aileu, Ainaro). Hatulia, Railaco: common trade usage.
    // coords: enwiki municipality articles.
    private fun timorLeste() = Origin(
        "Timor-Leste",
        listOf(
            Place(
                "에르메라", "Ermera",
                subs = listOf(
                    Place("레테포호", "Letefoho"),
                    Place("하툴리아", "Hatulia"),
                    Place("라일라코", "Railaco"),
                ),
                lat = -8.83, lng = 125.38,
            ),
            Place("아일레우", "Aileu", lat = -8.72, lng = 125.57),
            Place("아이나로", "Ainaro", subs = listOf(Place("마우비세", "Maubisse")), lat = -9.08, lng = 125.48),
        ),
    )

    // source: Hamdan Coffee "Yemeni coffee regions explained"; Coffeeness / JayArr Yemen guides (Hayma Dakhiliya, Hayma
    // Kharijiya, Bani Matar, Bani Ismail, Haraaz, Yafa, Khawlan, Anis, Sanani); Korean: 커피 리브레 (하라즈), 빈브라더스
    // (마나카 하라즈), 카페뮤제오 (모카 마타리).
    // coords: enwiki — Jabal Haraz, Bani Matar district, Al Haymah Ad Dakhiliyah district, Bani Ismail, Yafa'a, Sanaa,
    // Khawlan district, Jahran district (Anis).
    private fun yemen() = Origin(
        "Yemen",
        listOf(
            Place("하라즈", "Haraz", listOf("Haraaz", "Harazi", "하라지"), listOf(Place("마나카", "Manakha")), lat = 15.17, lng = 43.75),
            Place("바니 마타르", "Bani Matar", listOf("Bani Mattar", "Mattari", "Matari", "마타리"), lat = 15.17, lng = 44.08),
            Place(
                "하이마", "Hayma", listOf("Haymah", "Al Haymah", "Haimi", "하이미"),
                listOf(
                    Place("하이마 다킬리야", "Hayma Dakhiliya", listOf("Al Haymah Ad Dakhiliyah")),
                    Place("하이마 카리지야", "Hayma Kharijiya", listOf("Al Haymah Al Kharijiyah")),
                ),
                lat = 15.17, lng = 43.83,
            ),
            Place("바니 이스마일", "Bani Ismail", listOf("Ismaili", "이스마일리"), lat = 15.21, lng = 43.64),
            Place("야파이", "Yafa'i", listOf("Yafa", "Yafai", "Yafei", "야파"), lat = 13.65, lng = 45.22),
            Place("사나", "Sana'a", listOf("Sanani", "사나니"), lat = 15.35, lng = 44.21),
            Place("카울란", "Khawlan", lat = 15.27, lng = 44.77),
            Place("아니스", "Anis", lat = 14.73, lng = 44.31),
        ),
    )
}
