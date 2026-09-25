package com.coffeejournal.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.backup.BackupCodec
import com.coffeejournal.data.backup.BackupService
import com.coffeejournal.data.backup.ImportMode
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.data.repo.BeanMetaRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.MyRecipeRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.RoadmapRepository
import com.coffeejournal.data.repo.SaveEntryPipeline
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.data.repo.StudyRepository
import com.coffeejournal.domain.model.Book
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.MyRecipe
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.DdayRules
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Backup export → restore into a fresh database (replace / merge), plus importing a web-app backup file. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class BackupFlowTest : FlowTestBase() {

    private val bagBytes1 = byteArrayOf(-1, -40, -1, 1, 2, 3, 4)
    private val bagBytes2 = byteArrayOf(-1, -40, -1, 9, 8, 7)
    private val groundsBytes = byteArrayOf(-1, -40, -1, 5, 5, 5)
    private val miscBytes = byteArrayOf(-1, -40, -1, 42)

    /** Sample journal plus photos, a recipe and a legacy web field so every collection has content. */
    private fun seedEverything() = runBlocking {
        SampleData.seed()
        val photos: PhotoStore = koinGet()
        val entries: EntryRepository = koinGet()
        val misc: MiscRepository = koinGet()
        val e1 = entries.getById("e1")!!
        entries.upsert(e1.copy(bagPhotos = listOf(photos.save(bagBytes1), photos.save(bagBytes2)), groundsPhoto = photos.save(groundsBytes), tags = listOf("legacy")))
        val m1 = misc.getById("m1")!!
        misc.upsert(m1.copy(photos = listOf(photos.save(miscBytes))))
        koinGet<MyRecipeRepository>().upsert(
            MyRecipe(id = "r1", name = "예가체프 91도", fromEntryId = "e2", beanName = "에티오피아 예가체프 워카 첼베사", rating = 4, dose = "15", water = "240", temp = "91",
                dripper = "V60", filter = "V60 표백", grind = "C40 24클릭", time = "2:30", steps = listOf(RecipeStep("0:00", "50", "10", "뜸")), createdAt = 1_780_000_000_000L)
        )
        koinGet<SettingsRepository>().put("last-filter", "dripbag")
        // SampleData writes records directly; register their roasteries / farms / varieties the way saving them would
        koinGet<SaveEntryPipeline>().backfill(entries.getAll())
    }

    /** Every collection with photo file names replaced by their bytes, so two databases can be compared. */
    private fun normalized(): Map<String, Any?> = runBlocking {
        val s = koinGet<BackupService>().snapshot()
        mapOf(
            "entries" to s.entries.map { it.copy(bagPhotos = emptyList(), groundsPhoto = null) }.sortedBy { it.id },
            "entryPhotos" to s.photos.map { "${it.ownerId}/${it.kind}/${it.index}=${it.bytes.toList()}" }.sorted(),
            "miscItems" to s.miscItems.map { it.copy(photos = emptyList()) }.sortedBy { it.id },
            "miscPhotos" to s.miscPhotos.map { "${it.ownerId}/${it.index}=${it.bytes.toList()}" }.sorted(),
            "blends" to s.blends.sortedBy { it.id },
            "classes" to s.classes.sortedBy { it.id },
            "roadmap" to s.roadmap.sortedBy { it.position },
            "myRecipes" to s.myRecipes.sortedBy { it.id },
            "books" to s.books.sortedBy { it.id },
            "videos" to s.videos.sortedBy { it.id },
            "pantry" to s.pantryItems.sortedBy { it.id },
            "summaries" to s.beanSummaries.sortedBy { it.beanKey },
            "best" to s.bestRecipes.sortedBy { it.beanKey },
            "settings" to s.settings,
            "ddayStart" to s.ddayStart,
        )
    }

    /** Simulates a fresh install: new in-memory database and an empty photo directory. */
    private fun freshDevice() {
        stopKoin()
        File(context.cacheDir, "photos").deleteRecursively()
        startTestKoin(context)
    }

    private fun assertSameCollections(expected: Map<String, Any?>, actual: Map<String, Any?>) {
        expected.keys.forEach { key -> assertEquals("collection '$key' after restore", expected[key], actual[key]) }
    }

    private fun exportJson(): String = runBlocking { koinGet<BackupService>().export().json }

    private fun importJson(json: String, mode: ImportMode) = runBlocking {
        val result = koinGet<BackupService>().import(koinGet<BackupCodec>().decode(json), mode)
        assertTrue("import reported failures: ${result.lines.map { it.text() }}", result.allOk)
        result
    }

    @Test
    fun backup01_exportThenReplaceIntoFreshDatabase_restoresEverything() {
        seedEverything()
        val before = normalized()
        val json = exportJson()
        freshDevice()
        importJson(json, ImportMode.REPLACE)
        assertSameCollections(before, normalized())
    }

    @Test
    fun backup02_exportThenMergeIntoFreshDatabase_restoresEverything() {
        seedEverything()
        val before = normalized()
        val json = exportJson()
        freshDevice()
        importJson(json, ImportMode.MERGE)
        assertSameCollections(before, normalized())
    }

    /**
     * User decision / design §4.1: records are always merged by id, even for 교체, which replaces only the other
     * collections. 병합 keeps everything local.
     */
    @Test
    fun backup03_replaceKeepsLocalRecordsButReplacesOtherCollections_mergeKeepsEverything() {
        seedEverything()
        val before = normalized()
        val json = exportJson()
        val localPhoto = byteArrayOf(-1, -40, -1, 77)

        fun addLocalOnlyData() = runBlocking {
            val photo = koinGet<PhotoStore>().save(localPhoto)
            koinGet<EntryRepository>().upsert(Entry(id = "local1", createdAt = 1_788_000_000_000L, name = "로컬 전용 원두", bagPhotos = listOf(photo)))
            koinGet<StudyRepository>().upsertBook(Book(id = "localBook", createdAt = 1L, title = "로컬 책"))
            koinGet<MiscRepository>().upsert(MiscItem(id = "localMisc", type = MiscType.KETTLE, name = "로컬 주전자", createdAt = 1L))
            koinGet<PantryRepository>().upsert(PantryItem(id = "localBag", name = "로컬 봉투", createdAt = 1L))
        }

        freshDevice()
        addLocalOnlyData()
        importJson(json, ImportMode.REPLACE)
        val replaced = normalized()
        @Suppress("UNCHECKED_CAST")
        val replacedEntries = replaced.getValue("entries") as List<Entry>
        assertEquals("교체 keeps the local-only record", before["entries"], replacedEntries.filter { it.id != "local1" })
        assertTrue(replacedEntries.any { it.id == "local1" })
        val local1 = runBlocking { koinGet<EntryRepository>().getById("local1")!! }
        assertTrue("its photo file is kept too", runBlocking { koinGet<PhotoStore>().readBytes(local1.bagPhotos.single()) }!!.contentEquals(localPhoto))
        assertEquals(
            "every other collection is exactly the backup's (local book, kettle and bag are gone)",
            before.filterKeys { it != "entries" && it != "entryPhotos" },
            replaced.filterKeys { it != "entries" && it != "entryPhotos" },
        )
        assertEquals(before["entryPhotos"], photosWithoutLocal1(replaced))

        freshDevice()
        addLocalOnlyData()
        importJson(json, ImportMode.MERGE)
        val merged = normalized()
        @Suppress("UNCHECKED_CAST")
        fun ids(key: String) = (merged[key] as List<Any>).map { (it as? Entry)?.id ?: (it as? Book)?.id ?: (it as? MiscItem)?.id ?: (it as PantryItem).id }.toSet()
        assertTrue(ids("entries").containsAll(setOf("local1", "e1", "e2", "e3", "e4", "e5")))
        assertTrue(ids("books").containsAll(setOf("localBook", "b1")))
        assertTrue(ids("miscItems").containsAll(setOf("localMisc", "m1", "m7")))
        assertTrue(ids("pantry").containsAll(setOf("localBag", "p1", "p2")))
        assertEquals(before["entryPhotos"], photosWithoutLocal1(merged))
    }

    @Suppress("UNCHECKED_CAST")
    private fun photosWithoutLocal1(collections: Map<String, Any?>) = (collections.getValue("entryPhotos") as List<String>).filterNot { it.startsWith("local1/") }

    @Test
    fun backup04_exportIsIdempotent_reExportAfterRestoreMatches() {
        seedEverything()
        val json = exportJson()
        freshDevice()
        importJson(json, ImportMode.REPLACE)
        val again = exportJson()
        fun data(text: String): Map<String, kotlinx.serialization.json.JsonElement?> {
            val o = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
            return listOf("data", "rawData", "photos").associateWith { o[it] }
        }
        assertEquals(data(json), data(again))
    }

    /** The whole backup screen: export through the "save as" picker, change data, restore the file with 교체. */
    @Test
    fun backup06_backupScreen_saveFileThenRestoreWithReplace() {
        seedEverything()
        val before = normalized()
        launchApp()
        clickText("💾 백업")
        waitForText("전체 데이터 백업")
        clickText("💾 전체 데이터 백업")
        waitForText("✅ 백업 완료 — " + koinGet<BackupService>().fileName())
        assertTrue("export rows list the entry count", has(hasText("entries")) && has(hasText("5")))

        val shadow = Shadows.shadowOf(compose.activity)
        val save = shadow.nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, save.intent.action)
        assertEquals(koinGet<BackupService>().fileName(), save.intent.getStringExtra(Intent.EXTRA_TITLE))
        val file = File(context.cacheDir, "ui-backup.json").apply { delete(); createNewFile() }
        compose.runOnUiThread { shadow.receiveResult(save.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
        waitForText("파일로 저장했어요. 다른 앱(웹 버전 등)에서 복원하려면 저장된 파일을 그쪽으로 옮기세요.")
        assertTrue("backup file written", file.length() > 0 && file.readText().contains("\"entries\""))

        // local changes after the backup
        runBlocking {
            koinGet<EntryRepository>().delete("e3")
            koinGet<EntryRepository>().upsert(Entry(id = "after", createdAt = Dates.nowMillis(), name = "백업 이후 원두"))
            koinGet<StudyRepository>().deleteBook("b1")
        }

        clickText("📂 백업 파일에서 복원")
        val open = shadow.nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, open.intent.action)
        compose.runOnUiThread { shadow.receiveResult(open.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
        waitForText("백업 파일에서 복원")
        clickNode(hasText("교체") and hasClickAction())
        clickNode(dialogButton("복원"))
        waitForText("복원 결과")
        waitForText("모두 ✓ 로 떴어요. 각 탭에서 데이터가 잘 들어왔는지 확인해보세요.")
        val after = normalized()
        // records are merged by id even for 교체: e3 comes back, the record made after the backup stays
        @Suppress("UNCHECKED_CAST")
        val restoredEntries = after.getValue("entries") as List<Entry>
        assertEquals(before["entries"], restoredEntries.filter { it.id != "after" })
        assertTrue(restoredEntries.any { it.id == "after" })
        assertSameCollections(before.filterKeys { it != "entries" }, after)
    }

    /** miscBackup-8: the error panel shows its title once and only the message under it (web 7963-7964). */
    @Test
    fun backup07_unreadableFile_showsTitleOnceAndOnlyTheMessage() {
        launchApp()
        clickText("💾 백업")
        waitForText("전체 데이터 백업")
        clickText("📂 백업 파일에서 복원")
        val shadow = Shadows.shadowOf(compose.activity)
        val open = shadow.nextStartedActivityForResult
        val file = File(context.cacheDir, "not-a-backup.json").apply { writeText("not json at all") }
        compose.runOnUiThread { shadow.receiveResult(open.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
        waitForText("복원 중 오류가 발생했어요")
        waitForText("백업 파일 형식이 아니에요. (JSON을 읽을 수 없어요)")
        assertEquals("the sentence is not repeated under the title", 1, count(hasText("오류가 발생했어요", substring = true)))
        clickText("닫기")
        waitGone(hasText("복원 중 오류가 발생했어요"))
    }

    // ───────────────────────── web backup file ─────────────────────────

    private val webBackup = """
    {
      "exportedAt": "2026-09-20T01:02:03.456Z",
      "data": {
        "entries": [
          {
            "id": "mf8k2p1ab3c", "createdAt": 1789174800000, "category": "원두", "beanMode": "single", "blendComponents": [],
            "name": "에티오피아 우라가 웹테스트 (커피 리브레, 모모스)", "country": "에티오피아", "region": "Guji, Uraga", "process": "워시드",
            "processOther": "", "altitude": "2,100m", "variety": "74158", "farmProducer": "", "roastery": "", "selection": "",
            "washingStation": "", "beanPackageType": "standard", "isDripBag": false, "moisture": "", "density": "", "score": "",
            "arrival": "", "roastDate": "2026-09-01", "roasterDesc": "", "roast": "라이트", "bagWeight": "200", "price": "19000",
            "source": "커피 리브레", "expectedNotes": "자스민, 복숭아", "actualNotes": "자스민, 살구", "dripper": "V60", "filter": "",
            "dose": "15", "water": "240", "temp": "92", "grind": "24클릭", "waterType": "정수기 물", "time": "2:30", "hasPhoto": true,
            "photoFeedback": null, "hasBagPhoto": true, "steps": [{"time":"0:00","water":"40","note":"뜸","wait":"30"},{"time":"0:30","water":"200","note":"","wait":"60"}],
            "recipeRef": null, "beanGuidance": {"summary": "AI text kept for round trips"}, "consultation": null, "practice": null,
            "adviceChat": null, "noteChat": null, "rating": 4,
            "attributes": {"fragranceAroma": 8, "flavor": 8.25, "aftertaste": 7.75, "acidity": 8.5, "body": 7.5, "balance": 8, "uniformity": 10, "cleancup": 10, "sweetness": 10, "overall": 8.25},
            "attributeNotes": {"acidity": "살구 산미"}, "tags": [], "notes": "맑고 달다."
          },
          {
            "id": "mf8k3q2cd4e", "createdAt": 1789261200000, "category": "카페", "beanMode": "single", "blendComponents": [],
            "name": "콜롬비아 핑크 버번 웹테스트", "country": "콜롬비아", "region": "Huila", "process": "워시드", "processOther": "",
            "altitude": "", "variety": "Pink Bourbon", "farmProducer": "", "roastery": "프릳츠", "selection": "", "washingStation": "",
            "beanPackageType": "standard", "isDripBag": false, "moisture": "", "density": "", "score": "", "arrival": "", "roastDate": "",
            "roasterDesc": "", "roast": "미디엄 라이트", "bagWeight": "", "price": "7,000", "source": "", "cafeName": "프릳츠 원서점",
            "expectedNotes": "", "actualNotes": "딸기, 초콜릿", "dripper": "칼리타 웨이브", "filter": "", "dose": "", "water": "", "temp": "",
            "grind": "", "waterType": "", "time": "", "hasPhoto": false, "photoFeedback": null, "hasBagPhoto": false, "steps": [],
            "recipeRef": null, "beanGuidance": null, "consultation": null, "practice": null, "adviceChat": null, "noteChat": null,
            "rating": 0, "attributes": {}, "attributeNotes": {}, "tags": [], "notes": ""
          },
          {
            "id": "mf8k4r3ef5g", "createdAt": 1789347600000, "category": "커핑", "beanMode": "single", "blendComponents": [],
            "name": "리브레 퍼블릭 커핑 웹테스트", "country": "", "region": "", "process": "", "processOther": "", "altitude": "",
            "variety": "", "farmProducer": "", "roastery": "", "selection": "", "washingStation": "", "beanPackageType": "standard",
            "isDripBag": false, "moisture": "", "density": "", "score": "", "arrival": "", "roastDate": "", "roasterDesc": "", "roast": "",
            "bagWeight": "", "price": "", "source": "", "expectedNotes": "", "actualNotes": "", "dripper": "", "filter": "", "dose": "",
            "water": "", "temp": "", "grind": "", "waterType": "", "time": "", "hasPhoto": false, "photoFeedback": null, "hasBagPhoto": false,
            "steps": [], "recipeRef": null, "beanGuidance": null, "consultation": null, "practice": null, "adviceChat": null, "noteChat": null,
            "rating": 0, "attributes": {}, "attributeNotes": {}, "tags": [], "notes": "케냐가 제일 좋았다.",
            "cuppingType": "퍼블릭", "cuppingPlace": "리브레 퍼블릭 커핑 웹테스트",
            "cuppingBeans": "케냐 웹테스트 AA\n브라질 웹테스트 내추럴",
            "cuppingBeanNotes": [{"bean": "케냐 웹테스트 AA", "note": "블랙커런트"}],
            "cuppingBeanDetails": [
              {"name": "케냐 웹테스트 AA", "country": "케냐", "region": "Nyeri", "roastery": "커피 리브레", "farmProducer": "", "altitude": "",
               "variety": "SL28", "price": "", "rank": "1", "process": "워시드", "roast": "라이트", "expectedNotes": "", "actualNotes": "블랙커런트",
               "evaluation": {"acidity": "밝음"}, "evaluationScores": {"acidity": 9}, "memo": "", "beanMode": "single", "blendComponentsText": ""},
              {"name": "브라질 웹테스트 내추럴", "country": "브라질", "region": "", "roastery": "", "farmProducer": "", "altitude": "",
               "variety": "", "price": "", "rank": "2", "process": "내추럴", "roast": "미디엄", "expectedNotes": "", "actualNotes": "",
               "evaluation": {}, "evaluationScores": {}, "memo": "고소함", "beanMode": "single", "blendComponentsText": ""}
            ]
          },
          {
            "id": "legacy01", "createdAt": 1788400000000, "category": "원두", "name": "과테말라 드립백 웹테스트",
            "isDripBag": true, "tastingNotes": "캐러멜", "farm": "엘 인헤르토", "producer": "아길레라 가족", "dose": "10", "rating": 3
          }
        ],
        "miscItems": [
          {"id": "wm1", "type": "dripper", "name": "웹 오리가미", "notes": "", "since": "2026-02-01", "photo": "data:image/jpeg;base64,/9j/AQID", "createdAt": 1780000000000},
          {"id": "wm2", "type": "source", "name": "커피 리브레", "notes": "", "since": "", "photo": null, "createdAt": 1780000000001, "scope": "국내", "location": "서울", "status": "", "favorite": true}
        ],
        "blends": [{"id": "wb1", "name": "웹 블렌드", "date": "2026-09-05", "beans": [{"name": "에티오피아 우라가 웹테스트", "grams": "10"}], "notes": "", "createdAt": 1789000000000}],
        "classes": [{"id": "wc1", "createdAt": 1788000000000, "title": "웹 클래스", "classType": "oneday", "date": "2026-08-01", "startDate": "", "endDate": "", "notes": ""}],
        "roadmapData": [{"id": "starter", "title": "나의 로드맵", "range": "자유롭게 작성", "dayStart": 0, "dayEnd": 99999, "items": [{"id": "custom-1", "text": "커핑 10회", "done": true}]}],
        "myRecipes": [{"id": "wr1", "name": "우라가 레시피", "fromEntryId": "mf8k2p1ab3c", "beanName": "에티오피아 우라가 웹테스트 (커피 리브레, 모모스)", "rating": 4, "dose": "15", "water": "240", "temp": "92", "dripper": "V60", "filter": "", "grind": "24클릭", "time": "2:30", "steps": [{"time":"0:00","water":"40","note":"뜸","wait":"30"}], "createdAt": 1789174900000}],
        "books": [{"id": "wbk1", "createdAt": 1787000000000, "title": "웹 책", "author": "저자", "status": "완료", "startDate": "2026-06-01", "endDate": "2026-06-20", "rating": 5, "notes": ""}],
        "beanSummaries": {"에티오피아 우라가 웹테스트": {"text": "92도가 좋다", "generatedAt": 1789174900000}},
        "bestRecipes": {"에티오피아 우라가 웹테스트": "mf8k2p1ab3c"}
      },
      "rawData": {"ddayStart": "2026-01-15"},
      "photos": {
        "bag-photo:mf8k2p1ab3c": "[\"data:image/jpeg;base64,/9j/AAEC\",\"data:image/jpeg;base64,/9j/AAED\"]",
        "journal-photo:mf8k2p1ab3c": "data:image/jpeg;base64,/9j/AAEE"
      }
    }
    """.trimIndent()

    @Test
    fun backup05_importWebBackup_entriesAppearOnHomeAndCollectionsRestored() {
        val result = importJson(webBackup, ImportMode.MERGE)
        assertTrue(result.lines.any { it.text() == "entries: ✓ 4개 복원됨" })

        val entries = runBlocking { koinGet<EntryRepository>().getAll() }.associateBy { it.id }
        assertEquals(4, entries.size)
        val brew = entries.getValue("mf8k2p1ab3c")
        assertEquals(2, brew.bagPhotos.size)
        assertNotNull(brew.groundsPhoto)
        assertEquals(8.25, brew.attributes["flavor"])
        assertNotNull("AI fields preserved for round trips", brew.legacyExtra?.get("beanGuidance"))
        val cupping = entries.getValue("mf8k4r3ef5g")
        assertEquals(Category.CUPPING, cupping.category)
        assertEquals(listOf("케냐 웹테스트 AA", "브라질 웹테스트 내추럴"), cupping.cuppingBeans.map { it.name })
        assertEquals(9.0, cupping.cuppingBeans[0].evaluationScores["acidity"])
        val legacy = entries.getValue("legacy01")
        assertEquals(PackageType.DRIPBAG, legacy.packageType)
        assertEquals("캐러멜", legacy.actualNotes)
        assertEquals("엘 인헤르토(아길레라 가족)", legacy.farmProducer)

        val misc = runBlocking { koinGet<MiscRepository>().getAll() }.associateBy { it.id }
        assertEquals(1, misc.getValue("wm1").photos.size)
        assertEquals(true, misc.getValue("wm2").favorite)
        // web load-time backfill (script3.js 8238-8373) runs after the restore: values used by the records are registered
        fun names(type: String) = misc.values.filter { it.type == type }.map { it.name }.sorted()
        assertEquals("roastery from the parentheses and roastery fields, no duplicate 커피 리브레", listOf("커피 리브레", "프릳츠"), names(MiscType.SOURCE))
        assertEquals(listOf("모모스"), names(MiscType.SELECTION))
        assertEquals(listOf("엘 인헤르토(아길레라 가족)"), names(MiscType.FARM))
        assertEquals(listOf("74158", "Pink Bourbon", "SL28"), names(MiscType.VARIETY))
        assertEquals(listOf("웹 책"), runBlocking { koinGet<StudyRepository>().getBooks().map { it.title } })
        assertEquals(listOf("웹 클래스"), runBlocking { koinGet<StudyRepository>().getClasses().map { it.title } })
        assertEquals(listOf("커핑 10회"), runBlocking { koinGet<RoadmapRepository>().getAll().single().items.map { it.text } })
        assertEquals(listOf("우라가 레시피"), runBlocking { koinGet<MyRecipeRepository>().getAll().map { it.name } })
        assertEquals("mf8k2p1ab3c", runBlocking { koinGet<BeanMetaRepository>().getBest().single().entryId })
        assertEquals("2026-01-15", runBlocking { koinGet<SettingsRepository>().get(SettingsRepository.KEY_DDAY_START) })

        launchApp()
        waitForText("4 entries")
        waitForText(DdayRules.label(LocalDate(2026, 1, 15), Dates.today())!!)
        val group = hasText("에티오피아 우라가 웹테스트") and hasClickAction()
        scrollListTo(hasText("+ 새 기록 추가"), group)
        waitFor(group, "imported brew group on home")
    }
}
