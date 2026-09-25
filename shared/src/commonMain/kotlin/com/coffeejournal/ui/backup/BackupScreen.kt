package com.coffeejournal.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.data.backup.BackupService
import com.coffeejournal.data.backup.ExportResult
import com.coffeejournal.data.backup.ImportMode
import com.coffeejournal.data.backup.ImportResult
import com.coffeejournal.ui.platform.rememberJsonOpener
import com.coffeejournal.ui.platform.rememberJsonSaver
import com.coffeejournal.ui.platform.shareText
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.KeyValueRow
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.Seg
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BackupScreen(nav: NavHostController) {
    val vm = koinViewModel<BackupViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val saver = rememberJsonSaver { ok -> vm.onSaveResult(ok) }
    val opener = rememberJsonOpener { text -> vm.onFileLoaded(text) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitleBar("전체 데이터 백업", onBack = { nav.popBackStack() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter).padding(bottom = 96.dp)) {
            SectionLabel("백업")
            Text(
                "기록·장비·블렌드·클래스·로드맵·내 레시피·책·동영상·원두 보관함·원두 총정리·베스트 레시피·Coffee D-day와 사진을 JSON 파일 하나로 저장해요. " +
                    "웹 버전 커피 일지의 백업 파일과 서로 호환돼요. 사진이 함께 들어가서 파일이 커질 수 있어요.",
                style = AppType.bodyMuted,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    text = if (state.exporting) "백업 만드는 중..." else "💾 전체 데이터 백업",
                    enabled = !state.busy,
                    onClick = { vm.export { result -> saver(result.fileName, result.json) } },
                )
                GhostButton(
                    text = if (state.importing) "복원 중..." else "📂 백업 파일에서 복원",
                    enabled = !state.busy,
                    onClick = { opener() },
                )
            }
            if (state.busy) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Ink.accent, trackColor = Ink.surfaceRaised)
                HintText(if (state.exporting) "모든 기록과 사진을 모으는 중이에요..." else "백업 내용을 저장하는 중이에요. 이 화면을 닫지 마세요.")
            }
            state.error?.let { message ->
                Spacer(Modifier.height(12.dp))
                HairlineCard {
                    Text(if (message.startsWith("백업 중")) "백업 중 오류가 발생했어요" else "복원 중 오류가 발생했어요", style = AppType.cardTitle.copy(color = Ink.bad))
                    HintText(message)
                    Spacer(Modifier.height(8.dp))
                    GhostButton("닫기", small = true, onClick = { vm.dismissError() })
                }
            }
            state.export?.let { result -> ExportPanel(result, state.saveStatus, onSaveAgain = { saver(result.fileName, result.json) }) }
            state.importResult?.let { result -> ImportPanel(result) }
        }
    }

    state.pending?.let { pending ->
        var mode by remember { mutableStateOf(ImportMode.MERGE) }
        AlertDialog(
            onDismissRequest = { vm.cancelRestore() },
            shape = RectangleShape,
            containerColor = Ink.surface,
            titleContentColor = Ink.text,
            textContentColor = Ink.textMuted,
            title = { Text("백업 파일에서 복원", style = AppType.title) },
            text = {
                Column {
                    Text("이 백업 파일(${pending.exportedAtLabel})로 복원할까요?", style = AppType.body)
                    Spacer(Modifier.height(8.dp))
                    Text(pending.summary, style = AppType.small)
                    Spacer(Modifier.height(12.dp))
                    Seg(
                        options = listOf(ImportMode.MERGE.name, ImportMode.REPLACE.name),
                        value = mode.name,
                        onChange = { v -> mode = ImportMode.valueOf(v) },
                        allowClear = false,
                        labels = mapOf(ImportMode.MERGE.name to "병합", ImportMode.REPLACE.name to "교체"),
                    )
                    HintText(
                        if (mode == ImportMode.MERGE) "병합: 같은 id의 항목은 백업 내용으로 덮어쓰고, 나머지 데이터는 그대로 둬요."
                        else "교체: 지금 앱에 있는 데이터를 비운 뒤 이 백업 내용으로 채워요. 되돌릴 수 없어요.",
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.restore(mode) }) { Text("복원", style = AppType.body.copy(color = if (mode == ImportMode.REPLACE) Ink.bad else Ink.text)) } },
            dismissButton = { TextButton(onClick = { vm.cancelRestore() }) { Text("취소", style = AppType.body.copy(color = Ink.textMuted)) } },
        )
    }
}

@Composable
private fun ExportPanel(result: ExportResult, saveStatus: SaveStatus?, onSaveAgain: () -> Unit) {
    Spacer(Modifier.height(14.dp))
    HairlineCard {
        Text("✅ 백업 완료 — ${result.fileName}", style = AppType.cardTitle)
        Spacer(Modifier.height(8.dp))
        result.rows.forEach { (label, value) -> KeyValueRow(label, value) }
        SectionLabel("용량")
        KeyValueRow("전체 합계", BackupService.formatBytes(result.totalBytes))
        Spacer(Modifier.height(10.dp))
        HintText(
            when (saveStatus) {
                SaveStatus.SAVED -> "파일로 저장했어요. 다른 앱(웹 버전 등)에서 복원하려면 저장된 파일을 그쪽으로 옮기세요."
                SaveStatus.FAILED -> "파일 저장이 취소되었거나 실패했어요. 아래 버튼으로 다시 저장하거나 텍스트로 공유할 수 있어요."
                null -> "파일 저장 창이 열려요. 저장 위치를 고르면 백업 파일이 만들어져요."
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("다시 저장", small = true, onClick = onSaveAgain)
            GhostButton("텍스트로 공유", small = true, onClick = { shareText(result.fileName, result.json) })
        }
    }
}

@Composable
private fun ImportPanel(result: ImportResult) {
    Spacer(Modifier.height(14.dp))
    HairlineCard {
        Text("복원 결과", style = AppType.cardTitle)
        Spacer(Modifier.height(6.dp))
        result.lines.forEach { line ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(line.text(), style = AppType.small.copy(color = if (line.failed > 0) Ink.bad else if (line.skipped) Ink.textFaint else Ink.text))
            }
        }
        Spacer(Modifier.height(8.dp))
        HintText(if (result.allOk) "모두 ✓ 로 떴어요. 각 탭에서 데이터가 잘 들어왔는지 확인해보세요." else "✗ 표시된 항목은 복원되지 않았어요. 파일을 확인한 뒤 다시 시도해보세요.")
    }
}
