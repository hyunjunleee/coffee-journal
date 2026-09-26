package com.coffeejournal.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coffeejournal.ui.form.TextLink
import com.coffeejournal.ui.platform.openUrl
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.MinTouchTarget
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.SectionLabel

/** 출처 · 라이선스: where the content and look come from, and every open-source library in the app. */
@Composable
fun AboutScreen(nav: NavHostController) {
    val libraries = platformLibraries
    val groups = remember(libraries) { LibraryGroups.byLicense(libraries) }
    // keys of the credits / licenses whose full text is open
    val open = remember { mutableStateListOf<String>() }
    fun toggle(key: String) { if (key in open) open.remove(key) else open.add(key) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitleBar("출처 · 라이선스", onBack = { nav.popBackStack() })
        LazyColumn(
            Modifier.fillMaxSize().testTag("about-list"),
            contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, bottom = 96.dp),
        ) {
            item(key = "credits-label") { SectionLabel("데이터 · 디자인 출처") }
            items(Credits.all, key = { "credit-${it.title}" }) { credit ->
                CreditCard(credit, expanded = "credit-${credit.title}" in open, onToggle = { toggle("credit-${credit.title}") })
                Spacer(Modifier.height(10.dp))
            }
            item(key = "libraries-label") {
                SectionLabel("오픈소스 라이브러리", hint = "${libraries.size}개")
                Text(
                    "이 앱에 들어간 라이브러리와 라이선스예요. 목록은 빌드할 때 앱의 의존성에서 자동으로 만들어지고, 누르면 프로젝트 페이지가 열려요.",
                    style = AppType.bodyMuted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (groups.isEmpty()) {
                item(key = "libraries-empty") { EmptyNote("이 기기용 라이브러리 목록이 아직 만들어지지 않았어요.") }
            }
            groups.forEach { group ->
                item(key = "license-${group.spdx}") {
                    LicenseHeader(group, expanded = "license-${group.spdx}" in open, onToggle = { toggle("license-${group.spdx}") })
                }
                items(group.libraries, key = { "lib-${group.spdx}-${it.group}:${it.artifact}" }) { LibraryRow(it) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreditCard(credit: Credit, expanded: Boolean, onToggle: () -> Unit) {
    HairlineCard {
        Text(credit.title, style = AppType.cardTitle)
        Text(credit.body, style = AppType.bodyMuted, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            credit.links.forEach { (label, url) -> TextLink("$label ↗", Ink.textMuted, { openUrl(url) }) }
            if (credit.licenseText != null) TextLink(if (expanded) "라이선스 접기" else "라이선스 전문 보기", Ink.text, onToggle)
        }
        if (expanded && credit.licenseText != null) LicenseBody(credit.licenseText)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LicenseHeader(group: LicenseGroup, expanded: Boolean, onToggle: () -> Unit) {
    val text = LicenseTexts.text(group.spdx)
    val url = LicenseTexts.url(group.spdx)
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text("${LicenseTexts.title(group.spdx)} · ${group.libraries.size}개", style = AppType.cardTitle)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (text != null) TextLink(if (expanded) "전문 접기" else "전문 보기", Ink.text, onToggle)
            if (url != null) TextLink("라이선스 페이지 ↗", Ink.textMuted, { openUrl(url) })
        }
        if (expanded && text != null) LicenseBody(text)
        Hairline(color = Ink.text)
    }
}

@Composable
private fun LicenseBody(text: String) {
    Text(text.trim('\n'), style = AppType.small, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
}

@Composable
private fun LibraryRow(lib: Library) {
    val url = lib.url
    Column(
        Modifier.fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .then(if (url != null) Modifier.clickable(role = Role.Button, onClickLabel = "프로젝트 페이지 열기") { openUrl(url) } else Modifier)
            .padding(vertical = 6.dp),
    ) {
        Text(lib.name, style = AppType.body)
        Text("${lib.group}:${lib.artifact}:${lib.version}", style = AppType.monoSmall)
    }
    Hairline()
}
