package com.coffeejournal.domain

import com.coffeejournal.domain.reference.ScaForm
import com.coffeejournal.domain.rules.NoteCanon
import com.coffeejournal.domain.rules.ScaScoring
import kotlin.test.Test
import kotlin.test.assertEquals

/** Gap #2: slider readouts, the live total and chip input against the web (script3.js 3124-3190). */
class ScaReadoutTest {
    private fun attr(key: String) = (ScaForm.attrs + ScaForm.intensities).single { it.key == key }

    @Test fun stepTwoRowsPrintTwoDecimalsLikeTheWeb() {
        // web updateAttrDots: step !== 1 → Number(val).toFixed(2)
        assertEquals("10.00", ScaScoring.readout(attr("uniformity"), 10.0))
        assertEquals("8.00", ScaScoring.readout(attr("cleancup"), 8.0))
        assertEquals("0.00", ScaScoring.readout(attr("sweetness"), 0.0))
        // `val = attrValues[key] || 0` and `min === 0` → an unset 0-10 row reads 0.00, an unset 6-10 row a dash
        assertEquals("0.00", ScaScoring.readout(attr("uniformity"), null))
        assertEquals("–", ScaScoring.readout(attr("flavor"), null))
        assertEquals("–", ScaScoring.readout(attr("flavor"), 0.0))
    }

    @Test fun qualityAndIntensityRows() {
        assertEquals("8.25", ScaScoring.readout(attr("flavor"), 8.25))
        assertEquals("6.00", ScaScoring.readout(attr("overall"), 6.0))
        assertEquals("9.50", ScaScoring.readout(attr("acidity"), 9.5))
        // intensities: toFixed(1).replace(/\.0$/, '')
        assertEquals("3", ScaScoring.readout(attr("fragranceIntensity"), 3.0))
        assertEquals("3.5", ScaScoring.readout(attr("bodyIntensity"), 3.5))
        assertEquals("–", ScaScoring.readout(attr("acidityIntensity"), null))
    }

    @Test fun totalFormatsWithTwoDecimals() {
        // web updateAttrTotal: (Math.round(total * 100) / 100).toFixed(2)
        val scored = ScaScoring.defaultAttributes() + mapOf("flavor" to 8.25, "acidity" to 7.5)
        assertEquals("45.75", ScaScoring.format2(ScaScoring.effectiveTotal(scored)!!))
        assertEquals("80.00", ScaScoring.format2(80.0))
        assertEquals("-2.50", ScaScoring.format2(-2.5))
        assertEquals("0.00", ScaScoring.format2(-0.001))
    }

    @Test fun chipInputSplitsOnCommasAndDeduplicatesLikeASet() {
        // web addExpectedNoteFromInput: split(','), trim, filter(Boolean), [...new Set([...arr, ...added])]
        assertEquals(listOf("오렌지", "자몽", "꿀"), NoteCanon.addChips(listOf("오렌지"), " 자몽 ,, 오렌지, 꿀 ,자몽"))
        assertEquals(listOf("오렌지"), NoteCanon.addChips(listOf("오렌지", "오렌지"), " , "))
    }
}
