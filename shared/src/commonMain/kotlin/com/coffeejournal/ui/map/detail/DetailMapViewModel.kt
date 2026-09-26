package com.coffeejournal.ui.map.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.repo.CafePlaceRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.BeanRecords
import com.coffeejournal.ui.bean.b.FlatItemLogic
import com.coffeejournal.ui.bean.b.RoasteryMapModel
import com.coffeejournal.ui.map.CafeMapLogic
import com.coffeejournal.ui.map.CafeSpot
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.deriveOffMain
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

object DetailMapMode {
    const val VIEW = "view"
    const val PICK = "pick"
}

object DetailMapLayer {
    const val ROASTERY = "roastery"
    const val CAFE = "cafe"
}

/** Builds the detail map's routes from what the SGIS maps and panels know. */
object DetailMapRoutes {
    /** Centred on one pin, selected. */
    fun pin(layer: String, scope: String, pin: DetailPin): Route.DetailMap =
        Route.DetailMap(layer = layer, scope = scope, camera = pin.camera.encode(), focus = pin.key)

    /** Fitted to what the SGIS map shows (a 시·도, or the part of it zoomed into). */
    fun area(layer: String, scope: String, bounds: GeoBounds, focus: String? = null): Route.DetailMap =
        Route.DetailMap(layer = layer, scope = scope, bounds = bounds.encode(), focus = focus)

    /** The picker's "상세 지도에서 정확히": starts on the point already set, else on what the SGIS map shows. */
    fun pick(scope: String, name: String, point: GeoPoint?, bounds: GeoBounds?): Route.DetailMap = Route.DetailMap(
        mode = DetailMapMode.PICK, scope = scope, name = name,
        camera = point?.let { DetailCamera(it.lat, it.lng, PinPrecision.EXACT.zoom).encode() },
        bounds = bounds?.encode(),
    )
}

/**
 * The detail map picks its point for the location picker through the picker's SavedStateHandle under [KEY], encoded
 * like MapPickResult ("lat,lng").
 */
object DetailMapPick {
    const val KEY = "detail-map-pick"
}

/** The pins and panel data of the detail map, derived like the SGIS maps' (roasteries, or visited cafés). */
data class DetailMapContent(
    val loaded: Boolean = false,
    val pins: List<DetailPin> = emptyList(),
    val roasteries: RoasteryMapModel? = null,
    val records: List<BeanRecord> = emptyList(),
    val cafes: List<CafeSpot> = emptyList(),
)

class DetailMapViewModel(
    val route: Route.DetailMap,
    entries: EntryRepository,
    misc: MiscRepository,
    places: CafePlaceRepository,
) : ViewModel() {
    val pick: Boolean get() = route.mode == DetailMapMode.PICK
    val domestic: Boolean get() = route.scope != Scope.OVERSEAS

    val content: StateFlow<DetailMapContent> = (
        if (pick) flowOf(DetailMapContent(loaded = true))
        else combine(entries.observeAll(), misc.observeAll(), places.observeAll()) { e, m, p -> Triple(e, m, p) }
            .deriveOffMain { (e, m, p) ->
                if (route.layer == DetailMapLayer.CAFE) {
                    val spots = CafeMapLogic.spots(e, p)
                    DetailMapContent(true, DetailMapPins.cafes(spots), cafes = spots)
                } else {
                    val records = BeanRecords.flatten(e)
                    val model = FlatItemLogic.roasteryMap(m, records, route.scope)
                    DetailMapContent(true, DetailMapPins.roasteries(model.pins, domestic), model, records)
                }
            }
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailMapContent())
}
