package com.coffeejournal.ui.form.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.coffeejournal.ui.form.PhotoSlot
import com.coffeejournal.ui.form.RemoveButton
import com.coffeejournal.ui.platform.rememberCameraCapture
import com.coffeejournal.ui.platform.rememberImagePicker
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Ink

/** 원두 봉투 사진 2장 (web #bag-photo-section). */
@Composable
internal fun BagPhotoSection(
    slots: List<PhotoSlot>,
    photoModel: (PhotoSlot) -> Any?,
    onPick: (Int, ByteArray) -> Unit,
    onRemove: (Int) -> Unit,
) {
    slots.take(2).forEachIndexed { index, slot ->
        PhotoSlotRow(
            slot = slot,
            label = if (index == 0) "대표 사진" else "사진 2 (선택)",
            model = photoModel(slot),
            onPick = { onPick(index, it) },
            onRemove = { onRemove(index) },
        )
        Spacer(Modifier.padding(4.dp))
    }
}

@Composable
private fun PhotoSlotRow(slot: PhotoSlot, label: String, model: Any?, onPick: (ByteArray) -> Unit, onRemove: () -> Unit) {
    val pick = rememberImagePicker(maxItems = 1) { list -> list.firstOrNull()?.let(onPick) }
    val capture = rememberCameraCapture { bytes -> onPick(bytes) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (slot.hasImage && model != null) {
            Box(Modifier.size(96.dp).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)) {
                AsyncImage(model = model, contentDescription = label, modifier = Modifier.size(96.dp), contentScale = ContentScale.Crop)
            }
            RemoveButton(onRemove, label = "사진 삭제")
            Spacer(Modifier.width(8.dp))
            GhostButton("사진 변경", small = true, onClick = pick)
        } else {
            GhostButton(label, small = true, icon = AppIcons.camera, onClick = pick)
            if (capture != null) {
                Spacer(Modifier.width(8.dp))
                GhostButton("촬영", small = true, onClick = capture)
            }
        }
    }
}
