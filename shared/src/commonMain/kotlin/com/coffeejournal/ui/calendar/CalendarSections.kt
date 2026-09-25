package com.coffeejournal.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.ui.calendar.components.BrewRow
import com.coffeejournal.ui.calendar.components.EntryListRow
import com.coffeejournal.ui.nav.FormMode
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.SubTabs

/** 카페 / 커핑 filter: add button, month-or-all list with the cupping type chips (web #cafe-cupping-section). */
@Composable
internal fun CafeCuppingSection(state: CalendarUiState, vm: CalendarViewModel, nav: NavHostController) {
    val filter = state.filter
    val isCupping = filter == CalFilter.CUPPING
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            "+ $filter 기록 추가",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                nav.navigate(
                    Route.RecordForm(
                        mode = if (isCupping) FormMode.CUPPING else FormMode.CAFE,
                        cuppingType = if (isCupping) state.controls.cuppingType else null,
                    )
                )
            },
        )
        val title = if (state.controls.listScope == ListScope.MONTH) "${state.yearMonth.label} $filter 기록" else "전체 $filter 기록"
        SectionLabel(title)
        if (isCupping) {
            SubTabs(CuppingType.all, selected = state.controls.cuppingType, onSelect = vm::setCuppingType, modifier = Modifier.padding(bottom = 10.dp))
        }
        SubTabs(ListScope.all, selected = state.controls.listScope, onSelect = vm::setListScope, modifier = Modifier.padding(bottom = 12.dp))
        if (state.cafeCuppingList.isEmpty()) {
            EmptyNote("아직 $filter 기록이 없습니다.")
        } else {
            state.cafeCuppingList.forEach { en ->
                EntryListRow(en, onClick = { nav.navigate(Route.EntryDetail(en.id)) }, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

/** 원두 filter: this month's brew records, newest first (web #calendar-brew-section). */
@Composable
internal fun BrewSection(state: CalendarUiState, nav: NavHostController) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel("${state.yearMonth.label} 추출 기록 · 최신순")
        if (state.brewList.isEmpty()) {
            EmptyNote("이 달의 추출 기록이 없습니다.")
        } else {
            state.brewList.forEachIndexed { i, en ->
                if (i > 0) Hairline()
                BrewRow(en, onClick = { nav.navigate(Route.EntryDetail(en.id)) })
            }
        }
    }
}

/** 전체 filter: "커피 공부" with the roadmap and the cupping review collection (web #calendar-roadmap-embed). */
@Composable
internal fun StudySection(state: CalendarUiState, vm: CalendarViewModel, nav: NavHostController) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel("커피 공부")
        SubTabs(StudyTabs.all, selected = state.controls.studyTab, onSelect = vm::setStudyTab, modifier = Modifier.padding(bottom = 12.dp))
        when (state.controls.studyTab) {
            StudyTabs.REVIEWS -> CuppingReviewsSection(state.reviews, onOpen = { nav.navigate(Route.EntryDetail(it.id)) })
            else -> RoadmapSection(
                phases = state.roadmap,
                ddayCount = state.ddayCount,
                currentPhaseId = state.currentPhaseId,
                openPhaseId = state.openPhaseId,
                onTogglePhase = vm::togglePhase,
                onToggleItem = vm::toggleItem,
                onEditItem = vm::editItem,
                onDeleteItem = vm::deleteItem,
                onAddItem = vm::addItem,
                onAddPhase = vm::addPhase,
                onDeletePhase = vm::deletePhase,
            )
        }
    }
}
