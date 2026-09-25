package com.coffeejournal.ui.form

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MyRecipe
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.reference.CafeRecipes
import com.coffeejournal.domain.reference.Champions
import com.coffeejournal.domain.reference.GenericSteps
import com.coffeejournal.domain.reference.Processes
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.CuppingTypes
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.NoteCanon
import com.coffeejournal.domain.rules.Packages
import com.coffeejournal.domain.rules.Prices
import com.coffeejournal.domain.rules.RecipeSteps
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.nav.FormMode

/**
 * Pure conversions between [FormState] and [Entry], ported from the web save-entry / editEntry handlers.
 * No I/O here so every rule is unit-testable.
 */
internal object FormMapper {
    const val PROCESS_OTHER = "기타"
    const val DEFAULT_BAG_WEIGHT = "100"

    // ---------- opening ----------

    fun newState(mode: String, cuppingType: String?, now: Long, lastGrind: String = "", lastWaterType: String = ""): FormState {
        val category = when (mode) {
            FormMode.CAFE -> Category.CAFE
            FormMode.CUPPING -> Category.CUPPING
            else -> Category.BEAN
        }
        val brewLike = mode != FormMode.CAFE
        return FormState(
            mode = mode,
            category = category,
            createdAt = now,
            cuppingType = cuppingType?.takeIf { it in CuppingType.all } ?: CuppingType.PUBLIC,
            bagWeight = DEFAULT_BAG_WEIGHT,
            grind = if (brewLike) lastGrind else "",
            waterType = if (brewLike) lastWaterType else "",
            steps = if (brewLike) GenericSteps.example.map(StepForm::from) else emptyList(),
            attributes = ScaScoring.defaultAttributes(),
        ).let(::withStepsTime)
    }

    fun fromEntry(entry: Entry, mode: String): FormState {
        val isCupping = entry.isCupping
        val (seg, sub, other) = splitProcess(entry.process, entry.processOther)
        val beanMode = entry.beanMode.ifBlank { if (entry.blendComponents.isNotEmpty()) BeanMode.CUSTOM_BLEND else BeanMode.SINGLE }
        val rows = entry.blendComponents.map { BlendRowForm(it.name, it.grams) }
        return FormState(
            mode = mode,
            editingId = entry.id,
            category = entry.category.ifBlank { Category.BEAN },
            packageType = Packages.entryPackageType(entry),
            createdAt = entry.createdAt,
            cuppingType = if (isCupping) CuppingTypes.effective(entry) else entry.cuppingType.ifBlank { CuppingType.PUBLIC },
            cuppingPlace = entry.cuppingPlace,
            cuppingBeans = entry.cuppingBeans.map(::cuppingBeanForm).ifEmpty { listOf(CuppingBeanForm()) },
            cuppingNotes = if (isCupping) entry.notes else "",
            beanMode = beanMode,
            blendRows = if (beanMode == BeanMode.CUSTOM_BLEND && rows.isEmpty()) listOf(BlendRowForm(), BlendRowForm()) else rows,
            cafeName = entry.cafeName,
            name = entry.name,
            price = Prices.formatInput(entry.price),
            roastery = entry.roastery,
            selection = entry.selection,
            country = entry.country,
            region = entry.region,
            farmProducer = entry.farmProducer,
            washingStation = entry.washingStation,
            altitude = entry.altitude,
            variety = entry.variety,
            moisture = entry.moisture,
            density = entry.density,
            score = entry.score,
            process = seg,
            processSub = sub,
            processOther = other,
            roast = entry.roast,
            bagWeight = entry.bagWeight,
            arrival = entry.arrival,
            roastDate = Dates.roastDateWithYear(entry.roastDate, entry.createdAt),
            expectedNotes = NoteCanon.parseChips(entry.expectedNotes),
            roasterDesc = entry.roasterDesc,
            bagPhotos = listOf(PhotoSlot(entry.bagPhotos.getOrNull(0)), PhotoSlot(entry.bagPhotos.getOrNull(1))),
            dripper = entry.dripper,
            filter = entry.filter,
            grind = entry.grind,
            dose = entry.dose,
            water = entry.water,
            temp = entry.temp,
            time = entry.time,
            waterType = entry.waterType,
            steps = entry.steps.map(StepForm::from),
            appliedRecipeRef = entry.recipeRef,
            attributes = if (entry.attributes.any { it.value > 0 }) entry.attributes else ScaScoring.defaultAttributes(),
            attributeNotes = entry.attributeNotes,
            actualNotes = NoteCanon.parseChips(entry.actualNotes),
            notes = if (isCupping) "" else entry.notes,
        )
    }

