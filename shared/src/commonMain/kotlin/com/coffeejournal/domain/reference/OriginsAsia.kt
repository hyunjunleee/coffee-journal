package com.coffeejournal.domain.reference

import com.coffeejournal.domain.reference.OriginRegions.Origin
import com.coffeejournal.domain.reference.OriginRegions.Place

/**
 * [OriginRegions] of Asia, Oceania and Yemen: one private function per country, each with its sources above it;
 * Indonesia's larger regions have a private function of their own.
 */
internal object OriginsAsia {
    val all: List<Origin> = listOf(
        indonesia(), vietnam(), india(), china(), thailand(), myanmar(), laos(), papuaNewGuinea(),
        philippines(), taiwan(), timorLeste(), yemen(),
    )

    // source: Sucafina "Indonesia" (regions Aceh, Sumatra, Kerinci, West Java, Flores, Sulawesi; South Sumatra, Papua);
    // Wikipedia "Coffee production in Indonesia" (Tapanuli, Lake Toba, Sidikalang, Lintong Nihuta, Takengon, Lake Laut
    // Tawar, Kerinci, Lampung, South Sumatra, West Sumatra, Ijen Plateau, Blawan, Jampit, Pancoer, Kayumas, Toraja,
    // Enrekang, Kalosi, Mamasa, Kintamani, Ngada, Baliem Valley, Wamena, Moanemani, Bengkulu); Wikipedia "Geographical
    // indications in Indonesia" (Gayo, Sumatera Lintong, Sidikalang, Simalungun, Pulo Samosir, Tanah Karo, Sumatera
    // Mandailing, Sipirok, Sumatera Koerintji, Semendo, Empat Lawang, Kepahiang, Rejang Lebong, Lampung robusta, Java
    // Preanger, Java Sindoro Sumbing, Temanggung, Java Ijen-Raung, Kintamani Bali, Pupuan Bali, Flores Bajawa, Flores
    // Manggarai, Kalosi Enrekang, Baliem Wamena; the robusta GI of Lampung is held in West Lampung, Way Kanan and
    // Tanggamus); Royal Coffee offers (Takengon Atu Lintang, Bener Meriah Gayo, Gayo Highland, Lintong, Lake Toba
    // Samosir, Mandheling Sibaganding Tua, Kerinci Gunung Tujuh, Java Kayumas, Toraja Sapan Minanga, Flores Bajawa);
    // Sample Coffee "Asman Gayo Mill" (Pegasing) and Willkin (Bebesen, Pegasing, Bies); Sweet Maria's "Onan Ganjang"
    // (Humbang Hasundutan); specialtycoffee.id "Arabica Puntang" (Puntang, Malabar, Papandayan, Halu) and "Minang Solok
    // coffee" (Mount Talang) and "Kerinci coffee" (Kayu Aro, Gunung Tujuh); Sweet Maria's "Sumatra coffee overview"
    // (Siborongborong, Dolok Sanggul); Trijaya Agro Lestari (Tanah Karo); MPIG Kopi Arabika Flores Manggarai (Colol);
    // Crop to Cup "Solok Radjo" (Lembah Gumanti); Covoya offers (Java Sunda Hejo Papandayan, Java Pangalengan, Java
    // Leles Garut); Genuine Origin "West Java Bandung" (Pangalengan, Ciwidey, Lembang, Garut). Korean: TYPICA Korea
    // (수마트라, 자바, 웨스트 자바, 술라웨시, 발리); roaster usage (만델링, 아체 가요, 가요 마운틴, 린통, 토라자, 칼로시, 킨타마니, 바자와, 케린치); kowiki (타켕온, 베네르
    // 메리아, 토바호, 솔록, 람풍주, 파가르알람, 이젠산).
    // coords: enwiki — Takengon (Aceh Gayo), Humbang Hasundutan Regency (North Sumatra, the Lintong regency), Solok
    // Regency (West Sumatra), Kerinci Regency, Pagar Alam (South Sumatra), Kepahiang Regency (Bengkulu), Liwa,
    // Indonesia (Lampung), Bondowoso Regency (Java: Ijen), Kintamani, Bali, Bajawa (Flores), Rantepao (Sulawesi:
    // Toraja), Wamena (Papua).
    private fun indonesia() = Origin(
        "Indonesia",
        listOf(
            acehGayo(),
            northSumatra(),
            Place(
                "서수마트라", "West Sumatra", listOf("Sumatera Barat", "웨스트 수마트라"),
                listOf(
                    Place(
                        "솔록", "Solok", listOf("Solok Minang", "Minang Solok", "솔록 미낭"),
                        listOf(
                            Place("구눙 탈랑", "Gunung Talang", listOf("Mount Talang", "Talang")),
                            Place("렘바 구만티", "Lembah Gumanti"),
                        ),
                    ),
                ),
                lat = -0.97, lng = 100.82,
            ),
            Place(
                "케린치", "Kerinci", listOf("Kerinchi", "Koerintji", "Mount Kerinci", "Kerinci Mountain", "Jambi", "잠비"),
                listOf(
                    Place("구눙 투주", "Gunung Tujuh"),
                    Place("카유 아로", "Kayu Aro"),
                ),
                lat = -2.08, lng = 101.48,
            ),
            Place(
                "남수마트라", "South Sumatra", listOf("Sumatera Selatan", "사우스 수마트라"),
                listOf(
                    Place("파가르알람", "Pagar Alam", listOf("Pagaralam")),
                    Place("스멘도", "Semendo", listOf("Semende")),
                    Place("엠팟 라왕", "Empat Lawang"),
                ),
                lat = -4.02, lng = 103.25,
            ),
            Place(
                "벵쿨루", "Bengkulu", listOf("븡쿨루"),
                listOf(
                    Place("케파히앙", "Kepahiang"),
                    Place("레장 레봉", "Rejang Lebong"),
                ),
                lat = -3.66, lng = 102.55,
            ),
            Place(
                "람풍", "Lampung", listOf("람풍주"),
                listOf(
                    Place("서람풍", "West Lampung", listOf("Lampung Barat", "웨스트 람풍"), listOf(Place("리와", "Liwa"))),
                    Place("탕가무스", "Tanggamus"),
                ),
                lat = -5.03, lng = 104.08,
            ),
            java(),
            Place(
                "발리", "Bali",
                subs = listOf(
                    Place("킨타마니", "Kintamani"),
                    Place("푸푸안", "Pupuan"),
                ),
                lat = -8.26, lng = 115.33,
            ),
            Place(
                "플로레스", "Flores",
                subs = listOf(
                    Place("바자와", "Bajawa", listOf("Ngada", "응아다")),
                    Place("망가라이", "Manggarai", listOf("Ruteng", "루텡"), listOf(Place("촐롤", "Colol"))),
                ),
                lat = -8.81, lng = 120.96,
            ),
            sulawesi(),
            Place(
                "파푸아", "Papua", listOf("Highland Papua", "Papua Pegunungan", "Irian Jaya"),
                listOf(
                    Place(
                        "발리엠 밸리", "Baliem Valley", listOf("Baliem", "발리엠", "Lembah Baliem"),
                        listOf(
                            Place("와메나", "Wamena"),
                        ),
                    ),
                    Place("모아네마니", "Moanemani", listOf("Kamu Valley")),
                ),
                lat = -4.1, lng = 138.95,
            ),
        ),
    )

