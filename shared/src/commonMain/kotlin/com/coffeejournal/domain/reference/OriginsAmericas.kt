package com.coffeejournal.domain.reference

import com.coffeejournal.domain.reference.OriginRegions.Origin
import com.coffeejournal.domain.reference.OriginRegions.Place

/**
 * [OriginRegions] of the Americas (Central, South and the Caribbean): one private function per country, each with its
 * sources above it. The larger countries keep each region with sub-regions in a private function of its own, which keeps
 * every JVM method well under the 64 KB bytecode limit.
 */
internal object OriginsAmericas {
    val all: List<Origin> = listOf(
        brazil(), colombia(), peru(), ecuador(), bolivia(), mexico(), guatemala(), honduras(), elSalvador(),
        nicaragua(), costaRica(), panama(), jamaica(), dominicanRepublic(), cuba(), haiti(), venezuela(), puertoRico(),
        hawaii(), guyana(),
    )

    // source: BSCA "Cafés do Brasil são cultivados em 35 regiões produtoras" (Cerrado Mineiro, Mantiqueira de Minas, Campo
    // das Vertentes, Matas de Minas, Alta Mogiana, Região de Garça, Região de Pinhal, Oeste da Bahia, Montanhas do
    // Espírito Santo, Norte Pioneiro do Paraná, Matas de Rondônia, Caparaó) and its members list (Chapada de Minas,
    // Planalto de Vitória da Conquista); Atlantica Coffee "Cerrado Mineiro" (Patrocínio, Monte Carmelo, Araguari, Patos de
    // Minas, Campos Altos, Serra do Salitre, São Gotardo, Araxá, Carmo do Paranaíba); Ally Coffee offers (Coromandel;
    // Fazenda Dona Neném, Presidente Olegário); Prêmio Região do Cerrado Mineiro finalists (Ibiá); DataSebrae "IG
    // Mantiqueira de Minas" (its 25 municipalities: Carmo de Minas, Cristina, Dom Viçoso, Pedralva, Conceição das Pedras,
    // Jesuânia, Lambari, Olímpio Noronha, São Lourenço, Santa Rita do Sapucaí, Brazópolis, Baependi …); Sebrae/PR "Cafés
    // especiais do Norte Pioneiro do Paraná" (Carlópolis, Pinhalão, Ibaiti, Tomazina); O Tempo / Rádio Itatiaia on the
    // Chapada de Minas IG (Capelinha, Angelândia, Aricanduva, Itamarandiba, Minas Novas, Turmalina); Cafe Imports,
    // Sucafina and Ally Coffee Brazil offer sheets and the Alliance for Coffee Excellence farm directory (Brazil CoE
    // winners) for the other municipalities. Korean: 커피 리브레 / 커피플랜트 (세하도), 챕터원커피 (몬테 까르멜로); others by the 외래어 표기법
    // (Rondônia = 혼도니아).
    // coords: enwiki — Cerrado = Patrocínio, Mogiana = Franca, Campo das Vertentes = Santo Antônio do Amparo, Matas de
    // Minas = Manhuaçu, Chapada de Minas = Capelinha, Chapada Diamantina = Piatã, Planalto da Bahia = Vitória da
    // Conquista, Bahia Oeste = Luís Eduardo Magalhães, Espírito Santo = Venda Nova do Imigrante, Caparaó = Caparaó
    // National Park, Garça, Paraná = Carlópolis, Rondônia = Cacoal; Wikidata — Sul de Minas = Varginha (Q983524),
    // Mantiqueira de Minas = Carmo de Minas (Q1756231).
    private fun brazil() = Origin(
        "Brazil",
        listOf(
            brazilCerrado(),
            brazilSulDeMinas(),
            brazilMantiqueiraDeMinas(),
            brazilMogiana(),
            brazilCampoDasVertentes(),
            brazilMatasDeMinas(),
            brazilChapadaDeMinas(),
            brazilChapadaDiamantina(),
            brazilPlanaltoDaBahia(),
            brazilBahiaOeste(),
            brazilEspiritoSanto(),
            brazilCaparao(),
            brazilGarca(),
            brazilParana(),
            brazilRondonia(),
        ),
    )

    private fun brazilCerrado() = Place(
        "세하도", "Cerrado", listOf("Cerrado Mineiro", "세하도 미네이루", "세라도", "Cerrado de Minas", "Região do Cerrado Mineiro"),
        listOf(
            Place("파트로시니오", "Patrocínio", listOf("Patrocinio", "파트로시니우")),
            Place("카르모 도 파라나이바", "Carmo do Paranaíba", listOf("Carmo do Paranaiba", "카르무 두 파라나이바")),
            Place("몬테 카르멜로", "Monte Carmelo", listOf("몬테 까르멜로", "몬치 카르멜루")),
            Place("아라과리", "Araguari"),
            Place("세하 도 살리트레", "Serra do Salitre", listOf("세하 두 살리트리")),
            Place("캄포스 알토스", "Campos Altos", listOf("캄푸스 아우투스")),
            Place("코로만델", "Coromandel"),
            Place("이비아", "Ibiá", listOf("Ibia")),
            Place("프레지덴테 올레가리오", "Presidente Olegário", listOf("Presidente Olegario")),
            Place("파토스 데 미나스", "Patos de Minas", listOf("파투스 지 미나스")),
            Place("아라샤", "Araxá", listOf("Araxa")),
            Place("상 고타르도", "São Gotardo", listOf("Sao Gotardo")),
        ),
        lat = -18.93, lng = -46.98,
    )

    private fun brazilSulDeMinas() = Place(
        "술데미나스", "Sul de Minas", listOf("술 데 미나스", "술 지 미나스", "South Minas", "South of Minas"),
        listOf(
            Place("트레스 폰타스", "Três Pontas", listOf("Tres Pontas")),
            Place("바르지냐", "Varginha"),
            Place("보아 에스페란사", "Boa Esperança", listOf("Boa Esperanca")),
            Place("캄포스 제라이스", "Campos Gerais"),
            Place("네포무세노", "Nepomuceno"),
            Place("카르모 도 리오 클라로", "Carmo do Rio Claro"),
            Place("과슈페", "Guaxupé", listOf("Guaxupe")),
            Place("포소스 데 칼다스", "Poços de Caldas", listOf("Pocos de Caldas")),
            Place("마샤도", "Machado", listOf("마샤두")),
            Place("알페나스", "Alfenas"),
            Place("캄페스트레", "Campestre"),
            Place("트레스 코라송이스", "Três Corações", listOf("Tres Coracoes")),
            Place("일리시네아", "Ilicínea", listOf("Ilicinea")),
            Place("무잠비뉴", "Muzambinho"),
            Place("상 세바스티앙 도 파라이소", "São Sebastião do Paraíso", listOf("Sao Sebastiao do Paraiso")),
        ),
        lat = -21.55, lng = -45.43,
    )

    private fun brazilMantiqueiraDeMinas() = Place(
        "만티케이라 데 미나스", "Mantiqueira de Minas", listOf("만치케이라 지 미나스", "만티케이라", "Mantiqueira", "만치케이라"),
        listOf(
            Place("카르모 데 미나스", "Carmo de Minas", listOf("카르무 지 미나스", "까르모 데 미나스")),
            Place("크리스티나", "Cristina"),
            Place("동 비소주", "Dom Viçoso", listOf("Dom Vicoso", "돔 비소소")),
            Place("상 로렌소", "São Lourenço", listOf("Sao Lourenco", "상 로렌수")),
            Place("페드랄바", "Pedralva"),
            Place("콘세이상 다스 페드라스", "Conceição das Pedras", listOf("Conceicao das Pedras")),
            Place("제수아니아", "Jesuânia", listOf("Jesuania")),
            Place("올림피오 노로냐", "Olímpio Noronha", listOf("Olimpio Noronha")),
            Place("람바리", "Lambari"),
            Place("산타 리타 도 사푸카이", "Santa Rita do Sapucaí", listOf("Santa Rita do Sapucai")),
            Place("브라조폴리스", "Brazópolis", listOf("Brazopolis")),
            Place("바에펜디", "Baependi"),
        ),
        lat = -22.12, lng = -45.13,
    )

    private fun brazilMogiana() = Place(
        "모지아나", "Mogiana", listOf("Mogiana Paulista", "Alta Mogiana", "알타 모지아나"),
        listOf(
            Place(
                "알타 모지아나", "Alta Mogiana", listOf("High Mogiana"),
                listOf(
                    Place("프랑카", "Franca"),
                    Place("페드레굴류", "Pedregulho", listOf("페드레굴료")),
                    Place("이비라시", "Ibiraci"),
                    Place("크리스타이스 파울리스타", "Cristais Paulista"),
                    Place("파트로시니오 파울리스타", "Patrocínio Paulista", listOf("Patrocinio Paulista")),
                    Place("알티노폴리스", "Altinópolis", listOf("Altinopolis")),
                    Place("클라라발", "Claraval"),
                    Place("카페칭가", "Capetinga"),
                ),
            ),
            Place("모코카", "Mococa"),
            Place("카콘데", "Caconde"),
            Place("상 세바스티앙 다 그라마", "São Sebastião da Grama", listOf("Sao Sebastiao da Grama")),
            Place("에스피리토 산토 도 피냘", "Espírito Santo do Pinhal", listOf("Espirito Santo do Pinhal", "Pinhal", "피냘")),
        ),
        lat = -20.54, lng = -47.4,
    )

    private fun brazilCampoDasVertentes() = Place(
        "캄포 다스 베르텐테스", "Campo das Vertentes", listOf("Campos das Vertentes", "캄푸 다스 베르텐치스", "Vertentes"),
        listOf(
            Place("산토 안토니오 도 암파로", "Santo Antônio do Amparo", listOf("Santo Antonio do Amparo", "산투 안토니우 두 암파루")),
            Place("봉 수세소", "Bom Sucesso"),
            Place("라브라스", "Lavras"),
            Place("이비투루나", "Ibituruna"),
            Place("나자레노", "Nazareno"),
        ),
        lat = -20.95, lng = -44.92,
    )

    private fun brazilMatasDeMinas() = Place(
        "마타스 데 미나스", "Matas de Minas", listOf("마타스 지 미나스", "Zona da Mata"),
        listOf(
            Place("마누아수", "Manhuaçu", listOf("Manhuacu")),
            Place("아라퐁가", "Araponga"),
            Place("시모네지아", "Simonésia", listOf("Simonesia")),
            Place("산타나 도 마누아수", "Santana do Manhuaçu", listOf("Santana do Manhuacu")),
            Place("마르틴스 소아레스", "Martins Soares"),
            Place("레두토", "Reduto"),
            Place("루이스부르고", "Luisburgo"),
            Place("라지냐", "Lajinha"),
            Place("디비노", "Divino"),
            Place("카랑골라", "Carangola"),
            Place("에르발리아", "Ervália", listOf("Ervalia")),
            Place("비소자", "Viçosa", listOf("Vicosa")),
        ),
        lat = -20.26, lng = -42.03,
    )

    private fun brazilChapadaDeMinas() = Place(
        "샤파다 데 미나스", "Chapada de Minas", listOf("샤파다 지 미나스"),
        listOf(
            Place("카펠리냐", "Capelinha"),
            Place("미나스 노바스", "Minas Novas"),
            Place("투르말리나", "Turmalina"),
            Place("앙젤란디아", "Angelândia", listOf("Angelandia")),
            Place("아리칸두바", "Aricanduva"),
            Place("이타마란디바", "Itamarandiba"),
        ),
        lat = -17.69, lng = -42.52,
    )

    private fun brazilChapadaDiamantina() = Place(
        "샤파다 디아만티나", "Chapada Diamantina", listOf("샤파다 지아만치나", "샤파다", "Chapada"),
        listOf(
            Place("피아타", "Piatã", listOf("Piata")),
            Place("이비코아라", "Ibicoara"),
            Place("무쿠제", "Mucugê", listOf("Mucuge")),
            Place("바라 다 에스티바", "Barra da Estiva"),
            Place("아바이라", "Abaíra", listOf("Abaira")),
            Place("보니토", "Bonito"),
        ),
        lat = -13.15, lng = -41.77,
    )

    private fun brazilPlanaltoDaBahia() = Place(
        "플라날토 다 바이아", "Planalto da Bahia", listOf("Planalto de Vitória da Conquista", "Planalto de Conquista", "플라나우투 다 바이아"),
        listOf(
            Place("비토리아 다 콘키스타", "Vitória da Conquista", listOf("Vitoria da Conquista")),
            Place("바라 도 초사", "Barra do Choça", listOf("Barra do Choca")),
        ),
        lat = -14.87, lng = -40.84,
    )

