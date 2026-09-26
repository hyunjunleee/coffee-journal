package com.coffeejournal.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.db.AppDatabase
import com.coffeejournal.data.db.androidDatabaseBuilder
import com.coffeejournal.data.db.buildAppDatabase
import com.coffeejournal.data.repo.CafePlaceRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.domain.model.GeoPoint
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Room schema v1 → v2 (auto-migration: misc_items.lat / lng, table cafe_places). A database file is built exactly as
 * version 1 left it — the tables, indices and identity hash of the exported shared/schemas/…/1.json, user_version 1,
 * a few rows — and then opened by the app's own builder (BundledSQLiteDriver) at version 2.
 */
@RunWith(AndroidJUnit4::class)
// the same sandbox as ProductionStackBackupTest: the bundled SQLite's native library loads into one class loader only
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class DatabaseMigrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dbFile: File get() = context.getDatabasePath("coffee_journal.db")
    private var db: AppDatabase? = null

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val schemaDir = File(root, "shared/schemas/com.coffeejournal.data.db.AppDatabase")

    private fun deleteDb() = listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }

    @Before fun clean() = deleteDb()

    @After fun close() {
        runCatching { db?.close() }
        deleteDb()
    }

    /** Creates the version-1 file from the exported schema, with one record, two misc items and a setting. */
    private fun writeVersion1() {
        val schema = Json.parseToJsonElement(File(schemaDir, "1.json").readText()).jsonObject["database"]!!.jsonObject
        assertEquals(1, schema["version"]!!.jsonPrimitive.content.toInt())
        dbFile.parentFile!!.mkdirs()
        // the framework SQLite writes the same file format the app's bundled SQLite then opens
        val conn = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            schema["entities"]!!.jsonArray.forEach { e ->
                val table = e.jsonObject["tableName"]!!.jsonPrimitive.content
                conn.execSQL(e.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e.jsonObject["indices"]?.jsonArray?.forEach { ix -> conn.execSQL(ix.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            schema["setupQueries"]!!.jsonArray.forEach { conn.execSQL(it.jsonPrimitive.content) }
            conn.execSQL(
                "INSERT INTO misc_items (id, type, name, notes, since, status, scope, location, favorite, photosJson, createdAt) VALUES " +
                    "('m1', 'source', '커피 리브레', '', '', '', '국내', '서울 성동구', 1, '[]', 1780000000000), " +
                    "('m2', 'dripper', '오리가미', '세라믹', '2026-03-01', '보유', '', '', 0, '[\"a.jpg\"]', 1780000000001)"
            )
            val entryTable = schema["entities"]!!.jsonArray.single { it.jsonObject["tableName"]!!.jsonPrimitive.content == "entries" }.jsonObject
            val columns = entryTable["fields"]!!.jsonArray.map { it.jsonObject }
            val values = columns.map { f ->
                when (f["columnName"]!!.jsonPrimitive.content) {
                    "id" -> "'e1'"
                    "createdAt" -> "1780000000000"
                    "category" -> "'카페'"
                    "name" -> "'게이샤'"
                    "cafeName" -> "'FELT 청계천'"
                    else -> if (f["notNull"]?.jsonPrimitive?.content == "true") (if (f["affinity"]!!.jsonPrimitive.content == "TEXT") "''" else "0") else "NULL"
                }
            }
            conn.execSQL("INSERT INTO entries (${columns.joinToString { "`" + it["columnName"]!!.jsonPrimitive.content + "`" }}) VALUES (${values.joinToString()})")
            conn.execSQL("INSERT INTO settings (`key`, value) VALUES ('brew-start-date', '2026-01-01')")
            conn.version = 1
        } finally {
            conn.close()
        }
    }

    @Test
    fun version1Database_openedByVersion2_keepsItsRows_andGainsThePositionColumns() = runBlocking {
        assertEquals("the current schema is exported next to version 1", true, File(schemaDir, "2.json").exists())
        writeVersion1()
        val database = androidDatabaseBuilder(context).buildAppDatabase().also { db = it }
        val misc = MiscRepository(database.miscDao(), TempPhotoStore(File(context.cacheDir, "photos")))
        val items = misc.getAll().associateBy { it.id }
        assertEquals(setOf("m1", "m2"), items.keys)
        assertEquals(listOf("커피 리브레", "서울 성동구", true), items.getValue("m1").let { listOf(it.name, it.location, it.favorite) })
        assertEquals(listOf("a.jpg"), items.getValue("m2").photos)
        assertNull("no position after the migration", items.getValue("m1").point)
        assertEquals("FELT 청계천", EntryRepository(database.entryDao(), TempPhotoStore(File(context.cacheDir, "photos"))).getById("e1")!!.cafeName)
        assertEquals("2026-01-01", database.settingsDao().get("brew-start-date"))

        // the new column and table work
        misc.upsert(items.getValue("m1").copy(lat = 37.5446, lng = 127.0557))
        assertEquals(GeoPoint(37.5446, 127.0557), misc.getById("m1")!!.point)
        val cafes = CafePlaceRepository(database.cafePlaceDao())
        cafes.set("FELT 청계천", GeoPoint(37.5663, 126.991))
        cafes.set("felt 청계천", GeoPoint(37.57, 126.99))
        assertEquals(listOf("FELT 청계천" to GeoPoint(37.57, 126.99)), cafes.getAll().map { it.name to it.point })
        cafes.clear("Felt 청계천 ")
        assertEquals(0, cafes.getAll().size)
        Unit
    }
}
