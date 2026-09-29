package com.coffeejournal.domain.reference

import com.coffeejournal.domain.reference.OriginRegions.Origin
import com.coffeejournal.domain.reference.OriginRegions.Place

/** [OriginRegions] of the Americas (Central, South and the Caribbean): one private function per country, each with its sources above it. */
internal object OriginsAmericas {
    val all: List<Origin> = listOf(
        brazil(), colombia(), peru(), ecuador(), bolivia(), mexico(), guatemala(), honduras(), elSalvador(),
        nicaragua(), costaRica(), panama(), jamaica(), dominicanRepublic(), cuba(), haiti(), venezuela(), puertoRico(), hawaii(),
        guyana(),
    )

    // source: Cafe Imports "Brazil"; greencoffeebrazil.net "Brazilian coffee regions" (DO/IG names: Cerrado Mineiro,
    // Mantiqueira de Minas, Alta Mogiana, Matas de Minas, Caparaó, Montanhas do Espírito Santo); Sucafina "Santa Serra
    // Mantiqueira de Minas"; Korean: 커피 리브레 / 커피플랜트 (세하도), 챕터원커피 (몬테 까르멜로). Municipalities: common trade usage.
    // coords: enwiki — Cerrado = Patrocínio, Sul de Minas = Varginha, Mogiana = Franca, Matas de Minas = Manhuaçu,
    // Chapada Diamantina = Piatã, Espírito Santo = Venda Nova do Imigrante, Caparaó = Caparaó National Park.
    private fun brazil() = Origin(
        "Brazil",
        listOf(
            Place(
                "세하도", "Cerrado", listOf("Cerrado Mineiro", "세하도 미네이루", "세라도", "Cerrado de Minas"),
                listOf(
                    Place("파트로시니오", "Patrocínio", listOf("Patrocinio", "파트로시니우")),
                    Place("카르모 도 파라나이바", "Carmo do Paranaíba", listOf("Carmo do Paranaiba", "카르무 두 파라나이바")),
                    Place("몬테 카르멜로", "Monte Carmelo", listOf("몬테 까르멜로", "몬치 카르멜루")),
                    Place("아라과리", "Araguari"),
                ),
                lat = -18.93, lng = -46.98,
            ),
            Place(
                "술데미나스", "Sul de Minas", listOf("술 데 미나스", "술 지 미나스", "South Minas", "South of Minas"),
                listOf(
                    Place(
                        "만티케이라 데 미나스", "Mantiqueira de Minas", listOf("만치케이라 지 미나스", "만티케이라", "Mantiqueira"),
                        listOf(
                            Place("카르모 데 미나스", "Carmo de Minas", listOf("카르무 지 미나스")),
                            Place("크리스티나", "Cristina"),
                        ),
                    ),
                    Place("산토 안토니오 도 암파로", "Santo Antônio do Amparo", listOf("Santo Antonio do Amparo", "산투 안토니우 두 암파루")),
                    Place("트레스 폰타스", "Três Pontas", listOf("Tres Pontas")),
                    Place("바르지냐", "Varginha"),
                ),
                lat = -21.55, lng = -45.43,
            ),
            Place(
                "모지아나", "Mogiana", listOf("Mogiana Paulista"),
                listOf(
                    Place(
                        "알타 모지아나", "Alta Mogiana", listOf("High Mogiana"),
                        listOf(
                            Place("프랑카", "Franca"),
                            Place("페드레굴류", "Pedregulho", listOf("페드레굴료")),
                            Place("이비라시", "Ibiraci"),
                        ),
                    ),
                ),
                lat = -20.54, lng = -47.4,
            ),
            Place(
                "마타스 데 미나스", "Matas de Minas", listOf("마타스 지 미나스"),
                listOf(
                    Place("아라퐁가", "Araponga"),
                    Place("마누아수", "Manhuaçu", listOf("Manhuacu")),
                ),
                lat = -20.26, lng = -42.03,
            ),
            Place(
                "샤파다 디아만티나", "Chapada Diamantina", listOf("샤파다 지아만치나", "샤파다", "Chapada"),
                listOf(
                    Place("피아타", "Piatã", listOf("Piata")),
                    Place("이비코아라", "Ibicoara"),
                    Place("무쿠제", "Mucugê", listOf("Mucuge")),
                ),
                lat = -13.15, lng = -41.77,
            ),
            Place(
                "에스피리토 산토", "Espírito Santo", listOf("Espirito Santo", "이스피리투 산투"),
                listOf(
                    Place(
                        "몬타냐스 도 에스피리토 산토", "Montanhas do Espírito Santo", listOf("Montanhas do Espirito Santo"),
                        listOf(
                            Place("벤다 노바 도 이미그란테", "Venda Nova do Imigrante"),
                            Place("도밍고스 마르틴스", "Domingos Martins"),
                        ),
                    ),
                ),
                lat = -20.34, lng = -41.13,
            ),
            Place("카파라오", "Caparaó", listOf("Caparao"), lat = -20.43, lng = -41.79),
        ),
    )

