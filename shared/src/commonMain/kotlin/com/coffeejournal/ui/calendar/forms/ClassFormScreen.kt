package com.coffeejournal.ui.calendar.forms

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.ClassType
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.DateField
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.Seg
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val typeOptions = listOf(ClassType.ONEDAY, ClassType.RECURRING)
private val typeLabels = mapOf(ClassType.ONEDAY to "원데이 클래스", ClassType.RECURRING to "주/월별 클래스")

/** Web #class-form-panel: name, type, date or period, memo. */
@Composable
fun ClassFormScreen(nav: NavHostController, classId: String?) {
    val vm = koinViewModel<ClassFormViewModel>(key = "class-form-$classId") { parametersOf(classId) }
    val s by vm.state.collectAsStateWithLifecycle()
    // one pop per back / 취소 / successful save, even when tapped again during the exit transition
    val leave = dropUnlessResumed { nav.popBackStack() }
    FormScaffold(title = if (s.isEdit) "클래스 수정" else "클래스 추가", onBack = leave, onSave = { vm.save(leave) }, saving = s.saving, hasChanges = vm::hasChanges) {
        AppTextField(label = "클래스명", value = s.title, onValueChange = { v -> vm.update { copy(title = v, titleError = false) } }, placeholder = "예: 홈카페 원데이 클래스")
        RequiredHint(s.titleError, "클래스명을 입력해주세요")
        Spacer(Modifier.height(12.dp))
        FieldLabel("클래스 유형")
        Seg(typeOptions, value = s.classType, onChange = { v -> vm.update { copy(classType = v) } }, allowClear = false, labels = typeLabels)
        Spacer(Modifier.height(12.dp))
        if (s.isRecurring) {
            FieldLabel("기간")
            Row(Modifier.fillMaxWidth()) {
                DateField("시작일", s.startDate, onChange = { v -> vm.update { copy(startDate = v) } }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                DateField("종료일", s.endDate, onChange = { v -> vm.update { copy(endDate = v) } }, modifier = Modifier.weight(1f))
            }
        } else {
            DateField("날짜", s.date, onChange = { v -> vm.update { copy(date = v) } })
        }
        Spacer(Modifier.height(12.dp))
        AppTextField(
            label = "배운 것 메모", value = s.notes, onValueChange = { v -> vm.update { copy(notes = v) } },
            placeholder = "이 클래스에서 배운 내용을 적어두세요", singleLine = false, minLines = 4,
        )
    }
}
