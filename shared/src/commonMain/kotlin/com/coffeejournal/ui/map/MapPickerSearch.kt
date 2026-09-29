package com.coffeejournal.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.coffeejournal.ui.form.TextLink
import com.coffeejournal.ui.map.search.PlaceHit
import com.coffeejournal.ui.map.search.PlaceSearchTexts
import com.coffeejournal.ui.map.search.PlaceSource
import com.coffeejournal.ui.guide.KeyGuideLink
import com.coffeejournal.ui.guide.KeyHowTos
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton

/**
 * The picker's search above the map: the field (the place's name to start with) with 검색 — it searches only when
 * tapped or on the keyboard's search key — and "현재 위치"; below them what the search found (with "더 보기" when Kakao
 * has more), or why nothing. A [domestic] search that ran without Kakao says what a Kakao key adds, with its how-to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PickerSearch(
    s: MapPickerViewModel.State,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onPick: (PlaceHit) -> Unit,
    onClose: () -> Unit,
    onLocate: () -> Unit,
    onMore: () -> Unit,
    domestic: Boolean,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val search = {
        // ends a syllable still being composed and puts the keyboard away so the results show
        focus.clearFocus()
        keyboard?.hide()
        onSearch()
    }
    val busy = s.saving || s.search is MapPickerViewModel.Search.Running
    Column(modifier.fillMaxWidth().testTag("picker-search")) {
        FieldLabel(PlaceSearchTexts.FIELD)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppTextField(
                value = s.query, onValueChange = onQuery, modifier = Modifier.weight(1f), placeholder = PlaceSearchTexts.PLACEHOLDER,
                imeAction = ImeAction.Search, onImeAction = { if (!busy) search() }, enabled = !s.saving, capitalizeWords = false,
            )
            Spacer(Modifier.width(8.dp))
            PrimaryButton(PlaceSearchTexts.SEARCH, enabled = !busy, onClick = search)
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GhostButton(
                if (s.locating) PlaceSearchTexts.LOCATING else PlaceSearchTexts.HERE, small = true, icon = AppIcons.locate,
                enabled = !s.locating && !s.saving, onClick = onLocate,
            )
        }
        when (val r = s.search) {
            MapPickerViewModel.Search.Idle -> Unit
            MapPickerViewModel.Search.Running -> Text(PlaceSearchTexts.SEARCHING, style = AppType.small, modifier = Modifier.padding(top = 8.dp))
            is MapPickerViewModel.Search.Message -> Column(Modifier.padding(top = 8.dp).testTag("search-message")) {
                Text(r.text, style = AppType.small.copy(color = if (r.error) Ink.bad else Ink.textMuted))
                r.source?.let { Text(PlaceSearchTexts.source(it), style = AppType.faint, modifier = Modifier.padding(top = 2.dp)) }
                if (domestic && r.source != null && r.source != PlaceSource.KAKAO) KakaoTip()
            }
            is MapPickerViewModel.Search.Found -> {
                FoundList(r, onPick, onClose, onMore)
                if (domestic && PlaceSource.KAKAO !in r.sources) KakaoTip()
            }
        }
    }
}

/** What the search found: the places, "더 보기" when Kakao has more, and which searches answered (with the credit). */
@Composable
private fun FoundList(r: MapPickerViewModel.Search.Found, onPick: (PlaceHit) -> Unit, onClose: () -> Unit, onMore: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).testTag("search-results"),
    ) {
        r.notice?.let { Text(it, style = AppType.small.copy(color = Ink.bad), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
        r.hits.forEachIndexed { i, hit ->
            if (i > 0 || r.notice != null) Hairline()
            HitRow(hit) { onPick(hit) }
        }
        if (r.next != null || r.moreFailed) {
            Hairline()
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (r.moreFailed) Text(PlaceSearchTexts.MORE_FAILED, style = AppType.small.copy(color = Ink.bad), modifier = Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
                if (r.next != null) TextLink(
                    if (r.loadingMore) PlaceSearchTexts.MORE_LOADING else PlaceSearchTexts.MORE, Ink.text,
                    { if (!r.loadingMore) onMore() }, Modifier.testTag("search-more"),
                )
            }
        }
        Hairline()
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(PlaceSearchTexts.source(r.sources), style = AppType.faint, modifier = Modifier.testTag("search-source"))
                if (PlaceSource.OSM in r.sources) Text(PlaceSearchTexts.OSM_CREDIT, style = AppType.faint)
            }
            TextLink(PlaceSearchTexts.CLOSE, Ink.textMuted, onClose, Modifier.padding(end = 4.dp))
        }
    }
}

/** What a Kakao key adds to a 국내 search that ran without one, with the link to its how-to. */
@Composable
private fun KakaoTip() {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("kakao-tip")) {
        Text(PlaceSearchTexts.KAKAO_TIP, style = AppType.small)
        KeyGuideLink(KeyHowTos.KAKAO, PlaceSearchTexts.KAKAO_TIP_LINK)
    }
}

/** One found place: its name, its address when that says more, and its kind and distance when known. */
@Composable
private fun HitRow(hit: PlaceHit, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text(hit.name, style = AppType.body.copy(fontWeight = FontWeight.SemiBold))
        if (hit.address.isNotBlank() && hit.address != hit.name) Text(hit.address, style = AppType.small)
        val extra = listOfNotNull(hit.category, PlaceSearchTexts.distance(hit))
        if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = AppType.faint)
    }
}