    private fun acehGayo() = Place(
        "아체 가요", "Aceh Gayo",
        listOf(
            "Aceh", "아체", "아쩨", "Gayo", "가요", "Gayo Highlands", "Gayo Highland", "Gayo Mountain", "가요 마운틴",
        ),
        listOf(
            Place(
                "중부 아체", "Central Aceh", listOf("Aceh Tengah", "아체 텡아"),
                listOf(
                    Place("타켕온", "Takengon", listOf("타케곤", "Takengeun")),
                    Place("아투 린탕", "Atu Lintang"),
                    Place("페가싱", "Pegasing"),
                    Place("비에스", "Bies"),
                    Place("베베센", "Bebesen", listOf("Bebesan")),
                    Place("자공 제겟", "Jagong Jeget"),
                    Place("라웃 타와르 호수", "Lake Laut Tawar", listOf("Laut Tawar", "Lut Tawar", "Danau Laut Tawar")),
                ),
            ),
            Place("베네르 메리아", "Bener Meriah"),
            Place("가요 루에스", "Gayo Lues"),
        ),
        lat = 4.63, lng = 96.85,
    )

    private fun northSumatra() = Place(
        "북수마트라", "North Sumatra",
        listOf(
            "Sumatra (Mandheling)", "Sumatra", "Sumatera", "수마트라", "Sumatera Utara", "노스 수마트라", "Mandheling", "만델링",
            "만데링", "수마트라 만델링",
        ),
        listOf(
            Place(
                "훔방 하순두탄", "Humbang Hasundutan", listOf("Humbahas"),
                listOf(
                    Place("린통", "Lintong", listOf("Lintongnihuta", "Lintong Nihuta", "린통니후타")),
                    Place("돌록 상굴", "Dolok Sanggul", listOf("Doloksanggul")),
                    Place("오난 강장", "Onan Ganjang"),
                ),
            ),
            Place("다이리", "Dairi", subs = listOf(Place("시디칼랑", "Sidikalang"))),
            Place("시말룽운", "Simalungun", subs = listOf(Place("시바간딩", "Sibaganding", listOf("Sibaganding Tua")))),
            Place(
                "북타파눌리", "North Tapanuli", listOf("Tapanuli Utara", "Tapanuli", "타파눌리"),
                listOf(
                    Place("시보롱보롱", "Siborongborong"),
                ),
            ),
            Place("토바 호수", "Lake Toba", listOf("Toba", "토바", "토바호", "Danau Toba", "레이크 토바")),
            Place("사모시르", "Samosir"),
            Place("카로", "Karo", listOf("Tanah Karo", "타나 카로")),
            Place("만다일링 나탈", "Mandailing Natal", listOf("Mandailing", "만다일링")),
            Place("시피록", "Sipirok"),
        ),
        lat = 2.27, lng = 98.5,
    )

