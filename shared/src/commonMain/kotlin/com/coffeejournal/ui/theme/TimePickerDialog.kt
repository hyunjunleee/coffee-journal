@file:OptIn(ExperimentalMaterial3Api::class)

package com.coffeejournal.ui.theme

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * The time picker every time input opens (24-hour clock, archive colours from the Material theme): the record form's
 * time box and the reminder time in 알림 설정. Pair it with a [PickerBox] showing "HH:MM".
 */
@Composable
fun AppTimePickerDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onPick: (hour: Int, minute: Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Ink.bg, shape = RectangleShape, border = BorderStroke(Dimens.hairline, Ink.line)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GhostButton("취소", small = true, onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton("확인", small = true, onClick = { onPick(state.hour, state.minute) })
                }
            }
        }
    }
}
