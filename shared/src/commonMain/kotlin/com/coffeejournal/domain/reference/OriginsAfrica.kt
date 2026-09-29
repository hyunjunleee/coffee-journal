package com.coffeejournal.domain.reference

import com.coffeejournal.domain.reference.OriginRegions.Origin
import com.coffeejournal.domain.reference.OriginRegions.Place

/** [OriginRegions] of Africa: one private function per country, each with its sources above it. */
internal object OriginsAfrica {
    val all: List<Origin> = listOf(
        ethiopia(), kenya(), rwanda(), burundi(), tanzania(), uganda(), drCongo(), zambia(), malawi(), zimbabwe(),
        cameroon(), coteDIvoire(), madagascar(), guinea(),
    )

    // source: Wikipedia "Sidama Region" and "Guji Zone" (woreda lists); Trabocca "Yirgacheffe", "Aricha" and "Adado";
    // Royal Coffee "Ethiopia: Coffee's Heirloom" and Guji Hambela offers (Buku Sayisa, Dimtu); Daye Bensa (Shantawene,
    // Bombe, Keramo, Hamasho); Alo Coffee / Origin Coffee producer profile (Alo, Bensa); SNAP Coffee and The Coffee Quest
    // (Nensebo: Refisa, Riripa); Red Fox / Nemesis (Uraga: Yabitu Koba); Sucafina / Ally Coffee (Koke). Korean: 커피 리브레
    // and 코빈즈 Ethiopia listings (벤사, 코코세, 봄베, 아르베고나, 보체사, 루무다모, 하마쇼, 치리, 게뎁, 코체레, 넨세보,
    // 우라가, 함벨라, 리무, 카파, 벤치 마지), 달구네커피 (치레), 블로트커피 (보나 주리아), 원더룸 / HYTTE (부쿠 사이사).
    // coords: enwiki — Irgachefe (Yirgacheffe town), Sidama Region, Guji Zone, Harar, Jimma, Limmu Kosa, Bench Sheko,
    // Tepi, Nekemte (Lekempti), Gimbi, Illubabor Zone; Wikidata West Arsi Zone (Q6872255), Keffa Zone (Q6383412).
    private fun ethiopia() = Origin(
        "Ethiopia",
        listOf(
            Place(
                "예가체프", "Yirgacheffe", listOf("이르가체프", "Yirga Cheffe", "Yirgachefe", "게데오", "Gedeo"),
                listOf(
                    Place(
                        "예가체프 워레다", "Yirgacheffe Woreda", listOf("Yirga Chefe", "Yirgacheffe Town", "예가체프 타운"),
                        listOf(
                            Place("아리차", "Aricha"),
                            Place("이디도", "Idido", listOf("Edido", "에디도")),
                            Place("콩가", "Konga"),
                            Place("코케", "Koke"),
                            Place("게르시", "Gersi"),
                        ),
                    ),
                    Place("코체레", "Kochere", listOf("Kochore", "코초레"), listOf(Place("첼렐렉투", "Chelelektu"))),
                    Place(
                        "게뎁", "Gedeb", listOf("게데브"),
                        listOf(
                            Place("워카", "Worka", listOf("Werka")),
                            Place("워카 사카로", "Worka Sakaro"),
                            Place("첼베사", "Chelbesa", listOf("Worka Chelbesa", "워카 첼베사")),
                            Place("반코 고티티", "Banko Gotiti", listOf("Gotiti", "고티티")),
                            Place("첼첼레", "Chelchele"),
                            Place("할로 베리티", "Halo Beriti", listOf("Halo Bariti", "할로 바리티")),
                        ),
                    ),
                    Place("불레", "Bule", subs = listOf(Place("아다도", "Adado"))),
                    Place("웨나고", "Wenago", listOf("Wonago", "워나고")),
                    Place("딜라 주리아", "Dilla Zuria", listOf("Dilla", "딜라")),
                ),
                lat = 6.17, lng = 38.2,
            ),
            Place(
                "시다모", "Sidamo", listOf("시다마", "Sidama"),
                listOf(
                    Place(
                        "벤사", "Bensa",
                        subs = listOf(
                            Place("코코세", "Kokose"),
                            Place("봄베", "Bombe"),
                            Place("샨타웨네", "Shantawene", listOf("Shanta Wene", "샨타 웨네")),
                            Place("케라모", "Keramo", listOf("Karamo", "카라모")),
                            Place("하마쇼", "Hamasho"),
                            Place("알로", "Alo"),
                        ),
                    ),
                    Place("아로레사", "Aroresa"),
                    Place(
                        "아르베고나", "Arbegona",
                        subs = listOf(
                            Place("보체사", "Bochesa", listOf("Bochessa")),
                            Place("루무다모", "Rumudamo"),
                        ),
                    ),
                    Place("보나 주리아", "Bona Zuria", listOf("보나 조리아", "보나", "Bona")),
                    Place("치레", "Chire", listOf("치리", "Chere", "Chiri")),
                    Place("훌라", "Hula", listOf("Hulla")),
                    Place("부라", "Bura", listOf("Burra")),
                    Place("알레타 원도", "Aleta Wondo", listOf("Aleta Wendo", "알레타 웬도", "알레타 온도")),
                    Place("달레", "Dale"),
                    Place("다라", "Dara"),
                    Place("셰베디노", "Shebedino", listOf("쉐베디노")),
                    Place("웬쇼", "Wensho"),
                    Place("부르사", "Bursa"),
                ),
                lat = 6.67, lng = 38.5,
            ),
            Place(
                "구지", "Guji",
                subs = listOf(
                    Place(
                        "함벨라", "Hambela", listOf("Hambela Wamena", "함벨라 와메나"),
                        listOf(
                            Place("딤투", "Dimtu"),
                            Place("알라카", "Alaka"),
                            Place("부쿠 아벨", "Buku Abel"),
                            Place("부쿠 사이사", "Buku Sayisa", listOf("Buku Sayasa")),
                        ),
                    ),
                    Place("샤키소", "Shakiso", listOf("Odo Shakiso", "오도 샤키소")),
                    Place("우라가", "Uraga", subs = listOf(Place("야비투 코바", "Yabitu Koba"))),
                    Place("아돌라", "Adola", listOf("Adola Rede")),
                    Place("아나 소라", "Ana Sora", listOf("Anasora", "아나소라")),
                ),
                lat = 5.67, lng = 39.0,
            ),
            Place(
                "하라르", "Harrar", listOf("하라", "하레르", "Harar", "Harer"),
                listOf(
                    Place("동하라르게", "East Hararghe", listOf("이스트 하라르게", "East Harerge")),
                    Place("서하라르게", "West Hararghe", listOf("웨스트 하라르게", "West Harerge")),
                ),
                lat = 9.31, lng = 42.13,
            ),
            Place(
                "웨스트 아르시", "West Arsi", listOf("서아르시", "아르시", "Arsi"),
                listOf(
                    Place(
                        "넨세보", "Nensebo", listOf("Nansebo", "난세보"),
                        listOf(
                            Place("레피사", "Refisa", listOf("Rafisa")),
                            Place("리리파", "Riripa"),
                        ),
                    ),
                ),
                lat = 7.0, lng = 38.83,
            ),
            Place(
                "짐마", "Jimma", listOf("지마", "Djimmah", "Jima"),
                listOf(
                    Place("아가로", "Agaro"),
                    Place("게라", "Gera"),
                    Place("고마", "Gomma", listOf("Goma")),
                    Place("마나", "Mana"),
                ),
                lat = 7.67, lng = 36.83,
            ),
            Place(
                "리무", "Limu", listOf("림무", "Limmu"),
                listOf(
                    Place("코사", "Kosa", listOf("Limu Kosa", "Limmu Kossa", "Kossa")),
                    Place("세카", "Seka", listOf("Limu Seka", "Limmu Seka")),
                ),
                lat = 8.17, lng = 37.17,
            ),
            Place(
                "카파", "Kaffa", listOf("Kafa", "Keffa", "케파"),
                listOf(
                    Place("비타", "Bita"),
                    Place("게샤", "Gesha", listOf("게이샤", "Geisha")),
                ),
                lat = 7.18, lng = 36.05,
            ),
            Place(
                "벤치마지", "Bench Maji", listOf("벤치 마지", "Bench Sheko", "벤치 셰코", "벤치 쉐코"),
                listOf(
                    Place("셰코", "Sheko"),
                    Place("구라페르다", "Guraferda", listOf("Gura Ferda")),
                    Place("미잔", "Mizan", listOf("Mizan Teferi", "미잔 테페리")),
                ),
                lat = 6.25, lng = 35.17,
            ),
            Place("테피", "Tepi", lat = 7.2, lng = 35.42),
            Place("레켐티", "Lekempti", listOf("Wollega", "Welega", "월레가", "Nekemte", "네켐테"), lat = 9.08, lng = 36.55),
            Place("김비", "Ghimbi", listOf("Gimbi"), lat = 9.17, lng = 35.83),
            Place("일루바보르", "Illubabor", listOf("Ilubabor", "Illu Ababor"), lat = 8.25, lng = 36.0),
        ),
    )