    private fun java() = Place(
        "자바", "Java", listOf("Jawa", "자와"),
        listOf(
            Place(
                "웨스트 자바", "West Java",
                listOf(
                    "Jawa Barat", "서자바", "Preanger", "Priangan", "프리앙안", "프리앙거", "Java Preanger", "자바 프리앙안",
                ),
                listOf(
                    Place("푼탕", "Puntang", listOf("Gunung Puntang", "Mount Puntang", "구눙 푼탕")),
                    Place("말라바르", "Malabar", listOf("Gunung Malabar", "Mount Malabar", "말라바")),
                    Place("팡알렝안", "Pangalengan"),
                    Place("할루", "Halu", listOf("Gunung Halu", "구눙 할루")),
                    Place("가룻", "Garut"),
                    Place("파판다얀", "Papandayan", listOf("Gunung Papandayan", "Mount Papandayan")),
                    Place("치위데이", "Ciwidey"),
                    Place("렘방", "Lembang"),
                ),
            ),
            Place(
                "센트럴 자바", "Central Java", listOf("Jawa Tengah", "중부 자바"),
                listOf(
                    Place("트망궁", "Temanggung", listOf("테망궁")),
                    Place("워노소보", "Wonosobo"),
                    Place("신도로", "Sindoro", listOf("Gunung Sindoro", "Mount Sindoro")),
                    Place("숨빙", "Sumbing", listOf("Gunung Sumbing", "Mount Sumbing")),
                ),
            ),
            Place(
                "이젠", "Ijen", listOf("Ijen Plateau", "Kawah Ijen", "Ijen Raung", "Java Ijen", "자바 이젠", "이젠 고원", "이젠산"),
                listOf(
                    Place("본도워소", "Bondowoso"),
                    Place("카유마스", "Kayumas"),
                    Place("블라완", "Blawan"),
                    Place("잠핏", "Jampit", listOf("Djampit")),
                    Place("판추르", "Pancoer", listOf("Pancur")),
                ),
            ),
        ),
        lat = -7.94, lng = 113.98,
    )

    private fun sulawesi() = Place(
        "술라웨시", "Sulawesi",
        listOf(
            "Sulawesi (Toraja)", "Celebes", "셀레베스", "Toraja", "토라자", "Sulawesi Toraja", "술라웨시 토라자",
        ),
        listOf(
            Place(
                "토라자", "Toraja",
                subs = listOf(
                    Place("타나 토라자", "Tana Toraja", listOf("Makale", "마칼레")),
                    Place("북토라자", "North Toraja", listOf("Toraja Utara", "토라자 우타라", "Rantepao", "란테파오")),
                    Place("사판", "Sapan", listOf("Sapan Minanga")),
                ),
            ),
            Place("엔레캉", "Enrekang", subs = listOf(Place("칼로시", "Kalosi", listOf("Kalossi")))),
            Place("마마사", "Mamasa"),
        ),
        lat = -2.97, lng = 119.9,
    )

    // source: JayArr "Vietnam coffee origin guide"; Heritage (Vietnam Airlines) "Lam Dong and valleys of Arabica"; USDA
    // FAS "Vietnam Coffee Annual" (Central Highlands provinces Dak Lak, Lam Dong, Dak Nong, Gia Lai); Happy Vietnam
    // (VNA) "Lam Dong reshapes coffee industry" (arabica in Da Lat and Lac Duong, robusta in Di Linh, Bao Loc, Lam Ha);
    // Mercanta "Lac Duong microlot"; Lien Viet Green (Lang Biang); Son Duong Coffee (Nam Ban);
    // vietnamcoffeesupplier.com (Cau Dat, Son La, Khe Sanh arabica); Coffee Concept "Arabica regions of Vietnam" (Son
    // La: Mai Son, Thuan Chau; Dien Bien: Muong Ang); Helena Coffee "Khe Sanh Arabica – Quang Tri" (Huong Hoa).
    // Province names as before the 2025 mergers, as the trade still uses them. Korean: roaster usage (닥락, 람동, 달랏);
    // kowiki (부온마투옷, 자라이성, 선라성, 디엔비엔성, 꽝찌성, 닥농성); otherwise the 외래어 표기법 for Vietnamese (꺼우덧 = Cầu Đất; the
    // Korean-language press also writes 까우닷).
    // coords: enwiki province articles; Quang Tri = Khe Sanh (the coffee town).
    private fun vietnam() = Origin(
        "Vietnam",
        listOf(
            Place(
                "닥락", "Dak Lak", listOf("Đắk Lắk", "Daklak", "다크락"),
                listOf(
                    Place("부온마투옷", "Buon Ma Thuot", listOf("Buôn Ma Thuột", "Ban Me Thuot", "반메투옷")),
                ),
                lat = 12.67, lng = 108.05,
            ),
            Place(
                "람동", "Lam Dong", listOf("Lâm Đồng", "럼동"),
                listOf(
                    Place(
                        "달랏", "Da Lat", listOf("Đà Lạt", "Dalat", "다랏"),
                        listOf(
                            Place("꺼우덧", "Cau Dat", listOf("Cầu Đất", "까우닷", "까우덧")),
                        ),
                    ),
                    Place(
                        "락즈엉", "Lac Duong", listOf("Lạc Dương"),
                        listOf(
                            Place("랑비앙", "Lang Biang", listOf("Langbiang")),
                        ),
                    ),
                    Place("럼하", "Lam Ha", listOf("Lâm Hà"), listOf(Place("남반", "Nam Ban"))),
                    Place("지린", "Di Linh", listOf("디린")),
                    Place("바오록", "Bao Loc", listOf("Bảo Lộc")),
                ),
                lat = 11.95, lng = 108.43,
            ),
            Place("자라이", "Gia Lai", listOf("지아라이", "잘라이"), lat = 13.75, lng = 108.25),
            Place("닥농", "Dak Nong", listOf("Đắk Nông", "Daknong"), lat = 11.98, lng = 107.7),
            Place(
                "선라", "Son La", listOf("Sơn La", "손라"),
                listOf(
                    Place("마이선", "Mai Son", listOf("Mai Sơn")),
                    Place("투언쩌우", "Thuan Chau", listOf("Thuận Châu")),
                ),
                lat = 21.17, lng = 104.0,
            ),
            Place(
                "디엔비엔", "Dien Bien", listOf("Điện Biên"),
                listOf(
                    Place("므엉앙", "Muong Ang", listOf("Mường Ảng")),
                ),
                lat = 21.38, lng = 103.02,
            ),
            Place(
                "꽝찌", "Quang Tri", listOf("Quảng Trị"),
                listOf(
                    Place("케산", "Khe Sanh", listOf("캐사인")),
                    Place("흐엉호아", "Huong Hoa", listOf("Hướng Hóa")),
                ),
                lat = 16.62, lng = 106.73,
            ),
        ),
    )