    // source: Federación Nacional de Cafeteros, departmental committees (federaciondecafeteros.org/estructura);
    // ko.wikipedia 우일라주 (Pitalito and its neighbours); Korean: 커피 리브레 (우일라), 솔리드스트라이프 (피탈리토), 원더룸
    // (라 플라타). Other municipalities: common trade usage.
    // coords: enwiki department articles ("Huila Department" …); Sierra Nevada = "Sierra Nevada de Santa Marta".
    private fun colombia() = Origin(
        "Colombia",
        listOf(
            Place(
                "우일라", "Huila", listOf("후일라"),
                listOf(
                    Place("피탈리토", "Pitalito", subs = listOf(Place("브루셀라스", "Bruselas"))),
                    Place("산 아구스틴", "San Agustín", listOf("San Agustin")),
                    Place("아세베도", "Acevedo"),
                    Place("라 플라타", "La Plata"),
                    Place("가르손", "Garzón", listOf("Garzon")),
                    Place("팔레스티나", "Palestina"),
                    Place("이스노스", "Isnos"),
                    Place("수아사", "Suaza"),
                    Place("티마나", "Timaná", listOf("Timana")),
                    Place("라 아르헨티나", "La Argentina"),
                    Place("오포라파", "Oporapa"),
                    Place("히간테", "Gigante"),
                ),
                lat = 3.0, lng = -75.3,
            ),
            Place(
                "나리뇨", "Nariño", listOf("Narino"),
                listOf(
                    Place("부에사코", "Buesaco"),
                    Place("라 우니온", "La Unión", listOf("La Union")),
                    Place("산 로렌소", "San Lorenzo"),
                    Place("엘 타블론 데 고메스", "El Tablón de Gómez", listOf("El Tablon de Gomez", "El Tablón", "엘 타블론")),
                    Place("콘사카", "Consacá", listOf("Consaca")),
                    Place("산도나", "Sandoná", listOf("Sandona")),
                    Place("라 크루스", "La Cruz"),
                ),
                lat = 1.17, lng = -77.27,
            ),
            Place(
                "카우카", "Cauca",
                subs = listOf(
                    Place("포파얀", "Popayán", listOf("Popayan")),
                    Place("인사", "Inzá", listOf("Inza")),
                    Place("피엔다모", "Piendamó", listOf("Piendamo")),
                    Place("모랄레스", "Morales"),
                    Place("엘 탐보", "El Tambo"),
                    Place("카히비오", "Cajibío", listOf("Cajibio")),
                    Place("팀비오", "Timbío", listOf("Timbio")),
                ),
                lat = 2.45, lng = -76.62,
            ),
            Place(
                "톨리마", "Tolima",
                subs = listOf(
                    Place("플라나다스", "Planadas"),
                    Place("아타코", "Ataco"),
                    Place("차파랄", "Chaparral"),
                    Place("리오블랑코", "Rioblanco"),
                    Place("이바게", "Ibagué", listOf("Ibague")),
                ),
                lat = 4.43, lng = -75.23,
            ),
            Place(
                "안티오키아", "Antioquia", listOf("안티오퀴아"),
                listOf(
                    Place("우라오", "Urrao"),
                    Place("헤리코", "Jericó", listOf("Jerico")),
                    Place("안데스", "Andes"),
                    Place("시우다드 볼리바르", "Ciudad Bolívar", listOf("Ciudad Bolivar")),
                    Place("베타니아", "Betania"),
                ),
                lat = 6.22, lng = -75.57,
            ),
            Place(
                "킨디오", "Quindío", listOf("Quindio"),
                listOf(
                    Place("아르메니아", "Armenia"),
                    Place("필란디아", "Filandia"),
                    Place("살렌토", "Salento"),
                    Place("칼라르카", "Calarcá", listOf("Calarca")),
                    Place("부에나비스타", "Buenavista"),
                ),
                lat = 4.53, lng = -75.67,
            ),
            Place(
                "리사랄다", "Risaralda",
                subs = listOf(
                    Place("산타 로사 데 카발", "Santa Rosa de Cabal"),
                    Place("벨렌 데 움브리아", "Belén de Umbría", listOf("Belen de Umbria")),
                    Place("아피아", "Apía", listOf("Apia")),
                ),
                lat = 4.82, lng = -75.7,
            ),
            Place(
                "칼다스", "Caldas",
                subs = listOf(
                    Place("마니살레스", "Manizales"),
                    Place("친치나", "Chinchiná", listOf("Chinchina")),
                    Place("비야마리아", "Villamaría", listOf("Villamaria")),
                ),
                lat = 5.1, lng = -75.55,
            ),
            Place("산탄데르", "Santander", lat = 7.13, lng = -73.0),
            Place("노르테 데 산탄데르", "Norte de Santander", lat = 7.9, lng = -72.5),
            Place(
                "바예 델 카우카", "Valle del Cauca", listOf("Valle", "바예"),
                listOf(
                    Place("세비야", "Sevilla"),
                    Place("카이세도니아", "Caicedonia"),
                ),
                lat = 3.42, lng = -76.52,
            ),
            Place("쿤디나마르카", "Cundinamarca", lat = 5.0, lng = -74.15),
            Place("보야카", "Boyacá", listOf("Boyaca"), lat = 5.53, lng = -73.37),
            Place(
                "시에라 네바다", "Sierra Nevada", listOf("Sierra Nevada de Santa Marta", "시에라 네바다 데 산타 마르타"),
                listOf(
                    Place("막달레나", "Magdalena"),
                    Place("세사르", "Cesar"),
                    Place("라 과히라", "La Guajira"),
                ),
                lat = 10.87, lng = -73.72,
            ),
        ),
    )

