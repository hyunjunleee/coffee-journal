package com.coffeejournal.ui.bean

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.ui.theme.imeOverlapPadding
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.SubTabs
import com.coffeejournal.ui.theme.TopHeader
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** 원두 tab: header → horizontally scrolling sub tabs (default 커피 지도) → the selected view once the data has loaded. */
@Composable
fun BeanTabScreen(nav: NavHostController) {
    val vm = koinViewModel<BeanViewModel>()
    val data by vm.data.collectAsStateWithLifecycle()
    val selected by vm.selectedView.collectAsStateWithLifecycle()
    // a view asked for by another screen (the calendar's blend row → 블렌드) wins from the first frame, then sticks
    val requests = koinInject<BeanViewRequests>()
    val requested by requests.pending.collectAsStateWithLifecycle()
    LaunchedEffect(requested) { requested?.let { vm.selectView(it); requests.clear() } }
    val view = requested ?: selected
    val stateHolder = rememberSaveableStateHolder()

    Column(Modifier.fillMaxSize()) {
        // the web header is global: every tab shows the record count
        TopHeader(title = "coffee_journal / 2026", tagline = "[ personal coffee archive ]", right = if (data.loaded) "${data.entries.size} entries" else null)
        // the selected chip is scrolled into view (the default 커피 지도 is the 8th of 9)
        SubTabs(
            items = BeanViews.all,
            selected = view,
            onSelect = vm::selectView,
            labels = BeanViews.labels,
            // ✦ Competition Lots keeps the web's accent outline (.cal-subtab-special)
            special = setOf(BeanViews.SPECIALTY),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 10.dp),
        )
        // every sub view fills this box, so its scroll area ends at the keyboard (search fields, the process add form)
        Box(Modifier.fillMaxSize().imeOverlapPadding()) {
            // nothing until the records have loaded: the views would flash their empty-state copy first.
            // Each view keeps its own rememberSaveable state (search text, toggles) while another tab is showing.
            if (data.loaded) stateHolder.SaveableStateProvider(view) {
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
