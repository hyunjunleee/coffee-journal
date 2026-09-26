package com.coffeejournal.data.backup

import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.reference.CvaForm
import com.coffeejournal.domain.rules.CvaAssessment
import com.coffeejournal.domain.rules.CvaScoring
import com.coffeejournal.domain.rules.ScaScoring
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * CVA assessments live in the maps the web backup already has (`attributes` / `attributeNotes` of an entry,
 * `evaluationScores` / `evaluation` of a cupping bean) under `cva.` keys: the file format does not change, the app reads
 * them back unchanged, and the web's score (the ten SCA 2004 keys) does not see them.
 */
class CvaBackupTest {
    private val codec = BackupCodec()
    private val cva = CvaAssessment(
        intensity = mapOf("fragrance" to 8, "aroma" to 0, "flavor" to 12),
        affective = CvaForm.sectionKeys.associateWith { 7 } + ("overall" to 8),
        aromaDescriptors = listOf("floral", "berry"),
        flavorDescriptors = listOf("citrusFruit"),
        mainTastes = listOf("sour", "sweet"),
        mouthfeel = listOf("smooth"),
        notes = mapOf("aroma" to "자스민", "overall" to "깨끗하고 밝음"),
        nonUniformCups = 1, defectiveCups = 1, defects = listOf("phenolic"),
    )

    private val tasting = Entry(
        id = "e1", createdAt = 1_790_000_000_000L, category = Category.BEAN, name = "케냐 AA",
        attributes = CvaScoring.toScores(cva), attributeNotes = CvaScoring.toTexts(cva),
    )
    private val cupping = Entry(
        id = "e2", createdAt = 1_790_000_100_000L, category = Category.CUPPING, name = "커핑", cuppingPlace = "집",
        cuppingBeans = listOf(
            CuppingBean(name = "게이샤", evaluationScores = CvaScoring.toScores(cva), evaluation = CvaScoring.toTexts(cva)),
            CuppingBean(name = "브라질", evaluationScores = mapOf("aroma" to 8.0), evaluation = mapOf("aroma" to "견과")),
        ),
    )

    @Test fun appBackupRoundTripKeepsTheAssessment() {
        val back = codec.decode(codec.encode(BackupSnapshot(entries = listOf(tasting, cupping))))
        val e1 = back.entries.single { it.id == "e1" }
        assertEquals(tasting.attributes, e1.attributes)
        assertEquals(tasting.attributeNotes, e1.attributeNotes)
        assertEquals(cva, CvaScoring.fromMaps(e1.attributes, e1.attributeNotes))
        val bean = back.entries.single { it.id == "e2" }.cuppingBeans.first()
        assertEquals(cva, CvaScoring.fromMaps(bean.evaluationScores, bean.evaluation))
        assertEquals(mapOf("aroma" to 8.0), back.entries.single { it.id == "e2" }.cuppingBeans[1].evaluationScores)
        // Σ 57: 0.65625 × 57 + 52.75 − 2 − 4 = 84.156… → 84.25
        assertEquals(84.25, CvaScoring.scoreOf(e1))
    }

    @Test fun theFileUsesTheWebFieldsOnly() {
        val root = Json.parseToJsonElement(codec.encode(BackupSnapshot(entries = listOf(tasting)))).jsonObject
        val entry = root["data"]!!.jsonObject["entries"]!!.jsonArray.single().jsonObject
        val attrs = entry["attributes"]!!.jsonObject
        assertEquals("7", attrs["cva.affective.flavor"]!!.jsonPrimitive.content)
        assertEquals("phenolic", entry["attributeNotes"]!!.jsonObject["cva.defects"]!!.jsonPrimitive.content)
        assertEquals(null, entry["cva"], "no new top-level key")
    }

    /** A web file that went through the web app keeps the unknown keys (web editEntry copies en.attributes whole). */
    @Test fun webStyleFileWithCvaKeysImports_andWebMigrationsLeaveThemAlone() {
        val web = """
            {"exportedAt": "2026-09-26T00:00:00.000Z", "data": {"entries": [
              {"id": "w1", "createdAt": 1790000000000, "category": "원두", "name": "케냐",
               "attributes": {"flavor": 4, "uniformity": 10, "cva.affective.fragrance": 3, "cva.affective.aroma": 4, "cva.affective.flavor": 5,
                              "cva.affective.aftertaste": 5, "cva.affective.acidity": 5, "cva.affective.sweetness": 5, "cva.affective.mouthfeel": 5,
                              "cva.affective.overall": 5, "cva.intensity.acidity": 2},
               "attributeNotes": {"cva.cata.mainTastes": "sour"}}
            ]}}
        """.trimIndent()
        val e = codec.decode(web).entries.single()
        assertEquals(9.0, e.attributes["flavor"], "the web's own 1-5 → 6-10 migration still runs on 2004 keys")
        assertEquals(3.0, e.attributes["cva.affective.fragrance"], "…but never on CVA ratings")
        assertEquals(2.0, e.attributes["cva.intensity.acidity"])
        val a = CvaScoring.fromMaps(e.attributes, e.attributeNotes)
        assertEquals(listOf("sour"), a.mainTastes)
        assertEquals(CvaScoring.roundQuarter(37 * 0.65625 + 52.75), CvaScoring.affectiveScore(a))
        // the record has a 2004 score too (the web's form); both stay as they were
        assertEquals(19.0, ScaScoring.effectiveTotal(e.attributes.filterKeys { !CvaScoring.isCvaKey(it) }))
        assertNull(ScaScoring.effectiveTotal(CvaScoring.toScores(a)))
    }
}
