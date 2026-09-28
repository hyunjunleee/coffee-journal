package com.coffeejournal.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.coffeejournal.data.repo.CafePlaceRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.ui.form.AutocompleteField
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.BlockBackWhile
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.LeaveDialog
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.deriveOffMain
import com.coffeejournal.ui.theme.imeOverlapPadding
import com.coffeejournal.ui.theme.rememberLeaveGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Route.CafeAdd: the name typed, every café (for suggestions and the one the name already is), and the save. */
class CafeAddViewModel(entries: EntryRepository, private val cafes: CafePlaceRepository) : ViewModel() {
    data class State(
        val name: String = "",
        /** A save is running or has succeeded (the screen is closing): further saves are ignored. */
        val saving: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    val spots: StateFlow<List<CafeSpot>> = combine(entries.observeAll(), cafes.observeAll()) { e, p -> e to p }
        .deriveOffMain { (e, p) -> CafeMapLogic.spots(e, p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setName(v: String) = _state.update { if (it.saving) it else it.copy(name = v) }

    /** A name has been typed: leaving asks first (the screen opens empty). */
    fun hasChanges(): Boolean = _state.value.name.isNotBlank()

    /**
     * Keeps the café, then [onDone] with its name. A name that is already a café (any spelling) adds nothing and
     * goes on with that café's own spelling.
     */
    fun save(onDone: (String) -> Unit) {
        val s = _state.value
        val typed = s.name.trim()
        if (typed.isEmpty() || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val name = CafeMapLogic.find(spots.value, typed)?.name ?: typed
                cafes.add(name)
                onDone(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

/**
 * Route.CafeAdd, the "+ 카페 추가" of the café map, the calendar's café list and the detail map: a café by name, with
 * no visit needed. "지도에서 위치 지정" keeps it and opens the café picker in this screen's place (the picker saves the
 * position; back leads to where the café was added from); "위치 없이 저장" keeps it without a position. The field
 * suggests the cafés there are, and a name that already is one says so instead of making a second café.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CafeAddScreen(nav: NavHostController, vm: CafeAddViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val spots by vm.spots.collectAsStateWithLifecycle()
    val leave = dropUnlessResumed { nav.popBackStack() }
    BlockBackWhile(s.saving)
    val guard = rememberLeaveGuard(vm::hasChanges, busy = s.saving, leave = leave)
    LeaveDialog(guard)
    val existing = remember(spots, s.name) { CafeMapLogic.find(spots, s.name) }
    val names = remember(spots) { spots.map { it.name } }
    val canSave = s.name.isNotBlank() && !s.saving
    Column(Modifier.fillMaxSize().background(Ink.bg)) {
        ScreenTitleBar("카페 추가", onBack = guard::request)
        Column(Modifier.weight(1f).imeOverlapPadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
            Spacer(Modifier.height(16.dp))
            AutocompleteField(
                value = s.name, onValueChange = vm::setName, options = names, label = "카페 이름", placeholder = "예: OO카페 (서울 성수동)",
                hint = existing?.let { c ->
                    "이미 있는 카페예요: ${c.name} · ${c.visitText} · " + (c.point?.let { "📍 ${CafeMapLogic.placeLabel(it)}" } ?: "위치 미지정")
                },
            )
            HintText(
                "방문 기록 없이도 카페를 등록해 둘 수 있어요. 카페 지도·상세 지도·달력의 카페 목록에 바로 나타나요.",
                Modifier.padding(top = 8.dp),
            )
            FlowRow(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    if (existing?.point != null) "지도에서 위치 변경" else "지도에서 위치 지정", enabled = canSave,
                    onClick = {
                        vm.save { name ->
                            nav.navigate(Route.MapPicker(target = MapPickTarget.CAFE, name = name)) { popUpTo<Route.CafeAdd> { inclusive = true } }
                        }
                    },
                )
                if (existing == null) GhostButton("위치 없이 저장", enabled = canSave, onClick = { vm.save { leave() } })
                GhostButton("취소", enabled = !s.saving, onClick = guard::request)
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}
