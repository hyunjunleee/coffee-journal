@file:OptIn(ExperimentalMaterial3Api::class)

package com.coffeejournal.ui.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import kotlinx.datetime.LocalDate

private val epoch = LocalDate(1970, 1, 1)

/** "기록 날짜/시간": two tappable boxes opening the Material date and time pickers. */
@Composable
internal fun DateTimeField(label: String, epochMillis: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    val dt = Dates.toLocalDateTime(epochMillis)
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        FieldLabel(label)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueBox(Dates.isoDate(dt.date), Modifier.weight(1f)) { showDate = true }
            ValueBox("${Dates.pad2(dt.hour)}:${Dates.pad2(dt.minute)}", Modifier.weight(1f)) { showTime = true }
        }
    }
    if (showDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = Dates.daysBetween(epoch, dt.date).toLong() * Dates.DAY_MS)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            shape = RectangleShape,
            confirmButton = {
                PrimaryButton("확인", small = true, modifier = Modifier.padding(end = 12.dp, bottom = 8.dp), onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val date = Dates.plusDays(epoch, (ms / Dates.DAY_MS).toInt())
                        onChange(Dates.toMillis(date, dt.hour, dt.minute))
                    }
                    showDate = false
                })
            },
            dismissButton = { GhostButton("취소", small = true, onClick = { showDate = false }) },
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }
    if (showTime) {
        val timeState = rememberTimePickerState(initialHour = dt.hour, initialMinute = dt.minute, is24Hour = true)
        Dialog(onDismissRequest = { showTime = false }) {
            Surface(color = Ink.bg, shape = RectangleShape, border = BorderStroke(Dimens.hairline, Ink.line)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        GhostButton("취소", small = true, onClick = { showTime = false })
                        Spacer(Modifier.width(8.dp))
                        PrimaryButton("확인", small = true, onClick = {
                            onChange(Dates.toMillis(dt.date, timeState.hour, timeState.minute))
                            showTime = false
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueBox(text: String, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text, style = AppType.monoValue,
        modifier = modifier.background(Ink.surface).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
    )
}