    // source: Wikipedia "Coffee production in Kenya"; Kilimo News "14 Cooperatives and 74 Factories … Kirinyaga"; Mercanta /
    // Burman (Kiamugumo, Ngariama); corner.coffee "Nyeri County"; Korean: 로또커피 (니에리 카라티나), 와이즈커피 (오타야),
    // 함께커피 (키린야가), 워너빈 (키암부).
    // coords: enwiki county articles.
    private fun kenya() = Origin(
        "Kenya",
        listOf(
            Place(
                "니에리", "Nyeri",
                subs = listOf(
                    Place("오타야", "Othaya"),
                    Place("테투", "Tetu"),
                    Place("마티라", "Mathira"),
                    Place("카라티나", "Karatina"),
                    Place("무쿠르웨이니", "Mukurweini", listOf("Mukurwe-ini")),
                    Place("키에니", "Kieni"),
                ),
                lat = -0.42, lng = 36.95,
            ),
            Place(
                "키리냐가", "Kirinyaga", listOf("키린야가"),
                listOf(
                    Place(
                        "기추구", "Gichugu", listOf("Kirinyaga East"),
                        listOf(
                            Place("은가리아마", "Ngariama"),
                            Place("바라그위", "Baragwi"),
                        ),
                    ),
                    Place("은디아", "Ndia", listOf("Kirinyaga West")),
                ),
                lat = -0.5, lng = 37.28,
            ),
            Place(
                "엠부", "Embu",
                subs = listOf(
                    Place("루니엔제스", "Runyenjes"),
                    Place("만야타", "Manyatta"),
                ),
                lat = -0.53, lng = 37.45,
            ),
            Place(
                "키암부", "Kiambu",
                subs = listOf(
                    Place("루이루", "Ruiru"),
                    Place("티카", "Thika"),
                    Place("가툰두", "Gatundu"),
                    Place("기툰구리", "Githunguri"),
                ),
                lat = -1.17, lng = 36.83,
            ),
            Place(
                "무랑가", "Murang'a", listOf("Muranga", "무랑아"),
                listOf(
                    Place("캉에마", "Kangema"),
                    Place("키구모", "Kigumo"),
                    Place("칸다라", "Kandara"),
                    Place("가탕가", "Gatanga"),
                ),
                lat = -0.75, lng = 37.12,
            ),
            Place("메루", "Meru", lat = 0.05, lng = 37.63),
            Place("타라카니티", "Tharaka-Nithi", listOf("Tharaka Nithi", "타라카 니티"), lat = -0.3, lng = 38.0),
            Place("마차코스", "Machakos", lat = -1.5, lng = 37.25),
            Place("키시", "Kisii", lat = -0.67, lng = 34.75),
            Place("나쿠루", "Nakuru", lat = -0.5, lng = 36.0),
            Place("붕고마", "Bungoma", lat = 0.58, lng = 34.58),
        ),
    )