    // source: Coffee Board of India, 13 regional coffees (coffeeboard.gov.in "Coffee regions of India"; PIB "India's
    // Coffee Story from Farm to Cup"); Kodagu district's Coffee Board page (Madikeri, Virajpet, Somwarpet taluks);
    // Wikipedia "Coffee production in India" (Balehonnur, Yercaud, Kodaikanal, Thandigudi, Karbi Anglong, Diphu) and
    // the coffee towns' articles (Mudigere, Koppa, Sakleshpur, Kalpetta, Idukki, Coonoor, Valparai). Korean: roaster
    // usage (치크마갈루르, 쿠르그, 바바부단기리); 몬순 말라바르 is a process, not a region.
    // coords: enwiki — Chikmagalur, Kodagu district, Baba Budangiri, Sakleshpur (Manjarabad), Biligiriranga Hills,
    // Wayanad district, Idukki district (Travancore), Nilgiris district, Palani Hills, Servarayan Hills, Anaimalai
    // Hills, Araku Valley, Karbi Anglong district (Brahmaputra).
    private fun india() = Origin(
        "India",
        listOf(
            Place(
                "치크마갈루르", "Chikmagalur", listOf("치크마갈루루", "Chikkamagaluru"),
                listOf(
                    Place("무디게레", "Mudigere"),
                    Place("코파", "Koppa"),
                    Place("발레혼누르", "Balehonnur"),
                ),
                lat = 13.33, lng = 75.79,
            ),
            Place(
                "쿠르그", "Coorg", listOf("코다구", "Kodagu"),
                listOf(
                    Place("마디케리", "Madikeri", listOf("Mercara", "머케라")),
                    Place("비라지펫", "Virajpet", listOf("Virajpete")),
                    Place("솜와르펫", "Somwarpet"),
                ),
                lat = 12.42, lng = 75.74,
            ),
            Place(
                "바바부단기리", "Bababudangiris", listOf("Bababudangiri", "Baba Budan Giri", "바바 부단"),
                lat = 13.42, lng = 75.76,
            ),
            Place("만자라바드", "Manjarabad", subs = listOf(Place("사클레시푸르", "Sakleshpur")), lat = 12.89, lng = 75.72),
            Place("빌리기리", "Biligiris", listOf("Biligiri Rangan Hills", "BR Hills"), lat = 11.99, lng = 77.14),
            Place("와야나드", "Wayanad", listOf("Wayanaad"), listOf(Place("칼페타", "Kalpetta")), lat = 11.63, lng = 76.09),
            Place("트라반코르", "Travancore", subs = listOf(Place("이두키", "Idukki")), lat = 9.85, lng = 76.94),
            Place(
                "닐기리", "Nilgiris", listOf("Nilgiri", "닐기리스"),
                listOf(
                    Place("쿠누르", "Coonoor"),
                ),
                lat = 11.4, lng = 76.7,
            ),
            Place(
                "풀니", "Pulneys", listOf("Pulney Hills", "Palani Hills", "팔라니"),
                listOf(
                    Place("코다이카날", "Kodaikanal"),
                    Place("탄디구디", "Thandigudi", listOf("Thandikudi")),
                ),
                lat = 10.2, lng = 77.47,
            ),
            Place(
                "셰바로이", "Shevaroys", listOf("Sheveroys", "Shevaroy Hills", "Servarayan Hills", "Yercaud", "예르카우드"),
                lat = 11.83, lng = 78.27,
            ),
            Place(
                "아나말라이", "Anamalais", listOf("Anamalai", "Anaimalai Hills"),
                listOf(
                    Place("발파라이", "Valparai"),
                ),
                lat = 10.17, lng = 77.06,
            ),
            Place("아라쿠 밸리", "Araku Valley", listOf("Araku", "아라쿠"), lat = 18.33, lng = 82.87),
            Place(
                "브라마푸트라", "Brahmaputra",
                subs = listOf(
                    Place("카르비 앙롱", "Karbi Anglong", subs = listOf(Place("디푸", "Diphu"))),
                ),
                lat = 26.18, lng = 93.57,
            ),
        ),
    )

