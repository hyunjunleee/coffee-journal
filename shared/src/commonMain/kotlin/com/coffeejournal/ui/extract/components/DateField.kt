package com.coffeejournal.ui.extract.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.Ink
import kotlinx.datetime.LocalDate

/** Material date picker; dates travel as [LocalDate] and the picker's UTC-midnight millis stay inside this file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial?.let { it.toEpochDays().toLong() * Dates.DAY_MS })
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: return@TextButton
                onPick(LocalDate.fromEpochDays((millis / Dates.DAY_MS).toInt()))
            }) { Text("확인", style = AppType.body) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", style = AppType.body) } },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

/** Read-only field that opens the date picker; stores "YYYY-MM-DD" like the web's `<input type="date">`. */
@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "연도-월-일") {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        FieldLabel(label)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(Ink.surface)
                .border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value.ifBlank { placeholder },
                style = if (value.isBlank()) AppType.body.copy(color = Ink.textFaint) else AppType.body,
                modifier = Modifier.weight(1f),
            )
            if (value.isNotBlank()) {
                Text("×", style = AppType.body.copy(color = Ink.textFaint), modifier = Modifier.clickable { onChange("") }.padding(horizontal = 4.dp))
            }
        }
    }
    if (open) {
        DatePickerSheet(initial = Dates.parseIsoDate(value), onDismiss = { open = false }, onPick = { onChange(Dates.isoDate(it)); open = false })
    }
}
