package com.coffeejournal.ui.form

import com.coffeejournal.data.db.SettingEntity
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.nav.FormMode
import com.coffeejournal.ui.notify.MemorySettingsDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The record form's real-time draft: keys, what is kept, expiry, write order, and what counts as input. */
class RecordDraftsTest {
    private val now = Dates.toMillis(LocalDate(2026, 9, 28), 9, 0)
    private val dao = MemorySettingsDao()
    private val settings = SettingsRepository(dao)

    private fun typed(mode: String = FormMode.EXTRACT) =
        FormMapper.newState(mode, null, now).copy(name = "에티오피아 구지 함벨라", dose = "15", notes = "쥬시한 복숭아")

    @Test fun oneKeyPerNewRecordModeAndPerEditedRecord_allDeviceKeys() {
        val keys = listOf(
            RecordDrafts.keyFor(FormArgs(FormMode.EXTRACT)),
            RecordDrafts.keyFor(FormArgs(FormMode.CAFE)),
            RecordDrafts.keyFor(FormArgs(FormMode.CUPPING, cuppingType = "private")),
            RecordDrafts.keyFor(FormArgs(FormMode.EXTRACT, entryId = "e1")),
            RecordDrafts.keyFor(FormArgs(FormMode.CAFE, entryId = "e1")),
        )
        assertEquals(
            listOf(
                "device.draft.record.new.extract", "device.draft.record.new.cafe", "device.draft.record.new.cupping",
                "device.draft.record.edit.e1", "device.draft.record.edit.e1",
            ),
            keys,
        )
        // a backup neither writes nor restores them
        assertTrue(keys.all(SettingsRepository::isDeviceKey))
    }

    @Test fun roundTrip_keepsTheInput_notPickedPhotoBytes() = runTest {
        val drafts = RecordDrafts(settings, this)
        val key = RecordDrafts.keyFor(FormArgs())
        val state = typed().copy(
            bagPhotos = listOf(PhotoSlot(existingName = "stored.jpg"), PhotoSlot(pending = byteArrayOf(1, 2, 3))),
            error = FormError(null, "저장하지 못했어요"),
        )
        drafts.put(key, FormDrafts.draftOf(state, now)).join()
        val back = assertNotNull(drafts.load(key, now))
        assertEquals(now, back.savedAt)
        assertEquals(1, back.droppedPhotos, "the picked photo is counted, so the form can say it needs picking again")
        assertEquals(listOf(PhotoSlot(existingName = "stored.jpg"), PhotoSlot()), back.state.bagPhotos)
        assertNull(back.state.error, "errors are not kept")
        assertEquals(state.copy(bagPhotos = back.state.bagPhotos, error = null), back.state)
        // stored as small JSON text, no photo bytes
        val raw = assertNotNull(dao.rows.value[key])
        assertFalse(raw.contains("pending"))
    }

    @Test fun draftsOlderThanThirtyDays_areNotOffered_andAreDeleted() = runTest {
        val drafts = RecordDrafts(settings, this)
        val key = RecordDrafts.keyFor(FormArgs(FormMode.CAFE))
        drafts.put(key, FormDrafts.draftOf(typed(FormMode.CAFE), now)).join()
        assertNotNull(drafts.load(key, now + RecordDrafts.KEEP_DAYS * Dates.DAY_MS), "exactly 30 days: still offered")
        assertNull(drafts.load(key, now + RecordDrafts.KEEP_DAYS * Dates.DAY_MS + 1))
        assertNull(dao.rows.value[key], "an expired draft is deleted when it is found")
    }

    @Test fun unreadableDraft_isDropped() = runTest {
        val drafts = RecordDrafts(settings, this)
        val key = RecordDrafts.keyFor(FormArgs(entryId = "e7"))
        settings.put(key, "{not json")
        assertNull(drafts.load(key, now))
        assertNull(dao.rows.value[key])
    }

