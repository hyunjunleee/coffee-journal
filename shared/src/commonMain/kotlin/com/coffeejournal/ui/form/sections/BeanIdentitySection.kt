package com.coffeejournal.ui.form.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.rules.CountryLookup
import com.coffeejournal.domain.rules.Prices
import com.coffeejournal.ui.form.AutocompleteField
import com.coffeejournal.ui.form.BlendRowForm
import com.coffeejournal.ui.form.CompactField
import com.coffeejournal.ui.form.ErrorText
import com.coffeejournal.ui.form.FieldBlock
import com.coffeejournal.ui.form.FormField
import com.coffeejournal.ui.form.FormMapper
import com.coffeejournal.ui.form.FormState
import com.coffeejournal.ui.form.FormSuggestions
import com.coffeejournal.ui.form.FormTextField
import com.coffeejournal.ui.form.RemoveButton
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.Seg

/** 원두 구성 · 직접 블렌드 행 · 카페 이름 · 원두 이름(자동완성/자동 채움) · 가격 · 자동 채움 배너. */
@Composable
internal fun BeanIdentitySection(
    state: FormState,
    suggestions: FormSuggestions,
    nameFocus: FocusRequester,
    blendFocus: FocusRequester,
    onNameTyped: (String) -> Unit,
    onNameBlur: () -> Unit,
    update: ((FormState) -> FormState) -> Unit,
) {
    SectionLabel("원두 정보")
    if (!state.isCafe) {
        FieldBlock {
            FieldLabel("원두 구성")
            Seg(
                options = listOf(BeanMode.SINGLE, BeanMode.COMMERCIAL_BLEND, BeanMode.CUSTOM_BLEND),
                value = state.beanMode, allowClear = false,
                labels = mapOf(BeanMode.SINGLE to "단일 원두", BeanMode.COMMERCIAL_BLEND to "카페 블렌드", BeanMode.CUSTOM_BLEND to "직접 블렌드"),
                onChange = { mode ->
                    update { s ->
                        val rows = if (mode == BeanMode.CUSTOM_BLEND && s.blendRows.isEmpty()) listOf(BlendRowForm(), BlendRowForm()) else s.blendRows
                        s.copy(beanMode = mode, blendRows = rows, error = null)
                    }
                },
            )
        }
        if (state.isCustomBlend) BlendRows(state, suggestions, blendFocus, update)
    }
    if (state.isCafe) {
        FormTextField(
            value = state.cafeName, onValueChange = { v -> update { it.copy(cafeName = v) } }, label = "카페 이름",
            placeholder = "예: OO카페 (서울 성수동)", modifier = Modifier.padding(bottom = 10.dp),
        )
    }
    AutocompleteField(
        value = state.name, onValueChange = onNameTyped, options = suggestions.beanNames,
        label = if (state.isCustomBlend) "블렌드 이름 (선택)" else "원두 이름",
        placeholder = "예: 콜롬비아 라 플라타 게이샤 워시드",
        focusRequester = nameFocus,
        onFocusChanged = { focused -> if (!focused) onNameBlur() },
        error = if (state.error?.field == FormField.NAME) state.error.message else null,
        hint = if (state.isCustomBlend) "비워두면 구성 원두 이름으로 자동 생성돼요." else "마시는 중인 원두에서 선택하거나, 목록에 없는 이름을 입력해 새 원두로 등록할 수 있어요.",
        modifier = Modifier.padding(bottom = 4.dp),
    )
    FormMapper.nameParenHint(state.name)?.let { HintText(it) }
    Spacer(Modifier.height(6.dp))
    FormTextField(
        value = state.price, onValueChange = { v -> update { it.copy(price = v) } },
        label = if (state.isCafe) "한 잔 가격 (원)" else "원두 구매 가격 (원)", placeholder = "예: 18,000", keyboardType = KeyboardType.Number,
        onFocusChanged = { focused -> if (!focused) update { s -> if (s.price.isBlank()) s else s.copy(price = Prices.formatInput(s.price)) } },
        modifier = Modifier.padding(bottom = 10.dp),
    )
    if (state.autofillBanner) AutofillBanner { update { it.copy(autofillBanner = false) } }
}

@Composable
private fun BlendRows(state: FormState, suggestions: FormSuggestions, blendFocus: FocusRequester, update: ((FormState) -> FormState) -> Unit) {
    FieldBlock {
        FieldLabel("섞은 원두와 사용량")
        state.blendRows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Top) {
                AutocompleteField(
                    value = row.name, onValueChange = { v -> update { s -> s.copy(blendRows = s.blendRows.replaceAt(index, row.copy(name = v)), error = if (s.error?.field == FormField.BLEND_ROWS) null else s.error) } },
                    options = suggestions.blendBeanNames, placeholder = "원두 선택", modifier = Modifier.weight(1f),
                    focusRequester = if (index == 0) blendFocus else null,
                )
                Spacer(Modifier.width(8.dp))
                CompactField(
                    value = row.grams, onValueChange = { v -> update { s -> s.copy(blendRows = s.blendRows.replaceAt(index, row.copy(grams = v))) } },
                    placeholder = "그램(g)", keyboardType = KeyboardType.Decimal, modifier = Modifier.width(78.dp).padding(top = 10.dp),
                )
                RemoveButton(
                    onClick = { update { s -> val rest = s.blendRows.filterIndexed { i, _ -> i != index }; s.copy(blendRows = rest.ifEmpty { listOf(BlendRowForm()) }) } },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        GhostButton("+ 원두 추가", small = true, onClick = { update { it.copy(blendRows = it.blendRows + BlendRowForm()) } })
        if (state.error?.field == FormField.BLEND_ROWS) ErrorText(state.error.message)
        HintText("각 원두의 기존 기록에도 이번 추출이 함께 표시돼요.")
    }
}

@Composable
private fun AutofillBanner(onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).background(Ink.surfaceRaised).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "✓ 처음 등록한 날 입력했던 원두 정보를 자동으로 불러왔어요. 필요하면 그냥 고쳐서 입력하시면 돼요.",
            style = AppType.small.copy(color = Ink.text), modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text("닫기", style = AppType.small.copy(color = Ink.textMuted), modifier = Modifier.clickable(onClick = onClose).padding(4.dp))
    }
}

/** Web populateRegionDatalist: the country's producing regions, or all 60 when the country is unknown. */
internal fun regionOptions(country: String): List<String> {
    val c = CountryLookup.lookup(country)
    val names = if (c != null && c.regions.isNotEmpty()) c.regions.map { it.name } else CoffeeCountries.all.flatMap { it.regions }.map { it.name }
    return names.distinct().sorted()
}
