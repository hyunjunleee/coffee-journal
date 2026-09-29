package com.coffeejournal.ui.ai

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coffeejournal.ui.guide.GuideTexts
import com.coffeejournal.ui.guide.KeyGuideLink
import com.coffeejournal.ui.guide.KeyHowTos
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.Seg
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** 설정 › AI 노트 도우미: the way of answering, its key(s) with a link to each key's how-to, and the model. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiSettingsSection(modifier: Modifier = Modifier) {
    val vm = koinViewModel<AiSettingsViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = koinInject<AiSettingsFocus>()
    val requester = remember { BringIntoViewRequester() }
    var clearing by remember { mutableStateOf<AiKeySlot?>(null) }

    // opened from the answer screen's "설정에서 키 넣기": show this section, not the top of 설정
    LaunchedEffect(state.loaded) {
        if (state.loaded && focus.consume()) requester.bringIntoView()
    }

    Column(modifier.fillMaxWidth().bringIntoViewRequester(requester).testTag("ai-settings")) {
        // nothing until the saved settings are read, so the defaults do not flash first
        if (!state.loaded) return@Column
        val s = state.settings
        val p = s.provider
        Text(AiTexts.INTRO, style = AppType.bodyMuted)
        if (!state.storeSupported) Text(AiTexts.STORE_UNSUPPORTED, style = AppType.small.copy(color = Ink.bad), modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))

        FieldLabel("방식")
        Seg(
            options = AiProvider.entries.map { it.label },
            value = p.label,
            onChange = { label -> AiProvider.entries.firstOrNull { it.label == label }?.let(vm::setProvider) },
            allowClear = false,
        )
        HintText(p.summary)
        // each key's how-to opens full screen, so this section stays short
        p.keys.forEach { slot -> KeyHowTos.of(slot.guide)?.let { KeyGuideLink(it, GuideTexts.open(slot.label)) } }

        p.keys.forEach { slot ->
            Spacer(Modifier.height(10.dp))
            KeyField(
                slot, state.keys[slot] ?: KeyUi(), enabled = state.storeSupported,
                onSave = { vm.saveKey(slot, it) }, onCheck = { vm.checkKey(slot) }, onClear = { clearing = slot },
            )
        }
        HintText(AiTexts.KEY_HINT)

        Spacer(Modifier.height(12.dp))
        ModelField(p, s, onChange = { vm.setModel(p, it) })

        // only Tavily's searches are the app's to choose
        if (p == AiProvider.GEMINI_TAVILY) {
            Spacer(Modifier.height(12.dp))
            FieldLabel(AiTexts.SEARCH_DEPTH)
            Seg(
                options = SearchDepth.entries.map { it.label },
                value = s.searchDepth.label,
                onChange = { label -> SearchDepth.entries.firstOrNull { it.label == label }?.let(vm::setSearchDepth) },
                allowClear = false,
                modifier = Modifier.testTag("search-depth"),
            )
            HintText(AiTexts.SEARCH_DEPTH_HINT)
            Spacer(Modifier.height(12.dp))
            FieldLabel(AiTexts.PEOPLE)
            Seg(
                options = listOf(AiTexts.PEOPLE_ON, AiTexts.PEOPLE_OFF),
                value = if (s.people) AiTexts.PEOPLE_ON else AiTexts.PEOPLE_OFF,
                onChange = { label -> vm.setPeople(label == AiTexts.PEOPLE_ON) },
                allowClear = false,
                modifier = Modifier.testTag("people"),
            )
            HintText(AiTexts.PEOPLE_HINT)
            Text(AiTexts.credits(s.tavilyCredits), style = AppType.small.copy(color = Ink.text), modifier = Modifier.padding(top = 6.dp).testTag("tavily-credits"))
        }
    }

    clearing?.let { slot ->
        AlertDialog(
            onDismissRequest = { clearing = null },
            shape = RectangleShape, containerColor = Ink.bg,
            title = { Text("키 지우기", style = AppType.title) },
            text = { Text("저장한 ${slot.label}를 이 휴대폰에서 지울까요?", style = AppType.body) },
            confirmButton = { PrimaryButton(AiTexts.CLEAR, small = true, onClick = { vm.clearKey(slot); clearing = null }) },
            dismissButton = { GhostButton("취소", small = true, onClick = { clearing = null }) },
        )
    }
}

/**
 * One key: a password-style field and 저장 until it is saved, then only its last four characters with 키 확인 and
 * 지우기. The typed text is not kept in the saved instance state, so a key never lands in the activity's bundle.
 */
@Composable
private fun KeyField(slot: AiKeySlot, ui: KeyUi, enabled: Boolean, onSave: (String) -> Unit, onCheck: () -> Unit, onClear: () -> Unit) {
    FieldLabel(slot.label)
    val masked = ui.masked
    if (masked != null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("저장됨 $masked", style = AppType.monoValue, modifier = Modifier.weight(1f).testTag("key-${slot.name}"))
            GhostButton(if (ui.checking) AiTexts.CHECKING else AiTexts.CHECK, small = true, enabled = !ui.checking, onClick = onCheck)
            Spacer(Modifier.width(6.dp))
            GhostButton(AiTexts.CLEAR, small = true, danger = true, onClick = onClear)
        }
        ui.check?.let { c ->
            Text(
                c.label, style = AppType.small.copy(color = if (c.ok) Ink.good else Ink.bad),
                modifier = Modifier.padding(top = 4.dp).testTag("key-check-${slot.name}"),
            )
            c.error?.let { e -> listOfNotNull(e.hint, e.detail).forEach { Text(it, style = AppType.faint) } }
        }
    } else {
        var typed by remember(slot) { mutableStateOf("") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = true,
                placeholder = { Text(slot.placeholder, style = AppType.input.copy(color = Ink.textFaint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            PrimaryButton(AiTexts.SAVE, small = true, enabled = enabled && typed.isNotBlank(), onClick = { onSave(typed); typed = "" })
        }
    }
    when (slot) {
        AiKeySlot.TAVILY -> HintText(AiTexts.TAVILY_CHECK_HINT)
        AiKeySlot.GEMINI -> HintText(AiTexts.GEMINI_CHECK_HINT)
        else -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelField(p: AiProvider, s: AiSettings, onChange: (String) -> Unit) {
    FieldLabel(AiTexts.MODEL)
    AppTextField(value = s.typedModel(p), onValueChange = onChange, placeholder = p.defaultModel)
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        p.presets.forEach { preset ->
            Chip(text = preset, selected = s.model(p) == preset, onClick = { onChange(preset) }, toggle = false)
        }
    }
    HintText("비워 두면 기본 모델(${p.defaultModel})을 써요. 다른 이름을 직접 적어도 돼요.")
}
