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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.ui.form.sections.BagPhotoSection
import com.coffeejournal.ui.form.sections.BasicSection
import com.coffeejournal.ui.form.sections.BeanIdentitySection
import com.coffeejournal.ui.form.sections.BeanInfoSection
import com.coffeejournal.ui.form.sections.CuppingSection
import com.coffeejournal.ui.form.sections.RecipeLauncherSection
import com.coffeejournal.ui.form.sections.RecipeSection
import com.coffeejournal.ui.form.sections.TastingSection
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Route.RecordForm — the record input form in 원두 / 카페 / 커핑 mode, new or editing. */
@Composable
fun RecordFormScreen(nav: NavHostController, mode: String, entryId: String?, cuppingType: String?) {
    val vm = koinViewModel<RecordFormViewModel> { parametersOf(FormArgs(mode, entryId, cuppingType)) }
    val state by vm.state.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val nameFocus = remember { FocusRequester() }
    val blendFocus = remember { FocusRequester() }
    val cuppingFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                is FormEvent.Saved -> { nav.popBackStack(); nav.navigate(Route.EntryDetail(ev.entryId)) }
                FormEvent.NotFound -> nav.popBackStack()
            }
        }
    }
    LaunchedEffect(state.error) {
        val field = state.error?.field ?: return@LaunchedEffect
        val requester = when (field) {
            FormField.NAME -> nameFocus
            FormField.BLEND_ROWS -> blendFocus
            FormField.CUPPING_BEAN_NAME -> cuppingFocus
        }
        runCatching { requester.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(Ink.bg).statusBarsPadding()) {
        ScreenTitleBar(title = if (state.isEdit) "기록 수정" else "새 기록", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
            if (loaded) {
                RecordFormBody(state, suggestions, vm, nav, nameFocus, blendFocus, cuppingFocus)
            }
            Spacer(Modifier.height(96.dp))
        }
        FormActions(state, onSave = vm::save, onCancel = { nav.popBackStack() })
    }
}

@Composable
private fun RecordFormBody(
    state: FormState,
    suggestions: FormSuggestions,
    vm: RecordFormViewModel,
    nav: NavHostController,
    nameFocus: FocusRequester,
    blendFocus: FocusRequester,
    cuppingFocus: FocusRequester,
) {
    val update: ((FormState) -> FormState) -> Unit = vm::update
    if (state.isBrew) {
        RecipeLauncherSection(
            open = state.openLauncher,
            myRecipes = suggestions.myRecipes,
            onToggle = { l -> update { it.copy(openLauncher = if (it.openLauncher == l) null else l) } },
            onChampion = vm::applyChampion,
            onCafe = vm::applyCafeRecipe,
            onMine = vm::applyMyRecipe,
            onDeleteMine = vm::deleteMyRecipe,
            onOpenMyRecipes = { nav.navigate(Route.MyRecipes) },
        )
    }
    BasicSection(state, update)
    if (state.isCupping) {
        CuppingSection(state, suggestions, cuppingFocus, update)
        return
    }
    BeanIdentitySection(state, suggestions, nameFocus, blendFocus, vm::onNameTyped, vm::onNameBlur, update)
    BeanInfoSection(state, suggestions, update)
    if (!state.isCafe) BagPhotoSection(state.bagPhotos, vm::photoModel, vm::setPhoto, vm::removePhoto)
    RecipeSection(state, suggestions, update)
    TastingSection(state, update)
}

/** Sticky bottom bar (web .form-actions). */
@Composable
private fun FormActions(state: FormState, onSave: () -> Unit, onCancel: () -> Unit) {
    // safeDrawing bottom = max(navigation bar, keyboard): the bar stays above the IME without double padding.
    Column(Modifier.fillMaxWidth().background(Ink.bg).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
        Hairline(color = Ink.text, thickness = Dimens.rule)
        state.error?.takeIf { it.field == null }?.let { ErrorText(it.message, Modifier.padding(horizontal = Dimens.gutter)) }
        Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = when { state.saving -> "저장 중..."; state.isEdit -> "수정 저장"; else -> "저장" },
                onClick = onSave, enabled = !state.saving, modifier = Modifier.weight(1f),
            )
            GhostButton("취소", onClick = onCancel)
        }
    }
}
