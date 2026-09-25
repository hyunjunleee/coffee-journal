package com.coffeejournal.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.rules.Dates
import kotlinx.datetime.LocalDate

/** Placeholder of an empty date field: the wording of the web's native `<input type="date">`. */
const val DATE_PLACEHOLDER = "연도-월-일"

/**
 * The one date input of the app (web `<input type="date">`): a boxed value that opens the date picker, "YYYY-MM-DD"
 * in and out, and a × that clears it when [clearable].
 */
@Composable
fun DateField(
    label: String?,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = DATE_PLACEHOLDER,
    clearable: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        if (label != null) FieldLabel(label)
        PickerBox(
            text = value,
            placeholder = placeholder,
            onClick = { open = true },
            onClear = if (clearable && value.isNotBlank()) ({ onChange("") }) else null,
            clearLabel = "날짜 지우기",
        )
    }
    if (open) {
        AppDatePickerDialog(
            initial = Dates.parseIsoDate(value),
            onDismiss = { open = false },
            onPick = { onChange(Dates.isoDate(it)); open = false },
        )
    }
}

/** The boxed look shared by date and time inputs: value (or a faint placeholder) and an optional × on the right. */
@Composable
fun PickerBox(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    onClear: (() -> Unit)? = null,
    clearLabel: String = "지우기",
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(Ink.surface)
            .border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.ifBlank { placeholder },
            style = AppType.body.copy(color = if (text.isBlank()) Ink.textFaint else Ink.text),
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        if (onClear != null) {
            // a full 48dp target; the × itself stays small
            Box(
                Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClear).semantics { contentDescription = clearLabel },
                contentAlignment = Alignment.Center,
            ) { Text("×", style = AppType.body.copy(color = Ink.textFaint)) }
        } else {
            Spacer(Modifier.width(14.dp))
        }
    }
}

/**
 * The date picker dialog every date input opens. Dates travel as [LocalDate]; the picker's UTC-midnight millis stay
 * inside this function. Colours come from the Material theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = (initial ?: Dates.today()).toEpochDays() * Dates.DAY_MS)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        confirmButton = {
            Row(Modifier.padding(end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("취소", small = true, onClick = onDismiss)
                PrimaryButton("확인", small = true, onClick = {
                    val millis = state.selectedDateMillis ?: return@PrimaryButton
                    onPick(LocalDate.fromEpochDays(millis.floorDiv(Dates.DAY_MS)))
                })
            }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}