    private fun brazilBahiaOeste() = Place(
        "바이아 오에스테", "Bahia Oeste", listOf("Oeste da Bahia", "Oeste Baiano", "Western Bahia", "서부 바이아"),
        listOf(
            Place("루이스 에두아르도 마갈량이스", "Luís Eduardo Magalhães", listOf("Luis Eduardo Magalhaes")),
            Place("바헤이라스", "Barreiras", listOf("바레이라스")),
            Place("상 데시데리오", "São Desidério", listOf("Sao Desiderio")),
        ),
        lat = -12.09, lng = -45.8,
    )

    private fun brazilEspiritoSanto() = Place(
        "에스피리토 산토", "Espírito Santo", listOf("Espirito Santo", "이스피리투 산투"),
        listOf(
            Place(
                "몬타냐스 도 에스피리토 산토", "Montanhas do Espírito Santo", listOf("Montanhas do Espirito Santo"),
                listOf(
                    Place("벤다 노바 도 이미그란테", "Venda Nova do Imigrante"),
                    Place("도밍고스 마르틴스", "Domingos Martins"),
                    Place("브레제투바", "Brejetuba"),
                    Place("아폰소 클라우디오", "Afonso Cláudio", listOf("Afonso Claudio")),
                    Place("콘세이상 도 카스텔로", "Conceição do Castelo", listOf("Conceicao do Castelo")),
                    Place("카스텔로", "Castelo"),
                    Place("바르젱 알타", "Vargem Alta"),
                    Place("산타 마리아 데 제티바", "Santa Maria de Jetibá", listOf("Santa Maria de Jetiba")),
                ),
            ),
            Place("상 가브리엘 다 팔랴", "São Gabriel da Palha", listOf("Sao Gabriel da Palha")),
            Place("빌라 발레리오", "Vila Valério", listOf("Vila Valerio")),
            Place("자과레", "Jaguaré", listOf("Jaguare")),
        ),
        lat = -20.34, lng = -41.13,
    )

    private fun brazilCaparao() = Place(
        "카파라오", "Caparaó", listOf("Caparao", "Serra do Caparaó", "Serra do Caparao"),
        listOf(
            Place("알토 카파라오", "Alto Caparaó", listOf("Alto Caparao")),
            Place("에스페라 펠리스", "Espera Feliz"),
            Place("알토 제키티바", "Alto Jequitibá", listOf("Alto Jequitiba")),
            Place("마누미림", "Manhumirim"),
            Place("도레스 도 리오 프레토", "Dores do Rio Preto"),
            Place("디비노 데 상 로렌소", "Divino de São Lourenço", listOf("Divino de Sao Lourenco")),
            Place("과수이", "Guaçuí", listOf("Guacui")),
            Place("이우나", "Iúna", listOf("Iuna")),
            Place("이루피", "Irupi"),
            Place("이바티바", "Ibatiba"),
            Place("이비티라마", "Ibitirama"),
            Place("무니스 프레이레", "Muniz Freire"),
        ),
        lat = -20.43, lng = -41.79,
    )

    private fun brazilGarca() = Place(
        "가르사", "Garça", listOf("Garca", "Região de Garça", "Alta Paulista", "알타 파울리스타", "Garça-Marília", "Centro-Oeste Paulista"),
        listOf(
            Place("마릴리아", "Marília", listOf("Marilia")),
            Place("베라 크루스", "Vera Cruz"),
            Place("갈리아", "Gália", listOf("Galia")),
            Place("폼페이아", "Pompeia"),
        ),
        lat = -22.22, lng = -49.65,
    )

    private fun brazilParana() = Place(
        "파라나", "Paraná", listOf("Parana", "Norte Pioneiro", "Norte Pioneiro do Paraná", "노르테 피오네이로"),
        listOf(
            Place("카를로폴리스", "Carlópolis", listOf("Carlopolis")),
            Place("피냘랑", "Pinhalão", listOf("Pinhalao")),
            Place("토마지나", "Tomazina"),
            Place("이바이티", "Ibaiti"),
        ),
        lat = -23.42, lng = -49.68,
    )

    private fun brazilRondonia() = Place(
        "혼도니아", "Rondônia", listOf("Rondonia", "론도니아", "Matas de Rondônia", "Matas de Rondonia", "마타스 데 혼도니아", "Robustas Amazônicos", "Robustas Amazonicos"),
        listOf(
            Place("카코알", "Cacoal"),
            Place("알타 플로레스타 도에스테", "Alta Floresta d'Oeste", listOf("Alta Floresta do Oeste")),
            Place("미니스트로 안드레아자", "Ministro Andreazza"),
            Place("노바 브라질란디아 도에스테", "Nova Brasilândia d'Oeste", listOf("Nova Brasilandia d'Oeste")),
            Place("롤림 데 모우라", "Rolim de Moura"),
            Place("상 미겔 도 과포레", "São Miguel do Guaporé", listOf("Sao Miguel do Guapore")),
            Place("에스피강 도에스테", "Espigão d'Oeste", listOf("Espigao d'Oeste")),
        ),
        lat = -11.44, lng = -61.45,
    )

    // source: Federación Nacional de Cafeteros, departmental committees (federaciondecafeteros.org/estructura) and their
    // coffee municipalities; Cafe Imports "Genesis Coffee Sourcing of Huila" (San Agustín, Acevedo, Tarqui) and Inzá
    // (Cauca) offers; Royal Coffee Tolima offers (Gaitania; Multicoop: Planadas, Rioblanco, Chaparral) and Nariño offers;
    // Covoya and Ally Coffee Nariño offers (Buesaco, La Unión); Mercanta "Inzá Cauca Community Lot"; ko.wikipedia 우일라주
    // (Pitalito and its neighbours); the other municipalities as importers' offer sheets and the Alliance for Coffee
    // Excellence farm directory (Colombia Cup of Excellence winners) name them. Korean: 커피 리브레 (우일라 피탈리토, 나리뇨 엘 타블론),
    // 솔리드스트라이프 (피탈리토), 원더룸 (라 플라타), 알마씨엘로 (나리노 라 유니온, 엘 탐보, 안티오키아 메데인); the rest by the 외래어 표기법.
    // coords: enwiki department articles ("Huila Department" …); Sierra Nevada = "Sierra Nevada de Santa Marta".
    private fun colombia() = Origin(
        "Colombia",
        listOf(
            colombiaHuila(),
            colombiaNarino(),
            colombiaCauca(),
            colombiaTolima(),
            colombiaAntioquia(),
            colombiaQuindio(),
            colombiaRisaralda(),
            colombiaCaldas(),
            colombiaSantander(),
            colombiaNorteDeSantander(),
            colombiaValleDelCauca(),
            colombiaCundinamarca(),
            colombiaBoyaca(),
            colombiaSierraNevada(),
            colombiaCaqueta(),
            colombiaMeta(),
        ),
    )

    private fun colombiaHuila() = Place(
        "우일라", "Huila", listOf("후일라"),
        listOf(
            Place("피탈리토", "Pitalito", subs = listOf(Place("브루셀라스", "Bruselas"))),
            Place("산 아구스틴", "San Agustín", listOf("San Agustin")),
            Place("아세베도", "Acevedo", subs = listOf(Place("산 아돌포", "San Adolfo"))),
            Place("라 플라타", "La Plata"),
            Place("가르손", "Garzón", listOf("Garzon")),
            Place("팔레스티나", "Palestina"),
            Place("이스노스", "Isnos"),
            Place("수아사", "Suaza"),
            Place("티마나", "Timaná", listOf("Timana")),
            Place("라 아르헨티나", "La Argentina"),
            Place("오포라파", "Oporapa"),
            Place("히간테", "Gigante"),
            Place("타르키", "Tarqui"),
            Place("살라도블랑코", "Saladoblanco", listOf("Salado Blanco")),
            Place("엘리아스", "Elías", listOf("Elias")),
            Place("알헤시라스", "Algeciras"),
            Place("산타 마리아", "Santa María", listOf("Santa Maria")),
            Place("테루엘", "Teruel"),
            Place("네이바", "Neiva"),
            Place("과달루페", "Guadalupe"),
            Place("나타가", "Nátaga", listOf("Nataga")),
            Place("테살리아", "Tesalia"),
            Place("이키라", "Íquira", listOf("Iquira")),
            Place("엘 피탈", "El Pital", listOf("Pital")),
            Place("바라야", "Baraya"),
        ),
        lat = 3.0, lng = -75.3,
    )

    private fun colombiaNarino() = Place(
        "나리뇨", "Nariño", listOf("Narino", "나리노"),
        listOf(
            Place("부에사코", "Buesaco"),
            Place("라 우니온", "La Unión", listOf("La Union", "라 유니온")),
            Place("산 로렌소", "San Lorenzo"),
            Place("엘 타블론 데 고메스", "El Tablón de Gómez", listOf("El Tablon de Gomez", "El Tablón", "엘 타블론")),
            Place("콘사카", "Consacá", listOf("Consaca")),
            Place("산도나", "Sandoná", listOf("Sandona")),
            Place("라 크루스", "La Cruz"),
            Place("산 파블로", "San Pablo"),
            Place("알반", "Albán", listOf("Alban", "San José de Albán")),
            Place("아르볼레다", "Arboleda", listOf("Berruecos")),
            Place("콜론", "Colón", listOf("Colon", "Génova")),
            Place("벨렌", "Belén", listOf("Belen")),
            Place("산 베르나르도", "San Bernardo"),
            Place("타미낭고", "Taminango"),
            Place("차차귀", "Chachagüí", listOf("Chachagui")),
            Place("리나레스", "Linares"),
            Place("사마니에고", "Samaniego"),
            Place("라 플로리다", "La Florida"),
            Place("안쿠야", "Ancuya"),
            Place("야쿠안케르", "Yacuanquer"),
            Place("엘 탐보", "El Tambo"),
        ),
        lat = 1.17, lng = -77.27,
    )

    private fun colombiaCauca() = Place(
        "카우카", "Cauca",
        subs = listOf(
            Place("포파얀", "Popayán", listOf("Popayan")),
            Place("인사", "Inzá", listOf("Inza")),
            Place("피엔다모", "Piendamó", listOf("Piendamo")),
            Place("모랄레스", "Morales"),
            Place("엘 탐보", "El Tambo"),
            Place("카히비오", "Cajibío", listOf("Cajibio")),
            Place("팀비오", "Timbío", listOf("Timbio")),
            Place("토토로", "Totoró", listOf("Totoro")),
            Place("파에스", "Páez", listOf("Paez", "Belalcázar")),
            Place("라 베가", "La Vega"),
            Place("소타라", "Sotará", listOf("Sotara")),
            Place("로사스", "Rosas"),
            Place("라 시에라", "La Sierra"),
            Place("볼리바르", "Bolívar", listOf("Bolivar")),
            Place("칼도노", "Caldono"),
            Place("산탄데르 데 킬리차오", "Santander de Quilichao"),
            Place("부에노스 아이레스", "Buenos Aires"),
            Place("수아레스", "Suárez", listOf("Suarez")),
            Place("수크레", "Sucre"),
            Place("아르헬리아", "Argelia"),
            Place("발보아", "Balboa"),
            Place("알마게르", "Almaguer"),
            Place("산 세바스티안", "San Sebastián", listOf("San Sebastian")),
            Place("실비아", "Silvia"),
        ),
        lat = 2.45, lng = -76.62,
    )

    private fun colombiaTolima() = Place(
        "톨리마", "Tolima",
        subs = listOf(
            Place("플라나다스", "Planadas", subs = listOf(Place("가이타니아", "Gaitania"))),
            Place("아타코", "Ataco"),
            Place("차파랄", "Chaparral"),
            Place("리오블랑코", "Rioblanco", subs = listOf(Place("에레라", "Herrera"))),
            Place("이바게", "Ibagué", listOf("Ibague")),
            Place("리바노", "Líbano", listOf("Libano")),
            Place("프레스노", "Fresno"),
            Place("산 안토니오", "San Antonio"),
            Place("로비라", "Rovira"),
            Place("돌로레스", "Dolores"),
            Place("비야에르모사", "Villahermosa"),
            Place("산타 이사벨", "Santa Isabel"),
            Place("오르테가", "Ortega"),
            Place("에르베오", "Herveo"),
            Place("카사비앙카", "Casabianca"),
            Place("팔로카빌도", "Palocabildo"),
            Place("마리키타", "Mariquita", listOf("San Sebastián de Mariquita")),
            Place("안소아테기", "Anzoátegui", listOf("Anzoategui")),
            Place("카하마르카", "Cajamarca"),
        ),
        lat = 4.43, lng = -75.23,
    )