    // source: Sucafina "Rwanda"; Covoya (Simbi, Huye); Raw Material "Rwanda"; Korean: 커만사 Rwanda listings (니야마쉐케,
    // 니야루구루, 루치로, 가츠보, 루와마가나).
    // coords: enwiki district articles.
    private fun rwanda() = Origin(
        "Rwanda",
        listOf(
            Place("후예", "Huye", listOf("Butare", "부타레"), listOf(Place("마라바", "Maraba")), lat = -2.52, lng = 29.7),
            Place("냐마셰케", "Nyamasheke", listOf("니야마셰케", "니야마쉐케"), lat = -2.37, lng = 29.15),
            Place("냐루구루", "Nyaruguru", listOf("니야루구루"), lat = -2.7, lng = 29.52),
            Place("냐마가베", "Nyamagabe", listOf("니야마가베"), lat = -2.4, lng = 29.47),
            Place("루치로", "Rutsiro", lat = -1.92, lng = 29.32),
            Place("카롱기", "Karongi", listOf("Kibuye", "키부예"), lat = -2.15, lng = 29.39),
            Place("루시지", "Rusizi", listOf("Cyangugu"), lat = -2.48, lng = 28.9),
            Place("가켄케", "Gakenke", lat = -1.7, lng = 29.78),
            Place("룰린도", "Rulindo", lat = -1.73, lng = 30.0),
            Place("부레라", "Burera", lat = -1.49, lng = 29.81),
            Place("가치보", "Gatsibo", listOf("가츠보"), lat = -1.6, lng = 30.45),
            Place("르와마가나", "Rwamagana", listOf("루와마가나"), lat = -1.97, lng = 30.35),
        ),
    )

