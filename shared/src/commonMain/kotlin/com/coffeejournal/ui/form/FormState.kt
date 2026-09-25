package com.coffeejournal.ui.form

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.ui.nav.FormMode

/** Arguments of the record form route, passed to the view model through Koin parameters. */
data class FormArgs(val mode: String = FormMode.EXTRACT, val entryId: String? = null, val cuppingType: String? = null)

/** Fields the form can scroll to / focus when validation fails. */
enum class FormField { NAME, BLEND_ROWS, CUPPING_BEAN_NAME }

data class FormError(val field: FormField?, val message: String)

enum class RecipeLauncher { CHAMPIONS, CAFE, MINE }

/** One editable row of the extraction log (web .step-row). */
data class StepForm(
    val time: String = "",
    val wait: String = "",
    val pour: Boolean = false,
    val water: String = "",
    val note: String = "",
) {
    fun toStep(): RecipeStep = RecipeStep(time = time.trim(), water = if (pour) water.trim() else "", wait = wait.trim(), note = note.trim())

    companion object {
        fun from(step: RecipeStep): StepForm =
            StepForm(time = step.time, wait = step.wait, pour = step.water.isNotBlank(), water = step.water, note = step.note)
    }
}

/** One "섞은 원두와 사용량" row of a custom blend. */
data class BlendRowForm(val name: String = "", val grams: String = "")

/** One bean card of a cupping session (web .cupping-bean-card). */
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
    val memo: String = "",
)

/** A bag photo slot: an already stored file, a freshly picked image, or empty. */
class PhotoSlot(val existingName: String? = null, val pending: ByteArray? = null) {
    val hasImage: Boolean get() = existingName != null || pending != null

    override fun equals(other: Any?): Boolean =
        other is PhotoSlot && other.existingName == existingName &&
            ((other.pending == null && pending == null) || (other.pending != null && pending != null && other.pending.contentEquals(pending)))

    override fun hashCode(): Int = (existingName?.hashCode() ?: 0) * 31 + (pending?.contentHashCode() ?: 0)
}

/** Everything the record form edits. Kept flat so every field change is a simple copy. */
data class FormState(
    val mode: String = FormMode.EXTRACT,
    val editingId: String? = null,
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
    // 테이스팅
    val attributes: Map<String, Double> = emptyMap(),
    val attributeNotes: Map<String, String> = emptyMap(),
    val actualNotes: List<String> = emptyList(),
    val actualInput: String = "",
    val notes: String = "",
    // 화면 상태
    val autofillBanner: Boolean = false,
    val openLauncher: RecipeLauncher? = null,
    val flavorWheelOpen: Boolean = false,
    val error: FormError? = null,
    val saving: Boolean = false,
) {
    val isEdit: Boolean get() = editingId != null
    val isCupping: Boolean get() = category == Category.CUPPING
    val isCafe: Boolean get() = category == Category.CAFE
    val isBrew: Boolean get() = category == Category.BEAN
    val isCustomBlend: Boolean get() = beanMode == BeanMode.CUSTOM_BLEND
    /** The category segment is fixed to 원두 when the form was opened from the extract tab. */
    val showCategorySeg: Boolean get() = mode != FormMode.EXTRACT
}
