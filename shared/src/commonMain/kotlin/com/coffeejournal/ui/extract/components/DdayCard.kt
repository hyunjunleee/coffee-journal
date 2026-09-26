package com.coffeejournal.ui.extract.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.DdayRules
import com.coffeejournal.ui.theme.AppDatePickerDialog
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.DateField
import com.coffeejournal.ui.theme.Badge
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import kotlinx.datetime.LocalDate

/** Web #dday-bar: setup row until a start date exists, then the "Coffee D-n" pill (long press to change). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DdaySection(start: LocalDate?, label: String?, milestone: DdayRules.Milestone?, onSave: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    if (start == null || label == null) {
        var chosen by remember { mutableStateOf<LocalDate?>(null) }
        HairlineCard(modifier) {
            Text("커피 처음 마신 날을 기록해두면 여기 며칠째인지 보여드려요.", style = AppType.bodyMuted)
            Spacer(Modifier.height(10.dp))
            DateField(label = "커피 처음 마신 날", value = chosen?.let { Dates.isoDate(it) } ?: "", onChange = { chosen = Dates.parseIsoDate(it) })
            Spacer(Modifier.height(10.dp))
            PrimaryButton("저장", enabled = chosen != null, small = true, onClick = { chosen?.let(onSave) })
        }
        return
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier
                .background(Ink.accent)
                .combinedClickable(onClick = {}, onLongClick = { picking = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(label, style = AppType.displayNumber.copy(color = Ink.bg))
            Text("${Dates.isoDate(start).replace('-', '.')} 첫 추출", style = AppType.monoSmall.copy(color = Ink.surfaceRaised))
        }
        if (milestone != null) {
            if (milestone.big) {
                Text(milestone.text, style = AppType.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink.accent))
            } else {
                Badge(milestone.text, color = Ink.textMuted)
            }
        }
        Spacer(Modifier.width(Dimens.gutter))
    }
    if (picking) {
        AppDatePickerDialog(initial = start, onDismiss = { picking = false }, onPick = { onSave(it); picking = false })
    }
}
