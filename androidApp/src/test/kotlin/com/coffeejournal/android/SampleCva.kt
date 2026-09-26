package com.coffeejournal.android

import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.CvaAssessment
import com.coffeejournal.domain.rules.CvaScoring
import com.coffeejournal.domain.rules.Dates
import kotlinx.datetime.LocalDate

/** A CVA tasting for the screenshots of the CVA detail and the statistics charts. */
object SampleCva {
    val assessment = CvaAssessment(
        intensity = mapOf("fragrance" to 10, "aroma" to 9, "flavor" to 11, "aftertaste" to 8, "acidity" to 12, "sweetness" to 9, "mouthfeel" to 7),
        affective = mapOf("fragrance" to 8, "aroma" to 7, "flavor" to 8, "aftertaste" to 7, "acidity" to 8, "sweetness" to 7, "mouthfeel" to 6, "overall" to 8),
        aromaDescriptors = listOf("floral", "berry", "citrusFruit"),
        flavorDescriptors = listOf("fruity", "berry", "sweet"),
        mainTastes = listOf("sour", "sweet"),
        mouthfeel = listOf("smooth"),
        notes = mapOf("aroma" to "자스민과 라즈베리", "flavor" to "블랙커런트, 긴 단맛", "overall" to "산미가 밝고 깨끗하다."),
        nonUniformCups = 1,
    )

    val tasting = Entry(
        id = "cva1", createdAt = Dates.toMillis(LocalDate(2026, 9, 24), 9, 0), category = Category.BEAN,
        name = "케냐 키리냐가 AA", country = "케냐", region = "Kirinyaga", variety = "SL28, SL34", process = "워시드", roastery = "프릳츠",
        dripper = "V60", dose = "15", water = "250", temp = "93", time = "2:40", actualNotes = "블랙커런트, 자몽",
        attributes = CvaScoring.toScores(assessment), attributeNotes = CvaScoring.toTexts(assessment),
    )
}