    // source: Sucafina "China"; GoKunming "Coffee trade in Yunnan" (Pu'er, Baoshan, Dehong, Lincang, Xishuangbanna);
    // Wikipedia "Coffee production in China" (Pu'er, Menglian, Baoshan, Dehong, Hainan), "Ruili", "Mangshi" (Dehong's
    // coffee); Penguin Specialty "China Yunnan Lujiangba Arabica Baoshan"; Sixth Tone "A coffee of exile and memory
    // comes home to Hainan" and Xinhua "Coffee brews industrial revival in island province" (Xinglong in Wanning,
    // Fushan in Chengmai). Korean: kowiki (푸얼시, 바오산시, 더훙, 린창시, 시솽반나, 멍롄, 루이리시, 망시, 하이난성, 완닝시, 청마이현).
    // coords: enwiki — Pu'er City (Yunnan, about half of its coffee), Wanning (Hainan, Xinglong coffee).
    private fun china() = Origin(
        "China",
        listOf(
            Place(
                "윈난", "Yunnan", listOf("운남"),
                listOf(
                    Place("푸얼", "Pu'er", listOf("Puer", "보이", "Simao", "쓰마오"), listOf(Place("멍롄", "Menglian"))),
                    Place("바오산", "Baoshan", listOf("보산"), listOf(Place("루장", "Lujiang", listOf("Lujiangba")))),
                    Place("린창", "Lincang", listOf("임창")),
                    Place("더훙", "Dehong", listOf("덕굉"), listOf(Place("루이리", "Ruili"), Place("망시", "Mangshi"))),
                    Place("시솽반나", "Xishuangbanna", listOf("서쌍판납")),
                ),
                lat = 22.79, lng = 100.98,
            ),
            Place(
                "하이난", "Hainan",
                subs = listOf(
                    Place("싱룽", "Xinglong"),
                    Place("푸산", "Fushan"),
                ),
                lat = 18.8, lng = 110.4,
            ),
        ),
    )

    // source: Sprudge "Guide to coffee in Chiang Rai"; Wikipedia "Coffee production in Thailand" (Doi Chang, Doi Tung,
    // Chumphon robusta), "Wawi" (the Mae Suai sub-district of Doi Chaang and Doi Wawee); Doi Chaang Coffee "About us"
    // (Baan Doi Chang, Wawee); Wawee Coffee (Doi Wawee); Burman Coffee "Thailand Doi Pangkhon" and "Thailand Lanna
    // Coffee Doi Inthanon"; Green Coffee Collective (Doi Saket); Punthai Coffee (Mae Chaem); Chiang Rai Times "Chiang
    // Rai specialty coffee" (Mae Salong); Paradise Roasters "Thailand Nan Doi Phukhaa"; THAILAND.GO.TH "Inviting coffee
    // lovers to Mae Hong Son" (Pang Mapha). Korean: roaster usage (도이창, 도이뚱); otherwise the 외래어 표기법 for Thai.
    // coords: enwiki province articles.
    private fun thailand() = Origin(
        "Thailand",
        listOf(
            Place(
                "치앙마이", "Chiang Mai", listOf("Chiangmai"),
                listOf(
                    Place("도이 사켓", "Doi Saket", listOf("Doi Saked", "도이사켓")),
                    Place("도이 인타논", "Doi Inthanon", listOf("Inthanon", "도이인타논")),
                    Place("매쨈", "Mae Chaem", listOf("매챔")),
                ),
                lat = 18.84, lng = 98.97,
            ),
            Place(
                "치앙라이", "Chiang Rai", listOf("Chiangrai"),
                listOf(
                    Place("도이창", "Doi Chang", listOf("Doi Chaang", "도이 창")),
                    Place("도이 와위", "Doi Wawee", listOf("Wawee", "Wawi", "와위")),
                    Place("도이뚱", "Doi Tung", listOf("도이 퉁", "도이퉁")),
                    Place("도이 매살롱", "Doi Mae Salong", listOf("Mae Salong", "매살롱")),
                    Place("도이 빵콘", "Doi Pangkhon", listOf("Pangkhon", "Pang Khon", "빵콘")),
                ),
                lat = 19.9, lng = 99.82,
            ),
            Place("매홍손", "Mae Hong Son", subs = listOf(Place("빵마파", "Pang Mapha")), lat = 19.29, lng = 97.96),
            Place(
                "난", "Nan", listOf("난주"),
                listOf(
                    Place("도이 푸카", "Doi Phu Kha", listOf("Doi Phukhaa", "Phu Kha")),
                ),
                lat = 18.77, lng = 100.78,
            ),
            Place("춤폰", "Chumphon", lat = 10.49, lng = 99.18),
        ),
    )