    // source: Perfect Daily Grind "A Guide to Peru's Coffee-Producing Regional Profiles"; Trabocca "Cajamarca" region guide.
    // coords: enwiki — Cajamarca, Amazonas, San Martín = department articles; Junín = Chanchamayo province, Cusco =
    // La Convención province, Puno = Sandia province, Pasco = Oxapampa province (the coffee-growing provinces).
    private fun peru() = Origin(
        "Peru",
        listOf(
            Place(
                "카하마르카", "Cajamarca",
                subs = listOf(
                    Place("하엔", "Jaén", listOf("Jaen")),
                    Place("산 이그나시오", "San Ignacio"),
                    Place("쿠테르보", "Cutervo"),
                    Place("초타", "Chota"),
                ),
                lat = -6.61, lng = -78.78,
            ),
            Place(
                "아마소나스", "Amazonas", listOf("아마조나스"),
                listOf(
                    Place("로드리게스 데 멘도사", "Rodríguez de Mendoza", listOf("Rodriguez de Mendoza")),
                    Place("루야", "Luya"),
                    Place("우트쿠밤바", "Utcubamba"),
                ),
                lat = -6.22, lng = -77.85,
            ),
            Place(
                "산 마르틴", "San Martín", listOf("San Martin"),
                listOf(
                    Place("모요밤바", "Moyobamba"),
                    Place("라마스", "Lamas"),
                ),
                lat = -7.2, lng = -76.8,
            ),
            Place(
                "후닌", "Junín", listOf("Junin"),
                listOf(
                    Place("찬차마요", "Chanchamayo"),
                    Place("사티포", "Satipo"),
                ),
                lat = -11.05, lng = -75.33,
            ),
            Place(
                "쿠스코", "Cusco", listOf("Cuzco"),
                listOf(
                    Place("라 콘벤시온", "La Convención", listOf("La Convencion"), listOf(Place("키야밤바", "Quillabamba"))),
                ),
                lat = -12.86, lng = -72.69,
            ),
            Place("푸노", "Puno", subs = listOf(Place("산디아", "Sandia")), lat = -14.25, lng = -69.43),
            Place(
                "파스코", "Pasco",
                subs = listOf(
                    Place("비야 리카", "Villa Rica"),
                    Place("옥사팜파", "Oxapampa"),
                ),
                lat = -10.57, lng = -75.41,
            ),
        ),
    )

    // source: xliiicoffee.com Ecuador region guide and Expat Ecuador coffee guide (Loja, Vilcabamba, Gonzanamá, Quilanga,
    // Catamayo, Intag, Pacto, Nanegalito, Zamora-Chinchipe, Zaruma); List+Beisler "Loja, Valle de Vilcabamba".
    // coords: enwiki province articles; El Oro = Zaruma.
    private fun ecuador() = Origin(
        "Ecuador",
        listOf(
            Place(
                "로하", "Loja",
                subs = listOf(
                    Place("빌카밤바", "Vilcabamba"),
                    Place("킬랑가", "Quilanga"),
                    Place("곤사나마", "Gonzanamá", listOf("Gonzanama")),
                    Place("카타마요", "Catamayo"),
                ),
                lat = -4.02, lng = -79.48,
            ),
            Place(
                "피친차", "Pichincha",
                subs = listOf(
                    Place("팍토", "Pacto"),
                    Place("나네갈리토", "Nanegalito"),
                ),
                lat = -0.25, lng = -78.58,
            ),
            Place("임바부라", "Imbabura", subs = listOf(Place("인탁", "Intag", listOf("Intag Valley"))), lat = 0.35, lng = -78.13),
            Place("사모라 친치페", "Zamora Chinchipe", listOf("Zamora-Chinchipe"), lat = -4.1, lng = -78.88),
            Place("엘 오로", "El Oro", subs = listOf(Place("사루마", "Zaruma")), lat = -3.68, lng = -79.6),
        ),
    )

    // source: Market Lane "Bolivia: putting the 'special' in specialty coffee" (Caranavi, Coroico, Nor & Sud Yungas,
    // Samaipata); Royal Coffee Agricafe offers (Caranavi, Samaipata).
    // coords: enwiki Caranavi, Coroico (for Yungas); eswiki Samaipata.
    private fun bolivia() = Origin(
        "Bolivia",
        listOf(
            Place("카라나비", "Caranavi", lat = -15.83, lng = -67.57),
            Place("융가스", "Yungas", listOf("Los Yungas"), listOf(Place("코로이코", "Coroico")), lat = -16.18, lng = -67.73),
            Place("사마이파타", "Samaipata", lat = -18.18, lng = -63.88),
        ),
    )

