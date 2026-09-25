@file:OptIn(ExperimentalMaterial3Api::class)

package com.coffeejournal.ui.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.TimePicker
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
import com.coffeejournal.ui.theme.DateField
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PickerBox
import com.coffeejournal.ui.theme.PrimaryButton

/** "기록 날짜/시간": the app's shared date field (no clearing: a record always has a date) and a matching time box. */
@Composable
internal fun DateTimeField(label: String, epochMillis: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    val dt = Dates.toLocalDateTime(epochMillis)
    var showTime by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        FieldLabel(label)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField(
                label = null,
                value = Dates.isoDate(dt.date),
                onChange = { iso -> Dates.parseIsoDate(iso)?.let { date -> onChange(Dates.toMillis(date, dt.hour, dt.minute)) } },
                clearable = false,
                modifier = Modifier.weight(1f),
            )
            PickerBox("${Dates.pad2(dt.hour)}:${Dates.pad2(dt.minute)}", onClick = { showTime = true }, modifier = Modifier.weight(1f))
        }
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
