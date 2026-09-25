package com.coffeejournal.ui.extract

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.reference.RoastLevels
import com.coffeejournal.ui.theme.imeOverlapPadding
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.BlockBackWhile
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.ChipInput
import com.coffeejournal.ui.theme.DateField
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.InputFilters
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.Seg
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val PACKAGE_OPTIONS = listOf(PackageType.STANDARD, PackageType.DRIPBAG, PackageType.SAMPLE)
private val PACKAGE_LABELS = PACKAGE_OPTIONS.associateWith { PackageType.label(it) }

/** Web #bean-pantry-form as its own screen; labels and placeholders are the web originals. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantryEditorScreen(nav: NavHostController, itemId: String?) {
    val vm = koinViewModel<PantryEditorViewModel> { parametersOf(itemId ?: "") }
    val form by vm.form.collectAsStateWithLifecycle()
    LaunchedEffect(form.saved) { if (form.saved) nav.popBackStack() }
    // one pop per back / 취소 even when tapped again during the exit transition
    val leave = dropUnlessResumed { nav.popBackStack() }

    val saving = form.saving && !form.saved
    BlockBackWhile(saving)
    Column(Modifier.fillMaxSize()) {
        ScreenTitleBar(title = "원두 보관함", onBack = { if (!saving) leave() })
        if (!form.loaded) return
        Column(
            Modifier.fillMaxSize().imeOverlapPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter).padding(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(form.name, { v -> vm.update { copy(name = v) } }, label = "원두 이름", placeholder = "예: 에티오피아 벤사 내추럴")
            AppTextField(form.roastery, { v -> vm.update { copy(roastery = v) } }, label = "로스터리", placeholder = "예: 커피 리브레")
            Column {
                FieldLabel("원두 형태")
                Seg(PACKAGE_OPTIONS, form.packageType, { v -> vm.update { copy(packageType = v) } }, allowClear = false, labels = PACKAGE_LABELS)
            }
            AppTextField(form.weight, { v -> vm.update { copy(weight = v) } }, label = "봉투 용량 (g)", placeholder = "예: 200", keyboardType = KeyboardType.Number, inputFilter = InputFilters::decimal)
            AppTextField(
                form.price, vm::setPrice, label = "구매 가격 (원)", placeholder = "예: 18,000", keyboardType = KeyboardType.Number,
                modifier = Modifier.onFocusChanged { if (!it.hasFocus) vm.formatPrice() },
            )
            if (form.unitPriceText.isNotBlank()) HintText(form.unitPriceText)
            Column {
                FieldLabel("배전도")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoastLevels.all.forEach { level ->
                        val on = form.roastLevel == level
                        Chip(level, selected = on, onClick = { vm.update { copy(roastLevel = if (on) "" else level) } })
                    }
                }
                if (form.roastLevel.isBlank()) HintText("선택")
            }
            DateField("로스팅 날짜", form.roastDate, { v -> vm.update { copy(roastDate = v) } })
            DateField("구매 날짜", form.purchaseDate, { v -> vm.update { copy(purchaseDate = v) } })
            DateField("예상 피크 시작일 (선택)", form.peakStart, { v -> vm.update { copy(peakStart = v) } })
            DateField("예상 피크 종료일 (선택)", form.peakEnd, { v -> vm.update { copy(peakEnd = v) } })
            Column {
                FieldLabel("예상 노트")
                ChipInput(
                    chips = form.expectedNotes, onChipsChange = { c -> vm.update { copy(expectedNotes = c) } },
                    input = form.noteInput, onInputChange = { v -> vm.update { copy(noteInput = v) } },
                )
            }
            AppTextField(form.notes, { v -> vm.update { copy(notes = v) } }, label = "메모", placeholder = "보관 장소, 개봉 예정일 등", singleLine = false, minLines = 3)
            form.error?.let { Text(it, style = AppType.small.copy(color = Ink.bad)) }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(if (form.isEdit) "수정 저장" else "저장", onClick = vm::save, enabled = !form.saving, modifier = Modifier.weight(1f))
                GhostButton("취소", onClick = leave, enabled = !saving, modifier = Modifier.weight(1f))
            }
        }
    }
}