    // source: Perfect Daily Grind "Coffee Origins: A Guide to Mexico"; Wikipedia "Coffee production in Mexico", "Soconusco",
    // "Ángel Albino Corzo" (Jaltenango), Puebla Sierra Norte towns (Cuetzalan, Xicotepec, Huauchinango). Atoyac: common
    // trade usage.
    // coords: enwiki state articles.
    private fun mexico() = Origin(
        "Mexico",
        listOf(
            Place(
                "치아파스", "Chiapas",
                subs = listOf(
                    Place("소코누스코", "Soconusco", subs = listOf(Place("타파출라", "Tapachula"))),
                    Place("할테낭고", "Jaltenango", listOf("Ángel Albino Corzo", "Angel Albino Corzo", "Jaltenango de la Paz")),
                    Place("야할론", "Yajalón", listOf("Yajalon")),
                ),
                lat = 16.53, lng = -92.45,
            ),
            Place(
                "오악사카", "Oaxaca", listOf("와하카"),
                listOf(
                    Place("플루마 이달고", "Pluma Hidalgo"),
                    Place("시에라 수르", "Sierra Sur"),
                ),
                lat = 17.05, lng = -96.66,
            ),
            Place(
                "베라크루스", "Veracruz", listOf("베라크루즈"),
                listOf(
                    Place("코아테펙", "Coatepec"),
                    Place("우아투스코", "Huatusco"),
                ),
                lat = 19.84, lng = -96.06,
            ),
            Place(
                "푸에블라", "Puebla",
                subs = listOf(
                    Place("쿠에찰란", "Cuetzalan"),
                    Place("시코테펙", "Xicotepec", listOf("Xicotepec de Juárez")),
                    Place("우아우친앙고", "Huauchinango"),
                ),
                lat = 19.0, lng = -97.88,
            ),
            Place("게레로", "Guerrero", subs = listOf(Place("아토약", "Atoyac", listOf("Atoyac de Álvarez"))), lat = 17.62, lng = -99.95),
            Place("나야리트", "Nayarit", lat = 22.0, lng = -105.0),
        ),
    )

    // source: ANACAFE's eight regions (Acatenango Valley, Antigua Coffee, Traditional Atitlán, Rainforest Cobán, Fraijanes
    // Plateau, Highland Huehue, New Oriente, Volcanic San Marcos) via Genuine Origin and Westrock origin reports; Korean:
    // natalie.co.kr / 바리스타 룰스 (8 regions, 뉴오리엔테). Municipalities: common trade usage.
    // coords: enwiki — Antigua Guatemala, Huehuetenango Department, Acatenango, Lake Atitlán, Cobán, Fraijanes,
    // Chiquimula Department (for New Oriente), San Marcos Department.
    private fun guatemala() = Origin(
        "Guatemala",
        listOf(
            Place(
                "안티구아", "Antigua", listOf("안티과", "Antigua Coffee", "Antigua Guatemala"),
                listOf(
                    Place("시우다드 비에하", "Ciudad Vieja"),
                    Place("산 미겔 두에냐스", "San Miguel Dueñas", listOf("San Miguel Duenas")),
                    Place("알로테낭고", "Alotenango", listOf("San Juan Alotenango")),
                ),
                lat = 14.56, lng = -90.73,
            ),
            Place(
                "우에우에테낭고", "Huehuetenango", listOf("후에후에테낭고", "우에우에", "Huehue", "Highland Huehue"),
                listOf(
                    Place("라 리베르타드", "La Libertad"),
                    Place("라 데모크라시아", "La Democracia"),
                    Place("산 안토니오 우이스타", "San Antonio Huista"),
                    Place("산타 크루스 바리야스", "Santa Cruz Barillas", listOf("Barillas", "바리야스")),
                    Place("산 페드로 넥타", "San Pedro Necta"),
                    Place("쿠일코", "Cuilco"),
                    Place("토도스 산토스 쿠추마탄", "Todos Santos Cuchumatán", listOf("Todos Santos", "토도스 산토스")),
                    Place("콘셉시온 우이스타", "Concepción Huista", listOf("Concepcion Huista")),
                    Place("치안틀라", "Chiantla"),
                    Place("아구아카탄", "Aguacatán", listOf("Aguacatan")),
                ),
                lat = 15.31, lng = -91.48,
            ),
            Place("아카테낭고", "Acatenango", listOf("Acatenango Valley"), lat = 14.5, lng = -90.88),
            Place(
                "아티틀란", "Atitlán", listOf("Atitlan", "Traditional Atitlán"),
                listOf(
                    Place("산 페드로 라 라구나", "San Pedro La Laguna"),
                    Place("산 후안 라 라구나", "San Juan La Laguna"),
                    Place("산티아고 아티틀란", "Santiago Atitlán", listOf("Santiago Atitlan")),
                    Place("산 루카스 톨리만", "San Lucas Tolimán", listOf("San Lucas Toliman")),
                ),
                lat = 14.7, lng = -91.2,
            ),
            Place(
                "코반", "Cobán", listOf("Coban", "Rainforest Cobán"),
                listOf(
                    Place("산 페드로 카르차", "San Pedro Carchá", listOf("Carchá", "Carcha")),
                    Place("산 크리스토발 베라파스", "San Cristóbal Verapaz", listOf("San Cristobal Verapaz")),
                ),
                lat = 15.48, lng = -90.37,
            ),
            Place("프라이하네스", "Fraijanes", listOf("Fraijanes Plateau"), lat = 14.47, lng = -90.43),
            Place("누에보 오리엔테", "Nuevo Oriente", listOf("뉴 오리엔테", "뉴오리엔테", "New Oriente"), lat = 14.8, lng = -89.54),
            Place("산 마르코스", "San Marcos", listOf("Volcanic San Marcos"), lat = 14.96, lng = -91.8),
        ),
    )

