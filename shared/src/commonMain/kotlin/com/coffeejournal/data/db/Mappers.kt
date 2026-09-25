package com.coffeejournal.data.db

import com.coffeejournal.domain.model.BeanSummary
import com.coffeejournal.domain.model.BestRecipe
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Book
import com.coffeejournal.domain.model.CoffeeClass
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MyRecipe
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.model.RoadmapItem
import com.coffeejournal.domain.model.RoadmapPhase
import com.coffeejournal.domain.model.Video
import com.coffeejournal.domain.rules.BeanNames
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal val dbJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

private val stepsSer = ListSerializer(RecipeStep.serializer())
private val blendSer = ListSerializer(BlendComponent.serializer())
private val strListSer = ListSerializer(String.serializer())
private val strMapSer = MapSerializer(String.serializer(), String.serializer())
private val dblMapSer = MapSerializer(String.serializer(), Double.serializer())
private val roadmapItemsSer = ListSerializer(RoadmapItem.serializer())

/** JSON cannot hold NaN/Infinity (encoding throws); a non-finite score is treated as not scored. */
private fun Map<String, Double>.finiteOnly(): Map<String, Double> = if (values.all { it.isFinite() }) this else filterValues { it.isFinite() }

private inline fun <T> parseOr(json: String?, default: T, block: (String) -> T): T =
    if (json.isNullOrBlank()) default else runCatching { block(json) }.getOrDefault(default)

fun Entry.toEntity(): EntryEntity = EntryEntity(
    id = id, createdAt = createdAt, category = category, beanMode = beanMode,
    blendComponentsJson = dbJson.encodeToString(blendSer, blendComponents),
    name = name, nameKey = BeanNames.coreBeanName(name), country = country, region = region, altitude = altitude,
    variety = variety, farmProducer = farmProducer, roastery = roastery, selection = selection, washingStation = washingStation,
    process = process, processOther = processOther, packageType = packageType, moisture = moisture, density = density,
    score = score, arrival = arrival, roastDate = roastDate, roasterDesc = roasterDesc, roast = roast, bagWeight = bagWeight,
    price = price, cafeName = cafeName, expectedNotes = expectedNotes, actualNotes = actualNotes, dripper = dripper,
    filter = filter, dose = dose, water = water, temp = temp, grind = grind, waterType = waterType, time = time,
    notes = notes, cuppingType = cuppingType, cuppingPlace = cuppingPlace,
    stepsJson = dbJson.encodeToString(stepsSer, steps),
    recipeRefJson = recipeRef?.let { dbJson.encodeToString(RecipeRef.serializer(), it) },
    attributesJson = dbJson.encodeToString(dblMapSer, attributes.finiteOnly()),
    attributeNotesJson = dbJson.encodeToString(strMapSer, attributeNotes),
    tagsJson = dbJson.encodeToString(strListSer, tags),
    bagPhotosJson = dbJson.encodeToString(strListSer, bagPhotos),
    groundsPhoto = groundsPhoto,
    legacyExtraJson = legacyExtra?.let { dbJson.encodeToString(JsonObject.serializer(), it) },
)

/**
 * Cupping bean rows are keyed purely by position ("<entryId>-<i>"). They are deleted and re-inserted on every save
 * and nothing refers to a bean id, so the id carried in from the form is ignored: a bean added after removing an
 * earlier one must not reuse a surviving bean's stored id, or INSERT OR REPLACE would silently drop that bean.
 */
fun Entry.toBeanEntities(): List<CuppingBeanEntity> = cuppingBeans.mapIndexed { i, b ->
    CuppingBeanEntity(
        id = "$id-$i", entryId = id, position = i, name = b.name, country = b.country, region = b.region,
        roastery = b.roastery, farmProducer = b.farmProducer, altitude = b.altitude, variety = b.variety, price = b.price,
        rank = b.rank, process = b.process, roast = b.roast, expectedNotes = b.expectedNotes, actualNotes = b.actualNotes,
        evaluationJson = dbJson.encodeToString(strMapSer, b.evaluation),
        evaluationScoresJson = dbJson.encodeToString(dblMapSer, b.evaluationScores.finiteOnly()),
        memo = b.memo, beanMode = b.beanMode, blendComponentsText = b.blendComponentsText,
    )
}

