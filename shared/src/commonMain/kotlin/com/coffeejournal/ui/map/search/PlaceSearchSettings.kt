package com.coffeejournal.ui.map.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.coffeejournal.ui.ai.AiSettingsViewModel
import com.coffeejournal.ui.form.TextLink
import com.coffeejournal.ui.platform.openUrl
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.compose.viewmodel.koinViewModel

/** The saved Kakao key as the settings show it: only its last four characters, and the last 키 확인. */
data class PlaceSearchSettingsUi(
    val loaded: Boolean = false,
    val masked: String? = null,
    val checking: Boolean = false,
    val check: KakaoKeyCheck? = null,
    val storeSupported: Boolean = true,
)

/** 설정 › 장소 검색: the optional Kakao REST API key (saved, masked, checked, cleared). */
class PlaceSearchSettingsViewModel(private val service: PlaceSearchService) : ViewModel() {
    private val _state = MutableStateFlow(PlaceSearchSettingsUi(storeSupported = service.storeSupported))
    val state: StateFlow<PlaceSearchSettingsUi> = _state.asStateFlow()
    private val writes = Mutex()

    init {
        viewModelScope.launch {
            val key = runCatching { service.kakaoKey() }.getOrNull()
            _state.update { it.copy(loaded = true, masked = key?.let(AiSettingsViewModel::mask)) }
        }
    }

    /** A pasted key, without spaces or line breaks (a copied key often carries one). */
    fun save(typed: String) {
        if (typed.isBlank() || !service.storeSupported) return
        write {
            service.saveKakaoKey(typed)
            val key = service.kakaoKey()
            _state.update { it.copy(masked = key?.let(AiSettingsViewModel::mask), check = null) }
        }
    }

    fun clear() = write {
        service.clearKakaoKey()
        _state.update { it.copy(masked = null, check = null, checking = false) }
    }

    fun check() {
        if (_state.value.checking || _state.value.masked == null) return
        _state.update { it.copy(checking = true, check = null) }
        viewModelScope.launch {
            val result = service.checkKakaoKey()
            _state.update { it.copy(checking = false, check = result) }
        }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch { writes.withLock { block() } }
    }
}

/**
 * 설정 › 장소 검색: what the picker's search uses, and the Kakao key — a password-style field and 저장 until it is
 * saved, then its last four characters with 키 확인 and 지우기. The typed text is not kept in the saved instance
 * state, so a key never lands in the activity's bundle.
 */
@Composable
fun PlaceSearchSettingsSection(modifier: Modifier = Modifier) {
    val vm = koinViewModel<PlaceSearchSettingsViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    var clearing by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().testTag("place-search-settings")) {
        if (!state.loaded) return@Column
        Text(PlaceSearchTexts.INTRO, style = AppType.bodyMuted)
        if (!state.storeSupported) Text(PlaceSearchTexts.STORE_UNSUPPORTED, style = AppType.small.copy(color = Ink.bad), modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))
        FieldLabel(PlaceSearchTexts.KAKAO_LABEL)
        val masked = state.masked
        if (masked != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("저장됨 $masked", style = AppType.monoValue, modifier = Modifier.weight(1f).testTag("kakao-key"))
                GhostButton(if (state.checking) PlaceSearchTexts.CHECKING else PlaceSearchTexts.CHECK, small = true, enabled = !state.checking, onClick = vm::check)
                Spacer(Modifier.width(6.dp))
                GhostButton(PlaceSearchTexts.CLEAR, small = true, danger = true, onClick = { clearing = true })
            }
            state.check?.let { c ->
                Text(c.label, style = AppType.small.copy(color = if (c.ok) Ink.good else Ink.bad), modifier = Modifier.padding(top = 4.dp).testTag("kakao-key-check"))
            }
        } else {
            var typed by remember { mutableStateOf("") }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.weight(1f),
                    enabled = state.storeSupported,
                    singleLine = true,
                    placeholder = { Text(PlaceSearchTexts.KAKAO_PLACEHOLDER, style = AppType.input.copy(color = Ink.textFaint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    textStyle = AppType.input,
                    shape = RectangleShape,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Ink.accent, unfocusedBorderColor = Ink.line,
                        focusedContainerColor = Ink.surface, unfocusedContainerColor = Ink.surface,
                        cursorColor = Ink.accent, focusedTextColor = Ink.text, unfocusedTextColor = Ink.text,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                PrimaryButton(PlaceSearchTexts.SAVE, small = true, enabled = state.storeSupported && typed.isNotBlank(), onClick = { vm.save(typed); typed = "" })
            }
        }
        HintText(PlaceSearchTexts.KAKAO_HINT)
        HintText(PlaceSearchTexts.KAKAO_GUIDE)
        TextLink("카카오 개발자 사이트 ↗", Ink.textMuted, { openUrl(PlaceSearchTexts.KAKAO_GUIDE_URL) })
        HintText(PlaceSearchTexts.KAKAO_PRIVACY)
    }

    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            shape = RectangleShape, containerColor = Ink.bg,
            title = { Text("키 지우기", style = AppType.title) },
            text = { Text("저장한 카카오 REST API 키를 이 휴대폰에서 지울까요? 국내 검색은 기기 지도 서비스로 돌아가요.", style = AppType.body) },
            confirmButton = { PrimaryButton(PlaceSearchTexts.CLEAR, small = true, onClick = { vm.clear(); clearing = false }) },
            dismissButton = { GhostButton("취소", small = true, onClick = { clearing = false }) },
        )
    }
}
