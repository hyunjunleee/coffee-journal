package com.coffeejournal.ui.form

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.rules.CvaAssessment
import com.coffeejournal.ui.nav.FormMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Arguments of the record form route, passed to the view model through Koin parameters. */
data class FormArgs(val mode: String = FormMode.EXTRACT, val entryId: String? = null, val cuppingType: String? = null)

/** Fields the form can scroll to / focus when validation fails. */
enum class FormField { NAME, BLEND_ROWS, CUPPING_BEAN_NAME }

@Serializable
data class FormError(val field: FormField?, val message: String)

enum class RecipeLauncher { CHAMPIONS, CAFE, MINE }

/** One editable row of the extraction log (web .step-row). */
@Serializable
data class StepForm(
    val time: String = "",
    val wait: String = "",
    val pour: Boolean = false,
    val water: String = "",
    val note: String = "",
) {
    /** Numbers that do not parse to a finite value ("NaN", "Infinity", "1e999") count as missing. */
    fun toStep(): RecipeStep = FormNumbers.finiteStep(
        RecipeStep(time = time.trim(), water = if (pour) water.trim() else "", wait = wait.trim(), note = note.trim())
    )

    companion object {
        fun from(step: RecipeStep): StepForm =
            StepForm(time = step.time, wait = step.wait, pour = step.water.isNotBlank(), water = step.water, note = step.note)
    }
}

/** One "섞은 원두와 사용량" row of a custom blend. */
@Serializable
data class BlendRowForm(val name: String = "", val grams: String = "")

/**
 * The green-coffee info of one bean, the fields that differ from bean to bean (web #bean-info-fields less the bag's
 * 총량 · 입고 시기 · 노트 · 설명). Bean 1 is the flat [FormState] fields; beans 2.. of a café blend are
 * [FormState.blendBeans]. In a later bean an empty [roastery], [roast] or [roastDate] is bean 1's (shown in grey).
 */
@Serializable
data class BeanForm(
    val roastery: String = "",
    val selection: String = "",
    val country: String = "",
    val region: String = "",
    val farmProducer: String = "",
    val washingStation: String = "",
    val altitude: String = "",
    val variety: String = "",
    val moisture: String = "",
    val density: String = "",
    val score: String = "",
    val process: String = "",
    val processOther: String = "",
    val processSub: String = "",
    val roast: String = "",
    val roastDate: String = "",
    /** The bean's share of the blend (%), asked once there are two beans or more. */
    val percent: String = "",
) {
    /** Nothing entered: an added block left empty is not saved. */
    val isBlank: Boolean
        get() = listOf(
            roastery, selection, country, region, farmProducer, washingStation, altitude, variety, moisture, density, score,
            process, processOther, processSub, roast, roastDate, percent,
        ).all { it.isBlank() }
}

/** One bean card of a cupping session (web .cupping-bean-card). */
@Serializable
data class CuppingBeanForm(
    val id: String = "",
    val name: String = "",
    val beanMode: String = BeanMode.SINGLE,
    val blendComponentsText: String = "",
    val country: String = "",
    val region: String = "",
    val roastery: String = "",
    val farmProducer: String = "",
    val altitude: String = "",
    val variety: String = "",
    val price: String = "",
    val rank: String = "",
    val process: String = "",
    val processOther: String = "",
    val processSub: String = "",
    val roast: String = "",
    val expectedNotes: List<String> = emptyList(),
    val expectedInput: String = "",
    val actualNotes: List<String> = emptyList(),
    val actualInput: String = "",
    val evaluation: Map<String, String> = emptyMap(),
    val evaluationScores: Map<String, Double> = emptyMap(),
    val evaluationOpen: Boolean = false,
    /** "항목별 평가" form: [ScoreForm.SCA2004] (the 8 fields above) or [ScoreForm.CVA]. */
    val scoreForm: String = ScoreForm.SCA2004,
    val cva: CvaAssessment = CvaAssessment(),
    val memo: String = "",
)

/** Which cupping form a tasting / cupping bean is scored on (feature-plan-v2 §2.3). One form per tasting is saved. */
object ScoreForm {
    const val SCA2004 = "sca2004"
    const val CVA = "cva"
    val labels = mapOf(SCA2004 to "SCA 2004", CVA to "CVA")
}

/**
 * A bag photo slot: an already stored file, a freshly picked image, or empty.
 * Only the stored file name survives process death; picked bytes are not written into the saved state.
 */
@Serializable
class PhotoSlot(val existingName: String? = null, @Transient val pending: ByteArray? = null) {
    val hasImage: Boolean get() = existingName != null || pending != null

    override fun equals(other: Any?): Boolean =
        other is PhotoSlot && other.existingName == existingName &&
            ((other.pending == null && pending == null) || (other.pending != null && pending != null && other.pending.contentEquals(pending)))

    override fun hashCode(): Int = (existingName?.hashCode() ?: 0) * 31 + (pending?.contentHashCode() ?: 0)
}

/**
 * Everything the record form edits. Kept flat so every field change is a simple copy.
 * Serializable so the view model can keep it in its SavedStateHandle across process death.
 */