    // ---------- saving ----------

    /** Text still sitting in a chip input box is committed before validation / saving (web save-entry). */
    fun commitPendingChips(state: FormState): FormState = state.copy(
        expectedNotes = if (state.expectedInput.isNotBlank()) NoteCanon.addChips(state.expectedNotes, state.expectedInput) else state.expectedNotes,
        expectedInput = "",
        actualNotes = if (state.actualInput.isNotBlank()) NoteCanon.addChips(state.actualNotes, state.actualInput) else state.actualNotes,
        actualInput = "",
        cuppingBeans = state.cuppingBeans.map { b ->
            b.copy(
                expectedNotes = if (b.expectedInput.isNotBlank()) NoteCanon.addChips(b.expectedNotes, b.expectedInput) else b.expectedNotes,
                expectedInput = "",
                actualNotes = if (b.actualInput.isNotBlank()) NoteCanon.addChips(b.actualNotes, b.actualInput) else b.actualNotes,
                actualInput = "",
            )
        },
    )

    fun validate(state: FormState): FormError? {
        if (state.isCupping) {
            if (state.cuppingBeans.none { it.name.isNotBlank() }) return FormError(FormField.CUPPING_BEAN_NAME, "원두 이름을 하나 이상 입력해 주세요.")
            return null
        }
        if (state.isCustomBlend && state.blendRows.count { it.name.isNotBlank() } < 2) {
            return FormError(FormField.BLEND_ROWS, "직접 블렌드는 섞은 원두를 2개 이상 적어 주세요.")
        }
        if (state.name.isBlank() && !state.isCustomBlend) return FormError(FormField.NAME, "원두 이름을 입력해 주세요.")
        return null
    }

