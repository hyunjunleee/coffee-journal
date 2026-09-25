package com.coffeejournal.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Record categories, kept as the Korean strings the web app stores so backups round-trip. */
object Category {
    const val BEAN = "원두"
    const val CAFE = "카페"
    const val CUPPING = "커핑"
    val all = listOf(BEAN, CAFE, CUPPING)
}

object PackageType {
    const val STANDARD = "standard"
    const val DRIPBAG = "dripbag"
    const val SAMPLE = "sample"
    fun label(type: String): String = when (type) {
        DRIPBAG -> "드립백"
        SAMPLE -> "소량"
        else -> "일반 원두"
    }
}

object BeanMode {
    const val SINGLE = "single"
    const val COMMERCIAL_BLEND = "commercialBlend"
    const val CUSTOM_BLEND = "customBlend"
    /** Used inside cupping bean rows. */
    const val BLEND = "blend"
}

object CuppingType {
    const val PUBLIC = "퍼블릭"
    const val HOME = "홈커핑"
    const val CLASS = "수업"
    val all = listOf(PUBLIC, HOME, CLASS)
}

@Serializable
data class RecipeStep(
    val time: String = "",
    val water: String = "",
    val wait: String = "",
    val note: String = "",
) {
    val isPour: Boolean get() = water.isNotBlank()
    val isEmpty: Boolean get() = time.isBlank() && water.isBlank() && wait.isBlank() && note.isBlank()
}

@Serializable
data class RecipeRef(
    val name: String,
    val steps: List<RecipeStep> = emptyList(),
)

@Serializable
data class BlendComponent(
    val name: String,
    val grams: String = "",
)

/** One bean inside a cupping session (web cuppingBeanDetails[]). */
@Serializable
data class CuppingBean(
    val id: String = "",
    val name: String = "",
    val country: String = "",
    val region: String = "",
    val roastery: String = "",
    val farmProducer: String = "",
    val altitude: String = "",
    val variety: String = "",
    val price: String = "",
    val rank: String = "",
    val process: String = "",
    val roast: String = "",
    val expectedNotes: String = "",
    val actualNotes: String = "",
    val evaluation: Map<String, String> = emptyMap(),
    val evaluationScores: Map<String, Double> = emptyMap(),
    val memo: String = "",
    val beanMode: String = BeanMode.SINGLE,
    val blendComponentsText: String = "",
)

/** A journal record: home brew (원두), cafe visit (카페) or cupping session (커핑). */
data class Entry(
    val id: String,
    val createdAt: Long,
    val category: String = Category.BEAN,
    val beanMode: String = BeanMode.SINGLE,
    val blendComponents: List<BlendComponent> = emptyList(),
    val name: String = "",
    val country: String = "",
    val region: String = "",
    val altitude: String = "",
    val variety: String = "",
    val farmProducer: String = "",
    val roastery: String = "",
    val selection: String = "",
    val washingStation: String = "",
    val process: String = "",
    val processOther: String = "",
    val packageType: String = PackageType.STANDARD,
    val moisture: String = "",
    val density: String = "",
    val score: String = "",
    val arrival: String = "",
    val roastDate: String = "",
    val roasterDesc: String = "",
    val roast: String = "",
    val bagWeight: String = "",
    val price: String = "",
    val cafeName: String = "",
    val expectedNotes: String = "",
    val actualNotes: String = "",
    val dripper: String = "",
    val filter: String = "",
    val dose: String = "",
    val water: String = "",
    val temp: String = "",
    val grind: String = "",
    val waterType: String = "",
    val time: String = "",
    val notes: String = "",
    val cuppingType: String = "",
    val cuppingPlace: String = "",
    val cuppingBeans: List<CuppingBean> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val recipeRef: RecipeRef? = null,
    val attributes: Map<String, Double> = emptyMap(),
    val attributeNotes: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(),
    /** File names inside the photo store, up to two, first is the cover. */
    val bagPhotos: List<String> = emptyList(),
    val groundsPhoto: String? = null,
    /** Web-only fields preserved for backup round trips (beanGuidance, photoFeedback, adviceChat, ...). */
    val legacyExtra: JsonObject? = null,
) {
    val isCupping: Boolean get() = category == Category.CUPPING
    val isCafe: Boolean get() = category == Category.CAFE
    val isBrew: Boolean get() = category == Category.BEAN
}

