package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.ui.map.CafeMapLogic
import com.coffeejournal.ui.map.MapPickResult
import com.coffeejournal.ui.map.MapPickTarget
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.imeOverlapPadding
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.BlockBackWhile
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.Seg
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Route.FlatItemForm: add / edit a roastery, importer, farm or process item (web *-form-panel). */
@Composable
fun FlatItemFormScreen(nav: NavHostController, type: String, itemId: String?, results: SavedStateHandle? = null) {
    val vm = koinViewModel<FlatItemFormViewModel> { parametersOf(type, itemId) }
    val s by vm.state.collectAsStateWithLifecycle()
    // the location picker answers through this destination's SavedStateHandle
    if (results != null) {
        val pick by results.getStateFlow<String?>(MapPickResult.KEY, null).collectAsStateWithLifecycle()
        LaunchedEffect(pick) {
            pick?.let { vm.applyPick(it); results.remove<String>(MapPickResult.KEY) }
        }
    }
    val spec = vm.spec
    // one pop per back / 취소 / successful save, even when tapped again during the exit transition
    val leave = dropUnlessResumed { nav.popBackStack() }
    BlockBackWhile(s.saving)
    Column(Modifier.fillMaxSize().background(Ink.bg)) {
        ScreenTitleBar("${spec.label} ${if (itemId == null) "추가" else "수정"}", onBack = { if (!s.saving) leave() })
        Column(Modifier.weight(1f).imeOverlapPadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
            Spacer(Modifier.height(16.dp))
            AppTextField(value = s.name, onValueChange = vm::setName, label = "이름", placeholder = spec.namePlaceholder, enabled = s.loaded)
            if (spec.hasStatus) {
                Spacer(Modifier.height(14.dp))
                FieldLabel("상태")
                Seg(options = listOf("", MiscStatus.CURIOUS), value = s.status, onChange = vm::setStatus, allowClear = false, labels = mapOf("" to "마셔봄", MiscStatus.CURIOUS to "궁금함"))
            }
            if (spec.hasScope) {
                Spacer(Modifier.height(14.dp))
                FieldLabel("국내/해외")
                Seg(options = listOf(Scope.DOMESTIC, Scope.OVERSEAS), value = s.scope, onChange = vm::setScope, allowClear = false)
                Spacer(Modifier.height(14.dp))
                AppTextField(value = s.location, onValueChange = vm::setLocation, label = "지역", placeholder = "예: 대한민국 서울특별시")
                MapPointField(
                    point = s.point, overseas = s.scope == Scope.OVERSEAS, enabled = s.loaded && !s.saving,
                    onPick = {
                        nav.navigate(Route.MapPicker(target = MapPickTarget.ROASTERY, name = s.name.trim(), scope = s.scope, point = s.point?.let { MapPickResult.encode(it) }))
                    },
                    onClear = vm::clearPoint,
                )
            }
            Spacer(Modifier.height(14.dp))
            AppTextField(value = s.notes, onValueChange = vm::setNotes, label = "메모 (선택)", placeholder = spec.notesPlaceholder, singleLine = false, minLines = 3)
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(if (itemId == null) "저장" else "수정 저장", enabled = s.loaded && !s.saving && s.name.isNotBlank(), onClick = { vm.save(leave) })
                GhostButton("취소", onClick = leave, enabled = !s.saving)
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

/** "지도 위치": the exact position (area + coordinates) or the note that the 지역 text places the pin, and the buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapPointField(point: GeoPoint?, overseas: Boolean, enabled: Boolean, onPick: () -> Unit, onClear: () -> Unit) {
    Spacer(Modifier.height(14.dp))
    FieldLabel("지도 위치")
    if (point == null) {
        Text("지정하지 않았어요. 지도에는 지역 이름으로 찾은 곳에 표시돼요.", style = AppType.small)
    } else {
        Text("📍 " + (MapPickResult.placeName(point, overseas) ?: "지역을 찾지 못한 곳"), style = AppType.body)
        Text(CafeMapLogic.coords(point), style = AppType.monoSmall)
    }
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton(if (point == null) "지도에서 위치 지정" else "지도에서 위치 변경", small = true, enabled = enabled, onClick = onPick)
        if (point != null) GhostButton("위치 지우기", small = true, enabled = enabled, onClick = onClear)
    }
}
