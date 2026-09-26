package com.coffeejournal.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.backup.BackupCodec
import com.coffeejournal.data.backup.BackupKeys
import com.coffeejournal.data.backup.BackupService
import com.coffeejournal.data.backup.ImportMode
import com.coffeejournal.data.repo.CafePlaceRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.Scope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Backup round trip of the map data (schema v2): roastery positions ride on `miscItems` as optional lat / lng keys, café
 * positions in the app extension key `cafePlaces`. 병합 matches cafés by name, 교체 swaps the table, and a web file
 * (neither key) restores without touching them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class MapBackupTest : FlowTestBase() {
    private val misc get() = koinGet<MiscRepository>()
    private val cafes get() = koinGet<CafePlaceRepository>()
    private val service get() = koinGet<BackupService>()

    private fun seed() = runBlocking {
        misc.upsertAll(listOf(
            MiscItem(id = "r1", type = MiscType.SOURCE, name = "리브레", scope = Scope.DOMESTIC, location = "서울 성동구", createdAt = 1, lat = 37.5446, lng = 127.0557),
            MiscItem(id = "r2", type = MiscType.SOURCE, name = "Kurasu", scope = Scope.OVERSEAS, location = "교토, 일본", createdAt = 2, lat = 34.9858, lng = 135.7588),
        ))
        cafes.set("FELT 청계천", GeoPoint(37.5663, 126.991))
        cafes.set("프릳츠 도화", GeoPoint(37.541, 126.951))
    }

    @Test
    fun exportThenReplace_restoresPositionsAndCafePlaces() = runBlocking {
        seed()
        val json = service.export().json
        assertTrue(json.contains("\"cafePlaces\""))
        // the device changes after the export
        misc.upsert(misc.getById("r1")!!.copy(lat = null, lng = null))
        cafes.clear("FELT 청계천")
        cafes.set("새 카페", GeoPoint(35.1, 129.0))
        val result = service.import(BackupCodec().decode(json), ImportMode.REPLACE)
        assertTrue(result.lines.map { it.text() }.toString(), result.allOk)
        assertTrue(result.lines.any { it.text() == "${BackupKeys.CAFE_PLACES}: ✓ 2개 복원됨" })
        assertEquals(GeoPoint(37.5446, 127.0557), misc.getById("r1")!!.point)
        assertEquals(GeoPoint(34.9858, 135.7588), misc.getById("r2")!!.point)
        assertEquals(listOf("FELT 청계천", "프릳츠 도화"), cafes.getAll().map { it.name })
    }

    @Test
    fun mergeMatchesCafesByName_andAWebFileLeavesTheMapDataAlone() = runBlocking {
        seed()
        val codec = BackupCodec()
        val app = """{"data":{"cafePlaces":[{"name":"felt 청계천","lat":37.57,"lng":126.99,"createdAt":5},{"name":"모모스","lat":35.227,"lng":129.088,"createdAt":6}]}}"""
        val merged = service.import(codec.decode(app), ImportMode.MERGE)
        assertTrue(merged.allOk)
        assertEquals(
            listOf("felt 청계천" to GeoPoint(37.57, 126.99), "모모스" to GeoPoint(35.227, 129.088), "프릳츠 도화" to GeoPoint(37.541, 126.951)),
            cafes.getAll().map { it.name to it.point },
        )
        // a web backup: the same roastery without a position, no cafePlaces — even 교체 keeps the cafés
        val web = """{"data":{"miscItems":[{"id":"r1","type":"source","name":"리브레","scope":"국내","location":"서울 성동구","createdAt":1}]}}"""
        val res = service.import(codec.decode(web), ImportMode.MERGE)
        assertTrue(res.lines.none { it.label == BackupKeys.CAFE_PLACES })
        assertEquals(GeoPoint(37.5446, 127.0557), misc.getById("r1")!!.point)
        service.import(codec.decode(web), ImportMode.REPLACE)
        assertEquals(3, cafes.getAll().size)
        // 교체 swaps the misc list for the file's, so the file's (absent) position counts there
        assertNull(misc.getById("r1")!!.point)
        assertNull(misc.getById("r2"))
    }
}
