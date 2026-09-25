package com.coffeejournal.data.backup

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BeanSummary
import com.coffeejournal.domain.model.BestRecipe
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Book
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CoffeeClass
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.MyRecipe
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.model.RoadmapItem
import com.coffeejournal.domain.model.RoadmapPhase
import com.coffeejournal.domain.model.Video
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Ids
import com.coffeejournal.domain.rules.Numbers
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs
import kotlin.math.floor

class BackupFormatException(message: String) : Exception(message)

/**
 * Reads and writes the web app's backup JSON. Writing follows the web field names exactly so the file
 * restores in the browser; reading is lenient (mixed number/string types, legacy field names, nulls).
 */
@OptIn(ExperimentalEncodingApi::class)
class BackupCodec {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }
    private val compact = Json { ignoreUnknownKeys = true; isLenient = true }
    private val base64 = Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
    private val stringList = ListSerializer(String.serializer())

    // ───────────────────────── encode ─────────────────────────

    fun encode(s: BackupSnapshot): String {
        val root = buildJsonObject {
            put("exportedAt", s.exportedAt ?: BackupDates.iso8601(Dates.nowMillis()))
            putJsonObject("data") {
                put(BackupKeys.ENTRIES, JsonArray(s.entries.map { encodeEntry(it, s) }))
                put(BackupKeys.MISC, JsonArray(s.miscItems.map { encodeMisc(it, s) }))
                put(BackupKeys.BLENDS, JsonArray(s.blends.map { encodeBlend(it) }))
                put(BackupKeys.CLASSES, JsonArray(s.classes.map { encodeClass(it) }))
                put(BackupKeys.ROADMAP, JsonArray(s.roadmap.sortedBy { it.position }.map { encodePhase(it) }))
                put(BackupKeys.MY_RECIPES, JsonArray(s.myRecipes.map { encodeRecipe(it) }))
                put(BackupKeys.BOOKS, JsonArray(s.books.map { encodeBook(it) }))
                put(BackupKeys.SUMMARIES, buildJsonObject {
                    s.beanSummaries.forEach { put(it.beanKey, buildJsonObject { put("text", it.text); put("generatedAt", it.generatedAt) }) }
                })
                put(BackupKeys.BEST, buildJsonObject { s.bestRecipes.forEach { put(it.beanKey, it.entryId) } })
                put(BackupKeys.VIDEOS, JsonArray(s.videos.map { encodeVideo(it) }))
                put(BackupKeys.PANTRY, JsonArray(s.pantryItems.map { encodePantry(it) }))
                put(BackupKeys.SETTINGS, buildJsonObject { s.settings.forEach { (k, v) -> put(k, v) } })
            }
            putJsonObject("rawData") { put(BackupKeys.DDAY, s.ddayStart?.takeIf { it.isNotBlank() }) }
            putJsonObject("photos") {
                s.entries.forEach { en ->
                    val bag = s.photosFor(en.id, PhotoKind.BAG)
                    if (bag.isNotEmpty()) put("$BAG_PREFIX${en.id}", compact.encodeToString(stringList, bag.map { dataUrl(it.bytes) }))
                    s.photosFor(en.id, PhotoKind.GROUNDS).firstOrNull()?.let { put("$JOURNAL_PREFIX${en.id}", dataUrl(it.bytes)) }
                }
            }
            putJsonObject("app") { put("name", APP_NAME); put("schema", SCHEMA) }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun encodeEntry(en: Entry, s: BackupSnapshot): JsonObject = buildJsonObject {
        val extra = en.legacyExtra
        val hasBag = en.bagPhotos.isNotEmpty() || s.photosFor(en.id, PhotoKind.BAG).isNotEmpty()
        val hasGrounds = en.groundsPhoto != null || s.photosFor(en.id, PhotoKind.GROUNDS).isNotEmpty()
        put("id", en.id)
        put("createdAt", en.createdAt)
        put("category", en.category)
        put("beanMode", en.beanMode)
        put("blendComponents", JsonArray(en.blendComponents.map { encodeBlendComponent(it) }))
        put("name", en.name)
        put("country", en.country)
        put("region", en.region)
        put("process", en.process)
        put("processOther", en.processOther)
        put("altitude", en.altitude)
        put("variety", en.variety)
        put("farmProducer", en.farmProducer)
        put("roastery", en.roastery)
        put("selection", en.selection)
        put("washingStation", en.washingStation)
        put("beanPackageType", en.packageType.ifBlank { PackageType.STANDARD })
        put("isDripBag", en.packageType == PackageType.DRIPBAG)
        put("moisture", en.moisture)
        put("density", en.density)
        put("score", en.score)
        put("arrival", en.arrival)
        put("roastDate", en.roastDate)
        put("roasterDesc", en.roasterDesc)
        put("roast", en.roast)
        put("bagWeight", en.bagWeight)
        put("price", if (en.isCupping) "" else en.price)
        put("source", if (en.isCupping) "" else BeanNames.parseNameRoastery(en.name))
        if (en.isCafe || en.cafeName.isNotBlank()) put("cafeName", en.cafeName)
        put("expectedNotes", en.expectedNotes)
        put("actualNotes", en.actualNotes)
        put("dripper", en.dripper)
        put("filter", en.filter)
        put("dose", en.dose)
        put("water", en.water)
        put("temp", en.temp)
        put("grind", en.grind)
        put("waterType", en.waterType)
        put("time", en.time)
        put("hasPhoto", hasGrounds)
        put("photoFeedback", extra?.get("photoFeedback") ?: JsonNull)
        put("hasBagPhoto", hasBag)
        put("steps", JsonArray(en.steps.map { encodeStep(it) }))
        put("recipeRef", en.recipeRef?.let { ref -> buildJsonObject { put("name", ref.name); put("steps", JsonArray(ref.steps.map { encodeStep(it) })) } } ?: JsonNull)
        for (key in listOf("beanGuidance", "consultation", "practice", "adviceChat", "noteChat")) put(key, extra?.get(key) ?: JsonNull)
        put("rating", extra?.get("rating")?.takeUnless { it is JsonNull } ?: JsonPrimitive(0))
        put("attributes", numberMap(en.attributes))
        put("attributeNotes", buildJsonObject { en.attributeNotes.forEach { (k, v) -> put(k, v) } })
        put("tags", JsonArray(en.tags.map { JsonPrimitive(it) }))
        put("notes", en.notes)
        if (en.isCupping || en.cuppingType.isNotBlank()) put("cuppingType", if (en.isCupping) en.cuppingType.ifBlank { CuppingType.PUBLIC } else en.cuppingType)
        if (en.isCupping || en.cuppingPlace.isNotBlank()) put("cuppingPlace", en.cuppingPlace)
        if (en.isCupping || en.cuppingBeans.isNotEmpty()) {
            put("cuppingBeans", en.cuppingBeans.joinToString("\n") { it.name })
            put("cuppingBeanNotes", JsonArray(en.cuppingBeans.filter { it.actualNotes.isNotBlank() }.map { b -> buildJsonObject { put("bean", b.name); put("note", b.actualNotes) } }))
            put("cuppingBeanDetails", JsonArray(en.cuppingBeans.map { encodeCuppingBean(it) }))
        }
    }

    private fun encodeCuppingBean(b: CuppingBean): JsonObject = buildJsonObject {
        put("name", b.name); put("country", b.country); put("region", b.region); put("roastery", b.roastery)
        put("farmProducer", b.farmProducer); put("altitude", b.altitude); put("variety", b.variety); put("price", b.price)
        put("rank", b.rank); put("process", b.process); put("roast", b.roast)
        put("expectedNotes", b.expectedNotes); put("actualNotes", b.actualNotes)
        put("evaluation", buildJsonObject { b.evaluation.forEach { (k, v) -> put(k, v) } })
        put("evaluationScores", numberMap(b.evaluationScores))
        put("memo", b.memo)
        put("beanMode", if (b.beanMode == BeanMode.BLEND) BeanMode.BLEND else BeanMode.SINGLE)
        put("blendComponentsText", b.blendComponentsText)
    }

    private fun encodeStep(s: RecipeStep): JsonObject = buildJsonObject { put("time", s.time); put("water", s.water); put("note", s.note); put("wait", s.wait) }
    private fun encodeBlendComponent(c: BlendComponent): JsonObject = buildJsonObject { put("name", c.name); put("grams", c.grams) }

    private fun encodeMisc(m: MiscItem, s: BackupSnapshot): JsonObject = buildJsonObject {
        val urls = s.miscPhotosFor(m.id).map { dataUrl(it.bytes) }
        put("id", m.id); put("type", m.type); put("name", m.name); put("notes", m.notes); put("since", m.since)
        put("photo", urls.firstOrNull())
        put("photos", JsonArray(urls.map { JsonPrimitive(it) }))
        put("status", m.status); put("scope", m.scope); put("location", m.location); put("favorite", m.favorite)
        put("createdAt", m.createdAt)
    }

    private fun encodeBlend(b: Blend): JsonObject = buildJsonObject {
        put("id", b.id); put("name", b.name); put("date", b.date)
        put("beans", JsonArray(b.beans.map { encodeBlendComponent(it) }))
        put("notes", b.notes); put("createdAt", b.createdAt)
    }

    private fun encodeClass(c: CoffeeClass): JsonObject = buildJsonObject {
        put("id", c.id); put("createdAt", c.createdAt); put("title", c.title); put("classType", c.classType)
        put("date", c.date); put("startDate", c.startDate); put("endDate", c.endDate); put("notes", c.notes)
    }

    private fun encodePhase(p: RoadmapPhase): JsonObject = buildJsonObject {
        put("id", p.id); put("title", p.title); put("range", p.range); put("dayStart", p.dayStart); put("dayEnd", p.dayEnd)
        put("items", JsonArray(p.items.map { i -> buildJsonObject { put("id", i.id); put("text", i.text); put("done", i.done) } }))
    }

    private fun encodeRecipe(r: MyRecipe): JsonObject = buildJsonObject {
        put("id", r.id); put("name", r.name); put("fromEntryId", r.fromEntryId); put("beanName", r.beanName); put("rating", r.rating)
        put("dose", r.dose); put("water", r.water); put("temp", r.temp); put("dripper", r.dripper); put("filter", r.filter)
        put("grind", r.grind); put("time", r.time)
        put("steps", JsonArray(r.steps.map { encodeStep(it) }))
        put("createdAt", r.createdAt)
    }

    private fun encodeBook(b: Book): JsonObject = buildJsonObject {
        put("id", b.id); put("createdAt", b.createdAt); put("title", b.title); put("author", b.author); put("status", b.status)
        put("startDate", b.startDate); put("endDate", b.endDate); put("rating", b.rating); put("notes", b.notes)
    }

    private fun encodeVideo(v: Video): JsonObject = buildJsonObject {
        put("id", v.id); put("createdAt", v.createdAt); put("title", v.title); put("channel", v.channel); put("url", v.url); put("notes", v.notes)
    }

    private fun encodePantry(p: PantryItem): JsonObject = buildJsonObject {
        put("id", p.id); put("name", p.name); put("roastery", p.roastery); put("packageType", p.packageType); put("weight", p.weight)
        put("price", p.price); put("roastLevel", p.roastLevel); put("roastDate", p.roastDate); put("purchaseDate", p.purchaseDate)
        put("peakStart", p.peakStart); put("peakEnd", p.peakEnd); put("expectedNotes", p.expectedNotes); put("notes", p.notes)
        put("status", p.status); put("openedAt", p.openedAt); put("createdAt", p.createdAt); put("sourceEntryId", p.sourceEntryId)
    }

    /** Whole numbers are written as integers (the web stores SCA scores like 8, not 8.0). */
    private fun numberMap(map: Map<String, Double>): JsonObject = buildJsonObject { map.forEach { (k, v) -> put(k, num(v)) } }
    private fun num(v: Double): JsonPrimitive = if (v == floor(v) && abs(v) < 1e15) JsonPrimitive(v.toLong()) else JsonPrimitive(v)

    fun dataUrl(bytes: ByteArray): String = "data:image/jpeg;base64," + base64.encode(bytes)

    // ───────────────────────── decode ─────────────────────────

    fun decode(text: String): BackupSnapshot {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: throw BackupFormatException("백업 파일 형식이 아니에요. (JSON을 읽을 수 없어요)")
        val data = root["data"] as? JsonObject ?: throw BackupFormatException("백업 파일 형식이 아니에요. (data 필드가 없어요)")
        val rawData = root["rawData"] as? JsonObject
        val present = buildSet {
            BackupKeys.all.forEach { key -> if (key != BackupKeys.DDAY && data[key].let { it != null && it !is JsonNull }) add(key) }
            if (rawData?.get(BackupKeys.DDAY).let { it != null && it !is JsonNull }) add(BackupKeys.DDAY)
        }
        val entries = data.arr(BackupKeys.ENTRIES).mapNotNull { (it as? JsonObject)?.let(::decodeEntry) }
        val miscPhotos = mutableListOf<PhotoBlob>()
        val miscItems = data.arr(BackupKeys.MISC).mapNotNull { (it as? JsonObject)?.let { o -> decodeMisc(o, miscPhotos) } }
        val summaries = (data[BackupKeys.SUMMARIES] as? JsonObject)?.mapNotNull { (key, v) ->
            when (v) {
                is JsonObject -> v.str("text").takeIf { it.isNotBlank() }?.let { BeanSummary(key, it, v.millis("generatedAt", 0L)) }
                is JsonPrimitive -> v.takeUnless { it is JsonNull }?.content?.takeIf { it.isNotBlank() }?.let { BeanSummary(key, it, 0L) }
                else -> null
            }
        } ?: emptyList()
        val best = (data[BackupKeys.BEST] as? JsonObject)?.mapNotNull { (key, v) ->
            val id = when (v) {
                is JsonObject -> v.str("entryId")
                is JsonPrimitive -> if (v is JsonNull) "" else v.content
                else -> ""
            }
            id.takeIf { it.isNotBlank() }?.let { BestRecipe(key, it) }
        } ?: emptyList()
        val settings = (data[BackupKeys.SETTINGS] as? JsonObject)?.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { k to it.content }
        }?.toMap() ?: emptyMap()
        return BackupSnapshot(
            entries = entries,
            photos = decodeEntryPhotos(root["photos"] as? JsonObject),
            miscItems = miscItems,
            miscPhotos = miscPhotos,
            blends = data.arr(BackupKeys.BLENDS).mapNotNull { (it as? JsonObject)?.let(::decodeBlend) },
            classes = data.arr(BackupKeys.CLASSES).mapNotNull { (it as? JsonObject)?.let(::decodeClass) },
            roadmap = data.arr(BackupKeys.ROADMAP).mapIndexedNotNull { i, it -> (it as? JsonObject)?.let { o -> decodePhase(o, i) } },
            myRecipes = data.arr(BackupKeys.MY_RECIPES).mapNotNull { (it as? JsonObject)?.let(::decodeRecipe) },
            books = data.arr(BackupKeys.BOOKS).mapNotNull { (it as? JsonObject)?.let(::decodeBook) },
            videos = data.arr(BackupKeys.VIDEOS).mapNotNull { (it as? JsonObject)?.let(::decodeVideo) },
            pantryItems = data.arr(BackupKeys.PANTRY).mapNotNull { (it as? JsonObject)?.let(::decodePantry) },
            beanSummaries = summaries,
            bestRecipes = best,
            settings = settings,
            ddayStart = rawData?.str(BackupKeys.DDAY)?.takeIf { it.isNotBlank() },
            exportedAt = root.str("exportedAt").takeIf { it.isNotBlank() },
            present = present,
        )
    }

    private fun decodeEntry(o: JsonObject): Entry {
        val id = o.str("id").ifBlank { Ids.newId() }
        val category = o.str("category").ifBlank { Category.BEAN }
        val packageType = when {
            o.str("beanPackageType") == PackageType.DRIPBAG -> PackageType.DRIPBAG
            o.str("beanPackageType") == PackageType.SAMPLE -> PackageType.SAMPLE
            o.bool("isDripBag") -> PackageType.DRIPBAG
            else -> PackageType.STANDARD
        }
        val actualNotes = firstNonBlank(o.str("actualNotes"), o.str("tastingNotes"), o.str("coffeeNotes"), o.str("myNotes"), o.str("note"))
        val farmProducer = o.str("farmProducer").ifBlank { BeanNames.formatFarmProducer(o.str("farm"), o.str("producer")) }
        val details = o.arr("cuppingBeanDetails").mapNotNull { (it as? JsonObject)?.let(::decodeCuppingBean) }.filter { it.name.isNotBlank() }
        val cuppingBeans = details.ifEmpty { legacyCuppingBeans(o) }
        val recipeRef = (o["recipeRef"] as? JsonObject)?.let { r ->
            val name = r.str("name")
            val steps = r.arr("steps").mapNotNull { (it as? JsonObject)?.let(::decodeStep) }
            if (name.isBlank() && steps.isEmpty()) null else RecipeRef(name, steps)
        }
        val legacy = buildJsonObject {
            for (key in LEGACY_KEYS) {
                val v = o[key] ?: continue
                if (v is JsonNull) continue
                if (key == "rating" && (v as? JsonPrimitive)?.content?.toDoubleOrNull() == 0.0) continue
                put(key, v)
            }
        }.takeIf { it.isNotEmpty() }
        return Entry(
            id = id,
            createdAt = o.millis("createdAt", Dates.nowMillis()),
            category = category,
            beanMode = o.str("beanMode").ifBlank { BeanMode.SINGLE },
            blendComponents = o.arr("blendComponents").mapNotNull { (it as? JsonObject)?.let(::decodeBlendComponent) },
            name = o.str("name"),
            country = o.str("country"),
            region = o.str("region"),
            altitude = o.str("altitude"),
            variety = o.str("variety"),
            farmProducer = farmProducer,
            roastery = o.str("roastery"),
            selection = o.str("selection"),
            washingStation = o.str("washingStation"),
            process = o.str("process"),
            processOther = o.str("processOther"),
            packageType = packageType,
            moisture = o.str("moisture"),
            density = o.str("density"),
            score = o.str("score"),
            arrival = o.str("arrival"),
            roastDate = o.str("roastDate"),
            roasterDesc = o.str("roasterDesc"),
            roast = o.str("roast"),
            bagWeight = o.str("bagWeight"),
            price = o.str("price"),
            cafeName = o.str("cafeName"),
            expectedNotes = o.str("expectedNotes"),
            actualNotes = actualNotes,
            dripper = o.str("dripper"),
            filter = o.str("filter"),
            dose = o.str("dose"),
            water = o.str("water"),
            temp = o.str("temp"),
            grind = o.str("grind"),
            waterType = o.str("waterType"),
            time = o.str("time"),
            notes = o.str("notes"),
            cuppingType = o.str("cuppingType"),
            cuppingPlace = o.str("cuppingPlace"),
            cuppingBeans = cuppingBeans,
            steps = o.arr("steps").mapNotNull { (it as? JsonObject)?.let(::decodeStep) },
            recipeRef = recipeRef,
            attributes = o.dblMap("attributes"),
            attributeNotes = o.strMap("attributeNotes"),
            tags = o.strList("tags"),
            bagPhotos = emptyList(),
            groundsPhoto = null,
            legacyExtra = legacy,
        )
    }

    private fun decodeCuppingBean(o: JsonObject): CuppingBean {
        val rawActual = o.str("actualNotes")
        val memoPresent = o["memo"].let { it != null && it !is JsonNull }
        val rawMemo = o.str("memo")
        val note = o.str("note")
        return CuppingBean(
            name = o.str("name"), country = o.str("country"), region = o.str("region"), roastery = o.str("roastery"),
            farmProducer = o.str("farmProducer").ifBlank { BeanNames.formatFarmProducer(o.str("farm"), o.str("producer")) },
            altitude = o.str("altitude"), variety = o.str("variety"), price = o.str("price"), rank = o.str("rank"),
            process = o.str("process"), roast = o.str("roast"), expectedNotes = o.str("expectedNotes"),
            actualNotes = rawActual.ifBlank { if (!memoPresent) note else "" },
            evaluation = o.strMap("evaluation"),
            evaluationScores = o.dblMap("evaluationScores"),
            memo = rawMemo.ifBlank { if (rawActual.isNotBlank()) note else "" },
            beanMode = if (o.str("beanMode") == BeanMode.BLEND) BeanMode.BLEND else BeanMode.SINGLE,
            blendComponentsText = o.str("blendComponentsText"),
        )
    }

    /** Old cupping records only stored bean names joined by newlines plus per-bean notes. */
    private fun legacyCuppingBeans(o: JsonObject): List<CuppingBean> {
        val names = when (val raw = o["cuppingBeans"]) {
            is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.content }
            is JsonPrimitive -> if (raw is JsonNull) emptyList() else raw.content.split('\n')
            else -> emptyList()
        }.map { it.trim() }.filter { it.isNotEmpty() }
        val notes = o.arr("cuppingBeanNotes").mapNotNull { it as? JsonObject }
        if (names.isEmpty()) return notes.mapNotNull { bn -> bn.str("bean").takeIf { it.isNotBlank() }?.let { CuppingBean(name = it, actualNotes = bn.str("note"), memo = bn.str("memo")) } }
        val byBean = notes.associateBy { it.str("bean") }
        return names.map { n -> val bn = byBean[n]; CuppingBean(name = n, actualNotes = bn?.str("note") ?: "", memo = bn?.str("memo") ?: "") }
    }

    private fun decodeStep(o: JsonObject) = RecipeStep(time = o.str("time"), water = o.str("water"), wait = o.str("wait"), note = o.str("note"))
    private fun decodeBlendComponent(o: JsonObject): BlendComponent? = o.str("name").takeIf { it.isNotBlank() || o.str("grams").isNotBlank() }?.let { BlendComponent(it, o.str("grams")) }

    private fun decodeMisc(o: JsonObject, photosOut: MutableList<PhotoBlob>): MiscItem? {
        // web loadMiscItems (script3.js 5177-5178) shows legacy 'equipment' items as kettles; storage may still hold the old type
        val type = o.str("type").let { if (it == LEGACY_EQUIPMENT) MiscType.KETTLE else it }
        if (type.isBlank()) return null
        val id = o.str("id").ifBlank { Ids.newId() }
        val urls = o.strList("photos").ifEmpty { listOfNotNull(o.str("photo").takeIf { it.isNotBlank() }) }
        urls.take(2).forEachIndexed { i, url -> decodeDataUrl(url)?.let { photosOut += PhotoBlob(id, PhotoKind.MISC, i, it) } }
        return MiscItem(
            id = id, type = type, name = o.str("name"), notes = o.str("notes"), since = o.str("since"), status = o.str("status"),
            scope = o.str("scope"), location = o.str("location"), favorite = o.bool("favorite"), photos = emptyList(),
            createdAt = o.millis("createdAt", Dates.nowMillis()),
        )
    }

    private fun decodeBlend(o: JsonObject) = Blend(
        id = o.str("id").ifBlank { Ids.newId() }, name = o.str("name"), date = o.str("date"),
        beans = o.arr("beans").mapNotNull { (it as? JsonObject)?.let(::decodeBlendComponent) }, notes = o.str("notes"),
        createdAt = o.millis("createdAt", Dates.nowMillis()),
    )

    private fun decodeClass(o: JsonObject) = CoffeeClass(
        id = o.str("id").ifBlank { Ids.newId() }, createdAt = o.millis("createdAt", Dates.nowMillis()), title = o.str("title"),
        classType = o.str("classType").ifBlank { "oneday" }, date = o.str("date"), startDate = o.str("startDate"), endDate = o.str("endDate"), notes = o.str("notes"),
    )

    private fun decodePhase(o: JsonObject, index: Int): RoadmapPhase {
        val id = o.str("id").ifBlank { "phase$index" }
        return RoadmapPhase(
            id = id, position = index, title = o.str("title"), range = o.str("range"),
            dayStart = o.int("dayStart", 0), dayEnd = o.int("dayEnd", 0),
            items = o.arr("items").mapIndexedNotNull { i, it -> (it as? JsonObject)?.let { io -> RoadmapItem(io.str("id").ifBlank { "$id-$i" }, io.str("text"), io.bool("done")) } },
        )
    }

    private fun decodeRecipe(o: JsonObject) = MyRecipe(
        id = o.str("id").ifBlank { Ids.newId() }, name = o.str("name"), fromEntryId = o.str("fromEntryId").takeIf { it.isNotBlank() },
        beanName = o.str("beanName"), rating = o.int("rating", 0), dose = o.str("dose"), water = o.str("water"), temp = o.str("temp"),
        dripper = o.str("dripper"), filter = o.str("filter"), grind = o.str("grind"), time = o.str("time"),
        steps = o.arr("steps").mapNotNull { (it as? JsonObject)?.let(::decodeStep) }, createdAt = o.millis("createdAt", Dates.nowMillis()),
    )

    private fun decodeBook(o: JsonObject) = Book(
        id = o.str("id").ifBlank { Ids.newId() }, createdAt = o.millis("createdAt", Dates.nowMillis()), title = o.str("title"), author = o.str("author"),
        status = o.str("status").ifBlank { "읽는 중" }, startDate = o.str("startDate"), endDate = o.str("endDate"), rating = o.int("rating", 0), notes = o.str("notes"),
    )

    private fun decodeVideo(o: JsonObject) = Video(
        id = o.str("id").ifBlank { Ids.newId() }, createdAt = o.millis("createdAt", Dates.nowMillis()), title = o.str("title"),
        channel = o.str("channel"), url = o.str("url"), notes = o.str("notes"),
    )

    private fun decodePantry(o: JsonObject) = PantryItem(
        id = o.str("id").ifBlank { Ids.newId() }, name = o.str("name"), roastery = o.str("roastery"), packageType = o.str("packageType").ifBlank { PackageType.STANDARD },
        weight = o.str("weight"), price = o.str("price"), roastLevel = o.str("roastLevel"), roastDate = o.str("roastDate"), purchaseDate = o.str("purchaseDate"),
        peakStart = o.str("peakStart"), peakEnd = o.str("peakEnd"), expectedNotes = o.str("expectedNotes"), notes = o.str("notes"),
        status = o.str("status").ifBlank { PantryItem.STATUS_UNOPENED }, openedAt = o.millisOrNull("openedAt"),
        createdAt = o.millis("createdAt", Dates.nowMillis()), sourceEntryId = o.str("sourceEntryId"),
    )

    private fun decodeEntryPhotos(photos: JsonObject?): List<PhotoBlob> {
        if (photos == null) return emptyList()
        val out = mutableListOf<PhotoBlob>()
        for ((key, value) in photos) {
            val raw = (value as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content ?: continue
            when {
                key.startsWith(BAG_PREFIX) -> {
                    val entryId = key.removePrefix(BAG_PREFIX)
                    val urls = if (raw.trimStart().startsWith("[")) {
                        runCatching { compact.parseToJsonElement(raw) as? JsonArray }.getOrNull()
                            ?.mapNotNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.content } ?: emptyList()
                    } else listOf(raw)
                    urls.take(2).forEachIndexed { i, url -> decodeDataUrl(url)?.let { out += PhotoBlob(entryId, PhotoKind.BAG, i, it) } }
                }
                key.startsWith(JOURNAL_PREFIX) -> decodeDataUrl(raw)?.let { out += PhotoBlob(key.removePrefix(JOURNAL_PREFIX), PhotoKind.GROUNDS, 0, it) }
            }
        }
        return out
    }

    /** Accepts `data:<mime>;base64,<payload>` or a bare base64 string. */
    fun decodeDataUrl(value: String): ByteArray? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val payload = if (trimmed.startsWith("data:")) {
            val comma = trimmed.indexOf(',')
            if (comma < 0) return null
            trimmed.substring(comma + 1)
        } else trimmed
        return runCatching { base64.decode(payload.filterNot { it.isWhitespace() }) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    // ───────────────────────── lenient accessors ─────────────────────────

    private fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? JsonArray) ?: emptyList()

    private fun JsonObject.str(key: String): String {
        val p = this[key] as? JsonPrimitive ?: return ""
        if (p is JsonNull) return ""
        return p.content
    }

    private fun JsonObject.bool(key: String): Boolean {
        val p = this[key] as? JsonPrimitive ?: return false
        if (p is JsonNull) return false
        return when (p.content.lowercase()) {
            "true" -> true
            "false", "" -> false
            else -> p.content.toDoubleOrNull()?.let { it != 0.0 && !it.isNaN() } ?: false
        }
    }

    /** Numbers are read leniently, but "NaN", "Infinity" and "1e999" count as missing. */
    private fun JsonObject.longOrNull(key: String): Long? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        val c = p.content.trim()
        c.toLongOrNull()?.let { return it }
        c.toDoubleOrNull()?.let { d -> return if (d.isFinite()) d.toLong() else null }
        Dates.parseDateTimeInput(c.take(16))?.let { return it }
        Dates.parseIsoDate(c)?.let { return Dates.startOfDayMillis(it) }
        return null
    }

    /** A timestamp; values outside years 1..9999 would make every date display throw, so they count as missing. */
    private fun JsonObject.millisOrNull(key: String): Long? = longOrNull(key)?.takeIf { it in MIN_MILLIS..MAX_MILLIS }

    private fun JsonObject.millis(key: String, default: Long): Long = millisOrNull(key) ?: default

    private fun JsonObject.int(key: String, default: Int): Int =
        longOrNull(key)?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt() ?: default

    private fun JsonObject.strList(key: String): List<String> = when (val v = this[key]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.content }
        is JsonPrimitive -> if (v is JsonNull || v.content.isBlank()) emptyList() else listOf(v.content)
        else -> emptyList()
    }

    private fun JsonObject.strMap(key: String): Map<String, String> =
        (this[key] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { k to it.content } }?.toMap() ?: emptyMap()

    /** Scores; a non-finite value ("NaN", "Infinity", "1e999") is dropped, i.e. treated as not scored. */
    private fun JsonObject.dblMap(key: String): Map<String, Double> =
        (this[key] as? JsonObject)?.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.let { Numbers.parse(it) }?.let { k to it }
        }?.toMap() ?: emptyMap()

    private fun firstNonBlank(vararg values: String): String = values.firstOrNull { it.isNotBlank() } ?: ""

    companion object {
        const val APP_NAME = "coffee-journal-mobile"
        const val SCHEMA = 1
        const val BAG_PREFIX = "bag-photo:"
        const val JOURNAL_PREFIX = "journal-photo:"
        private const val LEGACY_EQUIPMENT = "equipment"
        /** 0001-01-01T00:00Z .. 9999-12-31T23:59:59.999Z */
        private const val MIN_MILLIS = -62_135_596_800_000L
        private const val MAX_MILLIS = 253_402_300_799_999L
        /** Web-only fields kept verbatim inside Entry.legacyExtra so a re-export does not lose them. */
        val LEGACY_KEYS = listOf("beanGuidance", "photoFeedback", "adviceChat", "consultation", "practice", "noteChat", "rating")
    }
}
