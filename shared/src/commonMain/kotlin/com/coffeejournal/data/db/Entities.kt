package com.coffeejournal.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "entries", indices = [Index("createdAt"), Index("category"), Index("nameKey")])
data class EntryEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val category: String,
    val beanMode: String,
    val blendComponentsJson: String,
    val name: String,
    val nameKey: String,
    val country: String,
    val region: String,
    val altitude: String,
    val variety: String,
    val farmProducer: String,
    val roastery: String,
    val selection: String,
    val washingStation: String,
    val process: String,
    val processOther: String,
    val packageType: String,
    val moisture: String,
    val density: String,
    val score: String,
    val arrival: String,
    val roastDate: String,
    val roasterDesc: String,
    val roast: String,
    val bagWeight: String,
    val price: String,
    val cafeName: String,
    val expectedNotes: String,
    val actualNotes: String,
    val dripper: String,
    val filter: String,
    val dose: String,
    val water: String,
    val temp: String,
    val grind: String,
    val waterType: String,
    val time: String,
    val notes: String,
    val cuppingType: String,
    val cuppingPlace: String,
    val stepsJson: String,
    val recipeRefJson: String?,
    val attributesJson: String,
    val attributeNotesJson: String,
    val tagsJson: String,
    val bagPhotosJson: String,
    val groundsPhoto: String?,
    val legacyExtraJson: String?,
)

@Entity(
    tableName = "cupping_beans",
    indices = [Index("entryId")],
    foreignKeys = [ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
)
data class CuppingBeanEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val position: Int,
    val name: String,
    val country: String,
    val region: String,
    val roastery: String,
    val farmProducer: String,
    val altitude: String,
    val variety: String,
    val price: String,
    val rank: String,
    val process: String,
    val roast: String,
    val expectedNotes: String,
    val actualNotes: String,
    val evaluationJson: String,
    val evaluationScoresJson: String,
    val memo: String,
    val beanMode: String,
    val blendComponentsText: String,
)

data class EntryWithBeans(
    @Embedded val entry: EntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId") val beans: List<CuppingBeanEntity>,
)

@Entity(tableName = "pantry_items", indices = [Index("status"), Index("createdAt")])
data class PantryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val roastery: String,
    val packageType: String,
    val weight: String,
    val price: String,
    val roastLevel: String,
    val roastDate: String,
    val purchaseDate: String,
    val peakStart: String,
    val peakEnd: String,
    val expectedNotes: String,
    val notes: String,
    val status: String,
    val openedAt: Long?,
    val createdAt: Long,
    val sourceEntryId: String,
)

@Entity(tableName = "misc_items", indices = [Index("type"), Index("createdAt")])
data class MiscItemEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val notes: String,
    val since: String,
    val status: String,
    val scope: String,
    val location: String,
    val favorite: Boolean,
    val photosJson: String,
    val createdAt: Long,
    /** Schema v2 (auto-migration 1 → 2): the exact map position, both or neither. */
    val lat: Double? = null,
    val lng: Double? = null,
)

/** Schema v2: where a visited café is, one row per café name (café records only carry the name). */
@Entity(tableName = "cafe_places")
data class CafePlaceEntity(
    @PrimaryKey val name: String,
    val lat: Double?,
    val lng: Double?,
    val createdAt: Long,
    /** Schema v3 (auto-migration 2 → 3): the address of the place picked from a search, else null. */
    val address: String? = null,
)

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val title: String,
    val author: String,
    val status: String,
    val startDate: String,
    val endDate: String,
    val rating: Int,
    val notes: String,
)

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val title: String,
    val channel: String,
    val url: String,
    val notes: String,
)

@Entity(tableName = "classes")
data class ClassEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val title: String,
    val classType: String,
    val date: String,
    val startDate: String,
    val endDate: String,
    val notes: String,
)

@Entity(tableName = "blends")
data class BlendEntity(
    @PrimaryKey val id: String,
    val name: String,
    val date: String,
    val beansJson: String,
    val notes: String,
    val createdAt: Long,
)

@Entity(tableName = "my_recipes")
data class MyRecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val fromEntryId: String?,
    val beanName: String,
    val rating: Int,
    val dose: String,
    val water: String,
    val temp: String,
    val dripper: String,
    val filter: String,
    val grind: String,
    val time: String,
    val stepsJson: String,
    val createdAt: Long,
)

@Entity(tableName = "roadmap_phases")
data class RoadmapPhaseEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val title: String,
    val rangeLabel: String,
    val dayStart: Int,
    val dayEnd: Int,
    val itemsJson: String,
)

@Entity(tableName = "bean_summaries")
data class BeanSummaryEntity(
    @PrimaryKey val beanKey: String,
    val text: String,
    val generatedAt: Long,
)

@Entity(tableName = "best_recipes")
data class BestRecipeEntity(
    @PrimaryKey val beanKey: String,
    val entryId: String,
)

@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String)
