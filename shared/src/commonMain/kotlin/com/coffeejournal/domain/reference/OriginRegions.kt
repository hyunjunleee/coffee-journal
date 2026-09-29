package com.coffeejournal.domain.reference

/**
 * Representative coffee-growing regions and sub-regions of every producing country in [CoffeeCountries],
 * for the bean form's 지역 (region) and 세부 지역 (sub-region) dropdowns. Free text stays allowed; this is
 * only the list of suggestions.
 *
 * Shape: a country's top-level [Place]s are its regions (지역), the level the map groups records by. Their
 * [Place.subs] are the sub-regions (세부 지역) the trade commonly names: districts, woredas, counties,
 * municipalities or DO areas, and, where roasters name them too, one more level of kebeles, villages or
 * washing-station areas (e.g. 시다모 › 벤사 › 코코세 = Sidamo › Bensa › Kokose). Nesting is at most three deep.
 *
 * How it was compiled: by hand, from the region lists of national coffee bodies (FNC Colombia, ICAFE, ANACAFE,
 * IHCAFE, the Consejo Salvadoreño del Café, Coffee Board of India, PNG Coffee Industry Corporation), official
 * administrative lists (woredas of the Sidama Region and Guji Zone, Panama's corregimientos), Cup of Excellence
 * results, and importers' origin and offer pages (Royal Coffee, Cafe Imports, Trabocca, Sucafina, Ally Coffee,
 * Mercanta and others). Korean spellings are the ones Korean importers and roasters print on their products
 * (커피 리브레, 코빈즈, 커만사, 함께커피, 카페알트로 …); where no roaster usage was found the name is
 * transliterated by the 외래어 표기법 and common variants are listed in [Place.aliases]. The Korean spellings
 * the app already uses ([CoffeeCountries.regionSynonyms]: 예가체프, 시다모/시다마, 우일라, 타라주 …) are kept,
 * and every map-dot region of [CoffeeCountries.all] is a top-level Place whose [Place.en] (or an alias) is the
 * map's canonical name, so e.g. Ethiopia's Sidama region is `Place("시다모", "Sidamo", aliases = "시다마",
 * "Sidama" …)`. Where a political name changed (Sidama Region, Bench Sheko, Tanzania's Songwe) the trade name is
 * kept and the new name is an alias or a sibling. The sources used for each country are named in a
 * `// source:` comment above its block.
 *
 * Coordinates: every top-level region carries [Place.lat]/[Place.lng], rounded to 2 decimals, from the English
 * Wikipedia article coordinates (MediaWiki `prop=coordinates`) of the zone, department, county, district or town
 * the region is named after (Wikidata P625 or Spanish Wikipedia where the English article has none); for a trade
 * region that spans an area (e.g. West Valley, Cerrado, Mogiana) the point is that of its main producing town or
 * area. The article used for each region is named in the country's `coords:` comment.
 *
 * Checked: 2026-09-29.
 */
object OriginRegions {
    /**
     * A region or a place inside it: [ko] as Korean specialty-coffee roasters write it, [en] the usual English
     * (trade) name, [aliases] other spellings people type (Korean or English), [subs] the places inside it.
     * [lat]/[lng] (WGS84 decimal degrees) are an approximate centre point, set on every top-level region (the
     * world map puts a dot there) and left null on sub-places.
     */
    data class Place(val ko: String, val en: String, val aliases: List<String> = emptyList(), val subs: List<Place> = emptyList(), val lat: Double? = null, val lng: Double? = null)

    /** [countryEn] is exactly a [CoffeeCountries.Country.en]. */
    data class Origin(val countryEn: String, val regions: List<Place>)

    // One private function per country keeps each JVM method well under the 64 KB bytecode limit.
    val all: List<Origin> = listOf(
        brazil(), colombia(), peru(), ecuador(), bolivia(), mexico(), guatemala(), honduras(), elSalvador(),
        nicaragua(), costaRica(), panama(), jamaica(), dominicanRepublic(), cuba(), haiti(), venezuela(), puertoRico(),
        ethiopia(), kenya(), rwanda(), burundi(), tanzania(), uganda(), drCongo(), zambia(), malawi(), zimbabwe(),
        cameroon(), coteDIvoire(), madagascar(), indonesia(), vietnam(), india(), china(), thailand(), myanmar(), laos(),
        papuaNewGuinea(), philippines(), taiwan(), timorLeste(), yemen(), guinea(), guyana(),
    )

    // ───────────────────────────── Americas ─────────────────────────────

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

    // ───────────────────────────── Africa ─────────────────────────────

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

    // ───────────────────────────── Asia · Oceania ─────────────────────────────

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

    // source: map-dot region; Ziama-Macenta protected geographical indication (Agence Ecofin, origin-gi.com).
    // coords: enwiki Nzérékoré, Macenta.
    private fun guinea() = Origin(
        "Guinea",
        listOf(
            Place("은제레코레", "Nzérékoré", listOf("Nzerekore", "N'Zérékoré"), lat = 7.75, lng = -8.82),
            Place("마센타", "Macenta", listOf("Ziama-Macenta", "Ziama", "지아마"), lat = 8.55, lng = -9.47),
        ),
    )

    // source: map-dot region only. coords: Wikidata Rupununi (Q3102786).
    private fun guyana() = Origin(
        "Guyana",
        listOf(Place("루푸누니", "Rupununi", lat = 3.41, lng = -59.31)),
    )
}