    // source: IHCAFE's six regions (Copán, Opalaca, Montecillos, Agalta, El Paraíso, Comayagua) via Perfect Daily Grind
    // and PR Newswire; Alliance for Coffee Excellence "Honduras 2025" results (Lempira, Santa Bárbara, El Paraíso, La Paz,
    // Francisco Morazán); The Coffee Quest "Finca La Sierra" (El Cedral, Las Vegas, Santa Bárbara).
    // coords: enwiki — Marcala; department articles; Opalaca = San Francisco de Opalaca; Agalta = Sierra de Agalta
    // National Park.
    private fun honduras() = Origin(
        "Honduras",
        listOf(
            Place(
                "마르칼라", "Marcala", listOf("La Paz", "라 파스", "라파스", "Montecillos", "몬테시요스"),
                listOf(
                    Place("산티아고 데 푸링글라", "Santiago de Puringla", listOf("Santiago Puringla")),
                    Place("치나클라", "Chinacla"),
                ),
                lat = 14.32, lng = -87.68,
            ),
            Place(
                "산타 바르바라", "Santa Bárbara", listOf("Santa Barbara"),
                listOf(
                    Place("라스 베가스", "Las Vegas", subs = listOf(Place("엘 세드랄", "El Cedral"))),
                ),
                lat = 14.92, lng = -88.23,
            ),
            Place("코판", "Copán", listOf("Copan"), listOf(Place("코르킨", "Corquín", listOf("Corquin"))), lat = 14.77, lng = -88.78),
            Place("오코테페케", "Ocotepeque", lat = 14.43, lng = -89.18),
            Place("렘피라", "Lempira", lat = 14.58, lng = -88.58),
            Place("인티부카", "Intibucá", listOf("Intibuca"), lat = 14.32, lng = -88.15),
            Place("코마야과", "Comayagua", subs = listOf(Place("시과테페케", "Siguatepeque")), lat = 14.45, lng = -87.63),
            Place("엘 파라이소", "El Paraíso", listOf("El Paraiso"), lat = 13.93, lng = -86.85),
            Place("올란초", "Olancho", lat = 14.6, lng = -86.2),
            Place("프란시스코 모라산", "Francisco Morazán", listOf("Francisco Morazan"), lat = 14.1, lng = -87.22),
            Place("오팔라카", "Opalaca", lat = 14.58, lng = -88.3),
            Place("아갈타", "Agalta", lat = 15.01, lng = -85.85),
        ),
    )

    // source: Consejo Salvadoreño del Café's six cordilleras, via El Target "¿Sabes cuáles son las regiones cafetaleras en
    // El Salvador?". Towns: common trade usage.
    // coords: enwiki — Santa Ana Volcano, Apaneca, Chalatenango Department, Metapán, San Salvador (volcano) for
    // Quezaltepec, Berlín (El Salvador), San Vicente (volcano) for Chichontepec, Cacahuatique Mountains.
    private fun elSalvador() = Origin(
        "El Salvador",
        listOf(
            Place(
                "산타 아나", "Santa Ana", listOf("Santa Ana Volcano"),
                listOf(
                    Place("찰추아파", "Chalchuapa"),
                    Place("엘 콩고", "El Congo"),
                ),
                lat = 13.85, lng = -89.63,
            ),
            Place(
                "아파네카-일라마테펙", "Apaneca-Ilamatepec", listOf("Apaneca Ilamatepec", "Ilamatepec"),
                listOf(
                    Place("아파네카", "Apaneca"),
                    Place("후아유아", "Juayúa", listOf("Juayua")),
                    Place("콘셉시온 데 아타코", "Concepción de Ataco", listOf("Ataco", "아타코")),
                ),
                lat = 13.85, lng = -89.8,
            ),
            Place(
                "찰라테낭고", "Chalatenango",
                subs = listOf(
                    Place("라 팔마", "La Palma"),
                    Place("산 이그나시오", "San Ignacio"),
                ),
                lat = 14.17, lng = -89.08,
            ),
            Place("알로테펙-메타판", "Alotepec-Metapán", listOf("Alotepeque-Metapán", "Metapán", "Metapan", "메타판"), lat = 14.33, lng = -89.44),
            Place("엘 발사모-케살테펙", "El Bálsamo-Quezaltepec", listOf("El Bálsamo-Quezaltepeque", "El Balsamo", "엘 발사모"), lat = 13.73, lng = -89.29),
            Place(
                "테카파-치나메카", "Tecapa-Chinameca", listOf("Tecapa Chinameca"),
                listOf(
                    Place("베를린", "Berlín", listOf("Berlin")),
                    Place("알레그리아", "Alegría", listOf("Alegria")),
                ),
                lat = 13.5, lng = -88.53,
            ),
            Place("치촌테펙", "Chichontepec", listOf("Chinchontepec"), lat = 13.6, lng = -88.84),
            Place("카카우아티케", "Cacahuatique", lat = 13.77, lng = -88.21),
        ),
    )

