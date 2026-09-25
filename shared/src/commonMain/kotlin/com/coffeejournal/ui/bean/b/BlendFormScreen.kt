package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Route.BlendForm: name, date, bean rows with grams and a memo (web blend-form-panel). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlendFormScreen(nav: NavHostController, blendId: String?) {
    val vm = koinViewModel<BlendFormViewModel> { parametersOf(blendId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var focusedRow by remember { mutableIntStateOf(-1) }
    val canSave = s.loaded && BlendSources.validRows(s.rows).isNotEmpty()
    Column(Modifier.fillMaxSize().background(Ink.bg)) {
        ScreenTitleBar(if (blendId == null) "블렌드 기록 추가" else "블렌드 기록 수정", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
            Spacer(Modifier.height(16.dp))
            AppTextField(value = s.name, onValueChange = vm::setName, label = "블렌드 이름 (선택)", placeholder = "예: 에티오피아+콜롬비아 디카페인", enabled = s.loaded)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                AppTextField(value = s.date, onValueChange = vm::setDate, label = "날짜", placeholder = "YYYY-MM-DD", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                GhostButton("달력", onClick = { showPicker = true })
            }
            Spacer(Modifier.height(14.dp))
            FieldLabel("섞은 원두")
            s.rows.forEachIndexed { i, row ->
                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        AppTextField(
                            value = row.name, onValueChange = { vm.updateRow(i, name = it) }, placeholder = "원두 이름 (최근 마신 것부터 추천)",
                            modifier = Modifier.onFocusChanged { if (it.isFocused) focusedRow = i else if (focusedRow == i) focusedRow = -1 },
                        )
                        if (focusedRow == i) {
                            val q = row.name.trim().lowercase()
                            val matches = suggestions.filter { it != row.name && (q.isEmpty() || it.lowercase().contains(q)) }.take(6)
                            if (matches.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                matches.forEach { name -> Chip(name, onClick = { vm.updateRow(i, name = name) }) }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    AppTextField(value = row.grams, onValueChange = { vm.updateRow(i, grams = it) }, placeholder = "그램(g)", keyboardType = KeyboardType.Number, modifier = Modifier.width(92.dp))
                    Box(Modifier.size(Dimens.touch).clickable { vm.removeRow(i) }, contentAlignment = Alignment.Center) { Text("✕", style = AppType.body.copy(color = Ink.textFaint)) }
                }
            }
            GhostButton("+ 원두 추가", onClick = { vm.addRow() }, small = true)
            Spacer(Modifier.height(14.dp))
            AppTextField(value = s.notes, onValueChange = vm::setNotes, label = "메모", placeholder = "맛이 어땠는지, 비율을 어떻게 바꿔볼지 등", singleLine = false, minLines = 3)
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(if (blendId == null) "저장" else "수정 저장", enabled = canSave, onClick = { vm.save { nav.popBackStack() } })
                GhostButton("취소", onClick = { nav.popBackStack() })
            }
            Spacer(Modifier.height(96.dp))
        }
    }
    if (showPicker) {
        val initial = (Dates.parseIsoDate(s.date) ?: Dates.today()).toEpochDays().toLong() * Dates.DAY_MS
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            shape = RectangleShape,
            confirmButton = {
                GhostButton("확인", small = true, onClick = {
                    pickerState.selectedDateMillis?.let { vm.setDate(Dates.isoDate(LocalDate.fromEpochDays((it / Dates.DAY_MS).toInt()))) }
                    showPicker = false
                })
            },
            dismissButton = { GhostButton("취소", small = true, onClick = { showPicker = false }) },
        ) { DatePicker(state = pickerState) }
    }
}