@Serializable
data class FormState(
    val mode: String = FormMode.EXTRACT,
    val editingId: String? = null,
    /** Id a new record is saved under, fixed for the whole form session so a retry after a failure upserts the same row. */
    val draftId: String = "",
    val category: String = Category.BEAN,
    val packageType: String = PackageType.STANDARD,
    val createdAt: Long = 0L,
    // 커핑
    val cuppingType: String = CuppingType.PUBLIC,
    val cuppingPlace: String = "",
    val cuppingBeans: List<CuppingBeanForm> = listOf(CuppingBeanForm()),
    val cuppingNotes: String = "",
    // 원두 구성·이름
    val beanMode: String = BeanMode.SINGLE,
    val blendRows: List<BlendRowForm> = emptyList(),
    /** Beans 2.. of a café blend, one block each like bean 1's; "+ 원두 추가 (블렌드)" adds one. */
    val blendBeans: List<BeanForm> = emptyList(),
    /** Bean 1's share (%) of a café blend; bean 1's other fields are the flat ones under 원두 정보. */
    val firstBeanPercent: String = "",
    val cafeName: String = "",
    val name: String = "",
    val price: String = "",
    // 원두 정보
    val roastery: String = "",
    val selection: String = "",
    val country: String = "",
    val region: String = "",
    val farmProducer: String = "",
    val washingStation: String = "",
    val altitude: String = "",
    val variety: String = "",
    val moisture: String = "",
    val density: String = "",
    val score: String = "",
    val process: String = "",
    val processOther: String = "",
    val processSub: String = "",
    val roast: String = "",
    val bagWeight: String = "",
    val arrival: String = "",
    val roastDate: String = "",
    val expectedNotes: List<String> = emptyList(),
    val expectedInput: String = "",
    val roasterDesc: String = "",
    val bagPhotos: List<PhotoSlot> = listOf(PhotoSlot(), PhotoSlot()),
    // 레시피
    val dripper: String = "",
    val filter: String = "",
    val grind: String = "",
    val dose: String = "",
    val water: String = "",
    val temp: String = "",
    val tempHint: String = "",
    val time: String = "",
    val waterType: String = "",
    val steps: List<StepForm> = emptyList(),
    val appliedRecipeRef: RecipeRef? = null,
    // 계산기 (저장하지 않음)
    val calcOpen: Boolean = false,
    val calc: CalcForm = CalcForm(),
    // 테이스팅
    val scoreForm: String = ScoreForm.SCA2004,
    /** SCA 2004 attributes only; the CVA assessment is [cva]. */
    val attributes: Map<String, Double> = emptyMap(),
    val cva: CvaAssessment = CvaAssessment(),
    val attributeNotes: Map<String, String> = emptyMap(),
    val actualNotes: List<String> = emptyList(),
    val actualInput: String = "",
    val notes: String = "",
    // 화면 상태
    val autofillBanner: Boolean = false,
    /** An earlier record of the same bean exists: bag info and bag photos belong to that first registration (web lock). */
    val repeatBean: Boolean = false,
    val openLauncher: RecipeLauncher? = null,
    val flavorWheelOpen: Boolean = false,
    @Transient val error: FormError? = null,
    @Transient val saving: Boolean = false,
) {
    val isEdit: Boolean get() = editingId != null
    val isCupping: Boolean get() = category == Category.CUPPING
    val isCafe: Boolean get() = category == Category.CAFE
    val isBrew: Boolean get() = category == Category.BEAN
    /**
     * 원두 구성 as it is validated and saved. Cupping is always single; a custom blend left over after switching to 카페
     * (where the 원두 구성 segment and blend rows are hidden) no longer counts.
     */
    val effectiveBeanMode: String get() = when {
        isCupping -> BeanMode.SINGLE
        beanMode == BeanMode.CUSTOM_BLEND && !isCafe -> BeanMode.CUSTOM_BLEND
        // a second bean block makes a café blend; an older café blend record may have none of its beans entered
        blendBeans.isNotEmpty() || beanMode == BeanMode.COMMERCIAL_BLEND -> BeanMode.COMMERCIAL_BLEND
        else -> BeanMode.SINGLE
    }
    val isCustomBlend: Boolean get() = effectiveBeanMode == BeanMode.CUSTOM_BLEND
    /** Bean-info blocks shown: bean 1 and a café blend's other beans (a custom blend keeps one, its beans are its rows). */
    val beanCount: Int get() = if (isCupping || isCustomBlend) 1 else 1 + blendBeans.size

    /** Bean [index]'s block: bean 1 from the flat fields, the others from [blendBeans]. */
    fun bean(index: Int): BeanForm = if (index == 0) {
        BeanForm(
            roastery, selection, country, region, farmProducer, washingStation, altitude, variety, moisture, density, score,
            process, processOther, processSub, roast, roastDate, firstBeanPercent,
        )
    } else blendBeans[index - 1]

    /** The form with bean [index]'s block replaced by [b]. */
    fun withBean(index: Int, b: BeanForm): FormState = if (index == 0) {
        copy(
            roastery = b.roastery, selection = b.selection, country = b.country, region = b.region, farmProducer = b.farmProducer,
            washingStation = b.washingStation, altitude = b.altitude, variety = b.variety, moisture = b.moisture, density = b.density,
            score = b.score, process = b.process, processOther = b.processOther, processSub = b.processSub, roast = b.roast,
            roastDate = b.roastDate, firstBeanPercent = b.percent,
        )
    } else copy(blendBeans = blendBeans.mapIndexed { i, old -> if (i == index - 1) b else old })
    /** The category segment is fixed to 원두 when the form was opened from the extract tab. */
    val showCategorySeg: Boolean get() = mode != FormMode.EXTRACT
}
