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
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.BookStatus
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.Seg
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Web #book-form-panel as a full screen: title, author, status, reading dates, rating, memo. */
@Composable
fun BookFormScreen(nav: NavHostController, bookId: String?) {
    val vm = koinViewModel<BookFormViewModel>(key = "book-form-$bookId") { parametersOf(bookId) }
    val s by vm.state.collectAsStateWithLifecycle()
    FormScaffold(title = if (s.isEdit) "책 수정" else "책 추가", onBack = { nav.popBackStack() }, onSave = { vm.save { nav.popBackStack() } }) {
        AppTextField(label = "책 제목", value = s.title, onValueChange = { v -> vm.update { copy(title = v, titleError = false) } }, placeholder = "예: 커핑 바이블")
        RequiredHint(s.titleError, "책 제목을 입력해주세요")
        Spacer(Modifier.height(12.dp))
        AppTextField(label = "저자", value = s.author, onValueChange = { v -> vm.update { copy(author = v) } })
        Spacer(Modifier.height(12.dp))
        FieldLabel("상태")
        Seg(BookStatus.all, value = s.status, onChange = { v -> vm.update { copy(status = v) } }, allowClear = false)
        if (s.showDates) {
            Spacer(Modifier.height(12.dp))
            FieldLabel("읽은 기간")
            Row(Modifier.fillMaxWidth()) {
                DateField("읽기 시작한 날", s.startDate, onChange = { v -> vm.update { copy(startDate = v) } }, modifier = Modifier.weight(1f))
                if (s.showEndDate) {
                    Spacer(Modifier.width(8.dp))
                    DateField("읽은 날짜", s.endDate, onChange = { v -> vm.update { copy(endDate = v) } }, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FieldLabel("평점")
        StarRating(s.rating, onChange = { v -> vm.update { copy(rating = v) } })
        Spacer(Modifier.height(12.dp))
        AppTextField(
            label = "메모", value = s.notes, onValueChange = { v -> vm.update { copy(notes = v) } },
            placeholder = "인상 깊은 구절, 배운 점 등", singleLine = false, minLines = 4,
        )
    }
}