    private fun colombiaAntioquia() = Place(
        "안티오키아", "Antioquia", listOf("안티오퀴아"),
        listOf(
            Place("우라오", "Urrao"),
            Place("헤리코", "Jericó", listOf("Jerico")),
            Place("안데스", "Andes"),
            Place("시우다드 볼리바르", "Ciudad Bolívar", listOf("Ciudad Bolivar")),
            Place("베타니아", "Betania"),
            Place("살가르", "Salgar"),
            Place("콘코르디아", "Concordia"),
            Place("베툴리아", "Betulia"),
            Place("프레도니아", "Fredonia"),
            Place("타메시스", "Támesis", listOf("Tamesis")),
            Place("하르딘", "Jardín", listOf("Jardin")),
            Place("이스파니아", "Hispania"),
            Place("푸에블로리코", "Pueblorrico"),
            Place("베네시아", "Venecia"),
            Place("티티리비", "Titiribí", listOf("Titiribi")),
            Place("카라만타", "Caramanta"),
            Place("발파라이소", "Valparaíso", listOf("Valparaiso")),
            Place("타르소", "Tarso"),
            Place("산타 바르바라", "Santa Bárbara", listOf("Santa Barbara")),
            Place("아베호랄", "Abejorral"),
            Place("손손", "Sonsón", listOf("Sonson")),
            Place("에베히코", "Ebéjico", listOf("Ebejico")),
            Place("메데인", "Medellín", listOf("Medellin")),
        ),
        lat = 6.22, lng = -75.57,
    )

    private fun colombiaQuindio() = Place(
        "킨디오", "Quindío", listOf("Quindio"),
        listOf(
            Place("아르메니아", "Armenia"),
            Place("필란디아", "Filandia"),
            Place("살렌토", "Salento"),
            Place("칼라르카", "Calarcá", listOf("Calarca")),
            Place("부에나비스타", "Buenavista"),
            Place("시르카시아", "Circasia"),
            Place("몬테네그로", "Montenegro"),
            Place("킴바야", "Quimbaya"),
            Place("피하오", "Pijao"),
            Place("헤노바", "Génova", listOf("Genova")),
            Place("코르도바", "Córdoba", listOf("Cordoba")),
            Place("라 테바이다", "La Tebaida"),
        ),
        lat = 4.53, lng = -75.67,
    )

    private fun colombiaRisaralda() = Place(
        "리사랄다", "Risaralda",
        subs = listOf(
            Place("산타 로사 데 카발", "Santa Rosa de Cabal"),
            Place("벨렌 데 움브리아", "Belén de Umbría", listOf("Belen de Umbria")),
            Place("아피아", "Apía", listOf("Apia")),
            Place("산투아리오", "Santuario"),
            Place("페레이라", "Pereira"),
            Place("마르세야", "Marsella"),
            Place("과티카", "Guática", listOf("Guatica")),
            Place("킨치아", "Quinchía", listOf("Quinchia")),
            Place("미스트라토", "Mistrató", listOf("Mistrato")),
            Place("발보아", "Balboa"),
            Place("라 셀리아", "La Celia"),
        ),
        lat = 4.82, lng = -75.7,
    )

    private fun colombiaCaldas() = Place(
        "칼다스", "Caldas",
        subs = listOf(
            Place("마니살레스", "Manizales"),
            Place("친치나", "Chinchiná", listOf("Chinchina")),
            Place("비야마리아", "Villamaría", listOf("Villamaria")),
            Place("팔레스티나", "Palestina"),
            Place("네이라", "Neira"),
            Place("아과다스", "Aguadas"),
            Place("파코라", "Pácora", listOf("Pacora")),
            Place("살라미나", "Salamina"),
            Place("아란사수", "Aranzazu"),
            Place("안세르마", "Anserma"),
            Place("리오수시오", "Riosucio"),
            Place("수피아", "Supía", listOf("Supia")),
            Place("벨알카사르", "Belalcázar", listOf("Belalcazar")),
            Place("비테르보", "Viterbo"),
            Place("만사나레스", "Manzanares"),
            Place("마르케탈리아", "Marquetalia"),
            Place("펜실바니아", "Pensilvania"),
            Place("사마나", "Samaná", listOf("Samana")),
        ),
        lat = 5.1, lng = -75.55,
    )

    private fun colombiaSantander() = Place(
        "산탄데르", "Santander",
        subs = listOf(
            Place("산 힐", "San Gil"),
            Place("소코로", "Socorro"),
            Place("쿠리티", "Curití", listOf("Curiti")),
            Place("산 비센테 데 추쿠리", "San Vicente de Chucurí", listOf("San Vicente de Chucuri")),
            Place("엘 카르멘 데 추쿠리", "El Carmen de Chucurí", listOf("El Carmen de Chucuri")),
            Place("리오네그로", "Rionegro"),
            Place("레브리하", "Lebrija"),
            Place("엘 플라욘", "El Playón", listOf("El Playon")),
            Place("파라모", "Páramo", listOf("Paramo")),
            Place("바예 데 산 호세", "Valle de San José", listOf("Valle de San Jose")),
            Place("모고테스", "Mogotes"),
            Place("차랄라", "Charalá", listOf("Charala")),
            Place("오이바", "Oiba"),
            Place("수아이타", "Suaita"),
            Place("피에데쿠에스타", "Piedecuesta"),
            Place("로스 산토스", "Los Santos", listOf("Mesa de los Santos")),
            Place("사파토카", "Zapatoca"),
        ),
        lat = 7.13, lng = -73.0,
    )

    private fun colombiaNorteDeSantander() = Place(
        "노르테 데 산탄데르", "Norte de Santander",
        subs = listOf(
            Place("살라사르 데 라스 팔마스", "Salazar de las Palmas", listOf("Salazar")),
            Place("아르볼레다스", "Arboledas"),
            Place("치나코타", "Chinácota", listOf("Chinacota")),
            Place("톨레도", "Toledo"),
            Place("오카냐", "Ocaña", listOf("Ocana")),
            Place("아브레고", "Ábrego", listOf("Abrego")),
            Place("콘벤시온", "Convención", listOf("Convencion")),
            Place("테오라마", "Teorama"),
            Place("쿠쿠티야", "Cucutilla"),
            Place("두라니아", "Durania"),
            Place("그라말로테", "Gramalote"),
            Place("보찰레마", "Bochalema"),
            Place("사르디나타", "Sardinata"),
        ),
        lat = 7.9, lng = -72.5,
    )

    private fun colombiaValleDelCauca() = Place(
        "바예 델 카우카", "Valle del Cauca", listOf("Valle", "바예"),
        listOf(
            Place("세비야", "Sevilla"),
            Place("카이세도니아", "Caicedonia"),
            Place("트루히요", "Trujillo"),
            Place("툴루아", "Tuluá", listOf("Tulua"), listOf(Place("바라간", "Barragán", listOf("Barragan")))),
            Place("엘 카이로", "El Cairo"),
            Place("엘 아길라", "El Águila", listOf("El Aguila")),
            Place("아르헬리아", "Argelia"),
            Place("볼리바르", "Bolívar", listOf("Bolivar")),
            Place("다과", "Dagua"),
            Place("레스트레포", "Restrepo"),
            Place("리오프리오", "Riofrío", listOf("Riofrio")),
            Place("베르사예스", "Versalles"),
            Place("엘 도비오", "El Dovio"),
            Place("안세르마누에보", "Ansermanuevo"),
            Place("알칼라", "Alcalá", listOf("Alcala")),
            Place("우요아", "Ulloa"),
            Place("부가", "Buga", listOf("Guadalajara de Buga")),
        ),
        lat = 3.42, lng = -76.52,
    )

    private fun colombiaCundinamarca() = Place(
        "쿤디나마르카", "Cundinamarca",
        subs = listOf(
            Place("비오타", "Viotá", listOf("Viota")),
            Place("라 베가", "La Vega"),
            Place("실바니아", "Silvania"),
            Place("푸사가수가", "Fusagasugá", listOf("Fusagasuga")),
            Place("아놀라이마", "Anolaima"),
            Place("라 메사", "La Mesa"),
            Place("엘 콜레히오", "El Colegio"),
            Place("티바쿠이", "Tibacuy"),
            Place("아르벨라에스", "Arbeláez", listOf("Arbelaez")),
            Place("파초", "Pacho"),
            Place("노카이마", "Nocaima"),
            Place("사사이마", "Sasaima"),
            Place("비예타", "Villeta"),
            Place("산 후안 데 리오세코", "San Juan de Rioseco"),
            Place("키필레", "Quipile"),
            Place("카파라피", "Caparrapí", listOf("Caparrapi")),
            Place("야코피", "Yacopí", listOf("Yacopi")),
        ),
        lat = 5.0, lng = -74.15,
    )

    private fun colombiaBoyaca() = Place(
        "보야카", "Boyacá", listOf("Boyaca"),
        listOf(
            Place("모니키라", "Moniquirá", listOf("Moniquira")),
            Place("아르카부코", "Arcabuco"),
            Place("토귀", "Togüí", listOf("Togui")),
            Place("산 호세 데 파레", "San José de Pare", listOf("San Jose de Pare")),
            Place("치타라케", "Chitaraque"),
            Place("산타나", "Santana"),
            Place("미라플로레스", "Miraflores"),
            Place("세타키라", "Zetaquira"),
            Place("가라고아", "Garagoa"),
            Place("파우나", "Pauna"),
            Place("오탄체", "Otanche"),
            Place("마리피", "Maripí", listOf("Maripi")),
        ),
        lat = 5.53, lng = -73.37,
    )

    private fun colombiaSierraNevada() = Place(
        "시에라 네바다", "Sierra Nevada", listOf("Sierra Nevada de Santa Marta", "시에라 네바다 데 산타 마르타"),
        listOf(
            Place(
                "막달레나", "Magdalena",
                subs = listOf(
                    Place("산타 마르타", "Santa Marta"),
                    Place("민카", "Minca"),
                    Place("시에나가", "Ciénaga", listOf("Cienaga")),
                    Place("아라카타카", "Aracataca"),
                    Place("푼다시온", "Fundación", listOf("Fundacion")),
                ),
            ),
            Place("세사르", "Cesar", subs = listOf(Place("푸에블로 베요", "Pueblo Bello"), Place("바예두파르", "Valledupar"))),
            Place("라 과히라", "La Guajira", subs = listOf(Place("디부야", "Dibulla"))),
        ),
        lat = 10.87, lng = -73.72,
    )

    private fun colombiaCaqueta() = Place(
        "카케타", "Caquetá", listOf("Caqueta"),
        listOf(
            Place("플로렌시아", "Florencia"),
            Place("벨렌 데 로스 안다키에스", "Belén de los Andaquíes", listOf("Belen de los Andaquies")),
            Place("산 호세 델 프라과", "San José del Fragua", listOf("San Jose del Fragua")),
            Place("엘 돈세요", "El Doncello"),
            Place("엘 파우힐", "El Paujil"),
            Place("라 몬타니타", "La Montañita", listOf("La Montanita")),
        ),
        lat = 1.62, lng = -75.61,
    )

    private fun colombiaMeta() = Place(
        "메타", "Meta",
        subs = listOf(
            Place("메세타스", "Mesetas"),
            Place("레하니아스", "Lejanías", listOf("Lejanias")),
            Place("우리베", "Uribe"),
            Place("엘 카스티요", "El Castillo"),
            Place("쿠바랄", "Cubarral"),
        ),
        lat = 4.15, lng = -73.63,
    )

