package com.coffeejournal.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.ui.theme.CoffeeJournalTheme
import com.coffeejournal.ui.theme.Ink
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * design-1: the Material date and time pickers read colour roles and shapes the app never set, so they opened in the
 * baseline lavender with purple hour boxes and rounded corners. The theme now maps those roles onto the archive palette.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class PickerThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pickers_useInkIvoryAndSquareCorners() {
        lateinit var date: DatePickerColors
        lateinit var time: TimePickerColors
        lateinit var dialogShape: Shape
        lateinit var selectorShape: Shape
        compose.setContent {
            CoffeeJournalTheme {
                date = DatePickerDefaults.colors()
                time = TimePickerDefaults.colors()
                dialogShape = DatePickerDefaults.shape
                selectorShape = MaterialTheme.shapes.small
            }
        }
        compose.waitForIdle()

        assertEquals("date dialog container is ivory, not lavender", Ink.bg, date.containerColor)
        assertEquals(Ink.accent, date.selectedDayContainerColor)
        assertEquals(Ink.bg, date.selectedDayContentColor)
        assertEquals(Ink.accent, date.todayDateBorderColor)
        assertEquals(Ink.accent, date.selectedYearContainerColor)
        assertEquals(Ink.surfaceRaised, date.dayInSelectionRangeContainerColor)
        assertEquals(Ink.line, date.dividerColor)

        assertEquals("selected hour box is ink, not purple", Ink.accent, time.timeSelectorSelectedContainerColor)
        assertEquals(Ink.bg, time.timeSelectorSelectedContentColor)
        assertEquals(Ink.surfaceRaised, time.timeSelectorUnselectedContainerColor)
        assertEquals(Ink.text, time.timeSelectorUnselectedContentColor)
        assertEquals(Ink.surfaceRaised, time.clockDialColor)
        assertEquals(Ink.accent, time.selectorColor)
        assertEquals(Ink.accent, time.periodSelectorSelectedContainerColor)

        assertEquals("design §5.3: corner radius 0", RoundedCornerShape(0.dp), dialogShape)
        assertEquals(RoundedCornerShape(0.dp), selectorShape)
    }

    /** The pickers as every screen now shows them (recorded with :androidApp:recordRoborazziDebug). */
    @Test
    fun pickers_render() {
        compose.setContent {
            CoffeeJournalTheme {
                Surface(color = Ink.bg) {
                    Column(Modifier.testTag("pickers").padding(8.dp)) {
                        DatePicker(rememberDatePickerState(initialSelectedDateMillis = 1_790_294_400_000L))
                        TimePicker(rememberTimePickerState(initialHour = 9, initialMinute = 30, is24Hour = true))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("pickers").captureRoboImage("screenshots/44-pickers.png")
    }
}
