package com.coffeejournal.ui.form.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.ui.form.CuppingBeanForm
import com.coffeejournal.ui.form.FieldBlock
import com.coffeejournal.ui.form.FormField
import com.coffeejournal.ui.form.FormState
import com.coffeejournal.ui.form.FormSuggestions
import com.coffeejournal.ui.form.FormTextField
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Seg

/** 커핑 모드: 유형 · 장소 · 원두 카드 목록 · 전체적인 경험 (web #cupping-simple-fields). */
@Composable
internal fun CuppingSection(
    state: FormState,
    suggestions: FormSuggestions,
    firstBeanFocus: FocusRequester,
    update: ((FormState) -> FormState) -> Unit,
) {
    FieldBlock {
        FieldLabel("커핑 유형")
        Seg(
            options = CuppingType.all,
            value = state.cuppingType,
            allowClear = false,
            onChange = { v -> update { it.copy(cuppingType = v, cuppingPlace = if (v == CuppingType.HOME) "" else it.cuppingPlace) } },
        )
    }
    if (state.cuppingType != CuppingType.HOME) {
        FormTextField(
            value = state.cuppingPlace, onValueChange = { v -> update { it.copy(cuppingPlace = v) } },
            label = "장소", placeholder = "예: FELT 청계천", modifier = Modifier.padding(bottom = 10.dp),
        )
    }
    FieldLabel("원두 (각각 자세한 정보 입력 가능)")
    state.cuppingBeans.forEachIndexed { index, bean ->
        CuppingBeanCard(
            bean = bean,
            index = index,
            suggestions = suggestions,
            nameFocus = if (index == 0) firstBeanFocus else null,
            error = if (index == 0 && state.error?.field == FormField.CUPPING_BEAN_NAME) state.error.message else null,
            onChange = { change ->
                update { s ->
                    val current = s.cuppingBeans.getOrNull(index) ?: return@update s
                    val changed = change(current)
                    val clearError = s.error?.field == FormField.CUPPING_BEAN_NAME && changed.name.isNotBlank()
                    s.copy(cuppingBeans = s.cuppingBeans.replaceAt(index, changed), error = if (clearError) null else s.error)
                }
            },
            onRemove = {
                update { s ->
                    val rest = s.cuppingBeans.filterIndexed { i, _ -> i != index }
                    s.copy(cuppingBeans = rest.ifEmpty { listOf(CuppingBeanForm()) })
                }
            },
        )
        Spacer(Modifier.height(10.dp))
    }
    GhostButton("+ 원두 추가", small = true, onClick = { update { it.copy(cuppingBeans = it.cuppingBeans + CuppingBeanForm()) } })
    Spacer(Modifier.height(14.dp))
    FormTextField(
        value = state.cuppingNotes, onValueChange = { v -> update { it.copy(cuppingNotes = v) } },
        label = "전체적인 경험", placeholder = "어땠는지, 인상 깊었던 점, 배운 점 등", singleLine = false, minLines = 3,
    )
}

internal fun <T> List<T>.replaceAt(index: Int, value: T): List<T> = mapIndexed { i, old -> if (i == index) value else old }