    // source: Sweet Maria's "Nicaragua Coffee Regions, Matagalpa, Jinotega and …"; Wikipedia "Jinotega", "Matagalpa".
    // Municipalities: common trade usage.
    // coords: enwiki department articles.
    private fun nicaragua() = Origin(
        "Nicaragua",
        listOf(
            Place(
                "히노테가", "Jinotega", listOf("지노테가"),
                listOf(
                    Place("산 라파엘 델 노르테", "San Rafael del Norte"),
                    Place("라 콘코르디아", "La Concordia"),
                    Place("엘 쿠아", "El Cuá", listOf("El Cua")),
                    Place("산 세바스티안 데 얄리", "San Sebastián de Yalí", listOf("Yalí", "Yali")),
                ),
                lat = 13.08, lng = -86.0,
            ),
            Place(
                "마타갈파", "Matagalpa",
                subs = listOf(
                    Place("산 라몬", "San Ramón", listOf("San Ramon")),
                    Place("라 달리아", "La Dalia", listOf("El Tuma-La Dalia")),
                    Place("란초 그란데", "Rancho Grande"),
                ),
                lat = 12.92, lng = -85.92,
            ),
            Place(
                "누에바 세고비아", "Nueva Segovia", listOf("Segovia"),
                listOf(
                    Place("디필토", "Dipilto"),
                    Place("모존테", "Mozonte"),
                    Place("할라파", "Jalapa"),
                    Place("산 페르난도", "San Fernando"),
                    Place("마쿠엘리소", "Macuelizo"),
                ),
                lat = 13.76, lng = -86.19,
            ),
            Place(
                "마드리스", "Madriz", listOf("마드리즈"),
                listOf(
                    Place("산 후안 델 리오 코코", "San Juan del Río Coco", listOf("San Juan del Rio Coco")),
                    Place("라스 사바나스", "Las Sabanas"),
                ),
                lat = 13.48, lng = -86.58,
            ),
            Place("에스텔리", "Estelí", listOf("Esteli"), lat = 13.08, lng = -86.35),
            Place("보아코", "Boaco", lat = 12.47, lng = -85.67),
        ),
    )

    // source: ICAFE's eight regions (Tarrazú, Valle Occidental, Valle Central, Tres Ríos, Orosi, Turrialba, Brunca,
    // Guanacaste) via Sucafina "Costa Rica" and Wikipedia "Coffee production in Costa Rica"; Korean: 프래그런스커피 /
    // 워너빈 (따라주), 커피 리브레 (엘사르 데 사르세로). Cantons and districts: common trade usage.
    // coords: enwiki — Tarrazú (canton), Naranjo (canton) for West Valley, Heredia for Central Valley, La Unión (canton)
    // for Tres Ríos, Orosi (Cartago), Turrialba (canton), Pérez Zeledón (canton) for Brunca; Wikidata Q690026 Guanacaste.
    private fun costaRica() = Origin(
        "Costa Rica",
        listOf(
            Place(
                "타라주", "Tarrazú", listOf("따라주", "타라수", "Tarrazu", "Los Santos", "로스 산토스"),
                listOf(
                    Place("도타", "Dota", listOf("Santa María de Dota", "산타 마리아 데 도타"), listOf(Place("코페이", "Copey"))),
                    Place("레온 코르테스", "León Cortés", listOf("Leon Cortes", "San Pablo de León Cortés"), listOf(Place("야노 보니토", "Llano Bonito"))),
                    Place("산 마르코스", "San Marcos", listOf("San Marcos de Tarrazú")),
                    Place("산 로렌소", "San Lorenzo"),
                ),
                lat = 9.58, lng = -84.06,
            ),
            Place(
                "웨스트 밸리", "West Valley", listOf("웨스트밸리", "Valle Occidental", "바예 옥시덴탈"),
                listOf(
                    Place("나랑호", "Naranjo"),
                    Place("그레시아", "Grecia"),
                    Place("사르치", "Sarchí", listOf("Sarchi")),
                    Place("팔마레스", "Palmares"),
                    Place("산 라몬", "San Ramón", listOf("San Ramon")),
                    Place("아테나스", "Atenas"),
                    Place("사르세로", "Zarcero"),
                ),
                lat = 10.11, lng = -84.39,
            ),
            Place(
                "센트럴 밸리", "Central Valley", listOf("센트럴밸리", "Valle Central", "바예 센트랄"),
                listOf(
                    Place("에레디아", "Heredia"),
                    Place("바르바", "Barva"),
                    Place("포아스", "Poás", listOf("Poas")),
                    Place("알라후엘라", "Alajuela"),
                ),
                lat = 10.0, lng = -84.12,
            ),
            Place("트레스 리오스", "Tres Ríos", listOf("Tres Rios", "트레스리오스"), lat = 9.91, lng = -83.99),
            Place("오로시", "Orosi", lat = 9.69, lng = -83.76),
            Place("투리알바", "Turrialba", lat = 9.78, lng = -83.57),
            Place(
                "브룬카", "Brunca",
                subs = listOf(
                    Place("페레스 셀레돈", "Pérez Zeledón", listOf("Perez Zeledon")),
                    Place("코토 브루스", "Coto Brus"),
                ),
                lat = 9.34, lng = -83.72,
            ),
            Place("과나카스테", "Guanacaste", lat = 10.43, lng = -85.4),
        ),
    )

