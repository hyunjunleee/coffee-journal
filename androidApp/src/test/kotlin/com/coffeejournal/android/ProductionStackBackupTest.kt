package com.coffeejournal.android

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.room.useReaderConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.backup.BackupCodec
import com.coffeejournal.data.backup.BackupService
import com.coffeejournal.data.backup.ImportMode
import com.coffeejournal.data.db.AppDatabase
import com.coffeejournal.data.photo.AndroidPhotoStore
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.SaveEntryPipeline
import com.coffeejournal.di.dataModule
import com.coffeejournal.di.platformModule
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.nav.Features
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/**
 * Gap #4: the backup flows on the app's own platform module — Room on BundledSQLiteDriver with the database file on
 * disk (Robolectric's app data dir) and AndroidPhotoStore — instead of the in-memory framework-SQLite database and the
 * byte-copying TempPhotoStore the other flow tests use. Photos are real JPEGs, so decoding and re-encoding are real.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ProductionStackBackupTest : FlowTestBase() {

    private val dbFile: File get() = context.getDatabasePath("coffee_journal.db")
    private val photoDir: File get() = File(context.filesDir, "photos")

    private fun deleteDeviceData() {
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        photoDir.deleteRecursively()
    }

    private fun startProductionKoin() {
        startKoin {
            androidContext(context)
            modules(listOf(platformModule, dataModule) + Features.all.map { it.module })
        }
    }

    /** Runs after FlowTestBase started the in-memory graph: swap in the production one on an empty device. */
    @Before
    fun useProductionStack() {
        stopKoin()
        deleteDeviceData()
        startProductionKoin()
    }

    @After
    fun closeDatabase() {
        runCatching { koinGet<AppDatabase>().close() }
    }

    /** A new install: the database file and the photo directory are gone. */
    private fun freshDevice() {
        closeDatabase()
        stopKoin()
        deleteDeviceData()
        startProductionKoin()
    }

    /** A real photo: shapes and a gradient so the JPEG has structure to lose when re-encoded. */
    private fun jpeg(width: Int, height: Int, quality: Int, seed: Int = 1): ByteArray {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(200, 180 - seed * 10, 150))
        val paint = Paint().apply { isAntiAlias = true }
        for (i in 0 until 24) {
            paint.color = Color.rgb((i * 37 + seed * 11) % 256, (i * 71) % 256, (i * 13 + 90) % 256)
            canvas.drawCircle((i * 53 % width).toFloat(), (i * 29 % height).toFloat(), (width / 10 + i).toFloat(), paint)
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun size(bytes: ByteArray): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        return o.outWidth to o.outHeight
    }

    private fun isJpeg(bytes: ByteArray) = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()

    private val bag1 = lazy { jpeg(1280, 960, 82, 1) }
    private val bag2 = lazy { jpeg(700, 525, 70, 2) }
    private val grounds = lazy { jpeg(960, 1280, 82, 3) }
    private val misc = lazy { jpeg(640, 480, 82, 4) }

    private fun seedEverything() = runBlocking {
        SampleData.seed()
        val photos: PhotoStore = koinGet()
        val entries: EntryRepository = koinGet()
        val e1 = entries.getById("e1")!!
        entries.upsert(e1.copy(bagPhotos = listOf(photos.save(bag1.value), photos.save(bag2.value)), groundsPhoto = photos.save(grounds.value)))
        val m1 = koinGet<MiscRepository>().getById("m1")!!
        koinGet<MiscRepository>().upsert(m1.copy(photos = listOf(photos.save(misc.value))))
        koinGet<SaveEntryPipeline>().backfill(entries.getAll())
    }

    private fun normalized(): Map<String, Any?> = runBlocking {
        val s = koinGet<BackupService>().snapshot()
        mapOf(
            "entries" to s.entries.map { it.copy(bagPhotos = emptyList(), groundsPhoto = null) }.sortedBy { it.id },
            "entryPhotos" to s.photos.map { "${it.ownerId}/${it.kind}/${it.index}=${it.bytes.contentHashCode()}:${it.bytes.size}" }.sorted(),
            "miscItems" to s.miscItems.map { it.copy(photos = emptyList()) }.sortedBy { it.id },
            "miscPhotos" to s.miscPhotos.map { "${it.ownerId}/${it.index}=${it.bytes.contentHashCode()}:${it.bytes.size}" }.sorted(),
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

    private fun exportJson(): String = runBlocking { koinGet<BackupService>().export().json }

    private fun importJson(json: String, mode: ImportMode) = runBlocking {
        val result = koinGet<BackupService>().import(koinGet<BackupCodec>().decode(json), mode)
        assertTrue("import reported failures: ${result.lines.map { it.text() }}", result.allOk)
    }

    private fun payload(json: String) = (Json.parseToJsonElement(json) as JsonObject).let { o -> listOf("data", "rawData", "photos").associateWith { o[it] } }

    @Test
    fun prod01_productionDriverOnDisk_andTheRealPhotoStore() {
        seedEverything()
        assertTrue("database file on disk", dbFile.isFile && dbFile.length() > 0)
        assertTrue(koinGet<PhotoStore>() is AndroidPhotoStore)
        val version = runBlocking {
            koinGet<AppDatabase>().useReaderConnection { c -> c.usePrepared("SELECT sqlite_version()") { st -> st.step(); st.getText(0) } }
        }
        val (major, minor) = version.split('.').map { it.toInt() }
        assertTrue("bundled SQLite ($version), not the framework's", major > 3 || (major == 3 && minor >= 45))
        assertEquals(4, photoDir.listFiles()!!.size)
    }

    @Test
    fun prod02_exportThenReplaceAndMergeIntoFreshDevices_restoreEverything() {
        seedEverything()
        val before = normalized()
        val json = exportJson()
        freshDevice()
        importJson(json, ImportMode.REPLACE)
        assertEquals(before, normalized())
        freshDevice()
        importJson(json, ImportMode.MERGE)
        assertEquals(before, normalized())
    }

    /** backup04 on the real store: photos that already fit are stored byte for byte, so round trips are lossless. */
    @Test
    fun prod03_repeatedRoundTrips_keepEveryPhotoByteForByte() {
        seedEverything()
        val photosBefore = runBlocking { koinGet<BackupService>().snapshot().photos.associate { "${it.ownerId}/${it.kind}/${it.index}" to it.bytes } }
        assertArrayEquals("a fitting JPEG is stored as it is", bag2.value, photosBefore.getValue("e1/BAG/1"))
        val first = exportJson()
        freshDevice()
        importJson(first, ImportMode.REPLACE)
        val second = exportJson()
        freshDevice()
        importJson(second, ImportMode.REPLACE)
        val third = exportJson()
        assertEquals(payload(first), payload(second))
        assertEquals(payload(first), payload(third))
        val photosAfter = runBlocking { koinGet<BackupService>().snapshot().photos.associate { "${it.ownerId}/${it.kind}/${it.index}" to it.bytes } }
        photosBefore.forEach { (key, bytes) -> assertArrayEquals("photo $key unchanged after two restores", bytes, photosAfter.getValue(key)) }
    }

    /** The web stores 700px JPEGs at quality 0.7; restoring them must not re-encode them at q82 (bigger and worse). */
    @Test
    fun prod04_webBackupPhotos_areKeptAndDoNotGrow() {
        val bag = jpeg(700, 525, 70, 5)
        val journal = jpeg(525, 700, 70, 6)
        val url = { b: ByteArray -> "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(b) }
        // web layout: bag photos are a JSON array stored as a string, the grounds photo a plain data URL
        val web = buildJsonObject {
            put("exportedAt", "2026-09-20T01:02:03.456Z")
            putJsonObject("data") {
                put("entries", JsonArray(listOf(buildJsonObject {
                    put("id", "w1"); put("createdAt", 1_789_174_800_000L); put("category", "원두"); put("name", "웹 사진 원두")
                    put("hasBagPhoto", true); put("hasPhoto", true)
                })))
            }
            putJsonObject("rawData") {}
            putJsonObject("photos") {
                put("bag-photo:w1", JsonArray(listOf(JsonPrimitive(url(bag)))).toString())
                put("journal-photo:w1", url(journal))
            }
        }.toString()
        importJson(web, ImportMode.MERGE)
        val w1 = runBlocking { koinGet<EntryRepository>().getById("w1")!! }
        val storedBag = File(photoDir, w1.bagPhotos.single()).readBytes()
        val storedJournal = File(photoDir, w1.groundsPhoto!!).readBytes()
        assertArrayEquals(bag, storedBag)
        assertArrayEquals(journal, storedJournal)
        // and the file written back out carries the same data URLs
        val again = Json.parseToJsonElement(exportJson()) as JsonObject
        val photos = again["photos"] as JsonObject
        assertTrue(photos.toString().contains(url(journal)))
        assertTrue(photos.toString().contains(url(bag)))
    }

    @Test
    fun prod05_photoStore_downscalesLargeOnes_convertsOtherFormats_keepsFittingJpegs() = runBlocking {
        val store: PhotoStore = koinGet()
        val big = jpeg(2400, 1800, 95, 7)
        val bigStored = File(store.pathFor(store.save(big))).readBytes()
        assertTrue(isJpeg(bigStored))
        assertEquals(1280, max(size(bigStored).first, size(bigStored).second))
        assertTrue("re-encoded smaller", bigStored.size < big.size)

        val pngStored = File(store.pathFor(store.save(png(300, 200)))).readBytes()
        assertTrue("PNG converted to JPEG", isJpeg(pngStored))
        assertEquals(300 to 200, size(pngStored))

        val fitting = jpeg(1280, 720, 60, 8)
        assertArrayEquals(fitting, File(store.pathFor(store.save(fitting))).readBytes())
        assertTrue(AndroidPhotoStore.isStorableAsIs(fitting, PhotoStore.MAX_EDGE_PX))
        assertFalse(AndroidPhotoStore.isStorableAsIs(big, PhotoStore.MAX_EDGE_PX))
        assertFalse(AndroidPhotoStore.isStorableAsIs(png(10, 10), PhotoStore.MAX_EDGE_PX))
    }

    /** more03 on the real stack: editing keeps the photo files, deleting the record removes them. */
    @Test
    fun prod06_editKeepsPhotos_deleteRemovesFiles() {
        val photos: PhotoStore = koinGet()
        val (a, b) = runBlocking { photos.save(bag1.value) to photos.save(bag2.value) }
        runBlocking {
            koinGet<EntryRepository>().upsert(Entry(id = "ph1", createdAt = Dates.nowMillis() - 3_600_000, name = "사진 원두 테스트", dose = "13.5", bagPhotos = listOf(a, b)))
        }
        launchApp()
        typeInto("예: 벤사, 게이샤, 리브레", "사진 원두")
        waitForText("전체 기록에서", substring = true)
        clickNode(button("기록 보기"))
        waitUntil("detail opened from search") { has(button("수정")) && has(button("삭제")) }
        assertEquals(2, count(hasContentDescription("원두 봉투 사진")))
        clickText("수정")
        waitForText("기록 수정")
        replaceIn("13.5", "14")
        clickText("수정 저장")
        waitUntil("detail after edit") { !has(hasText("기록 수정")) && has(hasText("14g : ?g")) }
        assertEquals(listOf(a, b), runBlocking { koinGet<EntryRepository>().getById("ph1")!!.bagPhotos })
        assertTrue(File(photoDir, a).exists() && File(photoDir, b).exists())
        clickText("삭제")
        clickNode(dialogButton("삭제"))
        waitUntil("record deleted") { runBlocking { koinGet<EntryRepository>().getAll() }.isEmpty() }
        settle()
        assertFalse("photo files deleted with the record", File(photoDir, a).exists() || File(photoDir, b).exists())
    }

    /** backup06 on the real stack: the backup screen restores a saved file with 교체 through the app-wide runner. */
    @Test
    fun prod07_backupScreen_restoreFileWithReplace() {
        seedEverything()
        val before = normalized()
        val file = File(context.cacheDir, "prod-backup.json").apply { writeText(exportJson()) }
        freshDevice()
        launchApp()
        clickText("💾 백업")
        waitForText("전체 데이터 백업")
        clickText("📂 백업 파일에서 복원")
        val shadow = Shadows.shadowOf(compose.activity)
        val open = shadow.nextStartedActivityForResult
        compose.runOnUiThread { shadow.receiveResult(open.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
        waitForText("백업 파일에서 복원")
        clickNode(hasText("교체") and hasClickAction())
        clickNode(dialogButton("복원"))
        waitForText("복원 결과", substring = false)
        waitForText("모두 ✓ 로 떴어요. 각 탭에서 데이터가 잘 들어왔는지 확인해보세요.")
        assertEquals(before, normalized())
    }
}