    // source: Perfect Daily Grind "A Guide to Peru's Coffee-Producing Regional Profiles"; Khipu Coffee "The 10 Coffee
    // Regions of Peru" (Puno: Sandia, Alto Inambari; Cusco: Calca, La Convención; Ayacucho; Junín: Chanchamayo, Satipo,
    // Pangoa; Pasco: Villa Rica, Oxapampa, Pozuzo; Huánuco: Tingo María; San Martín: Moyobamba; Amazonas: Chachapoyas,
    // Rodríguez de Mendoza; Cajamarca: Jaén, San Ignacio; Piura: Huancabamba); Trabocca "Cajamarca" and "Tabaconas"; Royal
    // Coffee offers (Cajamarca Huabal, Amazonas Lonya Grande); Covoya (San Ignacio, Huabal); Seven Districts (Huabal,
    // Chirinos); Sweet Maria's "Peru Coffee Regions – Cuzco and Quillabamba"; Peru.info "Discover the coffee route"
    // (Quellouno, Quillabamba); the other districts as importers' offer sheets and the Alliance for Coffee Excellence farm
    // directory (Peru Cup of Excellence winners) name them. Korean: 외래어 표기법.
    // coords: enwiki — Cajamarca, Amazonas, San Martín = department articles; the coffee provinces for the rest: Junín =
    // Chanchamayo, Cusco = La Convención, Puno = Sandia, Pasco = Oxapampa, Huánuco = Leoncio Prado, Piura = Ayabaca,
    // Ayacucho = La Mar.
    private fun peru() = Origin(
        "Peru",
        listOf(
            peruCajamarca(),
            peruAmazonas(),
            peruSanMartin(),
            peruJunin(),
            peruCusco(),
            peruPuno(),
            peruPasco(),
            peruHuanuco(),
            peruPiura(),
            peruAyacucho(),
        ),
    )

    private fun peruCajamarca() = Place(
        "카하마르카", "Cajamarca",
        subs = listOf(
            Place(
                "하엔", "Jaén", listOf("Jaen"),
                listOf(
                    Place("우아발", "Huabal"),
                    Place("콜라사이", "Colasay"),
                    Place("촌탈리", "Chontalí", listOf("Chontali")),
                    Place("포마우아카", "Pomahuaca"),
                    Place("사이케", "Sallique"),
                    Place("라스 피리아스", "Las Pirias"),
                ),
            ),
            Place(
                "산 이그나시오", "San Ignacio",
                subs = listOf(
                    Place("치리노스", "Chirinos"),
                    Place("타바코나스", "Tabaconas"),
                    Place("우아랑고", "Huarango"),
                    Place("라 코이파", "La Coipa"),
                    Place("남바예", "Namballe"),
                    Place("산 호세 데 로우르데스", "San José de Lourdes", listOf("San Jose de Lourdes")),
                ),
            ),
            Place(
                "쿠테르보", "Cutervo",
                subs = listOf(
                    Place("산토 토마스", "Santo Tomás", listOf("Santo Tomas")),
                    Place("소코타", "Socota"),
                    Place("카야유크", "Callayuc"),
                ),
            ),
            Place("초타", "Chota"),
        ),
        lat = -6.61, lng = -78.78,
    )

    private fun peruAmazonas() = Place(
        "아마소나스", "Amazonas", listOf("아마조나스"),
        listOf(
            Place(
                "로드리게스 데 멘도사", "Rodríguez de Mendoza", listOf("Rodriguez de Mendoza"),
                listOf(
                    Place("우암보", "Huambo"),
                    Place("오미아", "Omia"),
                    Place("롱가르", "Longar"),
                ),
            ),
            Place("루야", "Luya"),
            Place(
                "우트쿠밤바", "Utcubamba",
                subs = listOf(
                    Place("론야 그란데", "Lonya Grande"),
                    Place("카하루로", "Cajaruro"),
                    Place("하말카", "Jamalca"),
                ),
            ),
            Place("바과", "Bagua", subs = listOf(Place("아랑고", "Aramango"), Place("코파인", "Copallín", listOf("Copallin")))),
            Place(
                "봉가라", "Bongará", listOf("Bongara"),
                listOf(
                    Place("플로리다", "Florida", listOf("Pomacochas")),
                    Place("얌브라스밤바", "Yambrasbamba"),
                ),
            ),
            Place("차차포야스", "Chachapoyas"),
        ),
        lat = -6.22, lng = -77.85,
    )

    private fun peruSanMartin() = Place(
        "산 마르틴", "San Martín", listOf("San Martin"),
        listOf(
            Place(
                "모요밤바", "Moyobamba",
                subs = listOf(
                    Place("헤펠라시오", "Jepelacio"),
                    Place("소리토르", "Soritor"),
                    Place("아바나", "Habana"),
                ),
            ),
            Place(
                "리오하", "Rioja",
                subs = listOf(
                    Place("누에바 카하마르카", "Nueva Cajamarca"),
                    Place("파르도 미겔", "Pardo Miguel"),
                    Place("아와훈", "Awajún", listOf("Awajun")),
                ),
            ),
            Place("라마스", "Lamas"),
            Place("엘 도라도", "El Dorado"),
            Place("토카체", "Tocache"),
            Place("마리스칼 카세레스", "Mariscal Cáceres", listOf("Mariscal Caceres", "Juanjuí")),
        ),
        lat = -7.2, lng = -76.8,
    )

    private fun peruJunin() = Place(
        "후닌", "Junín", listOf("Junin"),
        listOf(
            Place(
                "찬차마요", "Chanchamayo",
                subs = listOf(
                    Place("피차나키", "Pichanaqui", listOf("Pichanaki")),
                    Place("페레네", "Perené", listOf("Perene")),
                    Place("산 라몬", "San Ramón", listOf("San Ramon")),
                    Place("비톡", "Vitoc"),
                    Place("산 루이스 데 슈아로", "San Luis de Shuaro"),
                ),
            ),
            Place(
                "사티포", "Satipo",
                subs = listOf(
                    Place("판고아", "Pangoa", listOf("San Martín de Pangoa")),
                    Place("마사마리", "Mazamari"),
                    Place("리오 탐보", "Río Tambo", listOf("Rio Tambo")),
                    Place("리오 네그로", "Río Negro", listOf("Rio Negro")),
                ),
            ),
        ),
        lat = -11.05, lng = -75.33,
    )

    private fun peruCusco() = Place(
        "쿠스코", "Cusco", listOf("Cuzco"),
        listOf(
            Place(
                "라 콘벤시온", "La Convención", listOf("La Convencion"),
                listOf(
                    Place("키야밤바", "Quillabamba"),
                    Place("에차라티", "Echarati", listOf("Echarate")),
                    Place("산타 테레사", "Santa Teresa"),
                    Place("우아요파타", "Huayopata"),
                    Place("마라누라", "Maranura"),
                    Place("오코밤바", "Ocobamba"),
                    Place("케요우노", "Quellouno"),
                    Place("킴비리", "Kimbiri"),
                    Place("피차리", "Pichari"),
                    Place("빌카밤바", "Vilcabamba"),
                ),
            ),
            Place("칼카", "Calca", subs = listOf(Place("야나틸레", "Yanatile"))),
            Place("파우카르탐보", "Paucartambo", subs = listOf(Place("코스니파타", "Kosñipata", listOf("Kosnipata")))),
        ),
        lat = -12.86, lng = -72.69,
    )

    private fun peruPuno() = Place(
        "푸노", "Puno",
        subs = listOf(
            Place(
                "산디아", "Sandia",
                subs = listOf(
                    Place("산 후안 델 오로", "San Juan del Oro"),
                    Place("푸티나 풍코", "Putina Punco", listOf("San Pedro de Putina Punco")),
                    Place("알토 이남바리", "Alto Inambari"),
                    Place("야나우아야", "Yanahuaya"),
                ),
            ),
        ),
        lat = -14.25, lng = -69.43,
    )

    private fun peruPasco() = Place(
        "파스코", "Pasco",
        subs = listOf(
            Place("비야 리카", "Villa Rica"),
            Place("옥사팜파", "Oxapampa"),
            Place("촌타밤바", "Chontabamba"),
            Place("우앙카밤바", "Huancabamba"),
            Place("포수소", "Pozuzo"),
        ),
        lat = -10.57, lng = -75.41,
    )

    private fun peruHuanuco() = Place(
        "우아누코", "Huánuco", listOf("Huanuco"),
        listOf(
            Place("레온시오 프라도", "Leoncio Prado", subs = listOf(Place("팅고 마리아", "Tingo María", listOf("Tingo Maria")))),
            Place("친차오", "Chinchao"),
        ),
        lat = -9.29, lng = -75.99,
    )

    private fun peruPiura() = Place(
        "피우라", "Piura",
        subs = listOf(
            Place("아야바카", "Ayabaca", subs = listOf(Place("몬테로", "Montero"))),
            Place("우앙카밤바", "Huancabamba", subs = listOf(Place("칸차케", "Canchaque"), Place("랄라키스", "Lalaquiz"))),
            Place("모로폰", "Morropón", listOf("Morropon"), listOf(Place("찰라코", "Chalaco"))),
        ),
        lat = -4.64, lng = -79.72,
    )

    private fun peruAyacucho() = Place(
        "아야쿠초", "Ayacucho",
        subs = listOf(
            Place("라 마르", "La Mar", subs = listOf(Place("아이나", "Ayna"), Place("산타 로사", "Santa Rosa"))),
        ),
        lat = -13.01, lng = -73.98,
    )

