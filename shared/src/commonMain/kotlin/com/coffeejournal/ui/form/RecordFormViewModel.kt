package com.coffeejournal.ui.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.MyRecipeRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.SaveEntryPipeline
import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.MyRecipe
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.reference.CafeRecipes
import com.coffeejournal.domain.reference.Champions
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.BeanRecords
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Ids
import com.coffeejournal.domain.rules.Packages
import com.coffeejournal.domain.rules.PantryRules
import com.coffeejournal.ui.form.timer.BrewTimerResult
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.deriveOffMain
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Autocomplete sources for the form (web datalists). */
data class FormSuggestions(
    val beanNames: List<String> = emptyList(),
    val blendBeanNames: List<String> = emptyList(),
    val roasteries: List<String> = emptyList(),
    val selections: List<String> = emptyList(),
    val farms: List<String> = emptyList(),
    val drippers: List<String> = emptyList(),
    val filters: List<String> = emptyList(),
    val waters: List<String> = emptyList(),
    val myRecipes: List<MyRecipe> = emptyList(),
)

sealed interface FormEvent {
    data class Saved(val entryId: String, val wasEdit: Boolean) : FormEvent
    data object NotFound : FormEvent
}

class RecordFormViewModel(
    private val args: FormArgs,
    private val entries: EntryRepository,
    private val pantry: PantryRepository,
    private val misc: MiscRepository,
    private val myRecipes: MyRecipeRepository,
    private val pipeline: SaveEntryPipeline,
    private val photos: PhotoStore,
    /** Keeps the typed form across process death (e.g. while the camera app is in front). Null in plain unit tests. */
    private val savedState: SavedStateHandle? = null,
) : ViewModel() {
    private val restored: FormState? = savedState?.get<String>(STATE_KEY)?.let(FormStateCodec::decode)

    private val _state = MutableStateFlow(restored ?: FormMapper.newState(args.mode, args.cuppingType, Dates.nowMillis()))
    val state: StateFlow<FormState> = _state.asStateFlow()

    private val _loaded = MutableStateFlow(args.entryId == null)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** The running save, so a second tap on 저장 while it is in flight does nothing. */
    private var saveJob: Job? = null

    private val _events = MutableSharedFlow<FormEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<FormEvent> = _events

    private var existing: Entry? = null
    @Volatile private var allEntries: List<Entry> = emptyList()

    /** Built off the main thread (gap #10): it flattens every record. */
    val suggestions: StateFlow<FormSuggestions> = combine(
        entries.observeAll(), pantry.observeAll(), misc.observeAll(), myRecipes.observeAll(),
    ) { ens, items, miscItems, recipes -> SuggestionSources(ens, items, miscItems, recipes) }
        .deriveOffMain { src ->
            allEntries = src.entries
            buildSuggestions(src.entries, src.pantry, src.misc, src.recipes)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FormSuggestions())

    private class SuggestionSources(val entries: List<Entry>, val pantry: List<PantryItem>, val misc: List<MiscItem>, val recipes: List<MyRecipe>)

    init {
        if (savedState != null) {
            _state.drop(1).onEach { savedState[STATE_KEY] = FormStateCodec.encode(it) }.launchIn(viewModelScope)
        }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val id = args.entryId
        if (id != null) {
            val en = entries.getById(id)
            if (en == null) { _events.emit(FormEvent.NotFound); return }
            existing = en
            if (restored == null) {
                val repeat = FormMapper.hasEarlierSameBean(en, entries.getAll())
                _state.value = FormMapper.fromEntry(en, args.mode).copy(repeatBean = repeat, autofillBanner = repeat)
            }
            _loaded.value = true
            return
        }
        if (restored != null) return
        // 최근 원두 기록의 분쇄도·사용한 물을 기본값으로 (web openForm)
        val brews = entries.getAll().filter { it.isBrew }.sortedByDescending { it.createdAt }
        val lastGrind = brews.firstOrNull { it.grind.isNotBlank() }?.grind ?: ""
        val lastWater = brews.firstOrNull { it.waterType.isNotBlank() }?.waterType ?: ""
        _state.update { s ->
            if (s.mode == com.coffeejournal.ui.nav.FormMode.CAFE) s
            else s.copy(grind = s.grind.ifBlank { lastGrind }, waterType = s.waterType.ifBlank { lastWater })
        }
    }

    /** Every field change goes through here so the computed 총 추출시간 stays in sync. */
    fun update(transform: (FormState) -> FormState) {
        _state.update { FormMapper.withStepsTime(transform(it)) }
    }

    fun onNameTyped(name: String) = update { FormMapper.onNameTyped(it, name) }

    /** Web f-name blur: same-bean records (editing record excluded) feed the empty bag-info fields. */
    fun onNameBlur() {
        val s = _state.value
        val key = BeanNames.coreBeanName(s.name)
        if (key.isBlank()) { update { it.copy(autofillBanner = false, repeatBean = false) }; return }
        viewModelScope.launch {
            val all = allEntries.ifEmpty { entries.getAll() }
            val matches = all.filter { it.id != s.editingId && BeanNames.coreBeanName(it.name) == key }
            update { FormMapper.autofill(it, matches) }
        }
    }

    fun applyChampion(c: Champions.Champion) = update { FormMapper.applyChampion(it, c) }
    fun applyCafeRecipe(r: CafeRecipes.Recipe) = update { FormMapper.applyCafeRecipe(it, r) }
    fun applyMyRecipe(r: MyRecipe) = update { FormMapper.applyMyRecipe(it, r) }
    fun deleteMyRecipe(id: String) { viewModelScope.launch { myRecipes.delete(id) } }

    /** The brew timer's rows replace the step log (the timer asked before replacing a log of the user's own). */
    fun applyTimerSteps(steps: List<RecipeStep>) = update { it.copy(steps = steps.map(StepForm::from)) }

    /** Opens the brew timer with the applied recipe, telling it whether a log of the user's own would be replaced. */
    fun timerRoute(): Route.BrewTimer {
        val s = _state.value
        return Route.BrewTimer(recipe = BrewTimerResult.encodeRecipe(s.appliedRecipeRef?.takeIf { it.steps.isNotEmpty() }), hasLog = FormMapper.hasOwnStepLog(s))
    }

    fun setPhoto(index: Int, bytes: ByteArray) = update { s ->
        s.copy(bagPhotos = s.bagPhotos.mapIndexed { i, slot -> if (i == index) PhotoSlot(existingName = null, pending = bytes) else slot })
    }

    fun removePhoto(index: Int) = update { s ->
        s.copy(bagPhotos = s.bagPhotos.mapIndexed { i, slot -> if (i == index) PhotoSlot() else slot })
    }

    fun photoModel(slot: PhotoSlot): Any? = slot.pending ?: slot.existingName?.let { "file://" + photos.pathFor(it) }

    /**
     * Saves once per tap: a tap while a save is running is ignored. A new record keeps the form's [FormState.draftId],
     * so pressing 저장 again after a failure upserts the same row instead of adding a second copy.
     */
    fun save() {
        if (saveJob?.isActive == true || _state.value.saving) return
        val committed = FormMapper.commitPendingChips(_state.value)
        val error = FormMapper.validate(committed)
        if (error != null) { _state.value = committed.copy(error = error); return }
        val s = committed.copy(error = null, saving = true, draftId = committed.draftId.ifBlank { Ids.newId() })
        _state.value = s
        // NonCancellable: leaving the screen (e.g. system back) must not stop the save between the entry write and the
        // sibling / auto-registration / pantry steps; the event then simply has no listener.
        saveJob = viewModelScope.launch { withContext(NonCancellable) { persist(s) } }
    }

    private suspend fun persist(s: FormState) {
        val created = mutableListOf<String>()
        try {
            val id = s.editingId ?: s.draftId
            val isNew = s.editingId == null
            val previous = existing?.bagPhotos ?: emptyList()
            val finalPhotos = mutableListOf<String>()
            for (slot in s.bagPhotos) {
                val pending = slot.pending
                when {
                    pending != null -> photos.save(pending).also { created += it; finalPhotos += it }
                    slot.existingName != null -> finalPhotos += slot.existingName
                }
            }
            val entry = FormMapper.toEntry(s, id, existing, finalPhotos, Dates.nowMillis())
            pipeline.save(entry, isNew)
            previous.filter { it !in finalPhotos }.forEach { runCatching { photos.delete(it) } }
            _events.emit(FormEvent.Saved(id, wasEdit = !isNew))
        } catch (e: Exception) {
            // the pending photos stay in the form, so a retry saves them again; drop this attempt's copies
            created.forEach { runCatching { photos.delete(it) } }
            _state.update { it.copy(saving = false, error = FormError(null, "저장하지 못했어요: ${e.message ?: "알 수 없는 오류"}")) }
        }
    }

    /** Web populateBeanNameDatalist: only opened, standard, non-blend, non-decaf bags — beans being drunk now, not past records. */
    private fun buildSuggestions(ens: List<Entry>, items: List<PantryItem>, miscItems: List<MiscItem>, recipes: List<MyRecipe>): FormSuggestions {
        val seen = HashSet<String>()
        val beanNames = mutableListOf<String>()
        items.filter { it.isOpened && Packages.pantryPackageType(it) == PackageType.STANDARD && !BeanNames.nameSaysBlend(it.name) && !BeanNames.isDecaf(it.name, "", "") }
            .sortedWith(compareBy<PantryItem> { PantryRules.peakStartMillis(it) }.thenByDescending { it.openedAt ?: it.createdAt })
            .forEach { item -> val k = BeanNames.coreBeanName(item.name); if (k.isNotBlank() && seen.add(k)) beanNames += item.name }

        val blendSeen = HashSet<String>()
        val blendNames = ens.filter { it.isBrew && it.beanMode != BeanMode.CUSTOM_BLEND && it.name.isNotBlank() }
            .sortedByDescending { it.createdAt }
            .mapNotNull { en -> val k = BeanNames.coreBeanName(en.name); if (k.isNotBlank() && blendSeen.add(k)) en.name else null }

        fun ownedFirst(type: String): List<String> = ownedFirstNames(miscItems, type)

        val farmSeen = HashSet<String>()
        val farms = BeanRecords.flatten(ens).filter { it.farmProducer.isNotBlank() }.sortedByDescending { it.createdAt }
            .mapNotNull { r -> val v = r.farmProducer.trim(); if (farmSeen.add(v.lowercase())) v else null }

        val waterSeen = HashSet<String>()
        val waters = mutableListOf<String>()
        ownedFirst(MiscType.WATER).forEach { if (waterSeen.add(it.trim().lowercase())) waters += it.trim() }
        ens.filter { it.waterType.isNotBlank() }.sortedByDescending { it.createdAt }
            .forEach { if (waterSeen.add(it.waterType.trim().lowercase())) waters += it.waterType.trim() }

        return FormSuggestions(
            beanNames = beanNames,
            blendBeanNames = blendNames,
            roasteries = miscItems.filter { it.type == MiscType.SOURCE }.map { it.name }.distinct(),
            selections = miscItems.filter { it.type == MiscType.SELECTION }.map { it.name }.distinct(),
            farms = farms,
            drippers = ownedFirst(MiscType.DRIPPER),
            filters = ownedFirst(MiscType.FILTER),
            waters = waters,
            myRecipes = recipes.sortedByDescending { it.createdAt },
        )
    }

    private companion object {
        const val STATE_KEY = "recordForm"
    }
}

/** Equipment names of one misc type, owned items first (web dripper-datalist / filter-datalist). */
internal fun ownedFirstNames(miscItems: List<MiscItem>, type: String): List<String> = miscItems.filter { it.type == type }
    .sortedWith(compareBy<MiscItem> { if (it.status.isBlank() || it.status == MiscStatus.OWNED) 0 else 1 }.thenByDescending { it.createdAt })
    .map { it.name }.distinct()

/** JSON form of [FormState] for the SavedStateHandle; picked photo bytes are left out (stored file names are kept). */
internal object FormStateCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(state: FormState): String = json.encodeToString(FormState.serializer(), state)

    /** Null when the text cannot be read (e.g. written by an older app version). Transient flags come back reset. */
    fun decode(text: String): FormState? = runCatching { json.decodeFromString(FormState.serializer(), text) }.getOrNull()
}
