package com.coffeejournal.ui.guide

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.coffeejournal.ui.ai.GuideStep
import com.coffeejournal.ui.form.TextLink
import com.coffeejournal.ui.platform.openUrl
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.SectionLabel

/** The how-to sheet's own words (also read by the tests). */
object GuideTexts {
    const val CLOSE = "닫기"

    /** "Gemini API 키 받는 법 자세히 ›" for a key named "Gemini API 키". */
    fun open(keyName: String) = "$keyName 받는 법 자세히 ›"
}

/**
 * A link that opens [howTo] full screen over the screen it sits on. The sheet is not a route: closing it (닫기, the
 * back arrow or the phone's back) leaves 설정 or the picker exactly as it was, with anything typed there.
 */
@Composable
fun KeyGuideLink(howTo: KeyHowTo, label: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable(howTo.id) { mutableStateOf(false) }
    TextLink(label, Ink.text, { open = true }, modifier.testTag("key-guide-open-${howTo.id}"))
    if (open) KeyGuideSheet(howTo, onDismiss = { open = false })
}

/**
 * One key's how-to, full screen: the title with a back arrow, when it was checked, what the key is for, then each
 * section (numbered steps to follow in order, or points to read) with the pages it names as links, and 닫기 at the end.
 */
@Composable
fun KeyGuideSheet(howTo: KeyHowTo, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Ink.bg).safeDrawingPadding().testTag("key-guide")) {
            ScreenTitleBar(howTo.title, onBack = onDismiss)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
                Text(howTo.asOf, style = AppType.faint, modifier = Modifier.padding(top = 12.dp))
                Text(howTo.intro, style = AppType.body, modifier = Modifier.padding(top = 6.dp))
                howTo.sections.forEach { section ->
                    SectionLabel(section.heading, Modifier.padding(top = 20.dp))
                    section.items.forEachIndexed { i, item -> GuideItem(item, if (section.numbered) "${i + 1}." else "·", section.numbered) }
                }
                Spacer(Modifier.height(24.dp))
                GhostButton(GuideTexts.CLOSE, onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("key-guide-close"))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** One step or point: its marker ("3." or "·"), the text, and the pages it names as links under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuideItem(item: GuideStep, marker: String, numbered: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(marker, style = if (numbered) AppType.monoValue else AppType.body, modifier = Modifier.width(26.dp))
        Column(Modifier.weight(1f)) {
            Text(item.text, style = AppType.body)
            if (item.links.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                item.links.forEach { (label, url) -> TextLink("$label ↗", Ink.textMuted, { openUrl(url) }) }
            }
        }
    }
}
