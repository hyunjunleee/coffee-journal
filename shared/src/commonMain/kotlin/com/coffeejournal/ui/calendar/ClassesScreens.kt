package com.coffeejournal.ui.calendar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.ClassType
import com.coffeejournal.domain.model.CoffeeClass
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Badge
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.SubTabs
import org.koin.compose.viewmodel.koinViewModel

/** 클래스 chip: add button, "들은 클래스" with the type filter and cards (web #classes-view). */
@Composable
internal fun ClassesView(nav: NavHostController) {
    val vm = koinViewModel<ClassesViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Dimens.gutter)) {
        item(key = "add") {
            PrimaryButton("+ 클래스 추가", modifier = Modifier.fillMaxWidth(), onClick = { nav.navigate(Route.ClassForm()) })
            SectionLabel("들은 클래스")
            SubTabs(ClassLists.filters, selected = state.filter, onSelect = vm::setFilter, labels = ClassLists.filterLabels, modifier = Modifier.padding(bottom = 14.dp))
        }
        if (state.classes.isEmpty()) {
            item(key = "empty") {
                EmptyNote(
                    when {
                        state.totalCount == 0 -> "아직 기록한 클래스가 없습니다."
                        state.filter == ClassType.RECURRING -> "아직 주/월별 클래스 기록이 없습니다."
                        else -> "아직 원데이 클래스 기록이 없습니다."
                    }
                )
            }
        }
        items(state.classes, key = { it.id }) { c ->
            ClassCard(c, onEdit = { nav.navigate(Route.ClassForm(c.id)) }, onDelete = { vm.delete(c.id) }, modifier = Modifier.padding(bottom = 8.dp))
        }
        item(key = "bottom") { Spacer(Modifier.height(96.dp)) }
    }
}

/** Web classHtml: title, type badge, "YYYY년 M월 D일" or "A ~ B", memo. */
@Composable
private fun ClassCard(c: CoffeeClass, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    HairlineCard(modifier) {
        Text(c.title.ifBlank { "제목 없음" }, style = AppType.cardTitle)
        Spacer(Modifier.height(8.dp))
        Row { Badge(ClassLists.typeLabel(c)) }
        val date = ClassLists.dateLabel(c)
        if (date.isNotBlank()) Text(date, style = AppType.small, modifier = Modifier.padding(top = 6.dp))
        if (c.notes.isNotBlank()) Text(c.notes, style = AppType.bodyMuted, modifier = Modifier.padding(top = 8.dp))
        CardActions(onEdit = onEdit, onDelete = onDelete, confirmText = "\"${c.title.ifBlank { "제목 없음" }}\" 클래스 기록을 삭제할까요?")
    }
}
