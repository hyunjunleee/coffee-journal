package com.coffeejournal.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.ui.calendar.components.DayPanelSheet
import com.coffeejournal.ui.calendar.components.MonthGridView
import com.coffeejournal.ui.calendar.components.MonthHeader
import com.coffeejournal.ui.calendar.components.RangeLegend
import com.coffeejournal.ui.calendar.components.TodayBox
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.nav.navigateTab
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.SubTabs
import com.coffeejournal.ui.theme.TopHeader
import org.koin.compose.viewmodel.koinViewModel

private val bottomSpace = 96.dp

/** 커피 달력 tab root: header, six chips, then the calendar, study or classes view. */
@Composable
fun CalendarTabScreen(nav: NavHostController) {
    val vm = koinViewModel<CalendarViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopHeader(title = "coffee_journal / 2026", tagline = "[ personal coffee archive ]", right = null)
        SubTabs(
            items = CalTabs.all,
            selected = state.controls.tab,
            onSelect = vm::setTab,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 12.dp),
            dots = mapOf(
                CalTabs.STUDY to Ink.book, CalTabs.CLASSES to Ink.classPurple, CalTabs.CUPPING to Ink.cupping,
                CalTabs.CAFE to Ink.cafe, CalTabs.BEAN to Ink.accent,
            ),
        )
        when (state.controls.tab) {
            CalTabs.STUDY -> StudyView(nav)
            CalTabs.CLASSES -> ClassesView(nav)
            else -> CalendarView(state, vm, nav)
        }
    }
}

/** Month grid plus the filter-dependent section underneath (web #calendar-view). */
@Composable
private fun CalendarView(state: CalendarUiState, vm: CalendarViewModel, nav: NavHostController) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") {
            MonthHeader(
                title = state.yearMonth.label, onToday = vm::goToday, onPrev = vm::prevMonth, onNext = vm::nextMonth,
                modifier = Modifier.padding(horizontal = Dimens.gutter).padding(bottom = 12.dp),
            )
        }
        item(key = "grid") {
            MonthGridView(
                grid = state.grid,
                modifier = Modifier.padding(horizontal = Dimens.gutter),
                onDayClick = { cell ->
                    if (cell.opensDirectly) nav.navigate(Route.EntryDetail(cell.entries.first().id)) else vm.selectDate(cell.date)
                },
            )
        }
        item(key = "today") {
            Column(Modifier.padding(horizontal = Dimens.gutter)) {
                state.todayBean?.let { name ->
                    Spacer(Modifier.height(16.dp))
                    TodayBox(label = "Today", text = name)
                }
                RangeLegend(state.grid.visibleRanges, Modifier.padding(top = 14.dp))
            }
        }
        item(key = "section-${state.filter}") {
            Column(Modifier.padding(horizontal = Dimens.gutter)) {
                when (state.filter) {
                    CalFilter.CAFE, CalFilter.CUPPING -> CafeCuppingSection(state, vm, nav)
                    CalFilter.BEAN -> BrewSection(state, nav)
                    else -> StudySection(state, vm, nav)
                }
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(bottomSpace)) }
    }
    state.selectedCell?.let { cell ->
        DayPanelSheet(
            cell = cell,
            onDismiss = { vm.selectDate(null) },
            onEntry = { en -> vm.selectDate(null); nav.navigate(Route.EntryDetail(en.id)) },
            // web bug: blends jumped to a non-existent tab; the bean tab owns its blend view, so only switch tabs
            onBlend = { vm.selectDate(null); nav.navigateTab(Route.Bean) },
        )
    }
}
