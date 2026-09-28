package com.coffeejournal.ui.bean.b

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.repo.BlendRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.BeanRecords
import com.coffeejournal.ui.theme.deriveOffMain
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.KoreaProjection
import com.coffeejournal.domain.rules.Ids
import com.coffeejournal.ui.bean.BeanData
import com.coffeejournal.ui.map.MapPickResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Favorite toggling and deletion for roastery / importer / farm cards. */
class MiscItemsViewModel(private val misc: MiscRepository) : ViewModel() {
    fun toggleFavorite(item: MiscItem) { viewModelScope.launch { misc.upsert(item.copy(favorite = !item.favorite)) } }
    fun delete(id: String) { viewModelScope.launch { misc.delete(id) } }
}

class BlendsViewModel(private val blends: BlendRepository) : ViewModel() {
    fun delete(id: String) { viewModelScope.launch { blends.delete(id) } }
}

/** Live BeanData for the stand-alone detail routes (country / roastery) that are opened outside the tab. */
class BeanExtraDataViewModel(entries: EntryRepository, misc: MiscRepository, blends: BlendRepository) : ViewModel() {
    val data: StateFlow<BeanData> = combine(entries.observeAll(), misc.observeAll(), blends.observeAll()) { e, m, b -> BeanData(loaded = true, entries = e, miscItems = m, blends = b) }
        .deriveOffMain { it.copy(records = BeanRecords.flatten(it.entries), originRecords = BeanRecords.flatten(it.entries, blendBeans = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BeanData())
}

/** Shared add/edit form for source / selection / farm / process misc items (web makeFlatListTab form). */
class FlatItemFormViewModel(val type: String, private val itemId: String?, private val misc: MiscRepository) : ViewModel() {
    data class State(
        val name: String = "", val status: String = "", val scope: String = Scope.DOMESTIC, val location: String = "",
        val notes: String = "", val existing: MiscItem? = null, val loaded: Boolean = false,
        /** A save is running or has succeeded (the screen is closing): further saves are ignored. */
        val saving: Boolean = false,
        /** Exact position set with "지도에서 위치 지정" (roasteries only). */
        val point: GeoPoint? = null,
    )

    private val _state = MutableStateFlow(State(loaded = itemId == null))
    /** A new item's id, fixed for this form, so a repeated save could only upsert the same row. */
    private val newId = Ids.newId()
    val state: StateFlow<State> = _state
    val spec: FlatItemLogic.Spec = FlatItemLogic.spec(type)

    init {
        if (itemId != null) viewModelScope.launch {
            val m = misc.getById(itemId)
            _state.update { s ->
                if (m == null) s.copy(loaded = true)
                else s.copy(name = m.name, status = m.status, scope = m.scope.ifBlank { Scope.DOMESTIC }, location = m.location, notes = m.notes, existing = m, loaded = true, point = m.point)
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setStatus(v: String) = _state.update { it.copy(status = v) }
    fun setScope(v: String) = _state.update { s ->
        val scope = v.ifBlank { s.scope }
        // a point abroad cannot stay on a 국내 roastery (the Korea map would not show it), nor a Korean one on a 해외
        val keep = s.point?.takeIf { KoreaProjection.inKoreaBox(it) == (scope == Scope.DOMESTIC) }
        s.copy(scope = scope, point = keep)
    }

    /**
     * The location picker's answer ([MapPickResult]): sets or clears the position; an empty 지역 field is filled with
     * the area found there ("서울특별시 성동구", or the country abroad).
     */
    fun applyPick(result: String) = _state.update { s ->
        if (s.saving) return@update s
        val p = MapPickResult.decode(result) ?: return@update s.copy(point = null)
        val fill = if (s.location.isBlank()) MapPickResult.placeName(p, overseas = s.scope == Scope.OVERSEAS) else null
        s.copy(point = p, location = fill ?: s.location)
    }

    fun clearPoint() = _state.update { if (it.saving) it else it.copy(point = null) }
    fun setLocation(v: String) = _state.update { it.copy(location = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val name = s.name.trim()
        if (name.isEmpty() || s.saving || !s.loaded) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                // the row as it is now: its favourite mark may have changed since the form was loaded (gap #11)
                val base = s.existing?.let { misc.getById(it.id) ?: it } ?: MiscItem(id = newId, type = type, name = name, createdAt = Dates.nowMillis())
                misc.upsert(
                    base.copy(
                        name = name, notes = s.notes.trim(),
                        status = if (spec.hasStatus) s.status else base.status,
                        scope = if (spec.hasScope) s.scope else base.scope,
                        location = if (spec.hasScope) s.location.trim() else base.location,
                        lat = if (spec.hasScope) s.point?.lat else base.lat,
                        lng = if (spec.hasScope) s.point?.lng else base.lng,
                    )
                )
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

/** Hand-made blend form (web blend-form-panel). */
class BlendFormViewModel(private val blendId: String?, private val blends: BlendRepository, entries: EntryRepository) : ViewModel() {
    data class State(
        val name: String = "", val date: String = Dates.isoDate(Dates.today()), val rows: List<BlendComponent> = listOf(BlendComponent(""), BlendComponent("")),
        val notes: String = "", val existing: Blend? = null, val loaded: Boolean = false,
        /** A save is running or has succeeded (the screen is closing): further saves are ignored. */
        val saving: Boolean = false,
    )

    private val _state = MutableStateFlow(State(loaded = blendId == null))
    /** A new blend's id, fixed for this form, so a repeated save could only upsert the same row. */
    private val newId = Ids.newId()
    val state: StateFlow<State> = _state
    val suggestions: StateFlow<List<String>> = entries.observeAll()
        .deriveOffMain { e -> BlendSources.recentBeanNames(BeanRecords.flatten(e)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (blendId != null) viewModelScope.launch {
            val b = blends.getById(blendId)
            _state.update { s ->
                if (b == null) s.copy(loaded = true)
                else s.copy(name = b.name, date = b.date.ifBlank { s.date }, rows = b.beans.ifEmpty { listOf(BlendComponent("")) }, notes = b.notes, existing = b, loaded = true)
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setDate(v: String) = _state.update { it.copy(date = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun addRow() = _state.update { it.copy(rows = it.rows + BlendComponent("")) }
    fun updateRow(index: Int, name: String? = null, grams: String? = null) = _state.update { s ->
        s.copy(rows = s.rows.mapIndexed { i, r -> if (i == index) r.copy(name = name ?: r.name, grams = grams ?: r.grams) else r })
    }
    /** Removing the last row leaves one empty row, like the web. */
    fun removeRow(index: Int) = _state.update { s ->
        val rest = s.rows.filterIndexed { i, _ -> i != index }
        s.copy(rows = rest.ifEmpty { listOf(BlendComponent("")) })
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val beans = BlendSources.validRows(s.rows)
        if (beans.isEmpty() || s.saving || !s.loaded) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val now = Dates.nowMillis()
                blends.upsert(
                    Blend(
                        id = s.existing?.id ?: newId, name = s.name.trim(),
                        date = s.date.trim().ifEmpty { Dates.isoDate(Dates.today()) }, beans = beans, notes = s.notes.trim(),
                        createdAt = s.existing?.createdAt ?: now,
                    )
                )
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

/** Convenience for the roastery / importer / farm views. */
internal fun List<MiscItem>.ofType(type: String): List<MiscItem> = filter { it.type == type }
