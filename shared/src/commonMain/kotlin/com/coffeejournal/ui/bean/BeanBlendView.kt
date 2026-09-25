package com.coffeejournal.ui.bean

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coffeejournal.ui.bean.b.BlendItemCard
import com.coffeejournal.ui.bean.b.BlendSources
import com.coffeejournal.ui.bean.b.BlendsViewModel
import com.coffeejournal.ui.bean.b.EMPTY_BLENDS
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.SubTabs
import org.koin.compose.viewmodel.koinViewModel

private const val BLEND_DESCRIPTION = "카페 블렌드, 직접 섞어 마신 블렌드, 커핑에서 만난 블렌드를 한곳에서 봐요. 기록할 때 블렌드로 표시하면 자동으로 모여요."

/** 블렌드: hand-made blends, blend records and cupped blends in one filtered list (web #tab-practice). */
@Composable
fun BeanBlendView(nav: NavHostController, data: BeanData) {
    val vm = koinViewModel<BlendsViewModel>()
    var filter by rememberSaveable { mutableStateOf(BlendSources.ALL) }
    val items = remember(data.blends, data.entries, filter) { BlendSources.merge(data.blends, data.entries, filter) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
        Text("블렌드", style = AppType.title, modifier = Modifier.padding(top = 16.dp))
        Text(BLEND_DESCRIPTION, style = AppType.bodyMuted, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
        SubTabs(items = BlendSources.filters, selected = filter, onSelect = { filter = it }, labels = BlendSources.filterLabels)
        PrimaryButton("+ 블렌드 기록 추가", onClick = { nav.navigate(Route.BlendForm()) }, modifier = Modifier.padding(top = 18.dp))
        SectionLabel("블렌드 기록")
        if (items.isEmpty()) EmptyNote(EMPTY_BLENDS)
        items.forEach { item ->
            BlendItemCard(
                item,
                onEdit = { nav.navigate(Route.BlendForm(blendId = it.id)) },
                onDelete = { vm.delete(it.id) },
                onOpenEntry = { nav.navigate(Route.EntryDetail(it)) },
            )
        }
        Spacer(Modifier.height(96.dp))
    }
}