    /** Web save-entry: builds the record. [bagPhotos] are the final stored file names (already saved). */
    fun toEntry(state: FormState, id: String, existing: Entry?, bagPhotos: List<String>, now: Long): Entry {
        val s = commitPendingChips(state)
        val isCupping = s.isCupping
        val isCafe = s.isCafe
        val isBrew = s.isBrew
        val beanMode = if (isCupping) BeanMode.SINGLE else s.beanMode.ifBlank { BeanMode.SINGLE }
        val blendComponents = if (beanMode == BeanMode.CUSTOM_BLEND) {
            s.blendRows.filter { it.name.isNotBlank() }.map { BlendComponent(it.name.trim(), it.grams.trim()) }
        } else emptyList()
        val cuppingBeans = if (isCupping) s.cuppingBeans.filter { it.name.isNotBlank() }.map(::cuppingBeanModel) else existing?.cuppingBeans ?: emptyList()
        val name = if (isCupping) {
            s.cuppingPlace.trim().ifBlank { cuppingBeans.firstOrNull()?.name ?: "" }
        } else {
            s.name.trim().ifBlank { if (beanMode == BeanMode.CUSTOM_BLEND) customBlendName(blendComponents) else "" }
        }
        val dose = if (beanMode == BeanMode.CUSTOM_BLEND) {
            val sum = blendComponents.sumOf { it.grams.trim().toDoubleOrNull() ?: 0.0 }
            if (sum > 0) Prices.trimNumber(sum) else s.dose.trim()
        } else s.dose.trim()
        return Entry(
            id = id,
            createdAt = s.createdAt.takeIf { it > 0 } ?: existing?.createdAt ?: now,
            category = s.category.ifBlank { Category.BEAN },
            beanMode = beanMode,
            blendComponents = blendComponents,
            name = name,
            country = s.country.trim(),
            region = s.region.trim(),
            altitude = s.altitude.trim(),
            variety = s.variety.trim(),
            farmProducer = s.farmProducer.trim(),
            roastery = s.roastery.trim(),
            selection = s.selection.trim(),
            washingStation = s.washingStation.trim(),
            process = processValue(s.process, s.processSub),
            processOther = if (s.process == PROCESS_OTHER) s.processOther.trim() else "",
            packageType = if (isBrew) s.packageType.ifBlank { PackageType.STANDARD } else PackageType.STANDARD,
            moisture = s.moisture.trim(),
            density = s.density.trim(),
            score = s.score.trim(),
            arrival = s.arrival.trim(),
            roastDate = s.roastDate.trim(),
            roasterDesc = s.roasterDesc.trim(),
            roast = s.roast,
            bagWeight = s.bagWeight.trim(),
            price = if (isCupping) "" else Prices.normalize(s.price),
            cafeName = if (isCafe) s.cafeName.trim() else existing?.cafeName ?: "",
            expectedNotes = NoteCanon.joinChips(s.expectedNotes),
            actualNotes = NoteCanon.joinChips(s.actualNotes),
            dripper = s.dripper.trim(),
            filter = s.filter.trim(),
            dose = dose,
            water = s.water.trim(),
            temp = s.temp.trim(),
            grind = if (isBrew) s.grind.trim() else "",
            waterType = if (isBrew) s.waterType.trim() else "",
            time = s.time.trim(),
            notes = if (isCupping) s.cuppingNotes.trim() else s.notes.trim(),
            cuppingType = if (isCupping) s.cuppingType.ifBlank { CuppingType.PUBLIC } else existing?.cuppingType ?: "",
            cuppingPlace = if (isCupping) s.cuppingPlace.trim() else existing?.cuppingPlace ?: "",
            cuppingBeans = cuppingBeans,
            steps = if (isBrew) s.steps.map { it.toStep() }.filter { !it.isEmpty } else emptyList(),
            recipeRef = if (isBrew) s.appliedRecipeRef else null,
            attributes = if (isCupping) existing?.attributes ?: emptyMap() else s.attributes.filterValues { it > 0 },
            attributeNotes = if (isCupping) existing?.attributeNotes ?: emptyMap()
            else s.attributeNotes.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() },
            tags = existing?.tags ?: emptyList(),
            bagPhotos = bagPhotos,
            groundsPhoto = existing?.groundsPhoto,
            legacyExtra = existing?.legacyExtra,
        )
    }

    /** "허니" + "더블 퍼멘티드" → "허니(더블 퍼멘티드)"; anything else is stored as the segment value. */
    fun processValue(seg: String, sub: String): String {
        val s = sub.trim()
        return if (seg in Processes.mainSegments && s.isNotEmpty()) "$seg($s)" else seg
    }

    /** Stored process → (segment, sub type, other text). Unknown free text lands in 기타 so it is not lost. */
    fun splitProcess(process: String, processOther: String): Triple<String, String, String> {
        val parsed = BeanNames.parseFarmProducer(process)
        return when {
            parsed.farm in Processes.mainSegments -> Triple(parsed.farm, parsed.producer, "")
            process == PROCESS_OTHER -> Triple(PROCESS_OTHER, "", processOther)
            process.isNotBlank() -> Triple(PROCESS_OTHER, "", process)
            else -> Triple("", "", processOther)
        }
    }

    fun customBlendName(components: List<BlendComponent>): String = components.joinToString(" + ") { it.name }

    // ---------- cupping beans ----------

    fun cuppingBeanModel(b: CuppingBeanForm): CuppingBean {
        val process = when {
            b.process == PROCESS_OTHER -> b.processOther.trim().ifBlank { PROCESS_OTHER }
            else -> processValue(b.process, b.processSub)
        }
        return CuppingBean(
            id = b.id,
            name = b.name.trim(),
            country = b.country.trim(),
            region = b.region.trim(),
            roastery = b.roastery.trim(),
            farmProducer = b.farmProducer.trim(),
            altitude = b.altitude.trim(),
            variety = b.variety.trim(),
            price = Prices.normalize(b.price),
            rank = b.rank.trim(),
            process = process,
            roast = b.roast,
            expectedNotes = NoteCanon.joinChips(b.expectedNotes),
            actualNotes = NoteCanon.joinChips(b.actualNotes),
            evaluation = b.evaluation.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() },
            evaluationScores = b.evaluationScores.filterValues { it > 0 },
            memo = b.memo.trim(),
            beanMode = if (b.beanMode == BeanMode.BLEND) BeanMode.BLEND else BeanMode.SINGLE,
            blendComponentsText = b.blendComponentsText.trim(),
        )
    }

    fun cuppingBeanForm(b: CuppingBean): CuppingBeanForm {
        val parsed = BeanNames.parseFarmProducer(b.process)
        val (seg, sub, other) = when {
            parsed.farm in Processes.mainSegments -> Triple(parsed.farm, parsed.producer, "")
            b.process.isNotBlank() -> Triple(PROCESS_OTHER, "", if (b.process == PROCESS_OTHER) "" else b.process)
            else -> Triple("", "", "")
        }
        return CuppingBeanForm(
            id = b.id,
            name = b.name,
            beanMode = if (b.beanMode == BeanMode.BLEND) BeanMode.BLEND else BeanMode.SINGLE,
            blendComponentsText = b.blendComponentsText,
            country = b.country,
            region = b.region,
            roastery = b.roastery,
            farmProducer = b.farmProducer,
            altitude = b.altitude,
            variety = b.variety,
            price = Prices.formatInput(b.price),
            rank = b.rank,
            process = seg,
            processSub = sub,
            processOther = other,
            roast = b.roast,
            expectedNotes = NoteCanon.parseChips(b.expectedNotes),
            actualNotes = NoteCanon.parseChips(b.actualNotes),
            evaluation = b.evaluation,
            evaluationScores = b.evaluationScores,
            evaluationOpen = b.evaluation.isNotEmpty() || b.evaluationScores.isNotEmpty(),
            memo = b.memo,
        )
    }

    // ---------- name helpers ----------

    /** Web name-parse-hint: "괄호에서 인식: 로스터리: … · 출처: …". */
    fun nameParenHint(name: String): String? {
        val p = BeanNames.parseNameParens(name) ?: return null
        val hints = listOfNotNull(
            p.roastery.takeIf { it.isNotBlank() }?.let { "로스터리: $it" },
            p.source.takeIf { it.isNotBlank() }?.let { "출처: $it" },
            p.farm.takeIf { it.isNotBlank() }?.let { "농장: $it" },
            p.producer.takeIf { it.isNotBlank() }?.let { "생산자: $it" },
        )
        return if (hints.isEmpty()) null else "괄호에서 인식: ${hints.joinToString(" · ")}"
    }

    /** Typing a name: the parenthesised farm/producer fills 농장(생산자) only while that field is empty. */
    fun onNameTyped(state: FormState, name: String): FormState {
        val p = BeanNames.parseNameParens(name)
        val farm = if (p != null && (p.farm.isNotBlank() || p.producer.isNotBlank()) && state.farmProducer.isBlank()) {
            BeanNames.formatFarmProducer(p.farm, p.producer)
        } else state.farmProducer
        return state.copy(name = name, farmProducer = farm, error = if (state.error?.field == FormField.NAME) null else state.error)
    }

    /**
     * Web f-name blur: pull the bean's bag info from its earliest record. Unlike the web, which overwrote
     * everything, only fields that are still empty are filled (the default bag weight counts as empty).
     */
    fun autofill(state: FormState, matches: List<Entry>): FormState {
        if (matches.isEmpty()) return state.copy(autofillBanner = false)
        val sorted = matches.sortedBy { it.createdAt }
        val first = sorted.first()
        fun registered(pick: (Entry) -> String): String = sorted.firstOrNull { pick(it).isNotBlank() }?.let(pick) ?: ""
        fun fill(current: String, value: String): String = if (current.isBlank()) value else current
        val parens = BeanNames.parseNameParens(first.name)
        var s = state.copy(
            country = fill(state.country, first.country),
            region = fill(state.region, first.region),
            altitude = fill(state.altitude, first.altitude),
            variety = fill(state.variety, first.variety),
            farmProducer = fill(state.farmProducer, first.farmProducer),
            roastery = fill(state.roastery, first.roastery.ifBlank { parens?.roastery ?: "" }),
            selection = fill(state.selection, first.selection.ifBlank { parens?.source ?: "" }),
            washingStation = fill(state.washingStation, first.washingStation),
            moisture = fill(state.moisture, first.moisture),
            density = fill(state.density, first.density),
            score = fill(state.score, first.score),
            bagWeight = if (state.bagWeight.isBlank() || state.bagWeight == DEFAULT_BAG_WEIGHT) first.bagWeight.ifBlank { state.bagWeight } else state.bagWeight,
            arrival = fill(state.arrival, first.arrival),
            roastDate = fill(state.roastDate, registered { it.roastDate }),
            roasterDesc = fill(state.roasterDesc, first.roasterDesc),
            expectedNotes = state.expectedNotes.ifEmpty { NoteCanon.parseChips(first.expectedNotes) },
            roast = fill(state.roast, registered { it.roast }),
        )
        if (s.process.isBlank()) {
            val (seg, sub, other) = splitProcess(registered { it.process }, registered { it.processOther })
            s = s.copy(process = seg, processSub = sub, processOther = other)
        }
        if (state.isBrew && state.price.isBlank()) {
            sorted.firstOrNull { it.isBrew && it.price.isNotBlank() }?.let { s = s.copy(price = Prices.formatInput(it.price)) }
        }
        val changed = s != state
        return s.copy(autofillBanner = changed)
    }

    // ---------- recipes ----------

    fun applyChampion(state: FormState, c: Champions.Champion): FormState = state.copy(
        dose = Prices.trimNumber(c.dose), water = Prices.trimNumber(c.water), temp = c.temp.toString(), tempHint = "",
        dripper = c.dripper, openLauncher = null,
    )

    fun applyCafeRecipe(state: FormState, r: CafeRecipes.Recipe): FormState = withStepsTime(
        state.copy(
            dose = r.dose, water = r.water, temp = r.temp ?: "", tempHint = r.tempRange?.let { "권장 범위: $it" } ?: "",
            dripper = r.dripper, grind = r.grind, filter = r.filter ?: state.filter, time = r.time.ifBlank { state.time },
            steps = r.steps.map(StepForm::from), appliedRecipeRef = RecipeRef(r.name, r.steps), openLauncher = null,
        )
    )

    fun applyMyRecipe(state: FormState, r: MyRecipe): FormState = withStepsTime(
        state.copy(
            dose = r.dose, water = r.water, temp = r.temp, tempHint = "", dripper = r.dripper, filter = r.filter, grind = r.grind,
            time = r.time.ifBlank { state.time }, steps = r.steps.map(StepForm::from),
            appliedRecipeRef = RecipeRef(r.name, r.steps), openLauncher = null,
        )
    )

    // ---------- steps ----------

    /** Web updateStepsSummary: with a log present, 총 추출시간 always mirrors the computed value. */
    fun withStepsTime(state: FormState): FormState {
        val steps = state.steps.map { it.toStep() }.filter { !it.isEmpty }
        if (steps.isEmpty()) return state
        val computed = RecipeSteps.formatSec(RecipeSteps.summary(steps).totalTimeSec) ?: return state
        return if (computed == state.time) state else state.copy(time = computed)
    }
}