    // source: Wikipedia "Boquete District", "Renacimiento District", "Tierras Altas District" (corregimientos); Korean:
    // 카페알트로 (피에드라 칸델라), 커피벨트 / 투커피 (보케테).
    // coords: enwiki — Boquete (Chiriquí), Volcán (Panama), Renacimiento District, Chiriquí Province.
    private fun panama() = Origin(
        "Panama",
        listOf(
            Place(
                "보케테", "Boquete",
                subs = listOf(
                    Place("알토 키엘", "Alto Quiel"),
                    Place("팔미라", "Palmira"),
                    Place("하라미요", "Jaramillo"),
                    Place("로스 나랑호스", "Los Naranjos"),
                    Place("칼데라", "Caldera"),
                    Place("알토 보케테", "Alto Boquete"),
                    Place("바호 보케테", "Bajo Boquete"),
                ),
                lat = 8.78, lng = -82.44,
            ),
            Place(
                "볼칸", "Volcán", listOf("Volcan", "Volcán Barú", "볼칸 바루", "Tierras Altas", "티에라스 알타스"),
                listOf(
                    Place("세로 푼타", "Cerro Punta"),
                    Place("파소 안초", "Paso Ancho"),
                ),
                lat = 8.78, lng = -82.64,
            ),
            Place(
                "레나시미엔토", "Renacimiento",
                subs = listOf(
                    Place("산타 클라라", "Santa Clara"),
                    Place("피에드라 칸델라", "Piedra Candela", listOf("Piedra de Candela")),
                    Place("리오 세레노", "Río Sereno", listOf("Rio Sereno")),
                ),
                lat = 8.82, lng = -82.86,
            ),
            Place("치리키", "Chiriquí", listOf("Chiriqui"), lat = 8.43, lng = -82.43),
        ),
    )

    // source: Wikipedia "Jamaican Blue Mountain Coffee" (JACRA: St. Andrew, St. Thomas, Portland, St. Mary).
    // High Mountain: common trade name.
    // coords: enwiki — Blue Mountains (Jamaica); Manchester Parish for High Mountain.
    private fun jamaica() = Origin(
        "Jamaica",
        listOf(
            Place(
                "블루마운틴", "Blue Mountains", listOf("블루 마운틴", "Blue Mountain", "블루마운틴스"),
                listOf(
                    Place("세인트 앤드루", "St. Andrew", listOf("Saint Andrew")),
                    Place("세인트 토머스", "St. Thomas", listOf("Saint Thomas")),
                    Place("포틀랜드", "Portland"),
                    Place("세인트 메리", "St. Mary", listOf("Saint Mary")),
                ),
                lat = 18.1, lng = -76.67,
            ),
            Place("하이 마운틴", "High Mountain", listOf("Jamaica High Mountain"), lat = 18.05, lng = -77.53),
        ),
    )

    // source: Sweet Maria's "Dominican Republic Coffee Overview"; Homegrounds DR coffee guide (Barahona, Jarabacoa,
    // San José de Ocoa, Cibao Altura).
    // coords: enwiki — Jarabacoa, Barahona Province, San José de Ocoa.
    private fun dominicanRepublic() = Origin(
        "Dominican Rep.",
        listOf(
            Place("하라바코아", "Jarabacoa", lat = 19.12, lng = -70.63),
            Place("바라오나", "Barahona", lat = 18.2, lng = -71.1),
            Place("산 호세 데 오코아", "San José de Ocoa", listOf("San Jose de Ocoa", "Ocoa", "오코아"), lat = 18.55, lng = -70.5),
        ),
    )

