package com.coffeejournal.ui.bean

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.SubTabs
import com.coffeejournal.ui.theme.TopHeader
import org.koin.compose.viewmodel.koinViewModel

/** 원두 tab: header → horizontally scrolling sub tabs (default 커피 지도) → the selected view. */
@Composable
fun BeanTabScreen(nav: NavHostController) {
    val vm = koinViewModel<BeanViewModel>()
    val data by vm.data.collectAsStateWithLifecycle()
    val view by vm.selectedView.collectAsStateWithLifecycle()
    val stateHolder = rememberSaveableStateHolder()

    Column(Modifier.fillMaxSize()) {
        TopHeader(title = "coffee_journal / 2026", tagline = "[ personal coffee archive ]", right = "${data.records.size} beans")
        SubTabs(
            items = BeanViews.all,
            selected = view,
            onSelect = vm::selectView,
            labels = BeanViews.labels,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 10.dp),
        )
        Box(Modifier.fillMaxSize()) {
            // Each view keeps its own rememberSaveable state (search text, toggles) while another tab is showing.
            stateHolder.SaveableStateProvider(view) {
                when (view) {
                    BeanViews.NOTES -> BeanNotesView(nav, data)
                    BeanViews.PROCESS -> BeanProcessView(nav, data)
                    BeanViews.ROAST -> BeanRoastView(nav, data)
                    BeanViews.VARIETY -> BeanVarietyView(nav, data)
                    BeanViews.BLEND -> BeanBlendView(nav, data)
                    BeanViews.SOURCE -> BeanRoasteryView(nav, data)
                    BeanViews.SELECTION -> BeanSelectionView(nav, data)
                    BeanViews.SPECIALTY -> BeanSpecialtyView(nav, data)
                    else -> BeanMapView(nav, data)
                }
            }
        }
    }
}
