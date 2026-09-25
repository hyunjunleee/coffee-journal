package com.coffeejournal.ui.form.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.MyRecipe
import com.coffeejournal.domain.reference.CafeRecipes
import com.coffeejournal.domain.reference.Champions
import com.coffeejournal.domain.rules.Prices
import com.coffeejournal.ui.form.LauncherCard
import com.coffeejournal.ui.form.RecipeLauncher
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton

/** "레시피로 시작": 챔피언 / 카페 / 내 레시피 panels (web #form-recipe-launchers). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecipeLauncherSection(
    open: RecipeLauncher?,
    myRecipes: List<MyRecipe>,
    onToggle: (RecipeLauncher) -> Unit,
    onChampion: (Champions.Champion) -> Unit,
    onCafe: (CafeRecipes.Recipe) -> Unit,
    onMine: (MyRecipe) -> Unit,
    onDeleteMine: (String) -> Unit,
    onOpenMyRecipes: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text("레시피로 시작", style = AppType.sectionLabel)
        Spacer(Modifier.height(8.dp))
        // wraps instead of scrolling, so all three stay in sight on a narrow screen or with a large font
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("🏆 챔피언 레시피", small = true, onClick = { onToggle(RecipeLauncher.CHAMPIONS) })
            GhostButton("☕ 카페 레시피", small = true, onClick = { onToggle(RecipeLauncher.CAFE) })
            GhostButton("⭐ 내 레시피", small = true, onClick = { onToggle(RecipeLauncher.MINE) })
        }
        Spacer(Modifier.height(10.dp))
        when (open) {
            RecipeLauncher.CHAMPIONS -> ChampionsPanel(onChampion)
            RecipeLauncher.CAFE -> CafePanel(onCafe)
            RecipeLauncher.MINE -> MyRecipesPanel(myRecipes, onMine, onDeleteMine, onOpenMyRecipes)
            null -> {}
        }
    }
}

@Composable
private fun Intro(text: String) {
    Text(text, style = AppType.bodyMuted, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun ChampionsPanel(onApply: (Champions.Champion) -> Unit) {
    Intro("역대 월드 브루어스컵 우승자들의 실제 레시피예요. 대부분 희귀한 게이샤 등 특수한 원두 기준이라 그대로 따라 하기보다는, 비율·온도·붓는 방식의 아이디어를 참고용으로 보시면 좋아요. \"이 비율 적용\"을 누르면 새 기록 폼에 원두량/물량/온도가 채워져요.")
    Champions.all.asReversed().forEach { c ->
        LauncherCard(
            title = "${c.name} · ${c.country}", right = "WBrC ${c.year}",
            spec = "${Prices.trimNumber(c.dose)}g : ${Prices.trimNumber(c.water)}g · ${c.temp}°C · ${c.dripper}",
            desc = c.desc, applyLabel = "이 비율 적용 →", onApply = { onApply(c) }, highlight = c.highlight,
        )
    }
}

@Composable
private fun CafePanel(onApply: (CafeRecipes.Recipe) -> Unit) {
    Intro("유명 스페셜티 카페들이 공개한 브루 가이드예요. 원두나 로스팅에 따라 조정해서 쓰는 걸 전제로 한 레시피라, 참고용 출발점으로 보시면 좋아요. \"이 레시피 적용\"을 누르면 새 기록 폼에 원두량·물량·온도·붓기 단계까지 한 번에 채워져요.")
    CafeRecipes.all.forEach { c ->
        val spec = buildString {
            append("${c.dose}g : ${c.water}g")
            if (!c.temp.isNullOrBlank()) append(" · ${c.temp}°C")
            append(" · ${c.dripper}")
            if (!c.filter.isNullOrBlank()) append(" (${c.filter})")
            if (c.grind.isNotBlank()) append(" · ${c.grind}")
        }
        LauncherCard(title = c.name, right = c.place, spec = spec, desc = c.desc, applyLabel = "이 레시피 적용 →", onApply = { onApply(c) })
    }
}

@Composable
private fun MyRecipesPanel(recipes: List<MyRecipe>, onApply: (MyRecipe) -> Unit, onDelete: (String) -> Unit, onOpenMyRecipes: () -> Unit) {
    var pendingDelete by remember { mutableStateOf<MyRecipe?>(null) }
    if (recipes.isEmpty()) {
        Intro("아직 저장된 내 레시피가 없어요. 기록을 펼쳤을 때 \"⭐ 내 레시피로 저장\"을 누르거나, 아래에서 원두랑 상관없이 새로 만들어보세요.")
    } else {
        Intro("직접 저장한 나만의 레시피예요. 레시피랑 다르게 부었는데 오히려 맛있었던 추출을 기록해두거나, 원두랑 상관없이 미리 레시피를 만들어뒀다가 나중에 적용해보세요.")
    }
    GhostButton("+ 새 레시피 만들기", small = true, onClick = onOpenMyRecipes, modifier = Modifier.padding(bottom = 12.dp))
    recipes.forEach { r ->
        LauncherCard(
            title = r.name, right = if (r.rating > 0) "★".repeat(r.rating) else "",
            spec = "${r.dose.ifBlank { "?" }}g : ${r.water.ifBlank { "?" }}g · ${r.temp.ifBlank { "?" }}°C · ${r.dripper}",
            desc = r.beanName.takeIf { it.isNotBlank() }?.let { "원두: $it" }, applyLabel = "이 레시피 적용 →",
            onApply = { onApply(r) }, onDelete = { pendingDelete = r },
        )
    }
    pendingDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = RectangleShape, containerColor = Ink.bg,
            title = { Text("레시피 삭제", style = AppType.title) },
            text = { Text("\"${r.name}\" 레시피를 삭제할까요?", style = AppType.body) },
            confirmButton = { PrimaryButton("삭제", small = true, onClick = { onDelete(r.id); pendingDelete = null }) },
            dismissButton = { GhostButton("취소", small = true, onClick = { pendingDelete = null }) },
        )
    }
}