    // source: Sweet Maria's "Burundi Coffee Overview"; Wikipedia "Commune of Kabarore"; Sucafina Kibingo, Tala Buzira
    // (Muruta), Caroline's Coffee Mbirizi (Gatara), Mother Tongue Matongo. Provinces as the trade names them (pre-2025).
    // coords: enwiki province articles.
    private fun burundi() = Origin(
        "Burundi",
        listOf(
            Place(
                "카얀자", "Kayanza",
                subs = listOf(
                    Place("카바로레", "Kabarore"),
                    Place("무루타", "Muruta"),
                    Place("가타라", "Gatara"),
                    Place("마통고", "Matongo"),
                    Place("무항가", "Muhanga"),
                ),
                lat = -2.92, lng = 29.62,
            ),
            Place("응고지", "Ngozi", lat = -2.91, lng = 29.83),
            Place("키룬도", "Kirundo", lat = -2.55, lng = 30.09),
            Place("무람비야", "Muramvya", lat = -3.25, lng = 29.63),
            Place("기테가", "Gitega", lat = -3.47, lng = 29.96),
            Place("무잉가", "Muyinga", lat = -2.82, lng = 30.32),
        ),
    )

    // source: Royal Coffee "Tanzania" region page (Karatu, Oldeani, Rungwe, Mbozi, Ileje, Songea, Mbinga); Sucafina
    // "Tanzania" (Kagera, Bukoba). Mbozi and Ileje are in Songwe Region since 2016.
    // coords: enwiki region articles; Arusha = Karatu District (the coffee area west of Arusha).
    private fun tanzania() = Origin(
        "Tanzania",
        listOf(
            Place("킬리만자로", "Kilimanjaro", subs = listOf(Place("모시", "Moshi")), lat = -4.13, lng = 37.81),
            Place(
                "아루샤", "Arusha",
                subs = listOf(
                    Place("카라투", "Karatu", subs = listOf(Place("올데아니", "Oldeani"))),
                ),
                lat = -3.35, lng = 35.67,
            ),
            Place("음베야", "Mbeya", subs = listOf(Place("룽궤", "Rungwe", listOf("룽웨"))), lat = -8.91, lng = 33.46),
            Place(
                "송웨", "Songwe",
                subs = listOf(
                    Place("음보지", "Mbozi"),
                    Place("일레제", "Ileje"),
                ),
                lat = -8.52, lng = 32.54,
            ),
            Place(
                "루부마", "Ruvuma",
                subs = listOf(
                    Place("음빙가", "Mbinga"),
                    Place("송게아", "Songea"),
                ),
                lat = -10.69, lng = 36.26,
            ),
            Place("카게라", "Kagera", subs = listOf(Place("부코바", "Bukoba")), lat = -1.92, lng = 31.3),
        ),
    )

    // source: coffeegeography.com "Ugandan Coffee Regions"; Atlas Coffee Importers "Uganda"; Wikipedia "Bugisu sub-region".
    // coords: enwiki — Mount Elgon, Rwenzori Mountains, West Nile sub-region.
    private fun uganda() = Origin(
        "Uganda",
        listOf(
            Place(
                "마운트 엘곤", "Mount Elgon", listOf("엘곤", "엘곤산", "Elgon", "Mt. Elgon", "Mt Elgon"),
                listOf(
                    Place("부기수", "Bugisu"),
                    Place("시피 폭포", "Sipi Falls", listOf("Sipi", "시피")),
                    Place("캅초르와", "Kapchorwa"),
                    Place("음발레", "Mbale"),
                ),
                lat = 1.14, lng = 34.56,
            ),
            Place(
                "르웬조리", "Rwenzori", listOf("루웬조리", "Ruwenzori"),
                listOf(
                    Place("카세세", "Kasese"),
                    Place("분디부교", "Bundibugyo"),
                ),
                lat = 0.39, lng = 29.87,
            ),
            Place(
                "웨스트 나일", "West Nile",
                subs = listOf(
                    Place("좀보", "Zombo"),
                    Place("아루아", "Arua"),
                    Place("네비", "Nebbi"),
                ),
                lat = 3.0, lng = 31.2,
            ),
        ),
    )

