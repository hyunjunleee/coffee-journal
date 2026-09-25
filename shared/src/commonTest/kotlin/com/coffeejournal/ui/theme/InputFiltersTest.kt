package com.coffeejournal.ui.theme

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.coffeejournal.ui.form.FormMapper
import com.coffeejournal.ui.form.StepForm
import com.coffeejournal.ui.nav.FormMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Gap #3: the number filter of the web's type=number fields and the 총 추출시간 filter, applied inside the field. */
class InputFiltersTest {
    @Test fun decimal_takesNumbersBeingTyped_only() {
        assertEquals("", InputFilters.decimal(""))
        assertEquals("18", InputFilters.decimal("18"))
        assertEquals("18.", InputFilters.decimal("18."))
        assertEquals("18.5", InputFilters.decimal("18,5"))
        assertEquals(".5", InputFilters.decimal(".5"))
        assertNull(InputFilters.decimal("18g"))
        assertNull(InputFilters.decimal("NaN"))
        assertNull(InputFilters.decimal("Infinity"))
        assertNull(InputFilters.decimal("1e999"))
        assertNull(InputFilters.decimal("-3"))
        assertNull(InputFilters.decimal("1.2.3"))
    }

    @Test fun rejectedKeystrokeLeavesTheFieldAsItWas() {
        val field = ImeSafeText("18")
        assertNull(field.onEdit(TextFieldValue("18g", TextRange(3)), InputFilters::decimal))
        assertEquals(TextFieldValue("18", TextRange(2)), field.value)
        assertEquals("18.5", field.onEdit(TextFieldValue("18,5", TextRange(4)), InputFilters::decimal))
    }

    @Test fun stepsTime_decidesTheTotalTimeWhileTheStepsGiveOne() {
        val form = FormMapper.newState(FormMode.EXTRACT, null, 0L)
        assertEquals("2:10", FormMapper.stepsTime(form), "the example steps: 1:35 + 35 s")
        val field = ImeSafeText(form.time)
        val computed = FormMapper.stepsTime(form)
        assertNull(field.onEdit(TextFieldValue("2:109", TextRange(5))) { typed -> computed ?: typed }, "a keystroke the step log would undo")
        assertEquals("2:10", field.value.text)
        val free = form.copy(steps = listOf(StepForm()))
        assertNull(FormMapper.stepsTime(free), "no steps: the time is free text")
    }
}
