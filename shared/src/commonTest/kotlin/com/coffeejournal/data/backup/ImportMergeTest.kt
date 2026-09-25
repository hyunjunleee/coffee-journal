package com.coffeejournal.data.backup

import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.RoadmapItem
import com.coffeejournal.domain.model.RoadmapPhase
import kotlin.test.Test
import kotlin.test.assertEquals

/** 병합 rules of a restore: misc items de-duplicated by type + name (miscBackup-4), roadmap merged by id (miscBackup-6). */
class ImportMergeTest {
    private fun item(id: String, type: String, name: String, notes: String = "", photos: List<String> = emptyList(), favorite: Boolean = false) =
        MiscItem(id = id, type = type, name = name, notes = notes, photos = photos, favorite = favorite, createdAt = 1)

    @Test fun backupItemWithSameNameUpdatesTheLocalRowInsteadOfDuplicatingIt() {
        val local = listOf(item("app1", MiscType.SOURCE, "커피 리브레", favorite = true), item("app2", MiscType.DRIPPER, "오리가미", photos = listOf("old.jpg")))
        val incoming = listOf(
            item("web1", MiscType.SOURCE, "커피리브레", notes = "연남"),
            item("web2", MiscType.DRIPPER, " 오리가미 ", notes = "세라믹"),
            item("web3", MiscType.SELECTION, "모모스"),
        )
        val r = ImportMerge.misc(local, incoming, mapOf("web2" to listOf("new.jpg")))
        assertEquals(listOf("app1", "app2", "web3"), r.rows.map { it.id })
        val roastery = r.rows[0]
        assertEquals("커피리브레" to "연남", roastery.name to roastery.notes)
        assertEquals(true, roastery.favorite, "a local favourite stays a favourite")
        assertEquals(listOf("new.jpg"), r.rows[1].photos)
        assertEquals("세라믹", r.rows[1].notes)
        assertEquals(listOf("old.jpg"), r.obsoletePhotos, "the replaced local photo is deleted after commit")
    }

    @Test fun sameIdOverwritesAndKeepsLocalPhotosOnlyWhenNameMatched() {
        val local = listOf(item("m1", MiscType.KETTLE, "펠로우", photos = listOf("k.jpg")), item("m2", MiscType.FARM, "워카", photos = listOf("f.jpg")))
        val r = ImportMerge.misc(local, listOf(item("m1", MiscType.KETTLE, "펠로우 EKG"), item("w", MiscType.FARM, "워카")), emptyMap())
        assertEquals(listOf("m1", "m2"), r.rows.map { it.id })
        assertEquals(emptyList(), r.rows[0].photos, "same id: the backup item wins, photos included (web restore overwrites the key)")
        assertEquals(listOf("f.jpg"), r.rows[1].photos, "name match without backup photos keeps the local photos")
        assertEquals(listOf("k.jpg"), r.obsoletePhotos)
    }

    @Test fun registryDuplicatesInsideTheBackupCollapseButEquipmentDoesNot() {
        val incoming = listOf(
            item("a", MiscType.VARIETY, "Geisha"), item("b", MiscType.VARIETY, "geisha"),
            item("c", MiscType.DRIPPER, "V60"), item("d", MiscType.DRIPPER, "V60"),
        )
        val r = ImportMerge.misc(emptyList(), incoming, emptyMap())
        assertEquals(listOf("a", "c", "d"), r.rows.map { it.id })
        // an equipment row is claimed by one backup item only
        val r2 = ImportMerge.misc(listOf(item("mine", MiscType.DRIPPER, "V60")), incoming.drop(2), emptyMap())
        assertEquals(listOf("mine", "d"), r2.rows.map { it.id })
    }

    @Test fun roadmapMergeKeepsLocalPhasesAndItems() {
        val local = listOf(
            RoadmapPhase("starter", 0, "나의 로드맵", "자유롭게 작성", 0, 99999, listOf(RoadmapItem("i1", "커핑 5회"), RoadmapItem("mine", "로컬 항목"))),
            RoadmapPhase("appPhase", 1, "앱에서 추가", "", 100, 200, listOf(RoadmapItem("x", "x"))),
        )
        val incoming = listOf(
            RoadmapPhase("starter", 0, "웹 로드맵", "자유롭게 작성", 0, 99999, listOf(RoadmapItem("i1", "커핑 10회", done = true), RoadmapItem("web", "웹 항목"))),
            RoadmapPhase("webPhase", 1, "웹 단계", "", 0, 10),
        )
        val merged = ImportMerge.roadmap(local, incoming)
        assertEquals(listOf("starter" to 0, "appPhase" to 1, "webPhase" to 2), merged.map { it.id to it.position })
        assertEquals("웹 로드맵", merged[0].title)
        assertEquals(listOf("커핑 10회" to true, "로컬 항목" to false, "웹 항목" to false), merged[0].items.map { it.text to it.done })
        assertEquals(local[1], merged[1])
    }
}
