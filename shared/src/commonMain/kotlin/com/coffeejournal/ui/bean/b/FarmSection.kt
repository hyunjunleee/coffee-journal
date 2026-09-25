package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import kotlin.math.roundToInt

/** Farm (producer) cards under the coffee map (web #bean-view-map lower half). */
@Composable
fun FarmSection(
    farms: List<MiscItem>,
    records: List<BeanRecord>,
    query: String,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (MiscItem) -> Unit,
    onDelete: (MiscItem) -> Unit,
    onCountryTap: (String) -> Unit,
    highlightedFarm: String?,
    modifier: Modifier = Modifier,
    positions: FarmCardPositions? = null,
) {
    Column(modifier.fillMaxWidth()) {
        PrimaryButton("+ 추가", onClick = onAdd, modifier = Modifier.padding(top = 24.dp))
        Row(Modifier.padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) { Text("농장(생산자)", style = AppType.sectionLabel) }
        AppTextField(
            value = query, onValueChange = onQueryChange, placeholder = "농장·생산자 검색",
            trailing = { Icon(AppIcons.search, contentDescription = null, tint = Ink.textFaint) },
        )
        Spacer(Modifier.height(10.dp))
        val filtered = farms.filter { FlatItemLogic.matchesSearch(it, query) }
        StatusSplitList(filtered, label = "농장(생산자)", favoritable = false) { item ->
            MiscItemCard(
                item, onEdit = { onEdit(item) }, onDelete = { onDelete(item) }, highlighted = item.name == highlightedFarm,
                modifier = if (positions != null) Modifier.onGloballyPositioned { positions.onCard(item.name, it) } else Modifier,
            ) {
                FarmCountryInfo(FlatItemLogic.farmRecords(records, item.name), onCountryTap)
            }
        }
    }
}

/**
 * Where the farm cards and the scrolling page sit, so a farm tapped in the map panel can scroll its own card to the
 * middle of the screen (web jumpToFarm: `card.scrollIntoView({ block: 'center' })`).
 */
class FarmCardPositions {
    private var viewport: LayoutCoordinates? = null
    private val cards = HashMap<String, LayoutCoordinates>()

    /** The page's viewport: the scrolling column's coordinates outside its scroll. */
    fun onViewport(coordinates: LayoutCoordinates) { viewport = coordinates }
    fun onCard(name: String, coordinates: LayoutCoordinates) { cards[name] = coordinates }

    /** The scroll value that centres [name]'s card, or null while that card is not on the page. */
    fun centreTarget(name: String, scrollValue: Int): Int? {
        val page = viewport?.takeIf { it.isAttached } ?: return null
        val card = cards[name]?.takeIf { it.isAttached } ?: return null
        return FarmJump.target(scrollValue, page.localPositionOf(card, Offset.Zero).y, card.size.height, page.size.height)
    }
}

object FarmJump {
    /**
     * Scroll value that puts a card whose top is [cardTop] px below the viewport top in the middle of a viewport
     * [viewportHeight] px tall (top-aligned when the card is taller than the viewport). Not clamped to the scroll range.
     */
    fun target(scrollValue: Int, cardTop: Float, cardHeight: Int, viewportHeight: Int): Int {
        val offset = if (cardHeight >= viewportHeight) 0f else (viewportHeight - cardHeight) / 2f
        return (scrollValue + cardTop - offset).roundToInt()
    }
}

/** Web farmCountryInfo: "국가별로 마셔본 횟수 (눌러서 지도 보기)" rows that jump to the map. */
@Composable
fun FarmCountryInfo(matching: List<BeanRecord>, onCountryTap: (String) -> Unit) {
    if (matching.isEmpty()) return
    SubLabel("국가별로 마셔본 횟수 (눌러서 지도 보기)")
    FlatItemLogic.countryCounts(matching).forEach { (country, n) ->
        SourceBeanRow(country, right = "${n}번", onClick = { onCountryTap(country) })
    }
    BreakdownLine(matching)
}
