package com.coffeejournal.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.theme.AppTimePickerDialog
import com.coffeejournal.ui.theme.DateField
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.PickerBox

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
        AppTimePickerDialog(
            hour = dt.hour,
            minute = dt.minute,
            onDismiss = { showTime = false },
            onPick = { hour, minute ->
                onChange(Dates.toMillis(dt.date, hour, minute))
                showTime = false
            },
        )
    }
}
