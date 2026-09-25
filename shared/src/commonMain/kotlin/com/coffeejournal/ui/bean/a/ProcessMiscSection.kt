package com.coffeejournal.ui.bean.a

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.SectionLabel

/** Web processTab (makeFlatListTab 'process'): + 추가 inline form and the "내가 마셔본 가공 방식" cards. */
@Composable
internal fun ProcessMiscSection(
    miscItems: List<MiscItem>,
    records: List<BeanRecord>,
    onSave: (editingId: String?, name: String, notes: String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val items = remember(miscItems) { miscItems.filter { it.type == MiscType.PROCESS }.sortedByDescending { it.createdAt } }
    var formOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<MiscItem?>(null) }

    fun closeForm() { formOpen = false; editingId = null; name = ""; notes = "" }

    Column(Modifier.fillMaxWidth()) {
        if (!formOpen) {
            PrimaryButton("+ 추가", onClick = { formOpen = true })
        } else {
            HairlineCard {
                AppTextField(value = name, onValueChange = { name = it }, label = "이름", placeholder = "예: 내추럴")
                Spacer(Modifier.height(10.dp))
                AppTextField(value = notes, onValueChange = { notes = it }, label = "메모 (선택)", placeholder = "특징, 어떤 원두에서 봤는지 등", singleLine = false, minLines = 2)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(if (editingId != null) "수정 저장" else "저장", enabled = name.isNotBlank(), onClick = { onSave(editingId, name, notes); closeForm() })
                    GhostButton("취소", onClick = { closeForm() })
                }
            }
        }
        SectionLabel("내가 마셔본 가공 방식", modifier = Modifier.padding(top = 20.dp))
        if (items.isEmpty()) {
            EmptyNote("아직 등록한 게 없어요. 원두를 등록하면 자동으로도 여기 쌓여요.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { item ->
                    val breakdown = remember(records, item.name) { ProcessStats.breakdown(records, item.name, null) }
                    HairlineCard {
                        Text(item.name, style = AppType.cardTitle)
                        if (item.notes.isNotBlank()) Text(item.notes, style = AppType.bodyMuted, modifier = Modifier.padding(top = 4.dp))
                        if (breakdown.countries.isNotEmpty()) ProcessBreakdownBody(breakdown, onOpen, Modifier.padding(top = 4.dp))
                        Row(Modifier.padding(top = 12.dp)) {
                            GhostButton("수정", small = true, onClick = { formOpen = true; editingId = item.id; name = item.name; notes = item.notes })
                            Spacer(Modifier.width(8.dp))
                            GhostButton("삭제", small = true, danger = true, onClick = { pendingDelete = item })
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        ConfirmDeleteDialog(
            text = "\"${item.name}\"을(를) 목록에서 지울게요.",
            onConfirm = { onDelete(item.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}
