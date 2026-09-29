package com.coffeejournal.domain.reference

import com.coffeejournal.domain.reference.OriginRegions.Origin
import com.coffeejournal.domain.reference.OriginRegions.Place

/**
 * [OriginRegions] of Africa: one private function per country, each with its sources above it. Ethiopia's and
 * Kenya's larger regions have a private function of their own, which keeps every JVM method well under 64 KB.
 */
internal object OriginsAfrica {
    val all: List<Origin> = listOf(
        ethiopia(), kenya(), rwanda(), burundi(), tanzania(), uganda(), drCongo(), zambia(), malawi(), zimbabwe(),
        cameroon(), coteDIvoire(), madagascar(), guinea(),
    )

    // source: the regions as importers name them (Trabocca, Royal Coffee, Cafe Imports, Ally Coffee, Sucafina, Cup of
    // Excellence Ethiopia); the larger regions' woredas and kebeles are sourced above their functions. Others:
    // Wikipedia "East Hararghe Zone" and "West Hararghe Zone" (Deder, Kombolcha, Gara Muleta; Chiro, Habro, Gemechis),
    // "Jimma Zone", "Keffa Zone" and "Gesha" (Bita, Gesha woreda, Decha; Bonga town), "Bench Sheko Zone" (Sheko,
    // Guraferda, Mizan), "Sheka Zone" (Masha, Yeki, Anderacha), "Welega Province", "Illubabor Zone" (Metu, Yayu),
    // "Harenna Forest" (Bale); Sweet Maria's and Red Fox "Kata Muduga" (Agaro: Duromina, Nano Challa, Yukro, Hunda Oli,
    // Biftu Gudina); Trabocca "Gesha Village" (Gori Gesha forest, Bench Maji) and "Gora Kone" (Nensebo, West Arsi).
    // Political renames keep the trade name first (Sidama Region: Sidamo; Bench Sheko: Bench Maji). Korean: 커피 리브레
    // (리무 코사, 카파 비타, 넨세보, 벤치 마지), 커피 리브레 생두 / 오미토리 / 챕터원커피 (벤치마지), 원더룸 (게샤
    // 빌리지), Coffee Me Up (고리 게샤), 코빈즈 (리무, 카파, 벤치 마지).
    // coords: enwiki — Irgachefe (Yirgacheffe town), Sidama Region, Guji Zone, Harar, Jimma, Limmu Kosa, Bench Sheko
    // (Bench Maji), Tepi, Ethiopia, Nekemte (Lekempti), Welega Province, Gimbi, Illubabor Zone, Bale Zone, Amaro Zone;
    // Wikidata West Arsi Zone (Q6872255), Keffa Zone (Q6383412), Sheka Zone (Q7493221).
    private fun ethiopia() = Origin(
        "Ethiopia",
        listOf(
            ethiopiaYirgacheffe(),
            ethiopiaSidamo(),
            ethiopiaGuji(),
            Place(
                "하라르", "Harrar", listOf("하라", "하레르", "Harar", "Harer"),
                listOf(
                    Place(
                        "동하라르게", "East Hararghe", listOf("이스트 하라르게", "East Harerge"),
                        listOf(
                            Place("데데르", "Deder"),
                            Place("가라 물레타", "Gara Muleta"),
                            Place("콤볼차", "Kombolcha"),
                        ),
                    ),
                    Place(
                        "서하라르게", "West Hararghe", listOf("웨스트 하라르게", "West Harerge"),
                        listOf(
                            Place("치로", "Chiro"),
                            Place("하브로", "Habro"),
                            Place("게메치스", "Gemechis"),
                        ),
                    ),
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
                            Place("고라 코네", "Gora Kone", listOf("Gorakone")),
                        ),
                    ),
                    Place("코코사", "Kokosa"),
                ),
                lat = 7.0, lng = 38.83,
            ),
            Place(
                "짐마", "Jimma", listOf("지마", "Djimmah", "Jima"),
                listOf(
                    Place(
                        "아가로", "Agaro",
                        subs = listOf(
                            Place("두로미나", "Duromina"),
                            Place("나노 찰라", "Nano Challa"),
                            Place("유크로", "Yukro", listOf("Yukiro")),
                            Place("훈다 올리", "Hunda Oli"),
                            Place("비프투 구디나", "Biftu Gudina"),
                            Place("카타 무두가", "Kata Muduga"),
                        ),
                    ),
                    Place("고마", "Gomma", listOf("Goma")),
                    Place("게라", "Gera"),
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
                    Place("비타", "Bita", listOf("Bita Genet")),
                    Place("게샤", "Gesha"),
                    Place("데차", "Decha"),
                    Place("봉가", "Bonga"),
                ),
                lat = 7.18, lng = 36.05,
            ),
            Place(
                "벤치마지", "Bench Maji", listOf("벤치 마지", "Bench Sheko", "벤치 셰코", "벤치 쉐코"),
                listOf(
                    Place("셰코", "Sheko"),
                    Place("구라페르다", "Guraferda", listOf("Gura Ferda")),
                    Place("미잔", "Mizan", listOf("Mizan Teferi", "미잔 테페리", "Mizan Aman")),
                    Place("게샤 빌리지", "Gesha Village", listOf("게이샤 빌리지", "Geisha Village")),
                    Place("고리 게샤", "Gori Gesha", listOf("고리 게이샤", "Gori Geisha")),
                ),
                lat = 6.25, lng = 35.17,
            ),
            Place("테피", "Tepi", listOf("Teppi"), lat = 7.2, lng = 35.42),
            Place(
                "셰카", "Sheka", listOf("쉐카"),
                listOf(
                    Place("마샤", "Masha"),
                    Place("예키", "Yeki"),
                    Place("안데라차", "Anderacha"),
                ),
                lat = 7.74, lng = 35.48,
            ),
            Place("레켐티", "Lekempti", listOf("Lekemti", "Nekemte", "네켐테"), lat = 9.08, lng = 36.55),
            Place(
                "월레가", "Wollega", listOf("Welega", "Wellega", "웰레가"),
                listOf(
                    Place("동월레가", "East Wollega", listOf("East Welega")),
                    Place("서월레가", "West Wollega", listOf("West Welega")),
                    Place("켈렘 월레가", "Kellem Wollega", listOf("Kelem Welega")),
                ),
                lat = 9.0, lng = 36.0,
            ),
            Place("김비", "Ghimbi", listOf("Gimbi"), lat = 9.17, lng = 35.83),
            Place(
                "일루바보르", "Illubabor", listOf("Ilubabor", "Illu Ababor"),
                listOf(
                    Place("메투", "Metu", listOf("Mettu")),
                    Place("야유", "Yayu", listOf("Yayo")),
                ),
                lat = 8.25, lng = 36.0,
            ),
            Place(
                "발레", "Bale", listOf("Bale Mountains"),
                listOf(
                    Place("하레나", "Harenna", listOf("Harena", "Harenna Forest")),
                ),
                lat = 6.75, lng = 40.25,
            ),
            Place("아마로", "Amaro", listOf("Amaro Gayo", "아마로 가요"), lat = 5.83, lng = 37.83),
        ),
    )

    // source: Trabocca "The Yirgacheffe Zone Map" (the six woredas; Gelana and Abaya, in Oromia, traded as
    // Yirgacheffe), "Aricha" (Aricha, Gersi, Idido), "Adado" (Bule woreda) and "Hafursa Waro" (Abaya); Ally Coffee
    // (Koke kebele, Yirgacheffe woreda; Chelelektu, Kochere; Gedeb Chelbesa, Gedeb Gotiti); Royal Coffee and Covoya
    // (Dumerso, Yirgacheffe woreda); Melbourne Coffee Merchants and Royal Coffee (Adorsi, Aricha); Roastmasters (Reko,
    // Kochere); DRWakefield (Beloya, Kochere); The Coffee Quest (Danche, Chelbesa kebele, Gedeb: also Halo Beriti,
    // Banko Gotiti, Chelchele); Algrano / SNAP (Halo Beriti, Gedeb); Trabocca (Worka Sakaro). The Yirgacheffe woreda's
    // own kebeles sit directly under the region, as the bags print them (Yirgacheffe Konga). Korean: 커피 리브레
    // (예가체프 게뎁 초르소, 코체레, 할로 베리티), 코빈즈 (게뎁, 코체레).
    private fun ethiopiaYirgacheffe() = Place(
        "예가체프", "Yirgacheffe", listOf("이르가체프", "Yirga Cheffe", "Yirgachefe", "Yirga Chefe", "게데오", "Gedeo"),
        listOf(
            Place("콩가", "Konga"),
            Place("이디도", "Idido", listOf("Edido", "에디도")),
            Place("아리차", "Aricha"),
            Place("코케", "Koke"),
            Place("두메르소", "Dumerso"),
            Place("아도르시", "Adorsi"),
            Place("게르시", "Gersi"),
            Place(
                "코체레", "Kochere", listOf("Kochore", "코초레"),
                listOf(
                    Place("첼렐렉투", "Chelelektu", listOf("Chelelectu", "첼레렉투")),
                    Place("레코", "Reko"),
                    Place("벨로야", "Beloya", listOf("Biloya")),
                ),
            ),
            Place(
                "게뎁", "Gedeb", listOf("게데브", "Gedebe"),
                listOf(
                    Place("워카", "Worka", listOf("Werka")),
                    Place("워카 사카로", "Worka Sakaro"),
                    Place("첼베사", "Chelbesa", listOf("Worka Chelbesa", "워카 첼베사", "Chelbessa")),
                    Place("반코 고티티", "Banko Gotiti", listOf("Gotiti", "고티티")),
                    Place("첼첼레", "Chelchele", listOf("Banko Chelchele")),
                    Place("할로 베리티", "Halo Beriti", listOf("Halo Bariti", "할로 바리티", "Haloberiti")),
                    Place("단체", "Danche"),
                    Place("초르소", "Chorso"),
                ),
            ),
            Place("불레", "Bule", subs = listOf(Place("아다도", "Adado"))),
            Place("웨나고", "Wenago", listOf("Wonago", "워나고")),
            Place("딜라 주리아", "Dilla Zuria", listOf("Dilla", "딜라")),
            Place(
                "겔라나 아바야", "Gelana Abaya", listOf("Gelana", "Abaya", "겔라나", "아바야"),
                listOf(
                    Place("하푸르사", "Hafursa", listOf("Hafursa Waro", "하푸르사 와로")),
                ),
            ),
        ),
        lat = 6.17, lng = 38.2,
    )

    // source: Wikipedia "Sidama Region" (its woredas: Chere is the trade's Chire) and "Arbegona"; Daye Bensa (Bensa:
    // Shantawene, Bombe, Keramo, Hamasho, the Qonqana mother station; it also works in Bura, Chabe, Hoko, Aroresa and
    // Chire); Beanberry and Glen Lyon (Bochesa kebele, Arbegona); The Coffee Quest and Mercanta (Rumudamo, Arbegona);
    // Alo Coffee (Alo, Bensa); Coffee Review (Bursa); Trabocca "Gora Kone" (Refisa kebele, Nensebo, West Arsi, classed
    // as Sidamo). Korean: 커피 리브레 (시다모 치리, 벤사 무라고, 넨세보), 달구네커피 and 톨드어스토리 (치레),
    // 블로트커피 (보나 주리아), 함께커피 (아르베고나 루무다모), 커넥츠커피 (봄베), 토브공작소 (하마쇼).
    private fun ethiopiaSidamo() = Place(
        "시다모", "Sidamo", listOf("시다마", "Sidama"),
        listOf(
            Place(
                "벤사", "Bensa", listOf("Benssa"),
                listOf(
                    Place("코코세", "Kokose"),
                    Place("봄베", "Bombe"),
                    Place("샨타웨네", "Shantawene", listOf("Shanta Wene", "샨타 웨네")),
                    Place("케라모", "Keramo", listOf("Karamo", "카라모")),
                    Place("하마쇼", "Hamasho"),
                    Place("알로", "Alo"),
                    Place("무라고", "Murago"),
                    Place("콘카나", "Qonqana", listOf("Konkana")),
                    Place("다예", "Daye"),
                ),
            ),
            Place(
                "아르베고나", "Arbegona",
                subs = listOf(
                    Place("보체사", "Bochesa", listOf("Bochessa")),
                    Place("루무다모", "Rumudamo"),
                ),
            ),
            Place("아로레사", "Aroresa", listOf("Aroressa")),
            Place("치레", "Chire", listOf("치리", "Chere", "Chiri")),
            Place("보나 주리아", "Bona Zuria", listOf("보나 조리아", "보나", "Bona")),
            Place("부라", "Bura", listOf("Burra")),
            Place("호코", "Hoko"),
            Place("차베 감벨투", "Chabe Gambeltu", listOf("Chabe", "차베")),
            Place("훌라", "Hula", listOf("Hulla")),
            Place("부르사", "Bursa"),
            Place("알레타 원도", "Aleta Wondo", listOf("Aleta Wendo", "알레타 웬도", "알레타 온도")),
            Place("달레", "Dale"),
            Place("다라", "Dara"),
            Place("셰베디노", "Shebedino", listOf("쉐베디노")),
            Place("웬쇼", "Wensho", listOf("Wonsho")),
            Place(
                "넨세보", "Nensebo", listOf("Nansebo", "난세보"),
                listOf(
                    Place("레피사", "Refisa", listOf("Rafisa")),
                    Place("리리파", "Riripa"),
                    Place("고라 코네", "Gora Kone", listOf("Gorakone")),
                ),
            ),
        ),
        lat = 6.67, lng = 38.5,
    )

    // source: Wikipedia "Odo Shakiso" and "Ana Sora" (woredas; Shakiso is the woreda's town and the trade's name for
    // it); Ally Coffee and SNAP (Hambela Wamena: Dimtu, Alaka, Buku Abel, Buku Sayisa); Red Fox and Nemesis (Yabitu
    // Koba, Uraga), Coffee Shrub (Uraga Tome), Moonwake (Raro Boda, Uraga); Portrait Coffee (Kayon Mountain, Taro
    // kebele), Wood St and GoCoffeeGo (Suke Quto), Sample Coffee (Dambi Uddo), Torque Coffees (Gigesa), Coffee Review
    // (Shakiso Anasora); Ally Coffee and Atlas Coffee Importers (Kercha, Guracho); West Guji's Kercha and Bule Hora as
    // the trade lists them under Guji. Korean: 커피 리브레 (구지 우라가), 원더룸 / HYTTE (부쿠 사이사).
    private fun ethiopiaGuji() = Place(
        "구지", "Guji", listOf("Guji Zone"),
        listOf(
            Place(
                "함벨라", "Hambela", listOf("Hambela Wamena", "함벨라 와메나"),
                listOf(
                    Place("딤투", "Dimtu"),
                    Place("알라카", "Alaka"),
                    Place("부쿠 아벨", "Buku Abel"),
                    Place("부쿠 사이사", "Buku Sayisa", listOf("Buku Sayasa")),
                ),
            ),
            Place(
                "샤키소", "Shakiso", listOf("Odo Shakiso", "오도 샤키소"),
                listOf(
                    Place("카욘 마운틴", "Kayon Mountain", listOf("Kayon", "카욘")),
                    Place("수케 쿠토", "Suke Quto", listOf("Suke Kuto")),
                    Place("담비 우도", "Dambi Uddo", listOf("Denbi Uddo", "Dambi Udo")),
                    Place("세와나", "Sewana"),
                    Place("기게사", "Gigesa"),
                ),
            ),
            Place(
                "우라가", "Uraga",
                subs = listOf(
                    Place("야비투 코바", "Yabitu Koba"),
                    Place("토메", "Tome"),
                    Place("라로", "Raro", listOf("Raro Boda")),
                ),
            ),
            Place("아돌라", "Adola", listOf("Adola Rede")),
            Place("아나소라", "Anasora", listOf("Ana Sora", "아나 소라")),
            Place("케르차", "Kercha", subs = listOf(Place("구라초", "Guracho"))),
            Place("불레 호라", "Bule Hora"),
        ),
        lat = 5.67, lng = 39.0,
    )

    // source: Wikipedia "Coffee production in Kenya" (the regions: Mount Kenya and the Aberdares, Kisii, Bungoma,
    // Nakuru, Kericho, Nandi, Mt. Elgon, Machakos); the counties' sub-counties from their Wikipedia articles; the
    // factories under Nyeri and Kirinyaga are sourced above their functions. Others: Kilimo News and Fairtrade Africa
    // (Iyego FCS, Kangema: Gatubu); Red Rooster, Sucafina and Climate Smart Coffee (Kiru FCS of Mathioya: Ndiara,
    // Kamagogo; Kangunu FCS, Mathioya); Sucafina and Ritual (Karatu, Gatundu; Thiririka FCS's Githembe); Sucafina and
    // Mercanta (Gititu, Githunguri); The Coffee Quest (Komothai FCS's Gathiruini, Githunguri); Sucafina (Kirurumwe FCS
    // of Runyenjes: Kianjuki, Kevote); Mercanta and the Embu County Coffee Mill (Kavutiri, Runyenjes); Grand Parade
    // (Katheri FCS's Gichugene), La Tosteria (Kianjuri, North Imenti), Cafe Imports (Mikumbune FCS's Munani); Wikipedia
    // (Kipkelion union, Kericho). Korean: 워너빈 (키암부).
    // coords: enwiki county articles (Nyeri, Kirinyaga, Embu, Kiambu, Murang'a, Meru, Tharaka-Nithi, Machakos, Kisii,
    // Nakuru, Bungoma, Kericho, Nandi, Trans-Nzoia).
    private fun kenya() = Origin(
        "Kenya",
        listOf(
            kenyaNyeri(),
            kenyaKirinyaga(),
            Place(
                "엠부", "Embu",
                subs = listOf(
                    Place(
                        "루니엔제스", "Runyenjes",
                        subs = listOf(
                            Place("키안주키", "Kianjuki"),
                            Place("케보테", "Kevote"),
                            Place("카부티리", "Kavutiri"),
                        ),
                    ),
                    Place("만야타", "Manyatta"),
                ),
                lat = -0.53, lng = 37.45,
            ),
            Place(
                "키암부", "Kiambu",
                subs = listOf(
                    Place("가툰두", "Gatundu", subs = listOf(Place("카라투", "Karatu"), Place("기템베", "Githembe"))),
                    Place(
                        "기툰구리", "Githunguri",
                        subs = listOf(
                            Place("기티투", "Gititu"),
                            Place("코모타이", "Komothai"),
                            Place("가티루이니", "Gathiruini"),
                        ),
                    ),
                    Place("루이루", "Ruiru"),
                    Place("티카", "Thika"),
                ),
                lat = -1.17, lng = 36.83,
            ),
            Place(
                "무랑가", "Murang'a", listOf("Muranga", "무랑아"),
                listOf(
                    Place("캉에마", "Kangema", subs = listOf(Place("이예고", "Iyego"), Place("가투부", "Gatubu"))),
                    Place(
                        "마티오야", "Mathioya",
                        subs = listOf(
                            Place("은디아라", "Ndiara"),
                            Place("카마고고", "Kamagogo"),
                            Place("캉구누", "Kangunu"),
                        ),
                    ),
                    Place("키하루", "Kiharu"),
                    Place("키구모", "Kigumo"),
                    Place("칸다라", "Kandara"),
                    Place("가탕가", "Gatanga"),
                ),
                lat = -0.75, lng = 37.12,
            ),
            Place(
                "메루", "Meru",
                subs = listOf(
                    Place(
                        "이멘티", "Imenti", listOf("North Imenti", "South Imenti", "Central Imenti"),
                        listOf(
                            Place("기추게네", "Gichugene"),
                            Place("키안주리", "Kianjuri"),
                            Place("무나니", "Munani"),
                        ),
                    ),
                    Place("티가니아", "Tigania"),
                    Place("이겜베", "Igembe"),
                ),
                lat = 0.05, lng = 37.63,
            ),
            Place(
                "타라카니티", "Tharaka-Nithi", listOf("Tharaka Nithi", "타라카 니티"),
                listOf(
                    Place("추카", "Chuka"),
                    Place("마아라", "Maara"),
                ),
                lat = -0.3, lng = 38.0,
            ),
            Place("마차코스", "Machakos", lat = -1.5, lng = 37.25),
            Place("키시", "Kisii", lat = -0.67, lng = 34.75),
            Place("나쿠루", "Nakuru", lat = -0.5, lng = 36.0),
            Place(
                "붕고마", "Bungoma",
                subs = listOf(
                    Place("엘곤", "Mount Elgon", listOf("Mt. Elgon", "Mt Elgon")),
                ),
                lat = 0.58, lng = 34.58,
            ),
            Place("케리초", "Kericho", subs = listOf(Place("킵켈리온", "Kipkelion")), lat = -0.4, lng = 35.3),
            Place("난디", "Nandi", lat = 0.17, lng = 35.15),
            Place(
                "트랜스은조이아", "Trans-Nzoia", listOf("Trans Nzoia", "트랜스 은조이아"),
                listOf(
                    Place("키탈레", "Kitale"),
                ),
                lat = 1.1, lng = 34.95,
            ),
        ),
    )

    // source: Barichu FCS (Mathira East: Karatina, Karindundu, Gaturiri, Gatomboya factories); Sucafina and
    // Collaborative Coffee Source (Mugaga FCS, near Karatina: Kiamabara, Kieni, Kagumoini, Gathugu, Gatina); Wikipedia
    // "Coffee production in Kenya" (Gikanda FCS, Mathira: Gichathaini, Kangocho, Ndaroini; Tekangu FCS: Tegu, Karogoto,
    // Ngunguru, which Sucafina places by Karatina; Mutheka FCS: Chorong'i, Kigwandi); Sucafina (Gachatha, Tetu); Kenya
    // News Agency (Thiriku FCS, Tetu); Royal Coffee (Mutheka FCS of Tetu: Kaiguri, Muthuaini, Kiandu); Ozone Coffee and
    // Climate Smart Coffee (Othaya FCS: Chinga, Gura, Kamoini, Gatugi, Ichamama). Kieni here is Mugaga's factory, the
    // trade's Kieni. Korean: 커피명가 and 빈브라더스 (니에리 가톰보야), 로또커피 (니에리 카라티나), 와이즈커피
    // (오타야).
    private fun kenyaNyeri() = Place(
        "니에리", "Nyeri",
        subs = listOf(
            Place(
                "마티라", "Mathira",
                subs = listOf(
                    Place("카라티나", "Karatina"),
                    Place("가톰보야", "Gatomboya"),
                    Place("카린둔두", "Karindundu"),
                    Place("가투리리", "Gaturiri"),
                    Place("키암바라", "Kiamabara"),
                    Place("키에니", "Kieni"),
                    Place("카구모이니", "Kagumoini"),
                    Place("가투구", "Gathugu"),
                    Place("가티나", "Gatina"),
                    Place("기차타이니", "Gichathaini", listOf("Gichatha-ini")),
                    Place("캉고초", "Kangocho"),
                    Place("은다로이니", "Ndaroini"),
                    Place("테구", "Tegu"),
                    Place("카로고토", "Karogoto"),
                    Place("응궁구루", "Ngunguru"),
                ),
            ),
            Place(
                "테투", "Tetu",
                subs = listOf(
                    Place("가차타", "Gachatha"),
                    Place("티리쿠", "Thiriku"),
                    Place("초롱이", "Chorong'i", listOf("Chorongi", "초롱기")),
                    Place("키관디", "Kigwandi"),
                    Place("카이구리", "Kaiguri"),
                    Place("키안두", "Kiandu"),
                    Place("무투아이니", "Muthuaini", listOf("Muthua-ini")),
                ),
            ),
            Place(
                "오타야", "Othaya",
                subs = listOf(
                    Place("친가", "Chinga"),
                    Place("구라", "Gura"),
                    Place("카모이니", "Kamoini"),
                    Place("가투기", "Gatugi"),
                    Place("이차마마", "Ichamama"),
                ),
            ),
            Place("무쿠르웨이니", "Mukurweini", listOf("Mukurwe-ini")),
        ),
        lat = -0.42, lng = 36.95,
    )

    // source: Kilimo News "14 Cooperatives and 74 Factories … Kirinyaga"; Wikipedia "Coffee production in Kenya"
    // (Rung'eto FCS, Ngariama ward: Kii, Karimikui, Kiangoi; Baragwi FCS: Karumandi, Kianyaga, Gachame; Kibirigwi FCS);
    // Cafe Imports and La Cabra (Rung'eto, Gichugu); Sucafina and La Cabra (New Ngariama FCS: Kiamugumo, Kamwangi,
    // Kainamui; Thirikwa FCS near Ngariama: Gakuyuini). Korean: 함께커피 (키린야가), 시차원두 (카리미쿠이), 원더룸 and
    // 커피 리브레 (카이나무이), 월드베스트커피 (가쿠유이니).
    private fun kenyaKirinyaga() = Place(
        "키리냐가", "Kirinyaga", listOf("키린야가"),
        listOf(
            Place(
                "기추구", "Gichugu", listOf("Kirinyaga East"),
                listOf(
                    Place("은가리아마", "Ngariama"),
                    Place("키이", "Kii"),
                    Place("카리미쿠이", "Karimikui"),
                    Place("키앙고이", "Kiangoi"),
                    Place("키아무구모", "Kiamugumo"),
                    Place("카므왕기", "Kamwangi"),
                    Place("카이나무이", "Kainamui"),
                    Place("가쿠유이니", "Gakuyuini", listOf("가쿠이이니", "Gakuyu-ini")),
                    Place("바라그위", "Baragwi"),
                    Place("카루만디", "Karumandi"),
                    Place("키안야가", "Kianyaga"),
                    Place("가차메", "Gachame"),
                ),
            ),
            Place("은디아", "Ndia", listOf("Kirinyaga West"), listOf(Place("키비리그위", "Kibirigwi"))),
        ),
        lat = -0.5, lng = 37.28,
    )

    // source: Sucafina "Rwanda"; Covoya (Simbi, Huye); Huye Mountain Coffee (Huye); Sprudge and Melbourne Coffee
    // Merchants (Buf Cafe's Remera and Nyarusiza, Nyamagabe); Royal Coffee and Impexcor (Busanze, Nyaruguru); Sucafina
    // (Nyakizu, Nyaruguru); Sweet Maria's (Gitwe, Nyakabingo, Nyamasheke), Vesta (Kanzu, Nyamasheke), Muraho Trading
    // (Kilimbi, Nyamasheke), TYPICA (Gasharu, Nyamasheke; Sholi, Muhanga), 빈브라더스 (Abadatezuka at Cyato,
    // Nyamasheke); Kinunu CWS (Boneza, Rutsiro), Kopakama and Omwani (Bwishaza, Rutsiro); Wikipedia "Gitesi" (Karongi);
    // Belco and Melbourne Coffee Merchants (the Dukunde Kawa Musasa cooperative of Gakenke: Ruli, Mbilima, Nkara);
    // DRWakefield (Kinini, Rulindo); Sweet Maria's and Pre-Umber (Kageyo, Ngororero). Korean: TYPICA (니야마셰케,
    // 무항가), 커만사 (니야마쉐케, 니야루구루, 루치로, 가츠보, 루와마가나), 빈브라더스 (나야마시케, 시아토), 커피
    // 리브레 (부산제).
    // coords: enwiki district articles.
    private fun rwanda() = Origin(
        "Rwanda",
        listOf(
            Place(
                "후예", "Huye", listOf("Butare", "부타레"),
                listOf(
                    Place("마라바", "Maraba"),
                    Place("후예 마운틴", "Huye Mountain"),
                    Place("심비", "Simbi"),
                ),
                lat = -2.52, lng = 29.7,
            ),
            Place(
                "니야마셰케", "Nyamasheke", listOf("냐마셰케", "니야마쉐케", "나야마시케"),
                listOf(
                    Place("가샤루", "Gasharu"),
                    Place("칸주", "Kanzu"),
                    Place("기트웨", "Gitwe"),
                    Place("킬림비", "Kilimbi", listOf("Kirimbi")),
                    Place("시아토", "Cyato", listOf("Siato")),
                    Place("냐카빙고", "Nyakabingo"),
                ),
                lat = -2.37, lng = 29.15,
            ),
            Place(
                "니야루구루", "Nyaruguru", listOf("냐루구루"),
                listOf(
                    Place("부산제", "Busanze"),
                    Place("냐키주", "Nyakizu"),
                ),
                lat = -2.7, lng = 29.52,
            ),
            Place(
                "냐마가베", "Nyamagabe", listOf("니야마가베"),
                listOf(
                    Place("레메라", "Remera"),
                    Place("냐루시자", "Nyarusiza"),
                ),
                lat = -2.4, lng = 29.47,
            ),
            Place(
                "루치로", "Rutsiro",
                subs = listOf(
                    Place("키누누", "Kinunu"),
                    Place("코파카마", "Kopakama"),
                    Place("브위샤자", "Bwishaza"),
                ),
                lat = -1.92, lng = 29.32,
            ),
            Place("카롱기", "Karongi", listOf("Kibuye", "키부예"), listOf(Place("기테시", "Gitesi")), lat = -2.15, lng = 29.39),
            Place("루시지", "Rusizi", listOf("Cyangugu"), lat = -2.48, lng = 28.9),
            Place(
                "가켄케", "Gakenke",
                subs = listOf(
                    Place("무사사", "Musasa"),
                    Place("룰리", "Ruli"),
                    Place("음빌리마", "Mbilima"),
                    Place("은카라", "Nkara"),
                ),
                lat = -1.7, lng = 29.78,
            ),
            Place("룰린도", "Rulindo", subs = listOf(Place("키니니", "Kinini")), lat = -1.73, lng = 30.0),
            Place("응고로레로", "Ngororero", subs = listOf(Place("카게요", "Kageyo")), lat = -1.87, lng = 29.65),
            Place("무항가", "Muhanga", subs = listOf(Place("숄리", "Sholi")), lat = -2.08, lng = 29.75),
            Place("부레라", "Burera", lat = -1.49, lng = 29.81),
            Place("가치보", "Gatsibo", listOf("가츠보"), lat = -1.6, lng = 30.45),
            Place("르와마가나", "Rwamagana", listOf("루와마가나"), lat = -1.97, lng = 30.35),
        ),
    )

    // source: Sweet Maria's "Burundi Coffee Overview"; Wikipedia "Kayanza Province" (communes) and "Commune of
    // Kabarore"; Sucafina and Sweet Maria's (Kibingo, Kayanza commune), Backstory (Businde, Kayanza), Long Miles Coffee
    // (Heza, Gitwe hill, Matongo; Bukeye, Muramvya, and its hills Musumba, Gaharo, Munyinya), Caroline's Coffee
    // (Mbirizi, Gatara), Tala (Buzira, Muruta), Mother Tongue (Matongo); Nduwayezu Therence (Gatukuza, Gashikanwa,
    // Ngozi); Raw Coffee Company and XLIII (Nyagishiru, Buhinyuza, Muyinga). Provinces as the trade names them, the
    // ones before the 2025 reform (which merged them into Buhumuza, Bujumbura, Burunga, Butanyerera and Gitega).
    // coords: enwiki province articles.
    private fun burundi() = Origin(
        "Burundi",
        listOf(
            Place(
                "카얀자", "Kayanza",
                subs = listOf(
                    Place("키빙고", "Kibingo"),
                    Place("부신데", "Businde"),
                    Place("카바로레", "Kabarore"),
                    Place("무루타", "Muruta"),
                    Place("가타라", "Gatara", subs = listOf(Place("음비리지", "Mbirizi"))),
                    Place("마통고", "Matongo", subs = listOf(Place("헤자", "Heza"))),
                    Place("무항가", "Muhanga"),
                    Place("가홈보", "Gahombo"),
                    Place("부타간즈와", "Butaganzwa"),
                    Place("랑고", "Rango"),
                ),
                lat = -2.92, lng = 29.62,
            ),
            Place(
                "응고지", "Ngozi",
                subs = listOf(
                    Place("가시카누아", "Gashikanwa", subs = listOf(Place("가투쿠자", "Gatukuza"))),
                ),
                lat = -2.91, lng = 29.83,
            ),
            Place("키룬도", "Kirundo", lat = -2.55, lng = 30.09),
            Place(
                "무잉가", "Muyinga",
                subs = listOf(
                    Place("부히뉴자", "Buhinyuza", subs = listOf(Place("냐기시루", "Nyagishiru"))),
                ),
                lat = -2.82, lng = 30.32,
            ),
            Place(
                "무람비야", "Muramvya",
                subs = listOf(
                    Place(
                        "부케예", "Bukeye",
                        subs = listOf(
                            Place("무숨바", "Musumba"),
                            Place("가하로", "Gaharo"),
                            Place("무니냐", "Munyinya"),
                        ),
                    ),
                ),
                lat = -3.25, lng = 29.63,
            ),
            Place("기테가", "Gitega", lat = -3.47, lng = 29.96),
            Place("카루지", "Karuzi", listOf("Karusi", "카루시"), lat = -3.13, lng = 30.08),
            Place("음와로", "Mwaro", lat = -3.51, lng = 29.7),
            Place("부반자", "Bubanza", lat = -3.15, lng = 29.37),
            Place("치비토케", "Cibitoke", lat = -2.83, lng = 29.17),
        ),
    )

    // source: Royal Coffee "Tanzania" region page (Karatu, Oldeani, Rungwe, Mbozi, Ileje, Songea, Mbinga); Atlas Coffee
    // Importers "The Vast Variety of Tanzanian Specialty Coffee" (Moshi, the Kilimanjaro foothills, the Ngorongoro
    // foothills of Arusha, Mbeya, Mbozi and Songwe); Wikipedia "Kilimanjaro Region" (Hai, Moshi, Rombo, Siha districts;
    // Machame in Hai), "Arusha Region" (Meru and Ngorongoro districts) and "Kagera Region" (Bukoba, Karagwe); Sucafina
    // "Tanzania" (Kagera). Mbozi and Ileje are in Songwe Region since 2016.
    // coords: enwiki region articles (Kilimanjaro, Arusha, Mbeya, Songwe, Ruvuma, Kagera).
    private fun tanzania() = Origin(
        "Tanzania",
        listOf(
            Place(
                "킬리만자로", "Kilimanjaro",
                subs = listOf(
                    Place("모시", "Moshi"),
                    Place("하이", "Hai", subs = listOf(Place("마차메", "Machame"))),
                    Place("롬보", "Rombo"),
                    Place("시하", "Siha"),
                ),
                lat = -4.13, lng = 37.81,
            ),
            Place(
                "아루샤", "Arusha",
                subs = listOf(
                    Place("카라투", "Karatu", subs = listOf(Place("올데아니", "Oldeani"))),
                    Place("메루", "Meru", listOf("Mount Meru", "메루산", "Arumeru", "아루메루")),
                    Place("응고롱고로", "Ngorongoro"),
                ),
                lat = -3.39, lng = 36.68,
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
            Place(
                "카게라", "Kagera",
                subs = listOf(
                    Place("부코바", "Bukoba"),
                    Place("카라궤", "Karagwe"),
                ),
                lat = -1.92, lng = 31.3,
            ),
        ),
    )

    // source: coffeegeography.com "Ugandan Coffee Regions"; Atlas Coffee Importers "Uganda"; Wikipedia "Bugisu
    // sub-region" (its districts) and "Kapchorwa District" (Sebei, Sipi Falls); Colipse "Uganda Coffee" (Kigezi:
    // Kisoro, Kabale; West Nile: Zombo, Paidha); New Vision and Burman Coffee (Okoro, Zombo). Korean: 에이션빈 (우간다
    // 마운트 엘곤), 매뉴팩트커피 (부기수 마운틴 엘곤), 카페프라임 (부기수).
    // coords: enwiki — Mount Elgon, Rwenzori Mountains, West Nile sub-region, Kigezi sub-region, Masaka District.
    private fun uganda() = Origin(
        "Uganda",
        listOf(
            Place(
                "마운트 엘곤", "Mount Elgon", listOf("엘곤", "엘곤산", "마운틴 엘곤", "Elgon", "Mt. Elgon", "Mt Elgon"),
                listOf(
                    Place(
                        "부기수", "Bugisu",
                        subs = listOf(
                            Place("음발레", "Mbale"),
                            Place("부두다", "Bududa"),
                            Place("시론코", "Sironko"),
                            Place("불람불리", "Bulambuli"),
                            Place("마나프와", "Manafwa"),
                            Place("나미신드와", "Namisindwa"),
                        ),
                    ),
                    Place(
                        "세베이", "Sebei",
                        subs = listOf(
                            Place("캅초르와", "Kapchorwa"),
                            Place("시피 폭포", "Sipi Falls", listOf("Sipi", "시피")),
                            Place("크웬", "Kween"),
                            Place("부크워", "Bukwo"),
                        ),
                    ),
                ),
                lat = 1.14, lng = 34.56,
            ),
            Place(
                "르웬조리", "Rwenzori", listOf("루웬조리", "Ruwenzori"),
                listOf(
                    Place("카세세", "Kasese"),
                    Place("분디부교", "Bundibugyo"),
                    Place("카바롤레", "Kabarole", listOf("Fort Portal", "포트포털")),
                ),
                lat = 0.39, lng = 29.87,
            ),
            Place(
                "웨스트 나일", "West Nile",
                subs = listOf(
                    Place("좀보", "Zombo", subs = listOf(Place("오코로", "Okoro"), Place("파이다", "Paidha"))),
                    Place("아루아", "Arua"),
                    Place("네비", "Nebbi"),
                ),
                lat = 3.0, lng = 31.2,
            ),
            Place(
                "키게지", "Kigezi",
                subs = listOf(
                    Place("키소로", "Kisoro"),
                    Place("카발레", "Kabale"),
                ),
                lat = -1.22, lng = 29.89,
            ),
            Place("마사카", "Masaka", lat = -0.37, lng = 31.7),
        ),
    )

    // source: Rikolto "Speciality coffee from Kivu and Ituri" (Kalehe, Kabare, Idjwi, Beni-Lubero, Ituri); Cafe Imports
    // "DR Congo" and Trabocca "SOPACDI Co-operative" (Tsheya-Minova, Kalehe, South Kivu).
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
                            Place("미노바", "Minova"),
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

    // source: Perfect Daily Grind "Exploring Zambia as a coffee origin"; Corner Coffee Store Zambia guide (Isoka,
    // Kasama, Nakonde, Muchinga).
    // coords: enwiki — Nyika Plateau, Muchinga Province, Northern Province, Zambia.
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
            Place(
                "북부주", "Northern Province", listOf("Northern"),
                listOf(
                    Place("카사마", "Kasama"),
                ),
                lat = -11.0, lng = 31.0,
            ),
        ),
    )

    // source: Sucafina "Malawi"; Wikipedia "Mzuzu Coffee Planters Cooperative Union Limited" and Mzuzu Coffee /
    // Sustainable Harvest (its cooperatives: Misuku Hills in Chitipa, Phoka Hills and Viphya in Rumphi, Nkhata Bay
    // Highlands, South East Mzimba, Ntchisi); Perfect Daily Grind "Exploring Malawi as a coffee origin" (Thyolo).
    // coords: enwiki — Mzuzu, Misuku Hills, Viphya Mountains, Rumphi (Phoka Hills), Nkhata Bay; Wikidata Thyolo
    // District (Q722515).
    private fun malawi() = Origin(
        "Malawi",
        listOf(
            Place("음주주", "Mzuzu", lat = -11.46, lng = 34.02),
            Place("미수쿠 힐스", "Misuku Hills", listOf("Misuku", "미수쿠", "Chitipa"), lat = -9.67, lng = 33.55),
            Place("비피아", "Viphya", listOf("Viphya Mountains"), lat = -13.06, lng = 34.19),
            Place("포카", "Phoka", listOf("Phoka Hills", "포카 힐스", "Rumphi"), lat = -11.02, lng = 33.87),
            Place("은카타베이", "Nkhata Bay", listOf("은카타 베이", "Nkhata Bay Highlands"), lat = -11.6, lng = 34.3),
            Place("촐로", "Thyolo", listOf("티올로"), lat = -16.17, lng = 35.17),
        ),
    )

    // source: Colipse "20 Best African Coffees" / Zimbabwe notes (Chipinge, Chimanimani, Mutare and Vumba, Honde
    // Valley).
    // coords: enwiki — Chipinge, Chimanimani, Honde Valley, Bvumba Mountains.
    private fun zimbabwe() = Origin(
        "Zimbabwe",
        listOf(
            Place("치핑게", "Chipinge", lat = -20.2, lng = 32.62),
            Place("치마니마니", "Chimanimani", lat = -19.8, lng = 32.86),
            Place("혼데 밸리", "Honde Valley", lat = -18.5, lng = 32.85),
            Place("붐바", "Vumba", listOf("Bvumba", "Mutare"), lat = -19.1, lng = 32.78),
        ),
    )

    // source: Wikipedia "Coffee production in Cameroon" (grown in the West, Northwest, Littoral and four more regions;
    // arabica on the Bamileke and Bamoun plateaus; the Santa estate in Bamenda; the early garden at Nkongsamba), "West
    // Region (Cameroon)".
    // coords: enwiki region articles.
    private fun cameroon() = Origin(
        "Cameroon",
        listOf(
            Place(
                "서부주", "West Region", listOf("West", "Ouest", "웨스트"),
                listOf(
                    Place("바밀레케", "Bamileke"),
                    Place("바뭄", "Bamoun", listOf("Bamoum", "Noun")),
                ),
                lat = 5.5, lng = 10.5,
            ),
            Place(
                "북서부주", "Northwest Region", listOf("North West", "Nord-Ouest"),
                listOf(
                    Place("바멘다", "Bamenda"),
                ),
                lat = 6.33, lng = 10.5,
            ),
            Place(
                "연안주", "Littoral Region", listOf("Littoral"),
                listOf(
                    Place("은콩삼바", "Nkongsamba"),
                ),
                lat = 4.0, lng = 10.0,
            ),
        ),
    )

    // source: map-dot region (Man, seat of the Tonkpi region). coords: enwiki Man, Ivory Coast.
    private fun coteDIvoire() = Origin(
        "Côte d'Ivoire",
        listOf(
            Place("만", "Man", listOf("Tonkpi"), lat = 7.4, lng = -7.55),
        ),
    )

    // source: map-dot region (Antsirabe, seat of Vakinankaratra). coords: enwiki Antsirabe.
    private fun madagascar() = Origin(
        "Madagascar",
        listOf(
            Place("안치라베", "Antsirabe", listOf("Vakinankaratra"), lat = -19.87, lng = 47.03),
        ),
    )

    // source: map-dot region; Ziama-Macenta protected geographical indication (Agence Ecofin, origin-gi.com), for the
    // coffee of Forest Guinea.
    // coords: enwiki Nzérékoré, Macenta.
    private fun guinea() = Origin(
        "Guinea",
        listOf(
            Place(
                "은제레코레", "Nzérékoré", listOf("Nzerekore", "N'Zérékoré", "Forest Guinea", "Guinée forestière"),
                lat = 7.75, lng = -8.82,
            ),
            Place("마센타", "Macenta", listOf("Ziama-Macenta", "Ziama", "지아마"), lat = 8.55, lng = -9.47),
        ),
    )
}
