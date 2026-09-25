package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.ui.theme.imeOverlapPadding
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.BlockBackWhile
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.Seg
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Route.FlatItemForm: add / edit a roastery, importer, farm or process item (web *-form-panel). */
@Composable
fun FlatItemFormScreen(nav: NavHostController, type: String, itemId: String?) {
    val vm = koinViewModel<FlatItemFormViewModel> { parametersOf(type, itemId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val spec = vm.spec
    // one pop per back / 취소 / successful save, even when tapped again during the exit transition
    val leave = dropUnlessResumed { nav.popBackStack() }
    BlockBackWhile(s.saving)
    Column(Modifier.fillMaxSize().background(Ink.bg)) {
        ScreenTitleBar("${spec.label} ${if (itemId == null) "추가" else "수정"}", onBack = { if (!s.saving) leave() })
        Column(Modifier.weight(1f).imeOverlapPadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
            Spacer(Modifier.height(16.dp))
            AppTextField(value = s.name, onValueChange = vm::setName, label = "이름", placeholder = spec.namePlaceholder, enabled = s.loaded)
            if (spec.hasStatus) {
                Spacer(Modifier.height(14.dp))
                FieldLabel("상태")
                Seg(options = listOf("", MiscStatus.CURIOUS), value = s.status, onChange = vm::setStatus, allowClear = false, labels = mapOf("" to "마셔봄", MiscStatus.CURIOUS to "궁금함"))
            }
            if (spec.hasScope) {
                Spacer(Modifier.height(14.dp))
                FieldLabel("국내/해외")
                Seg(options = listOf(Scope.DOMESTIC, Scope.OVERSEAS), value = s.scope, onChange = vm::setScope, allowClear = false)
                Spacer(Modifier.height(14.dp))
                AppTextField(value = s.location, onValueChange = vm::setLocation, label = "지역", placeholder = "예: 대한민국 서울특별시")
            }
            Spacer(Modifier.height(14.dp))
            AppTextField(value = s.notes, onValueChange = vm::setNotes, label = "메모 (선택)", placeholder = spec.notesPlaceholder, singleLine = false, minLines = 3)
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(if (itemId == null) "저장" else "수정 저장", enabled = s.loaded && !s.saving && s.name.isNotBlank(), onClick = { vm.save(leave) })
                GhostButton("취소", onClick = leave, enabled = !s.saving)
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}