    // source: Sucafina "Ywangan fully washed"; JayArr "Myanmar coffee: Shan State"; TYPICA Korea "Biome Coffee"
    // producer story (유왕간, 핀우린, 샨 주); Myanmar coffee development plan zones via MyanTrade (Ywangan, Pindaya, Pyin Oo
    // Lwin); Wikipedia "Pyin Oo Lwin". Korean: TYPICA Korea; kowiki (삔울륀, 삔더야, 만달레도).
    // coords: enwiki — Ywangan Township (Shan State), Pyin Oo Lwin (Mandalay Region).
    private fun myanmar() = Origin(
        "Myanmar",
        listOf(
            Place(
                "샨주", "Shan State", listOf("샨 주", "Shan", "샨"),
                listOf(
                    Place("유왕간", "Ywangan", listOf("이왕안")),
                    Place("삔더야", "Pindaya", listOf("핀다야")),
                ),
                lat = 21.16, lng = 96.44,
            ),
            Place(
                "만달레이", "Mandalay Region", listOf("Mandalay", "만달레도"),
                listOf(
                    Place("핀우린", "Pyin Oo Lwin", listOf("삔울륀", "핀우른", "Pyin U Lwin", "Pyinoolwin", "Maymyo", "메이묘")),
                ),
                lat = 22.03, lng = 96.46,
            ),
        ),
    )

    // source: Wikipedia "Coffee production in Laos" (Bolaven Plateau, Paksong), "Paksong"; laoscoffee.org "Bolaven
    // Plateau". Korean: roaster usage (볼라벤, 팍송); kowiki 볼라웬고원.
    // coords: enwiki Paksong (the plateau's coffee town).
    private fun laos() = Origin(
        "Laos",
        listOf(
            Place(
                "볼라벤 고원", "Bolaven Plateau", listOf("볼라벤", "Bolaven", "볼라웬고원", "Boloven"),
                listOf(
                    Place("팍송", "Paksong", listOf("Pakxong")),
                ),
                lat = 15.18, lng = 106.24,
            ),
        ),
    )

    // source: PNG Coffee Industry Corporation "coffee growing areas" (cic.org.pg); Wikipedia "Coffee production in
    // Papua New Guinea" (Aiyura, Simbu, Morobe), "Eastern Highlands Province", "Jiwaka Province" (Banz); Royal Coffee
    // offers (Western Highlands, Nebilyer Valley; New Guinea Highlands in Goroka); Burman Coffee "Kainantu Konkua"
    // (Kainantu, Okapa, Obura-Wonenara); Sample Coffee "Marawaka"; Covoya "PNG A/X Lufa"; Mercanta "Introducing: Papua
    // New Guinea" (Rika Rika in Henganofi and Okapa, Atuwauka in Asaro); InterAmerican "Korofeigu Organic" (Bena Bena);
    // TYPICA Korea "Alpha Coffee" (아유라, 오카파, 오브라).
    // coords: enwiki — Eastern Highlands Province, Mount Hagen (Western Highlands), Morobe Province, Enga Province;
    // Wikidata Jiwaka Province (Q1400625), Chimbu Province (Q599448).
    private fun papuaNewGuinea() = Origin(
        "Papua New Guinea",
        listOf(
            Place(
                "이스턴 하이랜드", "Eastern Highlands", listOf("동부 고원주", "EHP"),
                listOf(
                    Place("고로카", "Goroka"),
                    Place(
                        "카이난투", "Kainantu",
                        subs = listOf(
                            Place("아유라", "Aiyura", listOf("Ayura", "Aiyura Valley", "아이유라")),
                        ),
                    ),
                    Place("오카파", "Okapa"),
                    Place("오브라", "Obura", listOf("Obura-Wonenara", "오부라"), listOf(Place("마라와카", "Marawaka"))),
                    Place("헨가노피", "Henganofi"),
                    Place("루파", "Lufa"),
                    Place("아사로", "Asaro", listOf("Asaro Valley")),
                    Place("베나베나", "Bena Bena", listOf("Benabena", "Bena")),
                ),
                lat = -6.07, lng = 145.39,
            ),
            Place(
                "웨스턴 하이랜드", "Western Highlands", listOf("서부 고원주", "WHP"),
                listOf(
                    Place("마운트 하겐", "Mount Hagen", listOf("Mt. Hagen", "하겐")),
                    Place("와기 밸리", "Wahgi Valley", listOf("Waghi Valley", "Waghi", "Wahgi")),
                    Place("네빌리어 밸리", "Nebilyer Valley", listOf("Nebilyer")),
                ),
                lat = -5.86, lng = 144.24,
            ),
            Place("지와카", "Jiwaka", subs = listOf(Place("반즈", "Banz")), lat = -6.0, lng = 144.58),
            Place("심부", "Simbu", listOf("Chimbu", "침부"), lat = -6.33, lng = 144.92),
            Place("모로베", "Morobe", lat = -6.83, lng = 146.67),
            Place("엥가", "Enga", lat = -5.5, lng = 143.5),
        ),
    )