    @Test fun pruning_removesOnlyOldOrBrokenRecordDrafts() = runTest {
        val drafts = RecordDrafts(settings, this)
        val fresh = RecordDrafts.keyFor(FormArgs())
        val old = RecordDrafts.keyFor(FormArgs(entryId = "gone"))
        val broken = RecordDrafts.keyFor(FormArgs(FormMode.CUPPING))
        drafts.put(fresh, FormDrafts.draftOf(typed(), now - 2 * Dates.DAY_MS))
        drafts.put(old, FormDrafts.draftOf(typed(), now - 31 * Dates.DAY_MS))
        settings.put(broken, "?")
        settings.put("device.reminders.enabled", "true")
        settings.put(SettingsRepository.KEY_DDAY_START, "2026-01-01")
        drafts.pruneExpired(now).join()
        assertEquals(setOf(fresh, "device.reminders.enabled", SettingsRepository.KEY_DDAY_START), dao.rows.value.keys)
    }

    @Test fun writesLandInTheOrderTheyWereAskedFor() = runTest {
        // the first write is slow: a later write or delete must still land after it
        var slowNext = true
        val slow = object : com.coffeejournal.data.db.SettingsDao by dao {
            override suspend fun put(entity: SettingEntity) {
                if (slowNext) { slowNext = false; delay(500) }
                dao.put(entity)
            }
        }
        val drafts = RecordDrafts(SettingsRepository(slow), this)
        val key = RecordDrafts.keyFor(FormArgs())
        drafts.put(key, FormDrafts.draftOf(typed().copy(notes = "처음"), now))
        drafts.put(key, FormDrafts.draftOf(typed().copy(notes = "나중"), now))
        testScheduler.advanceUntilIdle()
        assertEquals("나중", drafts.load(key, now)?.state?.notes)

        slowNext = true
        drafts.put(key, FormDrafts.draftOf(typed().copy(notes = "지우기 전"), now))
        drafts.delete(key)
        testScheduler.advanceUntilIdle()
        assertNull(dao.rows.value[key], "a delete asked for after a write is not undone by it")
    }

    @Test fun whatCountsAsInput() {
        val opened = FormMapper.newState(FormMode.EXTRACT, null, now)
        fun changed(s: FormState) = FormDrafts.changed(opened, s)
        // what only the screen uses
        assertFalse(changed(opened))
        assertFalse(changed(opened.copy(openLauncher = RecipeLauncher.CAFE, flavorWheelOpen = true, calcOpen = true, calc = CalcForm(tds = "1.3"))))
        assertFalse(changed(opened.copy(autofillBanner = true, repeatBean = true, tempHint = "권장 범위: 88–92", draftId = "other")))
        assertFalse(changed(opened.copy(error = FormError(FormField.NAME, "원두 이름을 입력해주세요."), saving = true)))
        assertFalse(changed(opened.copy(cuppingBeans = opened.cuppingBeans.map { it.copy(evaluationOpen = true) })))
        // what the user wrote or picked
        assertTrue(changed(opened.copy(name = "케냐")))
        assertTrue(changed(opened.copy(createdAt = now - Dates.DAY_MS)), "a changed date counts")
        assertTrue(changed(opened.copy(expectedInput = "자두")), "a note still in the chip box counts")
        assertTrue(changed(opened.copy(bagPhotos = listOf(PhotoSlot(pending = byteArrayOf(9)), PhotoSlot()))))
        assertTrue(changed(opened.copy(steps = emptyList())), "clearing the example log counts")
    }

    @Test fun aReopenedDraft_startsWithPanelsClosed() {
        val draft = FormDrafts.draftOf(typed().copy(flavorWheelOpen = true, openLauncher = RecipeLauncher.MINE, calcOpen = true), now)
        val s = FormDrafts.reopened(draft, FormArgs(FormMode.EXTRACT))
        assertFalse(s.flavorWheelOpen)
        assertNull(s.openLauncher)
        assertTrue(s.calcOpen, "the calculator is part of the recipe card, it may stay open")
        assertEquals("에티오피아 구지 함벨라", s.name)
    }
}
