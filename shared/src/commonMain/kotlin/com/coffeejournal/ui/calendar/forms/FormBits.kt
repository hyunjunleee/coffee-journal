package com.coffeejournal.ui.calendar.forms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import kotlinx.datetime.LocalDate

/** Title bar, scrolling fields, then [저장][취소] (web .form-actions). */
@Composable
internal fun FormScaffold(title: String, onBack: () -> Unit, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitleBar(title = title, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter).padding(top = 12.dp)) {
            content()
            Spacer(Modifier.height(24.dp))
            Row {
                PrimaryButton("저장", onClick = onSave, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                GhostButton("취소", onClick = onBack, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
internal fun RequiredHint(show: Boolean, text: String) {
    if (show) Text(text, style = AppType.faint.copy(color = Ink.bad), modifier = Modifier.padding(top = 4.dp))
}

/** ISO date field backed by the Material 3 date picker dialog; × clears. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        FieldLabel(label)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).background(Ink.surface)
                .border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value.ifBlank { "날짜 선택" }, style = AppType.body.copy(color = if (value.isBlank()) Ink.textFaint else Ink.text), modifier = Modifier.weight(1f))
            if (value.isNotBlank()) {
                Text("×", style = AppType.body.copy(color = Ink.textFaint), modifier = Modifier.clickable { onChange("") }.padding(horizontal = 4.dp))
            }
        }
    }
    if (open) {
        val initial = Dates.parseIsoDate(value)?.let { it.toEpochDays().toLong() * Dates.DAY_MS }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            shape = RectangleShape,
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms -> onChange(Dates.isoDate(LocalDate.fromEpochDays((ms / Dates.DAY_MS).toInt()))) }
                    open = false
                }) { Text("확인", style = AppType.body) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("취소", style = AppType.body) } },
        ) { DatePicker(state = pickerState) }
    }
}

/** Five tappable stars, whole numbers only; tapping the current value again clears it (web #bk-rating-row). */
@Composable
internal fun StarRating(rating: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier) {
        (1..5).forEach { v ->
            Text(
                "★",
                style = AppType.body.copy(fontSize = 26.sp, color = if (v <= rating) Ink.accent else Ink.line),
                modifier = Modifier.clickable { onChange(if (v == rating) 0 else v) }.padding(end = 6.dp),
            )
        }
    }
    HintText("별을 다시 누르면 해제돼요")
}