    // source: ECHOstore "Coffee Country: Philippine coffee regions" (Benguet, Sagada, Kalinga, Batangas and Lipa,
    // Cavite and Amadeo, Mount Apo, Bukidnon with Mount Kitanglad and Mount Kalatungan, Mount Matutum, Sultan Kudarat
    // and Kalamansig); Wikipedia "Coffee production in the Philippines", "Kapeng barako", "Benguet coffee", "Sagada
    // coffee"; Si'jop Capital sourcing (Atok, Kapangan); Manila Bulletin on Kapatagan (Mount Apo); Philippine Coffee
    // Board "Mindanao, coffee country". Korean: 외래어 표기법 for Filipino.
    // coords: enwiki — Batangas, Amadeo, Cavite, La Trinidad, Benguet, Sagada, Kalinga (province), Mount Apo, Bukidnon,
    // Mount Matutum, Sultan Kudarat.
    private fun philippines() = Origin(
        "Philippines",
        listOf(
            Place(
                "바탕가스", "Batangas", listOf("Barako", "Kapeng Barako", "바라코"),
                listOf(
                    Place("리파", "Lipa"),
                ),
                lat = 13.83, lng = 121.0,
            ),
            Place("카비테", "Cavite", subs = listOf(Place("아마데오", "Amadeo")), lat = 14.17, lng = 120.93),
            Place(
                "벵겟", "Benguet",
                subs = listOf(
                    Place("아톡", "Atok"),
                    Place("카팡안", "Kapangan"),
                ),
                lat = 16.46, lng = 120.59,
            ),
            Place("사가다", "Sagada", listOf("Mountain Province"), lat = 17.08, lng = 120.9),
            Place("칼링가", "Kalinga", lat = 17.43, lng = 121.28),
            Place(
                "아포산", "Mount Apo", listOf("Mt. Apo", "마운트 아포"),
                listOf(
                    Place("카파타간", "Kapatagan"),
                ),
                lat = 6.99, lng = 125.27,
            ),
            Place(
                "부키드논", "Bukidnon",
                subs = listOf(
                    Place("키탕라드산", "Mount Kitanglad", listOf("Kitanglad", "Mt. Kitanglad")),
                    Place("칼라퉁안산", "Mount Kalatungan", listOf("Kalatungan", "Mt. Kalatungan")),
                ),
                lat = 7.92, lng = 125.08,
            ),
            Place("마투툼산", "Mount Matutum", listOf("Mt. Matutum", "Matutum"), lat = 6.43, lng = 125.11),
            Place("술탄 쿠다라트", "Sultan Kudarat", subs = listOf(Place("칼라만시그", "Kalamansig")), lat = 6.55, lng = 124.28),
        ),
    )

    // source: Taiwan Agriculture Tourism "Exploring hidden coffee treasures" (Gukeng and its Shibi Village, Alishan and
    // its Leye Village, Guoxing in Nantou, Pingtung, Hualien); Wikipedia "Dongshan District, Tainan" (famous for its
    // coffee); Perfect Daily Grind "A guide to the Taiwanese coffee sector" and Taiwan Panorama "Making the rounds of
    // Taiwan's coffee estates" (Pingtung Taiwu, Hualien Ruisui). Korean: kowiki (아리산, 구컹, 핑둥현, 난터우현, 화롄현).
    // coords: enwiki — Alishan (Chiayi), Gukeng, Dongshan District (Tainan), Pingtung County, Nantou County, Hualien
    // County.
    private fun taiwan() = Origin(
        "Taiwan",
        listOf(
            Place(
                "아리산", "Alishan", listOf("알리산", "Ali Mountain"),
                listOf(
                    Place("러예", "Leye"),
                ),
                lat = 23.35, lng = 120.8,
            ),
            Place("구컹", "Gukeng", listOf("Yunlin", "윈린"), listOf(Place("스비", "Shibi")), lat = 23.65, lng = 120.57),
            Place("둥산", "Dongshan", listOf("Tainan Dongshan"), lat = 23.28, lng = 120.44),
            Place("핑둥", "Pingtung", listOf("Pingdong", "핑퉁"), listOf(Place("타이우", "Taiwu")), lat = 22.68, lng = 120.49),
            Place("난터우", "Nantou", subs = listOf(Place("궈싱", "Guoxing")), lat = 23.9, lng = 120.69),
            Place("화롄", "Hualien", listOf("Hualian"), listOf(Place("루이쑤이", "Ruisui")), lat = 23.98, lng = 121.6),
        ),
    )

