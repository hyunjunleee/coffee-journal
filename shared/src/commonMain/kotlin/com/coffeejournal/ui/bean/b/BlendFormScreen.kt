package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import com.coffeejournal.ui.theme.imeOverlapPadding
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.BlockBackWhile
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.DateField
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.GlyphButton
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Route.BlendForm: name, date, bean rows with grams and a memo (web blend-form-panel). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlendFormScreen(nav: NavHostController, blendId: String?) {
    val vm = koinViewModel<BlendFormViewModel> { parametersOf(blendId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    var focusedRow by remember { mutableIntStateOf(-1) }
    val canSave = s.loaded && !s.saving && BlendSources.validRows(s.rows).isNotEmpty()
    // one pop per back / 취소 / successful save, even when tapped again during the exit transition
    val leave = dropUnlessResumed { nav.popBackStack() }
    BlockBackWhile(s.saving)
    Column(Modifier.fillMaxSize().background(Ink.bg)) {
        ScreenTitleBar(if (blendId == null) "블렌드 기록 추가" else "블렌드 기록 수정", onBack = { if (!s.saving) leave() })
        Column(Modifier.weight(1f).imeOverlapPadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
            Spacer(Modifier.height(16.dp))
            AppTextField(value = s.name, onValueChange = vm::setName, label = "블렌드 이름 (선택)", placeholder = "예: 에티오피아+콜롬비아 디카페인", enabled = s.loaded)
            Spacer(Modifier.height(14.dp))
            // web bl-date is a date input; saving without one uses today
            DateField("날짜", s.date, vm::setDate, clearable = false)
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
                                // a suggestion fills the row: a button, not an on/off choice
                                matches.forEach { name -> Chip(name, onClick = { vm.updateRow(i, name = name) }, toggle = false) }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // a number field like the web's type=number: letters and a second point are not taken
                    AppTextField(
                        value = row.grams, onValueChange = { vm.updateRow(i, grams = it) }, inputFilter = BlendSources::gramsInput,
                        placeholder = "그램(g)", keyboardType = KeyboardType.Decimal, modifier = Modifier.width(92.dp),
                    )
                    GlyphButton(
                        "✕", label = "원두 행 삭제", onClick = { vm.removeRow(i) },
                        modifier = Modifier.size(Dimens.touch).wrapContentSize(Alignment.Center), style = AppType.body.copy(color = Ink.textFaint),
                    )
                }
            }
            GhostButton("+ 원두 추가", onClick = { vm.addRow() }, small = true)
            Spacer(Modifier.height(14.dp))
            AppTextField(value = s.notes, onValueChange = vm::setNotes, label = "메모", placeholder = "맛이 어땠는지, 비율을 어떻게 바꿔볼지 등", singleLine = false, minLines = 3)
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(if (blendId == null) "저장" else "수정 저장", enabled = canSave, onClick = { vm.save(leave) })
                GhostButton("취소", onClick = leave, enabled = !s.saving)
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}