    // source: Wikipedia "Coffee production in Cuba" (Sierra Maestra, Escambray).
    // coords: enwiki — Sierra Maestra, Escambray Mountains.
    private fun cuba() = Origin(
        "Cuba",
        listOf(
            Place("시에라 마에스트라", "Sierra Maestra", lat = 20.0, lng = -76.75),
            Place("에스캄브라이", "Escambray", lat = 21.98, lng = -80.13),
        ),
    )

    // source: Perfect Daily Grind "A guide to coffee production in Haiti"; Burman Coffee APCAB offer (Belle-Anse/Thiotte).
    // coords: enwiki Chaîne de la Selle.
    private fun haiti() = Origin(
        "Haiti",
        listOf(
            Place("마시프 드 라 셀", "Massif de la Selle", listOf("La Selle", "라 셀"), listOf(Place("티오트", "Thiotte")), lat = 18.37, lng = -71.98),
        ),
    )

    // source: Kaffelito "Coffee-growing Regions of Venezuela: Táchira, Mérida, and Trujillo"; Wikipedia "Coffee
    // production in Venezuela" (Lara).
    // coords: enwiki state articles.
    private fun venezuela() = Origin(
        "Venezuela",
        listOf(
            Place("타치라", "Táchira", listOf("Tachira"), lat = 7.94, lng = -72.12),
            Place("메리다", "Mérida", listOf("Merida"), lat = 8.48, lng = -71.19),
            Place("트루히요", "Trujillo", lat = 9.42, lng = -70.5),
            Place("라라", "Lara", lat = 10.07, lng = -69.86),
        ),
    )

    // source: Wikipedia "Coffee production in Puerto Rico"; NCBA CLUSA (Adjuntas, Maricao, Jayuya, Yauco).
    // coords: enwiki municipality articles.
    private fun puertoRico() = Origin(
        "Puerto Rico",
        listOf(
            Place("야우코", "Yauco", lat = 18.04, lng = -66.85),
            Place("아훈타스", "Adjuntas", lat = 18.16, lng = -66.72),
            Place("하유야", "Jayuya", lat = 18.22, lng = -66.59),
            Place("마리카오", "Maricao", lat = 18.18, lng = -66.98),
        ),
    )

    // source: map-dot region only. coords: Wikidata Rupununi (Q3102786).
    private fun guyana() = Origin(
        "Guyana",
        listOf(Place("루푸누니", "Rupununi", lat = 3.41, lng = -59.31)),
    )

    // source: Hawaii Department of Agriculture's coffee origins (Kona, Kaʻū, Puna, Hāmākua, Maui, Kauaʻi, Oʻahu, Molokaʻi)
    // as the Hawaii Coffee Association lists them; towns as the Kona and Kaʻū farms and their buyers name them. Korean:
    // 하와이 코나 / 카우 as Korean roasters and importers list them; the others transliterated.
    // coords: enwiki — Kealakekua, Pāhala, Pāhoa, Kalāheo, Waialua; Wikidata — Honokaʻa (Q2070615), Kula (Q4243331),
    // Kualapuʻu (Q2020849). On the coffee map they are drawn in the Hawaii inset (ui/map/WorldMapInsets).
    private fun hawaii() = Origin(
        "Hawaii",
        listOf(
            Place(
                "코나", "Kona", listOf("Kona Coffee Belt", "Hawaii Kona", "하와이 코나"),
                listOf(
                    Place("홀루알로아", "Holualoa", listOf("Hōlualoa")),
                    Place("케알라케쿠아", "Kealakekua"),
                    Place("캡틴 쿡", "Captain Cook"),
                    Place("호나우나우", "Honaunau", listOf("Hōnaunau")),
                ),
                lat = 19.53, lng = -155.92,
            ),
            Place(
                "카우", "Ka'u", listOf("Kau", "Kaʻū"),
                listOf(Place("파할라", "Pahala", listOf("Pāhala")), Place("나알레후", "Naalehu", listOf("Nāʻālehu"))),
                lat = 19.2, lng = -155.48,
            ),
            Place("푸나", "Puna", subs = listOf(Place("파호아", "Pahoa", listOf("Pāhoa"))), lat = 19.49, lng = -154.95),
            Place("하마쿠아", "Hamakua", listOf("Hāmākua"), listOf(Place("호노카아", "Honokaa", listOf("Honokaʻa"))), lat = 20.08, lng = -155.46),
            Place("마우이", "Maui", subs = listOf(Place("쿨라", "Kula"), Place("카아나팔리", "Kaanapali", listOf("Kāʻanapali"))), lat = 20.79, lng = -156.33),
            Place("카우아이", "Kauai", listOf("Kauaʻi"), listOf(Place("칼라헤오", "Kalaheo", listOf("Kalāheo"))), lat = 21.92, lng = -159.53),
            Place("오아후", "Oahu", listOf("Oʻahu"), listOf(Place("와이알루아", "Waialua")), lat = 21.58, lng = -158.13),
            Place("몰로카이", "Molokai", listOf("Molokaʻi"), listOf(Place("쿠알라푸우", "Kualapuu", listOf("Kualapuʻu"))), lat = 21.16, lng = -157.05),
        ),
    )
}
