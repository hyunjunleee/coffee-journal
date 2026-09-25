package com.coffeejournal.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.MyRecipe
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import org.koin.compose.viewmodel.koinViewModel

/** Route.MyRecipes — saved recipes plus the "새 레시피 만들기" form (web 내 레시피 panel). */
@Composable
fun MyRecipesScreen(nav: NavHostController) {
    val vm = koinViewModel<MyRecipesViewModel>()
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MyRecipe?>(null) }

    Column(Modifier.fillMaxSize().background(Ink.bg).statusBarsPadding()) {
        ScreenTitleBar(title = "내 레시피", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
            Spacer(Modifier.height(14.dp))
            Text(
                if (recipes.isEmpty()) "아직 저장된 내 레시피가 없어요. 기록을 펼쳤을 때 \"⭐ 내 레시피로 저장\"을 누르거나, 아래에서 원두랑 상관없이 새로 만들어보세요."
                else "직접 저장한 나만의 레시피예요. 레시피랑 다르게 부었는데 오히려 맛있었던 추출을 기록해두거나, 원두랑 상관없이 미리 레시피를 만들어뒀다가 나중에 적용해보세요.",
                style = AppType.bodyMuted,
            )
            Spacer(Modifier.height(12.dp))
            GhostButton("+ 새 레시피 만들기", small = true, onClick = { showForm = !showForm })
            if (showForm) NewRecipeForm(onSave = { draft -> vm.create(draft).also { ok -> if (ok) showForm = false } }, onCancel = { showForm = false })
            Spacer(Modifier.height(16.dp))
            recipes.forEach { r ->
                RecipeCard(r, onDelete = { pendingDelete = r })
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(96.dp))
        }
    }
    pendingDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null }, shape = RectangleShape, containerColor = Ink.bg,
            title = { Text("레시피 삭제", style = AppType.title) },
            text = { Text("\"${r.name}\" 레시피를 삭제할까요?", style = AppType.body) },
            confirmButton = { PrimaryButton("삭제", small = true, onClick = { vm.delete(r.id); pendingDelete = null }) },
            dismissButton = { GhostButton("취소", small = true, onClick = { pendingDelete = null }) },
        )
    }
}

@Composable
private fun RecipeCard(r: MyRecipe, onDelete: () -> Unit) {
    HairlineCard {
        Row(Modifier.fillMaxWidth()) {
            Text(r.name, style = AppType.cardTitle, modifier = Modifier.weight(1f))
            if (r.rating > 0) Text("★".repeat(r.rating), style = AppType.monoSmall)
        }
        Text("${r.dose.ifBlank { "?" }}g : ${r.water.ifBlank { "?" }}g · ${r.temp.ifBlank { "?" }}°C · ${r.dripper}", style = AppType.monoValue, modifier = Modifier.padding(top = 4.dp))
        if (r.beanName.isNotBlank()) Text("원두: ${r.beanName}", style = AppType.bodyMuted, modifier = Modifier.padding(top = 4.dp))
        Text(if (r.steps.isEmpty()) "단계 없음" else "단계 ${r.steps.size}개", style = AppType.faint, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 8.dp)) { GhostButton("삭제", small = true, danger = true, onClick = onDelete) }
    }
}

/** Web #new-my-recipe-form: name is required, everything else optional. */
@Composable
private fun NewRecipeForm(onSave: (MyRecipeDraft) -> Boolean, onCancel: () -> Unit) {
    var d by remember { mutableStateOf(MyRecipeDraft()) }
    var nameError by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        FormTextField(d.name, { d = d.copy(name = it); nameError = null }, label = "레시피 이름", placeholder = "예: 밝은 산미용 3단 푸어", error = nameError, modifier = Modifier.padding(bottom = 10.dp))
        TwoUp(
            { m -> FormTextField(d.dripper, { d = d.copy(dripper = it) }, m, label = "드리퍼") },
            { m -> FormTextField(d.filter, { d = d.copy(filter = it) }, m, label = "필터") },
        )
        TwoUp(
            { m -> FormTextField(d.grind, { d = d.copy(grind = it) }, m, label = "분쇄도") },
            { m -> FormTextField(d.dose, { d = d.copy(dose = it) }, m, label = "원두량 (g)", keyboardType = KeyboardType.Decimal) },
        )
        TwoUp(
            { m -> FormTextField(d.water, { d = d.copy(water = it) }, m, label = "물량 (g)", keyboardType = KeyboardType.Decimal) },
            { m -> FormTextField(d.temp, { d = d.copy(temp = it) }, m, label = "물 온도 (°C)", keyboardType = KeyboardType.Decimal) },
        )
        TwoUp({ m -> FormTextField(d.time, { d = d.copy(time = it) }, m, label = "총 추출시간", placeholder = "2:10") })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("저장", onClick = { if (!onSave(d)) nameError = "레시피 이름을 입력해 주세요." }, modifier = Modifier.weight(1f))
            GhostButton("취소", onClick = onCancel)
        }
    }
}
