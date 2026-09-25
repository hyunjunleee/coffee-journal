package com.coffeejournal.ui.form.sections

import androidx.compose.runtime.Composable
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.ui.form.DateTimeField
import com.coffeejournal.ui.form.FieldBlock
import com.coffeejournal.ui.form.FormState
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.Seg

/** 기록 종류 · 원두 형태 · 기록 날짜/시간. */
@Composable
internal fun BasicSection(state: FormState, update: ((FormState) -> FormState) -> Unit) {
    if (state.showCategorySeg) {
        SectionLabel("기록 종류")
        FieldBlock {
            Seg(
                options = Category.all,
                value = state.category,
                onChange = { cat -> update { it.copy(category = cat, error = null) } },
                allowClear = false,
                labels = mapOf(Category.BEAN to "원두 (집에서 내림)"),
            )
        }
    }
    if (state.isBrew) {
        FieldBlock {
            FieldLabel("원두 형태")
            Seg(
                options = listOf(PackageType.STANDARD, PackageType.DRIPBAG, PackageType.SAMPLE),
                value = state.packageType,
                onChange = { v -> update { it.copy(packageType = v) } },
                allowClear = false,
                labels = mapOf(PackageType.STANDARD to "일반 원두", PackageType.DRIPBAG to "드립백", PackageType.SAMPLE to "소량"),
            )
        }
    }
    DateTimeField(label = "기록 날짜/시간", epochMillis = state.createdAt, onChange = { ms -> update { it.copy(createdAt = ms) } })
}