    // source: Royal Coffee "Ecuador Coffee Export Review: Loja, Zamora Chinchipe & Pichincha"; Happy Gringo "Ecuadorian
    // Coffee – The Ultimate Guide" (Pichincha, Imbabura, Carchi, the Intag Valley, Loja, Zamora Chinchipe, El Oro);
    // xliiicoffee.com and Expat Ecuador coffee guides (Vilcabamba, Gonzanamá, Quilanga, Catamayo, Pacto, Nanegalito,
    // Zaruma); List+Beisler "Loja, Valle de Vilcabamba"; FAPECAFES member associations (PROCAFEQ: Espíndola and Quilanga;
    // APECAP: Palanda and Chinchipe; PROCAP: Puyango); AACRI (Río Intag); Cup of Excellence Ecuador 2021 results via Daily
    // Coffee News and Ecuador Times (Saraguro, Loja); Royal Coffee "Pichincha Galo Morales Cruz Loma" (San José de Minas);
    // Square One / Nossa Familia (Nanegal: Hakuna Matata, Maputo); ConQuito "Gualea tiene el mejor café del Distrito";
    // Finca El Guarangal (Perucho); Galápagos: Hacienda El Cafetal (San Cristóbal) and Santa Cruz highland farms as Galápagos
    // coffee is sold. Korean: 외래어 표기법.
    // coords: enwiki province articles; El Oro = Zaruma; Galápagos = San Cristóbal Island.
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
                    Place(
                        "에스핀돌라", "Espíndola", listOf("Espindola"),
                        listOf(
                            Place("아말루사", "Amaluza"),
                            Place("엘 아이로", "El Airo"),
                        ),
                    ),
                    Place("푸양고", "Puyango"),
                    Place("사라구로", "Saraguro"),
                ),
                lat = -4.02, lng = -79.48,
            ),
            Place(
                "사모라 친치페", "Zamora Chinchipe", listOf("Zamora-Chinchipe"),
                listOf(
                    Place("팔란다", "Palanda"),
                    Place("친치페", "Chinchipe", listOf("Zumba")),
                    Place("사모라", "Zamora"),
                ),
                lat = -4.1, lng = -78.88,
            ),
            Place(
                "피친차", "Pichincha",
                subs = listOf(
                    Place("팍토", "Pacto"),
                    Place("나네갈리토", "Nanegalito"),
                    Place("나네갈", "Nanegal"),
                    Place("괄레아", "Gualea"),
                    Place("페루초", "Perucho"),
                    Place("산 호세 데 미나스", "San José de Minas", listOf("San Jose de Minas")),
                ),
                lat = -0.25, lng = -78.58,
            ),
            Place(
                "임바부라", "Imbabura",
                subs = listOf(
                    Place(
                        "인탁", "Intag", listOf("Intag Valley"),
                        listOf(
                            Place("아푸엘라", "Apuela"),
                            Place("쿠에야헤", "Cuellaje", listOf("Seis de Julio de Cuellaje")),
                            Place("페냐에레라", "Peñaherrera", listOf("Penaherrera")),
                            Place("플라사 구티에레스", "Plaza Gutiérrez", listOf("Plaza Gutierrez")),
                            Place("가르시아 모레노", "García Moreno", listOf("Garcia Moreno")),
                            Place("바카스 갈린도", "Vacas Galindo"),
                        ),
                    ),
                    Place("코타카치", "Cotacachi"),
                ),
                lat = 0.35, lng = -78.13,
            ),
            Place(
                "엘 오로", "El Oro",
                subs = listOf(
                    Place("사루마", "Zaruma"),
                    Place("피냐스", "Piñas", listOf("Pinas")),
                    Place("포르토벨로", "Portovelo"),
                ),
                lat = -3.68, lng = -79.6,
            ),
            Place("카르치", "Carchi", lat = 0.45, lng = -78.05),
            // grown in the highlands of San Cristóbal and Santa Cruz; the simplified world map has no Galápagos outline,
            // so the dot is drawn in the sea where the islands are (WorldRegionsTest lets it)
            Place(
                "갈라파고스", "Galápagos", listOf("Galapagos", "Galápagos Islands"),
                listOf(Place("산크리스토발", "San Cristóbal", listOf("San Cristobal")), Place("산타크루스", "Santa Cruz")),
                lat = -0.83, lng = -89.43,
            ),
        ),
    )

    // source: Market Lane "Bolivia: putting the 'special' in specialty coffee" (Caranavi, Coroico, Nor & Sud Yungas,
    // Samaipata); Algrano "Coffee from Bolivia" (Los Yungas, Caranavi, Samaipata); Royal Coffee Agricafe "Sol de la
    // Mañana" offers (Colonia Bolinda, Caranavi); Catastro "Bolivia" (Taypiplaya, Yanacachi); the other municipalities as
    // importers' offer sheets and the Alliance for Coffee Excellence farm directory (Bolivia Cup of Excellence winners)
    // name them. Korean: 외래어 표기법.
    // coords: Wikidata Caranavi (Q1014468); enwiki Coroico (for Yungas); eswiki Samaipata.
    private fun bolivia() = Origin(
        "Bolivia",
        listOf(
            Place(
                "카라나비", "Caranavi",
                subs = listOf(
                    Place("타이피플라야", "Taypiplaya", listOf("Taipiplaya")),
                    Place("콜로니아 볼린다", "Colonia Bolinda", listOf("Bolinda")),
                    Place("알토 베니", "Alto Beni"),
                ),
                lat = -15.83, lng = -67.57,
            ),
            Place(
                "융가스", "Yungas", listOf("Los Yungas"),
                listOf(
                    Place("노르 융가스", "Nor Yungas", subs = listOf(Place("코로이코", "Coroico"), Place("코리파타", "Coripata"))),
                    Place(
                        "수드 융가스", "Sud Yungas",
                        subs = listOf(
                            Place("출루마니", "Chulumani"),
                            Place("이루파나", "Irupana"),
                            Place("야나카치", "Yanacachi"),
                            Place("라 아순타", "La Asunta"),
                        ),
                    ),
                ),
                lat = -16.18, lng = -67.73,
            ),
            Place("사마이파타", "Samaipata", lat = -18.18, lng = -63.88),
        ),
    )

    // source: Perfect Daily Grind "Coffee Origins: A Guide to Mexico" (Chiapas: Soconusco, Sierra Mariscal, Frailesca;
    // Oaxaca: Costa, Cañada, Sierra Norte, Mazateca, Mixteca, Sierra Sur, Juquila, Mixe); Mexico News Daily "The best
    // coffee in Mexico" (Jaltenango, Motozintla, Pluma Hidalgo); Covoya "Siltepec, Motozintla"; Wikipedia "Coffee
    // production in Mexico", "Soconusco", "Ángel Albino Corzo" (Jaltenango), "Coatepec, Veracruz", "Huatusco", and the
    // Sierra Norte de Puebla towns (Cuetzalan, Xicotepec, Huauchinango); the other municipalities as importers' offer
    // sheets and the Alliance for Coffee Excellence farm directory (Mexico Cup of Excellence winners) name them. Korean:
    // 외래어 표기법 (오악사카 as ko.wikipedia).
    // coords: enwiki state articles.
    private fun mexico() = Origin(
        "Mexico",
        listOf(
            mexicoChiapas(),
            mexicoOaxaca(),
            mexicoVeracruz(),
            mexicoPuebla(),
            mexicoGuerrero(),
            mexicoNayarit(),
        ),
    )

    private fun mexicoChiapas() = Place(
        "치아파스", "Chiapas",
        subs = listOf(
            Place(
                "소코누스코", "Soconusco",
                subs = listOf(
                    Place("타파출라", "Tapachula"),
                    Place("카카오아탄", "Cacahoatán", listOf("Cacahoatan")),
                    Place("우니온 후아레스", "Unión Juárez", listOf("Union Juarez")),
                    Place("우이스틀라", "Huixtla"),
                    Place("툭스틀라 치코", "Tuxtla Chico"),
                ),
            ),
            Place(
                "시에라 마리스칼", "Sierra Mariscal",
                subs = listOf(
                    Place("모토신틀라", "Motozintla"),
                    Place("실테펙", "Siltepec"),
                    Place("엘 포르베니르", "El Porvenir"),
                    Place("벨라 비스타", "Bella Vista"),
                ),
            ),
            Place(
                "프라일레스카", "Frailesca", listOf("La Frailesca"),
                listOf(
                    Place(
                        "할테낭고", "Jaltenango", listOf("Ángel Albino Corzo", "Angel Albino Corzo", "Jaltenango de la Paz"),
                    ),
                    Place("라 콘코르디아", "La Concordia"),
                    Place("몬테크리스토 데 게레로", "Montecristo de Guerrero"),
                    Place("비야 코르소", "Villa Corzo"),
                ),
            ),
            Place(
                "로스 알토스", "Los Altos", listOf("Altos de Chiapas"),
                listOf(
                    Place("체날로", "Chenalhó", listOf("Chenalho")),
                    Place("판텔로", "Pantelhó", listOf("Pantelho")),
                    Place("옥스추크", "Oxchuc"),
                    Place("테네하파", "Tenejapa"),
                ),
            ),
            Place("야할론", "Yajalón", listOf("Yajalon")),
            Place("틸라", "Tila"),
            Place("칠론", "Chilón", listOf("Chilon")),
            Place("오코싱고", "Ocosingo"),
            Place("툼발라", "Tumbalá", listOf("Tumbala")),
        ),
        lat = 16.53, lng = -92.45,
    )

    private fun mexicoOaxaca() = Place(
        "오악사카", "Oaxaca", listOf("와하카"),
        listOf(
            Place("플루마 이달고", "Pluma Hidalgo"),
            Place("시에라 수르", "Sierra Sur"),
            Place("시에라 노르테", "Sierra Norte", listOf("Sierra Juárez", "Sierra Juarez")),
            Place("시에라 마사테카", "Sierra Mazateca", listOf("Mazateca")),
            Place("시에라 미헤", "Sierra Mixe", listOf("Mixe", "시에라 믹세")),
            Place(
                "미스테카", "Mixteca",
                subs = listOf(
                    Place("산타 마리아 유쿠이티", "Santa María Yucuhiti", listOf("Santa Maria Yucuhiti", "Yucuhiti")),
                ),
            ),
            Place(
                "후킬라", "Juquila", listOf("Santa Catarina Juquila"),
                listOf(
                    Place("산 후안 라차오", "San Juan Lachao"),
                    Place("산토스 레예스 노팔라", "Santos Reyes Nopala"),
                ),
            ),
            Place(
                "로시차", "Loxicha", listOf("록시차"),
                listOf(
                    Place("칸델라리아 로시차", "Candelaria Loxicha"),
                    Place("산 아구스틴 로시차", "San Agustín Loxicha", listOf("San Agustin Loxicha")),
                ),
            ),
        ),
        lat = 17.05, lng = -96.66,
    )

    private fun mexicoVeracruz() = Place(
        "베라크루스", "Veracruz", listOf("베라크루즈"),
        listOf(
            Place("코아테펙", "Coatepec"),
            Place("시코", "Xico"),
            Place("테오셀로", "Teocelo"),
            Place("코사우틀란", "Cosautlán", listOf("Cosautlan", "Cosautlán de Carvajal")),
            Place("이스와칸", "Ixhuacán", listOf("Ixhuacan", "Ixhuacán de los Reyes")),
            Place("우아투스코", "Huatusco"),
            Place("토투틀라", "Totutla"),
            Place("틀랄테텔라", "Tlaltetela"),
            Place("이스우아틀란 델 카페", "Ixhuatlán del Café", listOf("Ixhuatlan del Cafe")),
            Place("초카만", "Chocamán", listOf("Chocaman")),
            Place("코르도바", "Córdoba", listOf("Cordoba")),
            Place("송골리카", "Zongolica"),
            Place("아찰란", "Atzalan"),
            Place("미산틀라", "Misantla"),
        ),
        lat = 19.84, lng = -96.06,
    )

    private fun mexicoPuebla() = Place(
        "푸에블라", "Puebla", listOf("Sierra Norte de Puebla"),
        listOf(
            Place("쿠에찰란", "Cuetzalan", listOf("Cuetzalan del Progreso")),
            Place("시코테펙", "Xicotepec", listOf("Xicotepec de Juárez")),
            Place("우아우치낭고", "Huauchinango"),
            Place("호노틀라", "Jonotla"),
            Place("틀라틀라우키테펙", "Tlatlauquitepec"),
            Place("우에이타말코", "Hueytamalco"),
            Place("시우아테우틀라", "Zihuateutla"),
            Place("파우아틀란", "Pahuatlán", listOf("Pahuatlan")),
        ),
        lat = 19.0, lng = -97.88,
    )

    private fun mexicoGuerrero() = Place(
        "게레로", "Guerrero",
        subs = listOf(
            Place("아토약", "Atoyac", listOf("Atoyac de Álvarez")),
            Place("말리날테펙", "Malinaltepec"),
            Place("산 루이스 아카틀란", "San Luis Acatlán", listOf("San Luis Acatlan")),
            Place("코유카 데 베니테스", "Coyuca de Benítez", listOf("Coyuca de Benitez")),
        ),
        lat = 17.62, lng = -99.95,
    )

    private fun mexicoNayarit() = Place(
        "나야리트", "Nayarit",
        subs = listOf(
            Place("테픽", "Tepic"),
            Place("할리스코", "Xalisco"),
        ),
        lat = 22.0, lng = -105.0,
    )

    // source: ANACAFE's eight regions (Acatenango Valley, Antigua Coffee, Traditional Atitlán, Rainforest Cobán, Fraijanes
    // Plateau, Highland Huehue, New Oriente, Volcanic San Marcos) via Genuine Origin and Westrock origin reports; Cafe
    // Imports "Guatemala" and its Huehuetenango offers (Finca El Guachipilín, San Pedro Necta; ASPROCDEGUA: Cuilco, Santa
    // Bárbara, San Pedro Necta, Todos Santos; Waykan: Chajul, Cotzal, Nebaj of Quiché); The Coffee Quest "Finca La
    // Libertad"; Burman Coffee "Guatemalan Quiche"; Sucafina "Jalapa Fully Washed" and Semilla / Zab Café (El Durazno,
    // Mataquescuintla); the other municipalities as importers' offer sheets and the Alliance for Coffee Excellence farm
    // directory (Guatemala Cup of Excellence winners) name them. Korean: natalie.co.kr / 바리스타 룰스 (8 regions, 뉴오리엔테);
    // municipalities by the 외래어 표기법.
    // coords: enwiki — Antigua Guatemala, Huehuetenango Department, Acatenango, Lake Atitlán, Cobán, Fraijanes, Chiquimula
    // Department (for New Oriente), San Marcos Department, Quiché Department, Jalapa Department.
    private fun guatemala() = Origin(
        "Guatemala",
        listOf(
            guatemalaAntigua(),
            guatemalaHuehuetenango(),
            guatemalaAcatenango(),
            guatemalaAtitlan(),
            guatemalaCoban(),
            guatemalaFraijanes(),
            guatemalaNuevoOriente(),
            guatemalaSanMarcos(),
            guatemalaQuiche(),
            guatemalaJalapa(),
        ),
    )

    private fun guatemalaAntigua() = Place(
        "안티구아", "Antigua", listOf("안티과", "Antigua Coffee", "Antigua Guatemala"),
        listOf(
            Place("시우다드 비에하", "Ciudad Vieja"),
            Place("산 미겔 두에냐스", "San Miguel Dueñas", listOf("San Miguel Duenas")),
            Place("알로테낭고", "Alotenango", listOf("San Juan Alotenango")),
            Place("산 안토니오 아구아스 칼리엔테스", "San Antonio Aguas Calientes"),
            Place("산타 마리아 데 헤수스", "Santa María de Jesús", listOf("Santa Maria de Jesus")),
            Place("호코테낭고", "Jocotenango"),
            Place("파스토레스", "Pastores"),
            Place("산 후안 델 오비스포", "San Juan del Obispo"),
        ),
        lat = 14.56, lng = -90.73,
    )

    private fun guatemalaHuehuetenango() = Place(
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
            Place("산타 바르바라", "Santa Bárbara", listOf("Santa Barbara")),
            Place("산 라파엘 페찰", "San Rafael Petzal"),
            Place("콜로테낭고", "Colotenango"),
            Place("하칼테낭고", "Jacaltenango"),
            Place("산 일데폰소 이스타우아칸", "San Ildefonso Ixtahuacán", listOf("San Ildefonso Ixtahuacan")),
            Place("산티아고 치말테낭고", "Santiago Chimaltenango"),
            Place("산 후안 아티탄", "San Juan Atitán", listOf("San Juan Atitan")),
            Place("산타 아나 우이스타", "Santa Ana Huista"),
            Place("우니온 칸티닐", "Unión Cantinil", listOf("Union Cantinil")),
            Place("페타탄", "Petatán", listOf("Petatan")),
            Place("말라카탄시토", "Malacatancito"),
            Place("산 세바스티안 우에우에테낭고", "San Sebastián Huehuetenango", listOf("San Sebastian Huehuetenango")),
        ),
        lat = 15.31, lng = -91.48,
    )

    private fun guatemalaAcatenango() = Place(
        "아카테낭고", "Acatenango", listOf("Acatenango Valley"),
        listOf(
            Place("예포카파", "Yepocapa", listOf("San Pedro Yepocapa")),
        ),
        lat = 14.5, lng = -90.88,
    )

    private fun guatemalaAtitlan() = Place(
        "아티틀란", "Atitlán", listOf("Atitlan", "Traditional Atitlán"),
        listOf(
            Place("산 페드로 라 라구나", "San Pedro La Laguna"),
            Place("산 후안 라 라구나", "San Juan La Laguna"),
            Place("산티아고 아티틀란", "Santiago Atitlán", listOf("Santiago Atitlan")),
            Place("산 루카스 톨리만", "San Lucas Tolimán", listOf("San Lucas Toliman")),
            Place("산타 클라라 라 라구나", "Santa Clara La Laguna"),
            Place("산 파블로 라 라구나", "San Pablo La Laguna"),
            Place("산 마르코스 라 라구나", "San Marcos La Laguna"),
            Place("산 안토니오 팔로포", "San Antonio Palopó", listOf("San Antonio Palopo")),
        ),
        lat = 14.7, lng = -91.2,
    )

    private fun guatemalaCoban() = Place(
        "코반", "Cobán", listOf("Coban", "Rainforest Cobán", "Alta Verapaz", "알타 베라파스"),
        listOf(
            Place("산 페드로 카르차", "San Pedro Carchá", listOf("Carchá", "Carcha")),
            Place("산 크리스토발 베라파스", "San Cristóbal Verapaz", listOf("San Cristobal Verapaz")),
            Place("란킨", "Lanquín", listOf("Lanquin")),
            Place("카아본", "Cahabón", listOf("Cahabon", "Santa María Cahabón")),
            Place("세나우", "Senahú", listOf("Senahu")),
            Place("투쿠루", "Tucurú", listOf("Tucuru")),
            Place("타마우", "Tamahú", listOf("Tamahu")),
            Place("푸룰라", "Purulhá", listOf("Purulha")),
        ),
        lat = 15.48, lng = -90.37,
    )

    private fun guatemalaFraijanes() = Place(
        "프라이하네스", "Fraijanes", listOf("Fraijanes Plateau"),
        listOf(
            Place("산 호세 피눌라", "San José Pinula", listOf("San Jose Pinula")),
            Place("비야 카날레스", "Villa Canales"),
            Place("산타 카타리나 피눌라", "Santa Catarina Pinula"),
            Place("팔렌시아", "Palencia"),
            Place("바르베레나", "Barberena"),
        ),
        lat = 14.47, lng = -90.43,
    )

    private fun guatemalaNuevoOriente() = Place(
        "누에보 오리엔테", "Nuevo Oriente", listOf("뉴 오리엔테", "뉴오리엔테", "New Oriente"),
        listOf(
            Place("에스키풀라스", "Esquipulas"),
            Place("올로파", "Olopa"),
            Place("호코탄", "Jocotán", listOf("Jocotan")),
            Place("카모탄", "Camotán", listOf("Camotan")),
            Place("케살테페케", "Quezaltepeque"),
            Place("산 후안 에르미타", "San Juan Ermita"),
            Place("라 우니온", "La Unión", listOf("La Union")),
        ),
        lat = 14.8, lng = -89.54,
    )

    private fun guatemalaSanMarcos() = Place(
        "산 마르코스", "San Marcos", listOf("Volcanic San Marcos"),
        listOf(
            Place("엘 툼바도르", "El Tumbador"),
            Place("엘 케찰", "El Quetzal"),
            Place("라 레포르마", "La Reforma"),
            Place("누에보 프로그레소", "Nuevo Progreso"),
            Place("산 라파엘 피에 데 라 쿠에스타", "San Rafael Pie de la Cuesta"),
            Place("산 파블로", "San Pablo"),
            Place("타후물코", "Tajumulco"),
            Place("타카나", "Tacaná", listOf("Tacana")),
            Place("에스키풀라스 팔로 고르도", "Esquipulas Palo Gordo"),
        ),
        lat = 14.96, lng = -91.8,
    )

    private fun guatemalaQuiche() = Place(
        "키체", "Quiché", listOf("Quiche", "El Quiché", "El Quiche"),
        listOf(
            Place("차훌", "Chajul"),
            Place("네바흐", "Nebaj", listOf("Santa María Nebaj")),
            Place("우스판탄", "Uspantán", listOf("Uspantan")),
            Place("산 후안 코찰", "San Juan Cotzal"),
        ),
        lat = 15.03, lng = -91.15,
    )

    private fun guatemalaJalapa() = Place(
        "할라파", "Jalapa",
        subs = listOf(
            Place("마타케스쿠인틀라", "Mataquescuintla"),
        ),
        lat = 14.63, lng = -89.98,
    )

    // source: IHCAFE's six regions (Copán, Opalaca, Montecillos, Agalta, El Paraíso, Comayagua) via Perfect Daily Grind
    // "Introducing the 6 coffee regions of Honduras" and RedHonduras "Geographic regions of coffee in Honduras" (Opalaca:
    // Santa Bárbara, Intibucá, Lempira; Montecillos: La Paz, Comayagua); Royal Coffee "Honduras" (Marcala, Proyecto
    // Cabañas); Alliance for Coffee Excellence "Honduras 2025" results (Lempira, Santa Bárbara, El Paraíso, La Paz,
    // Francisco Morazán) and "Honduras Marks its 20th Cup of Excellence" (El Cedral, Santa Bárbara); The Coffee Quest
    // "Finca La Sierra" (El Cedral, Las Vegas, Santa Bárbara); the other municipalities as importers' offer sheets and the
    // Alliance for Coffee Excellence farm directory (Honduras Cup of Excellence winners) name them. Korean: 워너빈 (코판), 로스팅빈
    // / G마켓 생두 (산타바바라); the rest by the 외래어 표기법.
    // coords: enwiki — Marcala; department articles; Agalta = Sierra de Agalta National Park; Wikidata — Opalaca = San
    // Francisco de Opalaca (Q2393370).
    private fun honduras() = Origin(
        "Honduras",
        listOf(
            hondurasMarcala(),
            hondurasSantaBarbara(),
            hondurasCopan(),
            hondurasOcotepeque(),
            hondurasLempira(),
            hondurasIntibuca(),
            hondurasComayagua(),
            hondurasElParaiso(),
            hondurasOlancho(),
            Place("프란시스코 모라산", "Francisco Morazán", listOf("Francisco Morazan"), lat = 14.1, lng = -87.22),
            Place("오팔라카", "Opalaca", lat = 14.57, lng = -88.31),
            hondurasAgalta(),
        ),
    )

    private fun hondurasMarcala() = Place(
        "마르칼라", "Marcala", listOf("La Paz", "라 파스", "라파스", "Montecillos", "몬테시요스"),
        listOf(
            Place("산티아고 데 푸링글라", "Santiago de Puringla", listOf("Santiago Puringla")),
            Place("치나클라", "Chinacla"),
            Place("산 호세", "San José", listOf("San Jose")),
            Place("오파토로", "Opatoro"),
            Place("산타 아나", "Santa Ana"),
            Place("카바냐스", "Cabañas", listOf("Cabanas")),
            Place("야룰라", "Yarula"),
            Place("산타 엘레나", "Santa Elena"),
            Place("산 페드로 데 투툴레", "San Pedro de Tutule"),
            Place("과히키로", "Guajiquiro"),
        ),
        lat = 14.32, lng = -87.68,
    )

    private fun hondurasSantaBarbara() = Place(
        "산타 바르바라", "Santa Bárbara", listOf("Santa Barbara", "산타바바라", "산타 바바라"),
        listOf(
            Place("라스 베가스", "Las Vegas", subs = listOf(Place("엘 세드랄", "El Cedral"))),
            Place("산 루이스", "San Luis"),
            Place("누에바 프론테라", "Nueva Frontera"),
            Place("산 니콜라스", "San Nicolás", listOf("San Nicolas")),
            Place("트리니다드", "Trinidad"),
            Place("콘셉시온 델 수르", "Concepción del Sur", listOf("Concepcion del Sur")),
            Place("엘 니스페로", "El Níspero", listOf("El Nispero")),
            Place("일라마", "Ilama"),
            Place("아티마", "Atima"),
        ),
        lat = 14.92, lng = -88.23,
    )

    private fun hondurasCopan() = Place(
        "코판", "Copán", listOf("Copan"),
        listOf(
            Place("코르킨", "Corquín", listOf("Corquin")),
            Place("산타 로사 데 코판", "Santa Rosa de Copán", listOf("Santa Rosa de Copan")),
            Place("둘세 놈브레 데 코판", "Dulce Nombre de Copán", listOf("Dulce Nombre de Copan")),
            Place("쿠쿠야과", "Cucuyagua"),
            Place("코판 루이나스", "Copán Ruinas", listOf("Copan Ruinas")),
            Place("산 페드로 데 코판", "San Pedro de Copán", listOf("San Pedro de Copan")),
            Place("트리니다드 데 코판", "Trinidad de Copán", listOf("Trinidad de Copan")),
        ),
        lat = 14.77, lng = -88.78,
    )

    private fun hondurasOcotepeque() = Place(
        "오코테페케", "Ocotepeque",
        subs = listOf(
            Place("벨렌 괄초", "Belén Gualcho", listOf("Belen Gualcho")),
            Place("산 마르코스", "San Marcos"),
            Place("메르세데스", "Mercedes"),
            Place("라 라보르", "La Labor"),
            Place("루세르나", "Lucerna"),
            Place("센센티", "Sensenti"),
            Place("돌로레스 메렌돈", "Dolores Merendón", listOf("Dolores Merendon")),
        ),
        lat = 14.43, lng = -89.18,
    )

    private fun hondurasLempira() = Place(
        "렘피라", "Lempira",
        subs = listOf(
            Place("그라시아스", "Gracias"),
            Place("라 캄파", "La Campa"),
            Place("산 마누엘 콜로에테", "San Manuel Colohete"),
            Place("에란디케", "Erandique"),
            Place("레파에라", "Lepaera"),
            Place("산 안드레스", "San Andrés", listOf("San Andres")),
        ),
        lat = 14.58, lng = -88.58,
    )

    private fun hondurasIntibuca() = Place(
        "인티부카", "Intibucá", listOf("Intibuca"),
        listOf(
            Place("라 에스페란사", "La Esperanza"),
            Place("헤수스 데 오토로", "Jesús de Otoro", listOf("Jesus de Otoro")),
            Place("마사과라", "Masaguara"),
            Place("야마랑길라", "Yamaranguila"),
            Place("산 후안", "San Juan"),
            Place("산 이시드로", "San Isidro"),
        ),
        lat = 14.32, lng = -88.15,
    )

    private fun hondurasComayagua() = Place(
        "코마야과", "Comayagua",
        subs = listOf(
            Place("시과테페케", "Siguatepeque"),
            Place("산 헤로니모", "San Jerónimo", listOf("San Jeronimo")),
            Place("미나스 데 오로", "Minas de Oro"),
            Place("산 호세 데 코마야과", "San José de Comayagua", listOf("San Jose de Comayagua")),
            Place("타울라베", "Taulabé", listOf("Taulabe")),
            Place("에스키아스", "Esquías", listOf("Esquias")),
        ),
        lat = 14.45, lng = -87.63,
    )

    private fun hondurasElParaiso() = Place(
        "엘 파라이소", "El Paraíso", listOf("El Paraiso"),
        listOf(
            Place("단리", "Danlí", listOf("Danli")),
            Place("알라우카", "Alauca"),
            Place("트로헤스", "Trojes"),
            Place("산 마티아스", "San Matías", listOf("San Matias")),
            Place("귀노페", "Güinope", listOf("Guinope")),
            Place("하칼레아파", "Jacaleapa"),
            Place("테우파센티", "Teupasenti"),
            Place("유스카란", "Yuscarán", listOf("Yuscaran")),
        ),
        lat = 13.93, lng = -86.85,
    )

    private fun hondurasOlancho() = Place(
        "올란초", "Olancho",
        subs = listOf(
            Place("캄파멘토", "Campamento"),
            Place("후티칼파", "Juticalpa"),
        ),
        lat = 14.6, lng = -86.2,
    )

    private fun hondurasAgalta() = Place(
        "아갈타", "Agalta",
        subs = listOf(
            Place("괄라코", "Gualaco"),
            Place("산 에스테반", "San Esteban"),
            Place("카타카마스", "Catacamas"),
        ),
        lat = 15.01, lng = -85.85,
    )

    // source: Consejo Salvadoreño del Café's six cordilleras, via El Target "¿Sabes cuáles son las regiones cafetaleras en
    // El Salvador?"; Marca Sur "Denominaciones de Origen: zonas cafetaleras de El Salvador" (Chalatenango: La Palma, San
    // Ignacio; Tecapa-Chinameca: Berlín, Santiago de María, Chinameca; Apaneca, Ataco, Juayúa); the other municipalities
    // as importers' offer sheets and the Alliance for Coffee Excellence farm directory (El Salvador Cup of Excellence
    // winners) name them. Korean: 외래어 표기법.
    // coords: enwiki — Santa Ana Department, Apaneca, Chalatenango Department, Metapán, San Salvador (volcano) for
    // Quezaltepec, Berlín (El Salvador), San Vicente (volcano) for Chichontepec, Cacahuatique Mountains.
    private fun elSalvador() = Origin(
        "El Salvador",
        listOf(
            Place(
                "산타 아나", "Santa Ana", listOf("Santa Ana Volcano"),
                listOf(
                    Place("찰추아파", "Chalchuapa"),
                    Place("엘 콩고", "El Congo"),
                    Place("코아테페케", "Coatepeque"),
                ),
                lat = 14.09, lng = -89.51,
            ),
            Place(
                "아파네카-일라마테펙", "Apaneca-Ilamatepec", listOf("Apaneca Ilamatepec", "Ilamatepec"),
                listOf(
                    Place("아파네카", "Apaneca"),
                    Place("후아유아", "Juayúa", listOf("Juayua")),
                    Place("콘셉시온 데 아타코", "Concepción de Ataco", listOf("Ataco", "아타코")),
                    Place("살코아티탄", "Salcoatitán", listOf("Salcoatitan")),
                    Place("타쿠바", "Tacuba"),
                    Place("나우이살코", "Nahuizalco"),
                ),
                lat = 13.85, lng = -89.8,
            ),
            Place(
                "찰라테낭고", "Chalatenango",
                subs = listOf(
                    Place("라 팔마", "La Palma"),
                    Place("산 이그나시오", "San Ignacio", subs = listOf(Place("라스 필라스", "Las Pilas"))),
                    Place("시탈라", "Citalá", listOf("Citala")),
                    Place("둘세 놈브레 데 마리아", "Dulce Nombre de María", listOf("Dulce Nombre de Maria")),
                ),
                lat = 14.17, lng = -89.08,
            ),
            Place(
                "알로테펙-메타판", "Alotepec-Metapán", listOf("Alotepeque-Metapán", "Metapán", "Metapan", "메타판"),
                lat = 14.33, lng = -89.44,
            ),
            Place(
                "엘 발사모-케살테펙", "El Bálsamo-Quezaltepec", listOf("El Bálsamo-Quezaltepeque", "El Balsamo", "엘 발사모"),
                listOf(
                    Place("산타 테클라", "Santa Tecla"),
                    Place("코마사과", "Comasagua"),
                    Place("하야케", "Jayaque"),
                    Place("테페코요", "Tepecoyo"),
                    Place("탈니케", "Talnique"),
                    Place("케살테페케", "Quezaltepeque"),
                ),
                lat = 13.73, lng = -89.29,
            ),
            Place(
                "테카파-치나메카", "Tecapa-Chinameca", listOf("Tecapa Chinameca"),
                listOf(
                    Place("베를린", "Berlín", listOf("Berlin")),
                    Place("알레그리아", "Alegría", listOf("Alegria")),
                    Place("산티아고 데 마리아", "Santiago de María", listOf("Santiago de Maria")),
                    Place("치나메카", "Chinameca"),
                    Place("후쿠아파", "Jucuapa"),
                    Place("메르세데스 우마냐", "Mercedes Umaña", listOf("Mercedes Umana")),
                ),
                lat = 13.5, lng = -88.53,
            ),
            Place(
                "치촌테펙", "Chichontepec", listOf("Chinchontepec"),
                listOf(
                    Place("산 비센테", "San Vicente"),
                    Place("과달루페", "Guadalupe"),
                    Place("베라파스", "Verapaz"),
                    Place("테페티탄", "Tepetitán", listOf("Tepetitan")),
                ),
                lat = 13.6, lng = -88.84,
            ),
            Place(
                "카카우아티케", "Cacahuatique",
                subs = listOf(
                    Place("시우다드 바리오스", "Ciudad Barrios"),
                    Place("차펠티케", "Chapeltique"),
                    Place("카카오페라", "Cacaopera"),
                ),
                lat = 13.77, lng = -88.21,
            ),
        ),
    )

    // source: Sweet Maria's "Nicaragua Coffee Regions, Matagalpa, Jinotega and …"; Wikipedia "Jinotega", "Matagalpa",
    // "Nueva Segovia Department" (Ciudad Antigua, Dipilto, Jalapa, Mozonte …) and "Madriz Department"; Ally Coffee
    // "Cafetos Segovia"; Covoya and Royal Coffee Madriz offers (UCA San Juan del Río Coco); the other municipalities as
    // importers' offer sheets and the Alliance for Coffee Excellence farm directory (Nicaragua Cup of Excellence winners)
    // name them. Korean: 외래어 표기법.
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
                    Place("산타 마리아 데 판타스마", "Santa María de Pantasma", listOf("Pantasma")),
                    Place("위윌리", "Wiwilí", listOf("Wiwili", "Wiwilí de Jinotega")),
                    Place("산 호세 데 보카이", "San José de Bocay", listOf("San Jose de Bocay")),
                ),
                lat = 13.08, lng = -86.0,
            ),
            Place(
                "마타갈파", "Matagalpa",
                subs = listOf(
                    Place("산 라몬", "San Ramón", listOf("San Ramon")),
                    Place("라 달리아", "La Dalia", listOf("El Tuma-La Dalia")),
                    Place("란초 그란데", "Rancho Grande"),
                    Place("리오 블랑코", "Río Blanco", listOf("Rio Blanco")),
                    Place("마티과스", "Matiguás", listOf("Matiguas")),
                    Place("무이 무이", "Muy Muy"),
                    Place("산 디오니시오", "San Dionisio"),
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
                    Place("오코탈", "Ocotal"),
                    Place("엘 히카로", "El Jícaro", listOf("El Jicaro")),
                    Place("무라", "Murra"),
                    Place("킬랄리", "Quilalí", listOf("Quilali")),
                    Place("시우다드 안티구아", "Ciudad Antigua"),
                ),
                lat = 13.76, lng = -86.19,
            ),
            Place(
                "마드리스", "Madriz", listOf("마드리즈"),
                listOf(
                    Place("산 후안 델 리오 코코", "San Juan del Río Coco", listOf("San Juan del Rio Coco")),
                    Place("라스 사바나스", "Las Sabanas"),
                    Place("소모토", "Somoto"),
                    Place("산 호세 데 쿠스마파", "San José de Cusmapa", listOf("San Jose de Cusmapa")),
                    Place("텔파네카", "Telpaneca"),
                    Place("팔라카귀나", "Palacagüina", listOf("Palacaguina")),
                    Place("산 루카스", "San Lucas"),
                ),
                lat = 13.48, lng = -86.58,
            ),
            Place(
                "에스텔리", "Estelí", listOf("Esteli"),
                listOf(
                    Place("미라플로르", "Miraflor"),
                    Place("콘데가", "Condega"),
                    Place("푸에블로 누에보", "Pueblo Nuevo"),
                ),
                lat = 13.08, lng = -86.35,
            ),
            Place("보아코", "Boaco", lat = 12.47, lng = -85.67),
        ),
    )

    // source: ICAFE's eight regions (Tarrazú, Valle Occidental, Valle Central, Tres Ríos, Orosi, Turrialba, Brunca,
    // Guanacaste) via Sucafina "Costa Rica" and Wikipedia "Coffee production in Costa Rica"; Exclusive Coffees micro-mill
    // pages (Tarrazú: Copey and Santa María de Dota, San Pablo, Llano Bonito and Santa Cruz of León Cortés, San Lorenzo,
    // El Rodeo and Canet of San Marcos, Frailes; West Valley: Naranjo with Lourdes and Llano Bonito, Grecia, Atenas,
    // Zarcero, San Ramón with Piedades); Cafe Imports "Costa Rica" (Sabanilla de Alajuela); the other cantons and
    // districts as importers' offer sheets and the Alliance for Coffee Excellence farm directory (Costa Rica Cup of
    // Excellence winners) name them. Korean: 프래그런스커피 / 워너빈 (따라주), 커피 리브레 (엘사르 데 사르세로).
    // coords: enwiki — Tarrazú (canton), Naranjo (canton) for West Valley, Heredia (Costa Rica) for Central Valley, La
    // Unión (canton) for Tres Ríos, Orosi (Cartago), Turrialba (canton), Pérez Zeledón (canton) for Brunca; Wikidata
    // Q690026 Guanacaste.
    private fun costaRica() = Origin(
        "Costa Rica",
        listOf(
            costaRicaTarrazu(),
            costaRicaWestValley(),
            costaRicaCentralValley(),
            Place("트레스 리오스", "Tres Ríos", listOf("Tres Rios", "트레스리오스"), lat = 9.91, lng = -83.99),
            costaRicaOrosi(),
            costaRicaTurrialba(),
            costaRicaBrunca(),
            costaRicaGuanacaste(),
        ),
    )

    private fun costaRicaTarrazu() = Place(
        "타라주", "Tarrazú", listOf("따라주", "타라수", "Tarrazu", "Los Santos", "로스 산토스"),
        listOf(
            Place(
                "산 마르코스", "San Marcos", listOf("San Marcos de Tarrazú"),
                listOf(
                    Place("엘 로데오", "El Rodeo"),
                    Place("카넷", "Canet"),
                ),
            ),
            Place("산 로렌소", "San Lorenzo"),
            Place("산 카를로스", "San Carlos"),
            Place(
                "도타", "Dota", listOf("Santa María de Dota", "산타 마리아 데 도타"),
                listOf(
                    Place("코페이", "Copey"),
                    Place("하르딘", "Jardín", listOf("Jardin")),
                    Place("프로비덴시아", "Providencia"),
                ),
            ),
            Place(
                "레온 코르테스", "León Cortés", listOf("Leon Cortes", "San Pablo de León Cortés"),
                listOf(
                    Place("야노 보니토", "Llano Bonito"),
                    Place("산 안드레스", "San Andrés", listOf("San Andres")),
                    Place("산타 크루스", "Santa Cruz"),
                    Place("산 이시드로", "San Isidro"),
                ),
            ),
            Place("프라일레스", "Frailes"),
        ),
        lat = 9.58, lng = -84.06,
    )

    private fun costaRicaWestValley() = Place(
        "웨스트 밸리", "West Valley", listOf("웨스트밸리", "Valle Occidental", "바예 옥시덴탈"),
        listOf(
            Place("나랑호", "Naranjo", subs = listOf(Place("루르데스", "Lourdes"), Place("야노 보니토", "Llano Bonito"))),
            Place("그레시아", "Grecia"),
            Place("사르치", "Sarchí", listOf("Sarchi", "Valverde Vega")),
            Place("팔마레스", "Palmares"),
            Place("산 라몬", "San Ramón", listOf("San Ramon"), listOf(Place("피에다데스", "Piedades"))),
            Place("아테나스", "Atenas"),
            Place("사르세로", "Zarcero", listOf("Alfaro Ruiz")),
        ),
        lat = 10.11, lng = -84.39,
    )

    private fun costaRicaCentralValley() = Place(
        "센트럴 밸리", "Central Valley", listOf("센트럴밸리", "Valle Central", "바예 센트랄"),
        listOf(
            Place("에레디아", "Heredia"),
            Place("바르바", "Barva"),
            Place("포아스", "Poás", listOf("Poas")),
            Place("알라후엘라", "Alajuela", subs = listOf(Place("사바니야", "Sabanilla"), Place("카리살", "Carrizal"))),
            Place("산타 바르바라", "Santa Bárbara", listOf("Santa Barbara")),
            Place("산 라파엘", "San Rafael"),
            Place("산토 도밍고", "Santo Domingo"),
            Place("산 이시드로", "San Isidro"),
        ),
        lat = 10.0, lng = -84.12,
    )

    private fun costaRicaOrosi() = Place(
        "오로시", "Orosi",
        subs = listOf(
            Place("카치", "Cachí", listOf("Cachi")),
            Place("파라이소", "Paraíso", listOf("Paraiso")),
        ),
        lat = 9.69, lng = -83.76,
    )

    private fun costaRicaTurrialba() = Place(
        "투리알바", "Turrialba",
        subs = listOf(
            Place("후안 비냐스", "Juan Viñas", listOf("Juan Vinas")),
            Place("산타 크루스", "Santa Cruz"),
            Place("페히바예", "Pejibaye"),
        ),
        lat = 9.78, lng = -83.57,
    )

    private fun costaRicaBrunca() = Place(
        "브룬카", "Brunca",
        subs = listOf(
            Place(
                "페레스 셀레돈", "Pérez Zeledón", listOf("Perez Zeledon"),
                listOf(
                    Place("리바스", "Rivas"),
                    Place("플라타나레스", "Platanares"),
                    Place("리오 누에보", "Río Nuevo", listOf("Rio Nuevo")),
                    Place("파라모", "Páramo", listOf("Paramo")),
                    Place("카혼", "Cajón", listOf("Cajon")),
                ),
            ),
            Place(
                "코토 브루스", "Coto Brus",
                subs = listOf(
                    Place("산 비토", "San Vito"),
                    Place("사발리토", "Sabalito"),
                    Place("아구아 부에나", "Agua Buena"),
                    Place("피티에르", "Pittier"),
                ),
            ),
        ),
        lat = 9.34, lng = -83.72,
    )

    private fun costaRicaGuanacaste() = Place(
        "과나카스테", "Guanacaste",
        subs = listOf(
            Place("오한차", "Hojancha"),
            Place("니코야", "Nicoya"),
            Place("난다유레", "Nandayure"),
            Place("틸라란", "Tilarán", listOf("Tilaran")),
        ),
        lat = 10.43, lng = -85.4,
    )

    // source: Wikipedia "Boquete District", "Renacimiento District", "Tierras Altas District" (corregimientos); Boquete
    // Coffee Traders "Panama Coffee Regions: Boquete, Volcán & Renacimiento" (Alto Quiel, Volcancito, Palmira, Horqueta,
    // Jaramillo); Best of Panama results and importers' offer sheets for the other areas. Korean: 카페알트로 (피에드라 칸델라), 커피벨트 /
    // 투커피 (보케테).
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
                    Place("오르케타", "Horqueta"),
                    Place("볼칸시토", "Volcancito"),
                    Place("알토 리노", "Alto Lino"),
                ),
                lat = 8.78, lng = -82.44,
            ),
            Place(
                "볼칸", "Volcán", listOf("Volcan", "Volcán Barú", "볼칸 바루", "Tierras Altas", "티에라스 알타스"),
                listOf(
                    Place("세로 푼타", "Cerro Punta"),
                    Place("파소 안초", "Paso Ancho"),
                    Place("쿠에스타 데 피에드라", "Cuesta de Piedra"),
                    Place("누에바 칼리포르니아", "Nueva California"),
                ),
                lat = 8.78, lng = -82.64,
            ),
            Place(
                "레나시미엔토", "Renacimiento",
                subs = listOf(
                    Place("산타 클라라", "Santa Clara"),
                    Place("피에드라 칸델라", "Piedra Candela", listOf("Piedra de Candela")),
                    Place("리오 세레노", "Río Sereno", listOf("Rio Sereno")),
                    Place("카냐스 고르다스", "Cañas Gordas", listOf("Canas Gordas")),
                    Place("몬테 리리오", "Monte Lirio"),
                    Place("플라사 데 카이산", "Plaza de Caisán", listOf("Plaza de Caisan")),
                ),
                lat = 8.82, lng = -82.86,
            ),
            Place("치리키", "Chiriquí", listOf("Chiriqui"), lat = 8.43, lng = -82.43),
        ),
    )

    // source: Wikipedia "Jamaican Blue Mountain Coffee" (JACRA: St. Andrew, St. Thomas, Portland, St. Mary; Mavis Bank
    // factory). High Mountain: Jamaica Gleaner "JACRA moves to revive High Mountain coffee industry" (Manchester, St
    // Elizabeth).
    // coords: enwiki — Blue Mountains (Jamaica); Manchester Parish for High Mountain.
    private fun jamaica() = Origin(
        "Jamaica",
        listOf(
            Place(
                "블루마운틴", "Blue Mountains", listOf("블루 마운틴", "Blue Mountain", "블루마운틴스"),
                listOf(
                    Place("세인트 앤드루", "St. Andrew", listOf("Saint Andrew"), listOf(Place("메이비스 뱅크", "Mavis Bank"))),
                    Place("세인트 토머스", "St. Thomas", listOf("Saint Thomas")),
                    Place("포틀랜드", "Portland"),
                    Place("세인트 메리", "St. Mary", listOf("Saint Mary")),
                ),
                lat = 18.1, lng = -76.67,
            ),
            Place(
                "하이 마운틴", "High Mountain", listOf("Jamaica High Mountain"),
                listOf(
                    Place("맨체스터", "Manchester"),
                    Place("세인트 엘리자베스", "St. Elizabeth", listOf("Saint Elizabeth")),
                ),
                lat = 18.05, lng = -77.53,
            ),
        ),
    )

    // source: Sweet Maria's "Dominican Republic Coffee Overview" (Barahona, Juncalito, Ocoa, Cibao, Neyba); Homegrounds DR
    // coffee guide (Barahona, Jarabacoa, San José de Ocoa, Cibao Altura); Cocotu "Dominican Republic Specialty Coffee
    // Regions" (Constanza, Neyba, Juncalito); Wikipedia "Polo, Dominican Republic" and "Rancho Arriba" (their coffee).
    // Korean: 외래어 표기법.
    // coords: enwiki — Jarabacoa, Constanza (Dominican Republic), Juncalito, Barahona Province, San José de Ocoa; Wikidata
    // Neiba (Q2107258).
    private fun dominicanRepublic() = Origin(
        "Dominican Rep.",
        listOf(
            Place("하라바코아", "Jarabacoa", lat = 19.12, lng = -70.63),
            Place("콘스탄사", "Constanza", lat = 18.91, lng = -70.75),
            Place("훈칼리토", "Juncalito", lat = 19.22, lng = -70.82),
            Place("바라오나", "Barahona", subs = listOf(Place("폴로", "Polo")), lat = 18.2, lng = -71.1),
            Place(
                "산 호세 데 오코아", "San José de Ocoa", listOf("San Jose de Ocoa", "Ocoa", "오코아"),
                listOf(
                    Place("란초 아리바", "Rancho Arriba"),
                ),
                lat = 18.55, lng = -70.5,
            ),
            Place("네이바", "Neyba", listOf("Neiba", "Sierra de Neiba", "Sierra de Neyba"), lat = 18.49, lng = -71.42),
        ),
    )

    // source: Wikipedia "Coffee production in Cuba" (Sierra Maestra, Escambray; Topes de Collantes).
    // coords: enwiki — Sierra Maestra, Escambray Mountains.
    private fun cuba() = Origin(
        "Cuba",
        listOf(
            Place("시에라 마에스트라", "Sierra Maestra", lat = 20.0, lng = -76.75),
            Place(
                "에스캄브라이", "Escambray",
                subs = listOf(
                    Place("토페스 데 콜란테스", "Topes de Collantes"),
                ),
                lat = 21.98, lng = -80.13,
            ),
        ),
    )

    // source: Perfect Daily Grind "A guide to coffee production in Haiti"; Burman Coffee APCAB offer (Belle-Anse/Thiotte);
    // RECOCARNO (Réseau des coopératives caféières de la région Nord: Dondon, Saint-Raphaël). Korean: 외래어 표기법.
    // coords: enwiki — Chaîne de la Selle, Massif du Nord.
    private fun haiti() = Origin(
        "Haiti",
        listOf(
            Place(
                "마시프 드 라 셀", "Massif de la Selle", listOf("La Selle", "라 셀", "Chaîne de la Selle"),
                listOf(
                    Place("티오트", "Thiotte"),
                    Place("벨앙스", "Belle-Anse", listOf("Belle Anse")),
                ),
                lat = 18.37, lng = -71.98,
            ),
            Place(
                "마시프 뒤 노르", "Massif du Nord",
                subs = listOf(
                    Place("동동", "Dondon"),
                    Place("생라파엘", "Saint-Raphaël", listOf("Saint-Raphael", "Saint Raphaël")),
                ),
                lat = 19.58, lng = -72.42,
            ),
        ),
    )

    // source: Kaffelito "Coffee-growing Regions of Venezuela: Táchira, Mérida, and Trujillo"; Cafe Imports "Venezuela"
    // (Lara, Portuguesa, Trujillo, Táchira, Mérida …); Diario de Los Andes "Los pueblos de café" (Sanare, Biscucuy,
    // Chabasquén, Boconó, Santa Cruz de Mora, Rubio). Korean: 외래어 표기법.
    // coords: enwiki state articles.
    private fun venezuela() = Origin(
        "Venezuela",
        listOf(
            Place("타치라", "Táchira", listOf("Tachira"), listOf(Place("루비오", "Rubio")), lat = 7.94, lng = -72.12),
            Place(
                "메리다", "Mérida", listOf("Merida"),
                listOf(
                    Place("산타 크루스 데 모라", "Santa Cruz de Mora"),
                ),
                lat = 8.48, lng = -71.19,
            ),
            Place("트루히요", "Trujillo", subs = listOf(Place("보코노", "Boconó", listOf("Bocono"))), lat = 9.42, lng = -70.5),
            Place("라라", "Lara", subs = listOf(Place("사나레", "Sanare")), lat = 10.07, lng = -69.86),
            Place(
                "포르투게사", "Portuguesa",
                subs = listOf(
                    Place("비스쿠쿠이", "Biscucuy"),
                    Place("차바스켄", "Chabasquén", listOf("Chabasquen")),
                ),
                lat = 9.11, lng = -69.28,
            ),
        ),
    )

    // source: Wikipedia "Coffee production in Puerto Rico" (Yauco, Adjuntas, San Sebastián, Lares, Las Marías); NCBA CLUSA
    // (Adjuntas, Maricao, Jayuya, Yauco).
    // coords: enwiki municipality articles ("Yauco, Puerto Rico" …).
    private fun puertoRico() = Origin(
        "Puerto Rico",
        listOf(
            Place("야우코", "Yauco", lat = 18.04, lng = -66.85),
            Place("아훈타스", "Adjuntas", lat = 18.16, lng = -66.72),
            Place("하유야", "Jayuya", lat = 18.22, lng = -66.59),
            Place("마리카오", "Maricao", lat = 18.18, lng = -66.98),
            Place("라레스", "Lares", lat = 18.3, lng = -66.88),
            Place("산 세바스티안", "San Sebastián", listOf("San Sebastian"), lat = 18.34, lng = -66.99),
            Place("라스 마리아스", "Las Marías", listOf("Las Marias"), lat = 18.25, lng = -66.99),
        ),
    )

    // source: map-dot region only.
    // coords: Wikidata Rupununi (Q3102786).
    private fun guyana() = Origin(
        "Guyana",
        listOf(
            Place("루푸누니", "Rupununi", lat = 3.41, lng = -59.31),
        ),
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