    // source: Rikolto "Speciality coffee from Kivu and Ituri" (Kalehe, Kabare, Idjwi, Beni-Lubero, Ituri); Cafe Imports
    // "DR Congo" (SOPACDI, South Kivu).
    // coords: enwiki — Lake Kivu, Ituri Province.
    private fun drCongo() = Origin(
        "Dem. Rep. Congo",
        listOf(
            Place(
                "키부", "Kivu", listOf("Lake Kivu", "키부 호수"),
                listOf(
                    Place(
                        "남키부", "South Kivu", listOf("사우스 키부", "Sud-Kivu"),
                        listOf(
                            Place("칼레헤", "Kalehe"),
                            Place("이지위", "Idjwi", listOf("Idjwi Island", "이드지위")),
                            Place("카바레", "Kabare"),
                        ),
                    ),
                    Place(
                        "북키부", "North Kivu", listOf("노스 키부", "Nord-Kivu"),
                        listOf(
                            Place("베니", "Beni"),
                            Place("루베로", "Lubero"),
                        ),
                    ),
                ),
                lat = -2.0, lng = 29.0,
            ),
            Place("이투리", "Ituri", lat = 1.83, lng = 29.5),
        ),
    )

    // source: Perfect Daily Grind "Exploring Zambia as a coffee origin"; Corner Coffee Store Zambia guide (Isoka, Kasama,
    // Nakonde, Muchinga).
    // coords: enwiki — Nyika Plateau, Muchinga Province, Kasama (Northern Province).
    private fun zambia() = Origin(
        "Zambia",
        listOf(
            Place("니카 고원", "Nyika Plateau", listOf("니이카 고원", "Nyika"), lat = -10.35, lng = 33.6),
            Place(
                "무칭가", "Muchinga",
                subs = listOf(
                    Place("이소카", "Isoka"),
                    Place("나콘데", "Nakonde"),
                ),
                lat = -10.0, lng = 32.0,
            ),
            Place("북부주", "Northern Province", listOf("Northern"), listOf(Place("카사마", "Kasama")), lat = -10.21, lng = 31.18),
        ),
    )

    // source: Sucafina "Malawi"; Wikipedia "Mzuzu Coffee Planters Cooperative Union" (Misuku, Phoka, Viphya); Perfect
    // Daily Grind "Exploring Malawi as a coffee origin" (Thyolo).
    // coords: enwiki — Mzuzu, Chitipa District (Misuku Hills), Viphya Mountains; Wikidata Thyolo District (Q722515).
    private fun malawi() = Origin(
        "Malawi",
        listOf(
            Place("음주주", "Mzuzu", lat = -11.46, lng = 34.02),
            Place("미수쿠 힐스", "Misuku Hills", listOf("Misuku", "미수쿠", "Chitipa"), lat = -9.75, lng = 33.25),
            Place("비피아", "Viphya", lat = -13.06, lng = 34.19),
            Place("촐로", "Thyolo", lat = -16.17, lng = 35.17),
        ),
    )

    // source: Colipse "20 Best African Coffees" / Zimbabwe notes (Chipinge, Mutare and Vumba, Honde Valley).
    // coords: enwiki — Chipinge, Honde Valley, Bvumba Mountains.
    private fun zimbabwe() = Origin(
        "Zimbabwe",
        listOf(
            Place("치핑게", "Chipinge", lat = -20.2, lng = 32.62),
            Place("혼데 밸리", "Honde Valley", lat = -18.5, lng = 32.85),
            Place("붐바", "Vumba", listOf("Bvumba", "Mutare"), lat = -19.1, lng = 32.78),
        ),
    )

    // source: Wikipedia "Coffee production in Cameroon", "West Region (Cameroon)" (Bamileke and Bamoun highlands).
    // coords: enwiki region articles.
    private fun cameroon() = Origin(
        "Cameroon",
        listOf(
            Place("서부주", "West Region", listOf("West", "Ouest", "웨스트"), lat = 5.5, lng = 10.5),
            Place("북서부주", "Northwest Region", listOf("North West", "Nord-Ouest"), lat = 6.33, lng = 10.5),
        ),
    )

    // source: map-dot region only. coords: enwiki Man (Ivory Coast).
    private fun coteDIvoire() = Origin(
        "Côte d'Ivoire",
        listOf(Place("만", "Man", lat = 7.4, lng = -7.55)),
    )

    // source: map-dot region only. coords: enwiki Antsirabe.
    private fun madagascar() = Origin(
        "Madagascar",
        listOf(Place("안치라베", "Antsirabe", lat = -19.87, lng = 47.03)),
    )

    // source: map-dot region; Ziama-Macenta protected geographical indication (Agence Ecofin, origin-gi.com).
    // coords: enwiki Nzérékoré, Macenta.
    private fun guinea() = Origin(
        "Guinea",
        listOf(
            Place("은제레코레", "Nzérékoré", listOf("Nzerekore", "N'Zérékoré"), lat = 7.75, lng = -8.82),
            Place("마센타", "Macenta", listOf("Ziama-Macenta", "Ziama", "지아마"), lat = 8.55, lng = -9.47),
        ),
    )
}
