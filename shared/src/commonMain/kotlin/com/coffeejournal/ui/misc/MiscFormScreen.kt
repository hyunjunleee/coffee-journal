package com.coffeejournal.ui.misc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.ui.form.imeOverlapPadding
import com.coffeejournal.ui.platform.rememberCameraCapture
import com.coffeejournal.ui.platform.rememberImagePicker
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.GlyphButton
import com.coffeejournal.ui.theme.DateField
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.Seg
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MiscFormScreen(nav: NavHostController, type: String, itemId: String?) {
    val vm = koinViewModel<MiscFormViewModel> { parametersOf(type, itemId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val title = MiscListLogic.title(state.type)

    LaunchedEffect(state.done) { if (state.done) nav.popBackStack() }
    // one pop per back / 취소 even when tapped again during the exit transition
    val leave = dropUnlessResumed { nav.popBackStack() }

    Column(Modifier.fillMaxSize()) {
        ScreenTitleBar(if (state.isEdit) "$title 수정" else "$title 추가", onBack = leave)
        Column(
            Modifier.fillMaxSize().imeOverlapPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter).padding(top = 14.dp, bottom = 96.dp),
        ) {
            FieldLabel("상태")
            Seg(
                options = listOf(MiscStatus.OWNED, MiscStatus.CURIOUS),
                value = state.status,
                onChange = vm::setStatus,
                allowClear = false,
                labels = mapOf(MiscStatus.OWNED to "보유", MiscStatus.CURIOUS to "궁금한 장비"),
            )
            Spacer(Modifier.height(14.dp))
            AppTextField(value = state.name, onValueChange = vm::setName, label = "이름", placeholder = MiscListLogic.placeholder(state.type))
            Spacer(Modifier.height(14.dp))
            AppTextField(
                value = state.notes, onValueChange = vm::setNotes, label = "메모 (선택)", placeholder = MiscListLogic.notesPlaceholder(state.type),
                singleLine = false, minLines = 3,
            )
            Spacer(Modifier.height(14.dp))
            DateField(label = "사용 시작일 (선택)", value = state.since, onChange = vm::setSince)
            Spacer(Modifier.height(14.dp))
            FieldLabel("사진 (선택, 최대 2장 — 첫 번째가 대표 사진)")
            PhotoSlotRow(index = 0, slot = state.slots.getOrNull(0), emptyLabel = "대표 사진", photoPath = vm::photoPath, onPicked = { vm.setPhoto(0, it) }, onRemove = { vm.removePhoto(0) })
            Spacer(Modifier.height(8.dp))
            PhotoSlotRow(index = 1, slot = state.slots.getOrNull(1), emptyLabel = "사진 2 (선택)", photoPath = vm::photoPath, onPicked = { vm.setPhoto(1, it) }, onRemove = { vm.removePhoto(1) })
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(if (state.isEdit) "수정 저장" else "저장", enabled = state.canSave, onClick = vm::save)
                GhostButton("취소", onClick = leave)
            }
        }
    }
}

/** One upload slot (web .misc-photo-slot): preview with ✕, or gallery/camera buttons. */
@Composable
private fun PhotoSlotRow(index: Int, slot: PhotoSlot?, emptyLabel: String, photoPath: (String) -> String, onPicked: (ByteArray) -> Unit, onRemove: () -> Unit) {
    val pick = rememberImagePicker(maxItems = 1) { list -> list.firstOrNull()?.let(onPicked) }
    val capture = rememberCameraCapture { bytes -> onPicked(bytes) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (slot != null) {
            Box(Modifier.size(96.dp).background(Ink.surfaceRaised)) {
                AsyncImage(
                    model = when (slot) {
                        is PhotoSlot.Existing -> "file://" + photoPath(slot.fileName)
                        is PhotoSlot.Fresh -> slot.bytes
                    },
                    contentDescription = if (index == 0) "장비 대표 사진 미리보기" else "장비 사진 2 미리보기",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // 24dp mark, 48dp touch target around it (design §8)
                GlyphButton(
                    "✕", label = if (index == 0) "대표 사진 삭제" else "사진 2 삭제", onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Ink.accent).wrapContentSize(Alignment.Center),
                    style = AppType.small.copy(color = Ink.bg),
                )
            }
            Spacer(Modifier.width(10.dp))
            GhostButton("사진 변경", small = true, onClick = pick)
        } else {
            GhostButton(emptyLabel, small = true, icon = AppIcons.camera, onClick = pick)
            if (capture != null) {
                Spacer(Modifier.width(8.dp))
                GhostButton("촬영", small = true, onClick = capture)
            }
        }
    }
}
