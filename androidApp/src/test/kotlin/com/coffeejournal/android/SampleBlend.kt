package com.coffeejournal.android

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.Dates
import kotlinx.datetime.LocalDate

/** A café blend of two beans with their shares; bean 2 takes bean 1's roastery and roast (screenshots, flows). */
object SampleBlend {
    val house = Entry(
        id = "blend-house", createdAt = Dates.toMillis(LocalDate(2026, 9, 20), 9, 30), name = "하우스 블렌드 (프릳츠)",
        beanMode = BeanMode.COMMERCIAL_BLEND, roastery = "프릳츠", roast = "미디엄", roastDate = "2026. 9. 12",
        country = "브라질", region = "Cerrado", farmProducer = "파젠다 산타 이네스", variety = "Yellow Bourbon", process = "내추럴",
        price = "18000", bagWeight = "200", expectedNotes = "초콜릿, 견과, 체리", dripper = "V60", dose = "15", water = "240", temp = "92",
        blendComponents = listOf(
            BlendComponent(
                percent = "60", roastery = "프릳츠", country = "브라질", region = "Cerrado", farmProducer = "파젠다 산타 이네스",
                variety = "Yellow Bourbon", process = "내추럴", roast = "미디엄", roastDate = "2026. 9. 12",
            ),
            BlendComponent(percent = "40", country = "에티오피아", region = "Yirgacheffe", washingStation = "코체레", variety = "Heirloom", process = "워시드"),
        ),
    )
}
