package com.coffeejournal.ui.calendar.forms

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.ui.theme.AppTextField
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Web #video-form-panel: title, channel, link, memo. */
@Composable
fun VideoFormScreen(nav: NavHostController, videoId: String?) {
    val vm = koinViewModel<VideoFormViewModel>(key = "video-form-$videoId") { parametersOf(videoId) }
    val s by vm.state.collectAsStateWithLifecycle()
    FormScaffold(title = if (s.isEdit) "동영상 수정" else "동영상 추가", onBack = { nav.popBackStack() }, onSave = { vm.save { nav.popBackStack() } }) {
        AppTextField(label = "동영상 제목", value = s.title, onValueChange = { v -> vm.update { copy(title = v, titleError = false) } }, placeholder = "예: 추출 변수와 맛의 관계")
        RequiredHint(s.titleError, "동영상 제목을 입력해주세요")
        Spacer(Modifier.height(12.dp))
        AppTextField(label = "채널 · 제작자", value = s.channel, onValueChange = { v -> vm.update { copy(channel = v) } })
        Spacer(Modifier.height(12.dp))
        AppTextField(label = "링크", value = s.url, onValueChange = { v -> vm.update { copy(url = v) } }, placeholder = "https://", keyboardType = KeyboardType.Uri)
        Spacer(Modifier.height(12.dp))
        AppTextField(
            label = "메모", value = s.notes, onValueChange = { v -> vm.update { copy(notes = v) } },
            placeholder = "배운 점, 다시 볼 구간 등을 적어두세요", singleLine = false, minLines = 4,
        )
    }
}
