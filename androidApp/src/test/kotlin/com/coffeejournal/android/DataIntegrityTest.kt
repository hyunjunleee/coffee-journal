package com.coffeejournal.android

import androidx.compose.ui.test.hasText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.backup.BackupCodec
import com.coffeejournal.data.backup.BackupService
import com.coffeejournal.data.backup.ImportMode
import com.coffeejournal.data.backup.RestoreRunner
import com.coffeejournal.data.backup.RestoreState
import com.coffeejournal.data.db.AppDatabase
import com.coffeejournal.data.db.BookDao
import com.coffeejournal.data.db.BookEntity
import com.coffeejournal.data.db.PantryDao
import com.coffeejournal.data.db.PantryItemEntity
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.RoadmapRepository
import com.coffeejournal.data.repo.SaveEntryPipeline
import com.coffeejournal.data.repo.StudyRepository
import com.coffeejournal.di.AppScope
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.model.RoadmapItem
import com.coffeejournal.domain.model.RoadmapPhase
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.backup.BackupError
import com.coffeejournal.ui.backup.BackupViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** Save pipeline atomicity, cupping bean keys, all-or-nothing restore and non-finite numbers (audit bundle P1). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class DataIntegrityTest : FlowTestBase() {

    private val db: AppDatabase get() = koinGet()
    private fun photoFiles(): Set<String> = File(context.cacheDir, "photos").list()?.toSet() ?: emptySet()

    /** Polls a condition from the test (main) thread, letting viewModelScope work on the main looper run. */
    private fun pump(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            ShadowLooper.idleMainLooper()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for: $what")
            Thread.sleep(20)
        }
    }

    // ───────────────────────── data-2: the save pipeline is one transaction ─────────────────────────

    @Test
    fun pipelineSave_failureAfterTheEntryWriteRollsBackEverything_retryStoresOneRecord() = runBlocking {
        val entries: EntryRepository = koinGet()
        val misc: MiscRepository = koinGet()
        val pantry: PantryRepository = koinGet()
        entries.upsert(Entry(id = "sib", createdAt = 1_780_000_000_000L, name = "케냐 기통가 AA"))
        val failingPantry = PantryRepository(object : PantryDao by db.pantryDao() {
            override suspend fun upsert(item: PantryItemEntity) = throw IllegalStateException("disk full")
        })
        val broken = SaveEntryPipeline(entries, misc, failingPantry, koinGet())
        val entry = Entry(id = "new1", createdAt = Dates.nowMillis(), name = "케냐 기통가 AA (모모스)", variety = "SL28", farmProducer = "기통가", dose = "15")

        try {
            broken.save(entry, isNew = true)
            fail("the pantry step should have failed")
        } catch (e: IllegalStateException) {
            assertEquals("disk full", e.message)
        }
        assertNull("the record write was rolled back", entries.getById("new1"))
        assertEquals("the sibling fill was rolled back", "", entries.getById("sib")!!.variety)
        assertTrue("no misc item was registered", misc.getAll().isEmpty())

        // the form retries with the same id: one record, one bag, one registration each
        val pipeline: SaveEntryPipeline = koinGet()
        pipeline.save(entry, isNew = true)
        pipeline.save(entry, isNew = true)
        assertEquals(listOf("new1", "sib"), entries.getAll().map { it.id }.sorted())
        assertEquals("SL28", entries.getById("sib")!!.variety)
        val bag = pantry.getAll().single()
        assertEquals("form-6 / data-8 / flows-4: roastery from the name's parentheses", "모모스", bag.roastery)
        assertEquals(listOf(MiscType.FARM to "기통가", MiscType.VARIETY to "SL28"), misc.getAll().map { it.type to it.name }.sortedBy { it.first })
    }

    /** form-5 / data-7 / flows-5: a cupping bean's importer comes from "(로스터리, 셀렉션)" in its name. */
    @Test
    fun cuppingSave_registersTheImporterFromEachBeanName() = runBlocking {
        val cupping = Entry(id = "c1", createdAt = Dates.nowMillis(), category = Category.CUPPING, name = "퍼블릭 커핑",
            cuppingBeans = listOf(CuppingBean(name = "케냐 키암부 AB (커피 리브레, 모모스 셀렉션)"), CuppingBean(name = "에티오피아 구지 (리브레, Nordic Approach)")))
        koinGet<SaveEntryPipeline>().save(cupping, isNew = true)
        assertEquals(listOf("Nordic Approach", "모모스"), koinGet<MiscRepository>().getAll().filter { it.type == MiscType.SELECTION }.map { it.name }.sorted())
    }

    // ───────────────────────── form-1 / data-1: cupping bean keys ─────────────────────────

    @Test
    fun cuppingEdit_removeFirstBeanThenAddOne_keepsEveryBean() = runBlocking {
        val entries: EntryRepository = koinGet()
        val session = Entry(id = "E", createdAt = 1_780_000_000_000L, category = Category.CUPPING, name = "커핑",
            cuppingBeans = listOf(CuppingBean(name = "A", memo = "a"), CuppingBean(name = "B", memo = "b"), CuppingBean(name = "C", memo = "c")))
        entries.upsert(session)
        val stored = entries.getById("E")!!
        // the form drops A and appends a fresh card (blank id), then saves
        entries.upsert(stored.copy(cuppingBeans = stored.cuppingBeans.drop(1) + CuppingBean(name = "D", memo = "d")))
        val beans = entries.getById("E")!!.cuppingBeans
        assertEquals(listOf("B" to "b", "C" to "c", "D" to "d"), beans.map { it.name to it.memo })
    }

    // ───────────────────────── miscBackup-1 / platform-3: all-or-nothing restore ─────────────────────────

    @Test
    fun restore_failingHalfwayChangesNothingAndDeletesTheNewPhotoFiles() = runBlocking {
        SampleData.seed()
        val photos: PhotoStore = koinGet()
        val entries: EntryRepository = koinGet()
        entries.upsert(entries.getById("e1")!!.copy(bagPhotos = listOf(photos.save(byteArrayOf(-1, -40, 1)))))
        val backup = koinGet<BackupService>().snapshot()
        // local changes after the backup
        entries.delete("e3")
        entries.upsert(Entry(id = "after", createdAt = Dates.nowMillis(), name = "백업 이후 원두"))
        val before = koinGet<BackupService>().snapshot().copy(exportedAt = null)
        val filesBefore = photoFiles()

        val failingBooks = object : BookDao by db.bookDao() {
            override suspend fun upsertAll(items: List<BookEntity>) = throw IllegalStateException("disk full")
        }
        val study = StudyRepository(failingBooks, db.videoDao(), db.classDao())
        val service = BackupService(koinGet(), koinGet(), koinGet(), study, koinGet(), koinGet(), koinGet(), koinGet(), koinGet(), koinGet(), koinGet(), koinGet(), koinGet(), koinGet())
        try {
            service.import(backup, ImportMode.REPLACE)
            fail("books should have failed")
        } catch (e: IllegalStateException) {
            assertEquals("disk full", e.message)
        }
        assertEquals("nothing was applied", before, koinGet<BackupService>().snapshot().copy(exportedAt = null))
        assertEquals("photo files written for the restore were deleted", filesBefore, photoFiles())

        // through the screen: the error panel says the restore changed nothing (miscBackup-8: title + message only)
        val runner = RestoreRunner(service, AppScope())
        assertTrue(runner.start(backup, ImportMode.REPLACE))
        pump("restore failed") { runner.state.value is RestoreState.Failed }
        val vm = BackupViewModel(koinGet(), koinGet<BackupCodec>(), runner)
        pump("error shown") { vm.state.value.error != null }
        assertEquals(BackupError(restore = true, detail = "disk full", untouched = true), vm.state.value.error)
        assertEquals(before, koinGet<BackupService>().snapshot().copy(exportedAt = null))
    }

    @Test
    fun restore_keepsRunningAfterTheBackupScreenIsLeft() {
        SampleData.seed()
        val real: PhotoStore = koinGet()
        val entries: EntryRepository = koinGet()
        runBlocking { entries.upsert(entries.getById("e1")!!.copy(bagPhotos = listOf(real.save(byteArrayOf(-1, -40, 9))))) }
        val json = runBlocking { koinGet<BackupService>().export().json }
        runBlocking { entries.delete("e3") }
        // photo files are stored first; holding them keeps the restore in flight while the screen goes away
        val gate = CompletableDeferred<Unit>()
        val saving = CompletableDeferred<Unit>()
        val slow = object : PhotoStore by real {
            override suspend fun save(bytes: ByteArray): String { saving.complete(Unit); gate.await(); return real.save(bytes) }
        }
        val service = BackupService(koinGet(), koinGet(), koinGet(), koinGet(), koinGet(), koinGet(), koinGet(), koinGet(), koinGet(), slow, koinGet(), koinGet(), koinGet(), koinGet())
        val runner = RestoreRunner(service, AppScope())
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = BackupViewModel(koinGet(), koinGet<BackupCodec>(), runner) as T
        }
        val vm = ViewModelProvider(store, factory)[BackupViewModel::class.java]
        vm.onFileLoaded(json)
        pump("backup parsed") { vm.state.value.pending != null }
        vm.restore(ImportMode.MERGE)
        pump("restore storing photos") { runner.state.value is RestoreState.Running && saving.isCompleted }

        store.clear() // system back / the title-bar arrow pops the screen and clears its ViewModel
        gate.complete(Unit)
        pump("restore finished") { runner.state.value is RestoreState.Finished }
        assertTrue("e3 was restored although the screen was gone", runBlocking { entries.getById("e3") } != null)

        // opening the backup screen again shows how the restore ended
        val again = BackupViewModel(koinGet(), koinGet<BackupCodec>(), runner)
        pump("result shown") { again.state.value.importResult != null }
        assertTrue(again.state.value.importResult!!.allOk)
        assertEquals(RestoreState.Idle, runner.state.value)
    }

    /** miscBackup-4 / miscBackup-6: 병합 neither duplicates misc items by name nor replaces the roadmap. */
    @Test
    fun mergeRestore_keepsOneItemPerNameAndLocalRoadmapPhases() = runBlocking {
        SampleData.seed()
        val roadmap: RoadmapRepository = koinGet()
        roadmap.upsert(RoadmapPhase("appPhase", 1, "앱에서 추가한 단계", "", 100, 200, listOf(RoadmapItem("a1", "로스팅 수업"))))
        val json = """
            {"data": {
              "miscItems": [{"id": "wm2", "type": "source", "name": "커피리브레", "location": "서울 마포구", "createdAt": 1780000000001},
                            {"id": "wm3", "type": "dripper", "name": "오리가미 드리퍼 S", "createdAt": 1780000000002}],
              "roadmapData": [{"id": "starter", "title": "나의 로드맵", "range": "자유롭게 작성", "dayStart": 0, "dayEnd": 99999,
                               "items": [{"id": "custom-1", "text": "커핑 10회", "done": true}]}]
            }}
        """.trimIndent()
        val result = koinGet<BackupService>().import(koinGet<BackupCodec>().decode(json), ImportMode.MERGE)
        assertTrue(result.lines.map { it.text() }.toString(), result.allOk)
        val misc = koinGet<MiscRepository>().getAll()
        assertEquals(listOf("m4"), misc.filter { it.type == MiscType.SOURCE && it.name.replace(" ", "") == "커피리브레" }.map { it.id })
        assertEquals("서울 마포구", misc.single { it.id == "m4" }.location)
        assertEquals(listOf("m1"), misc.filter { it.type == MiscType.DRIPPER }.map { it.id })
        val phases = roadmap.getAll()
        assertEquals(listOf("starter", "appPhase"), phases.map { it.id })
        assertEquals(listOf("커핑 10회"), phases[0].items.map { it.text })
        assertEquals(listOf("로스팅 수업"), phases[1].items.map { it.text })
    }

    // ───────────────────────── home-1: "NaN" in number fields ─────────────────────────

    @Test
    fun nanAndInfinityInNumberFields_doNotCrashHomeOrPantry() {
        runBlocking {
            val name = "에티오피아 NaN 테스트"
            koinGet<EntryRepository>().upsert(Entry(id = "n1", createdAt = Dates.nowMillis() - 60_000, name = name, dose = "NaN",
                steps = listOf(RecipeStep("0:00", "Infinity", "NaN", "뜸"))))
            koinGet<EntryRepository>().upsert(Entry(id = "n2", createdAt = Dates.nowMillis(), name = name, dose = "1e999"))
            koinGet<PantryRepository>().upsertAll(listOf(
                PantryItem(id = "p1", name = name, weight = "NaN", price = "18000", status = PantryItem.STATUS_OPENED, openedAt = Dates.nowMillis(), createdAt = 1),
                PantryItem(id = "p2", name = "소량 NaN 테스트", weight = "Infinity", price = "5000", packageType = "sample", createdAt = 2),
            ))
        }
        launchApp()
        waitForText("2 entries")
        waitFor(hasText("잔여량 100g/100g"), "drinking card: NaN bag weight falls back to 100 g, NaN doses count as 0")
        clickText("원두 보관함")
        waitForText("소량 NaN 테스트")
    }
}