    // source: Royal Coffee "Timor-Leste Ermera Letefoho"; Sucafina "Railaco Natural"; Numero Uno "Timor-Leste Atsabe
    // washed"; Wikipedia "Ermera Municipality" (administrative posts Atsabe, Ermera, Hatulia, Letefoho, Railaco),
    // "Fatubessi, Ermera" (Hatulia's former SAPT coffee plantation), "Coffee industry of Timor-Leste" (Ermera, Liquica,
    // Aileu, Ainaro, Manufahi); Tourism Timor-Leste coffee (Maubisse, Aileu, Ainaro); 라멜라우 커피 (Mount Ramelau). Korean:
    // roaster usage (에르메라, 레테포호); kowiki (아일레우, 아이나루, 리키사, 마누파이).
    // coords: enwiki municipality articles.
    private fun timorLeste() = Origin(
        "Timor-Leste",
        listOf(
            Place(
                "에르메라", "Ermera",
                subs = listOf(
                    Place("레테포호", "Letefoho"),
                    Place("하툴리아", "Hatulia", subs = listOf(Place("파투베시", "Fatubessi"))),
                    Place("라일라코", "Railaco"),
                    Place("아차베", "Atsabe"),
                ),
                lat = -8.83, lng = 125.38,
            ),
            Place("아일레우", "Aileu", lat = -8.72, lng = 125.57),
            Place(
                "아이나로", "Ainaro", listOf("아이나루"),
                listOf(
                    Place("마우비세", "Maubisse"),
                    Place("라멜라우", "Ramelau", listOf("Mount Ramelau", "Tatamailau")),
                ),
                lat = -9.08, lng = 125.48,
            ),
            Place("리키사", "Liquiçá", listOf("Liquica"), lat = -8.68, lng = 125.2),
            Place("마누파이", "Manufahi", listOf("Same"), lat = -9.0, lng = 125.78),
        ),
    )

    // source: Hamdan Coffee "Yemeni coffee regions explained"; Coffeeness / JayArr Yemen guides (Hayma Dakhiliya, Hayma
    // Kharijiya, Bani Matar, Bani Ismail, Haraaz, Yafa, Khawlan, Anis, Sanani); Royal Coffee "Yemen green coffee export
    // review" (Bani Mater, Bani Hammad, Bura'a, Haraaz, Haimateen, the Udain region); Alliance for Coffee Excellence
    // "Best of Yemen 2025" (Hayma Dakhiliya, Hayma Kharijiya, Maghrib Ans, Badan); Qima Coffee "Yemen" (Sana'a, Dhamar,
    // Ibb, Mahwit, Raymah, Taizz); coffeebi "Coffee Arabica: the identity of Yemen" (Sanaa, Hajjah, Sa'dah, Al Mahweet,
    // Bura'a, Raymah, Yafea); Sweet Maria's "Yemen coffee overview" (Raimi, Ibb); Wikipedia "Dhale Governorate" (coffee
    // its main crop). Korean: 커피 리브레 (하라즈), 빈브라더스 (마나카 하라즈), 카페뮤제오 (모카 마타리); kowiki (라이마주, 하자주, 이브주, 달리주, 사다주).
    // coords: enwiki — Jabal Haraz, Bani Matar district, Al Haymah Ad Dakhiliyah district, Bani Ismail, Yafa'a, Sanaa,
    // Khawlan district, Dawran Aness district (Anis), Al Jabin district (Raymah), Al Mahwit, Hajjah Governorate, Ibb,
    // Dhale, Saada, Bani Hammad, Jabal Bura (Bura'a).
    private fun yemen() = Origin(
        "Yemen",
        listOf(
            Place(
                "하라즈", "Haraz", listOf("Haraaz", "Harazi", "하라지"),
                listOf(
                    Place("마나카", "Manakha", listOf("Manakhah")),
                ),
                lat = 15.17, lng = 43.75,
            ),
            Place(
                "바니 마타르", "Bani Matar", listOf("Bani Mattar", "Mattari", "Matari", "마타리", "Mocha Mattari", "모카 마타리"),
                lat = 15.17, lng = 44.08,
            ),
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
            Place("사나", "Sana'a", listOf("Sanani", "사나니", "Sanaa"), lat = 15.35, lng = 44.21),
            Place("카울란", "Khawlan", lat = 15.27, lng = 44.77),
            Place(
                "아니스", "Anis", listOf("Anisi", "Anesi", "아니시", "Ans"),
                listOf(
                    Place("마그리브 안스", "Maghrib Ans", listOf("Maghrib Anis")),
                ),
                lat = 14.83, lng = 44.11,
            ),
            Place("라이마", "Raymah", listOf("Raimi", "Raima", "라이미"), lat = 14.69, lng = 43.62),
            Place("마흐위트", "Mahwit", listOf("Al Mahwit", "Mahweet", "마위트"), lat = 15.47, lng = 43.55),
            Place("하자", "Hajjah", listOf("Hajja"), lat = 15.7, lng = 43.6),
            Place(
                "이브", "Ibb",
                subs = listOf(
                    Place("우다인", "Udayn", listOf("Al Udayn", "Udain", "Udaini", "Odaini", "우다이니")),
                    Place("바단", "Ba'dan", listOf("Badan")),
                ),
                lat = 13.97, lng = 44.17,
            ),
            Place("달레", "Al Dhale'e", listOf("Dhale", "Ad Dali'", "Al Dhale", "달리"), lat = 13.7, lng = 44.73),
            Place("사다", "Saada", listOf("Sa'dah", "Saadah"), lat = 16.94, lng = 43.76),
            Place("바니 함마드", "Bani Hammad", lat = 13.29, lng = 44.09),
            Place("부라아", "Bura'a", listOf("Bura", "Jabal Bura", "부라"), lat = 14.95, lng = 43.5),
        ),
    )
}