data class PantryItem(
    val id: String,
    val name: String,
    val roastery: String = "",
    val packageType: String = PackageType.STANDARD,
    val weight: String = "",
    val price: String = "",
    val roastLevel: String = "",
    val roastDate: String = "",
    val purchaseDate: String = "",
    val peakStart: String = "",
    val peakEnd: String = "",
    val expectedNotes: String = "",
    val notes: String = "",
    val status: String = STATUS_UNOPENED,
    val openedAt: Long? = null,
    val createdAt: Long,
    val sourceEntryId: String = "",
) {
    val isOpened: Boolean get() = status == STATUS_OPENED
    companion object {
        const val STATUS_UNOPENED = "unopened"
        const val STATUS_OPENED = "opened"
    }
}

object MiscType {
    const val DRIPPER = "dripper"
    const val FILTER = "filter"
    const val KETTLE = "kettle"
    const val THERMOMETER = "thermometer"
    const val SCALE = "scale"
    const val WATER = "water"
    const val SOURCE = "source"      // 로스터리 / 구입처
    const val SELECTION = "selection" // 생두 수입사
    const val PROCESS = "process"
    const val VARIETY = "variety"
    const val FARM = "farm"
    val equipment = listOf(DRIPPER, FILTER, KETTLE, THERMOMETER, SCALE, WATER)
}

object MiscStatus {
    const val OWNED = "보유"
    const val CURIOUS = "궁금함"
}

object Scope {
    const val DOMESTIC = "국내"
    const val OVERSEAS = "해외"
}

/** Equipment, roasteries, importers, processes, varieties and farms share one table (web misc-items). */
data class MiscItem(
    val id: String,
    val type: String,
    val name: String,
    val notes: String = "",
    val since: String = "",
    val status: String = "",
    val scope: String = "",
    val location: String = "",
    val favorite: Boolean = false,
    val photos: List<String> = emptyList(),
    val createdAt: Long,
)

object BookStatus {
    const val WANT = "읽고 싶음"
    const val READING = "읽는 중"
    const val DONE = "완료"
    val all = listOf(WANT, READING, DONE)
}

data class Book(
    val id: String,
    val createdAt: Long,
    val title: String,
    val author: String = "",
    val status: String = BookStatus.READING,
    val startDate: String = "",
    val endDate: String = "",
    val rating: Int = 0,
    val notes: String = "",
)

data class Video(
    val id: String,
    val createdAt: Long,
    val title: String,
    val channel: String = "",
    val url: String = "",
    val notes: String = "",
)

object ClassType {
    const val ONEDAY = "oneday"
    const val RECURRING = "recurring"
}

data class CoffeeClass(
    val id: String,
    val createdAt: Long,
    val title: String,
    val classType: String = ClassType.ONEDAY,
    val date: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val notes: String = "",
)

data class Blend(
    val id: String,
    val name: String = "",
    val date: String = "",
    val beans: List<BlendComponent> = emptyList(),
    val notes: String = "",
    val createdAt: Long,
)

data class MyRecipe(
    val id: String,
    val name: String,
    val fromEntryId: String? = null,
    val beanName: String = "",
    val rating: Int = 0,
    val dose: String = "",
    val water: String = "",
    val temp: String = "",
    val dripper: String = "",
    val filter: String = "",
    val grind: String = "",
    val time: String = "",
    val steps: List<RecipeStep> = emptyList(),
    val createdAt: Long,
)

@Serializable
data class RoadmapItem(val id: String, val text: String, val done: Boolean = false)

data class RoadmapPhase(
    val id: String,
    val position: Int,
    val title: String,
    val range: String,
    val dayStart: Int,
    val dayEnd: Int,
    val items: List<RoadmapItem> = emptyList(),
)

data class BeanSummary(val beanKey: String, val text: String, val generatedAt: Long)

data class BestRecipe(val beanKey: String, val entryId: String)

/** A bean-level record used by the 원두 tab statistics: one row per bean, cupping sessions expanded. */
data class BeanRecord(
    val entryId: String,
    val parentEntryId: String?,
    val category: String,
    val createdAt: Long,
    val name: String,
    val country: String,
    val region: String,
    val farmProducer: String,
    val roastery: String,
    val selection: String,
    val altitude: String,
    val variety: String,
    val process: String,
    val processOther: String,
    val roast: String,
    val expectedNotes: String,
    val actualNotes: String,
    val notes: String,
    val beanMode: String,
    val blendComponents: List<BlendComponent>,
    val blendComponentsText: String,
    val score: String,
    val cafeName: String,
    val cuppingPlace: String,
    val cuppingType: String,
    val roasterDesc: String,
    val packageType: String,
) {
    /** Where the coffee was had: cafe name, cupping place or 집 추출. */
    val place: String
        get() = when (category) {
            Category.CAFE -> cafeName
            Category.CUPPING -> cuppingPlace
            else -> ""
        }
}