fun EntryWithBeans.toDomain(): Entry = with(entry) {
    Entry(
        id = id, createdAt = createdAt, category = category, beanMode = beanMode,
        blendComponents = parseOr(blendComponentsJson, emptyList()) { dbJson.decodeFromString(blendSer, it) },
        name = name, country = country, region = region, altitude = altitude, variety = variety, farmProducer = farmProducer,
        roastery = roastery, selection = selection, washingStation = washingStation, process = process, processOther = processOther,
        packageType = packageType, moisture = moisture, density = density, score = score, arrival = arrival, roastDate = roastDate,
        roasterDesc = roasterDesc, roast = roast, bagWeight = bagWeight, price = price, cafeName = cafeName,
        expectedNotes = expectedNotes, actualNotes = actualNotes, dripper = dripper, filter = filter, dose = dose, water = water,
        temp = temp, grind = grind, waterType = waterType, time = time, notes = notes, cuppingType = cuppingType,
        cuppingPlace = cuppingPlace,
        cuppingBeans = beans.sortedBy { it.position }.map { it.toDomain() },
        steps = parseOr(stepsJson, emptyList()) { dbJson.decodeFromString(stepsSer, it) },
        recipeRef = parseOr(recipeRefJson, null) { dbJson.decodeFromString(RecipeRef.serializer(), it) },
        attributes = parseOr(attributesJson, emptyMap()) { dbJson.decodeFromString(dblMapSer, it) },
        attributeNotes = parseOr(attributeNotesJson, emptyMap()) { dbJson.decodeFromString(strMapSer, it) },
        tags = parseOr(tagsJson, emptyList()) { dbJson.decodeFromString(strListSer, it) },
        bagPhotos = parseOr(bagPhotosJson, emptyList()) { dbJson.decodeFromString(strListSer, it) },
        groundsPhoto = groundsPhoto,
        legacyExtra = parseOr(legacyExtraJson, null) { dbJson.decodeFromString(JsonObject.serializer(), it) },
    )
}

fun CuppingBeanEntity.toDomain(): CuppingBean = CuppingBean(
    id = id, name = name, country = country, region = region, roastery = roastery, farmProducer = farmProducer,
    altitude = altitude, variety = variety, price = price, rank = rank, process = process, roast = roast,
    expectedNotes = expectedNotes, actualNotes = actualNotes,
    evaluation = parseOr(evaluationJson, emptyMap()) { dbJson.decodeFromString(strMapSer, it) },
    evaluationScores = parseOr(evaluationScoresJson, emptyMap()) { dbJson.decodeFromString(dblMapSer, it) },
    memo = memo, beanMode = beanMode, blendComponentsText = blendComponentsText,
)

fun PantryItem.toEntity() = PantryItemEntity(id, name, roastery, packageType, weight, price, roastLevel, roastDate, purchaseDate, peakStart, peakEnd, expectedNotes, notes, status, openedAt, createdAt, sourceEntryId)
fun PantryItemEntity.toDomain() = PantryItem(id, name, roastery, packageType, weight, price, roastLevel, roastDate, purchaseDate, peakStart, peakEnd, expectedNotes, notes, status, openedAt, createdAt, sourceEntryId)

fun MiscItem.toEntity() = MiscItemEntity(id, type, name, notes, since, status, scope, location, favorite, dbJson.encodeToString(strListSer, photos), createdAt)
fun MiscItemEntity.toDomain() = MiscItem(id, type, name, notes, since, status, scope, location, favorite, parseOr(photosJson, emptyList()) { dbJson.decodeFromString(strListSer, it) }, createdAt)

fun Book.toEntity() = BookEntity(id, createdAt, title, author, status, startDate, endDate, rating, notes)
fun BookEntity.toDomain() = Book(id, createdAt, title, author, status, startDate, endDate, rating, notes)

fun Video.toEntity() = VideoEntity(id, createdAt, title, channel, url, notes)
fun VideoEntity.toDomain() = Video(id, createdAt, title, channel, url, notes)

fun CoffeeClass.toEntity() = ClassEntity(id, createdAt, title, classType, date, startDate, endDate, notes)
fun ClassEntity.toDomain() = CoffeeClass(id, createdAt, title, classType, date, startDate, endDate, notes)

fun Blend.toEntity() = BlendEntity(id, name, date, dbJson.encodeToString(blendSer, beans), notes, createdAt)
fun BlendEntity.toDomain() = Blend(id, name, date, parseOr(beansJson, emptyList()) { dbJson.decodeFromString(blendSer, it) }, notes, createdAt)

fun MyRecipe.toEntity() = MyRecipeEntity(id, name, fromEntryId, beanName, rating, dose, water, temp, dripper, filter, grind, time, dbJson.encodeToString(stepsSer, steps), createdAt)
fun MyRecipeEntity.toDomain() = MyRecipe(id, name, fromEntryId, beanName, rating, dose, water, temp, dripper, filter, grind, time, parseOr(stepsJson, emptyList()) { dbJson.decodeFromString(stepsSer, it) }, createdAt)

fun RoadmapPhase.toEntity() = RoadmapPhaseEntity(id, position, title, range, dayStart, dayEnd, dbJson.encodeToString(roadmapItemsSer, items))
fun RoadmapPhaseEntity.toDomain() = RoadmapPhase(id, position, title, rangeLabel, dayStart, dayEnd, parseOr(itemsJson, emptyList()) { dbJson.decodeFromString(roadmapItemsSer, it) })

fun BeanSummaryEntity.toDomain() = BeanSummary(beanKey, text, generatedAt)
fun BeanSummary.toEntity() = BeanSummaryEntity(beanKey, text, generatedAt)
fun BestRecipeEntity.toDomain() = BestRecipe(beanKey, entryId)
fun BestRecipe.toEntity() = BestRecipeEntity(beanKey, entryId)
