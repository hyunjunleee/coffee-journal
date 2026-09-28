package com.coffeejournal.ui.form

import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.rules.Dates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The record form as it was being written: the input and when it was last written. Picked bag photos are bytes the
 * settings table does not hold, so a slot with an unsaved photo comes back empty; [droppedPhotos] counts them, and the
 * form says they need picking again.
 */
@Serializable
internal data class RecordDraft(val savedAt: Long, val state: FormState, val droppedPhotos: Int = 0)

/** What the form shows after opening with a draft: the banner, with the photo note when photos were left out. */
data class RestoredDraft(val droppedPhotos: Int = 0)

/**
 * The record form's real-time draft (an accidental back or a closed app must not lose what was typed). The input is
 * written to the settings table a moment after each change, under a device key ([SettingsRepository.DEVICE_PREFIX]):
 * it outlives the screen and the app process, and a backup neither carries nor restores it. There is one draft per
 * new-record mode ([keyFor] `new.<mode>`) and one per edited record (`edit.<id>`); drafts not written for
 * [KEEP_DAYS] days are not offered and are deleted.
 *
 * Writes run one at a time in the order they were asked for, on [scope] (the app's), so the last one still lands
 * after the form has closed.
 */
class RecordDrafts(private val settings: SettingsRepository, private val scope: CoroutineScope) {
    private val lock = Mutex()

    /** The draft under [key], or null: none, unreadable (an older app version wrote it) or expired, which is deleted. */
    internal suspend fun load(key: String, now: Long = Dates.nowMillis()): RecordDraft? = lock.withLock {
        val raw = settings.get(key) ?: return@withLock null
        val draft = decode(raw)
        if (draft == null || expired(draft, now)) {
            settings.delete(key)
            null
        } else draft
    }

    internal fun put(key: String, draft: RecordDraft): Job = queue { settings.put(key, encode(draft)) }

    fun delete(key: String): Job = queue { settings.delete(key) }

    /** Deletes every record draft that is unreadable or older than [KEEP_DAYS] (a form never opened again). */
    fun pruneExpired(now: Long = Dates.nowMillis()): Job = queue {
        settings.getAll().forEach { (key, raw) ->
            if (key.startsWith(PREFIX) && decode(raw)?.let { !expired(it, now) } != true) settings.delete(key)
        }
    }

    /**
     * Starts [block] right away on the caller's thread up to the lock (the mutex serves waiters first come, first
     * served), so writes asked for one after another land in that order.
     */
    private fun queue(block: suspend () -> Unit): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        lock.withLock {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // a draft that cannot be written now is written again at the next change
            }
        }
    }

    companion object {
        const val PREFIX = SettingsRepository.DEVICE_PREFIX + "draft.record."
        const val KEEP_DAYS = 30

        /** `device.draft.record.new.<mode>` for a new record, `device.draft.record.edit.<entry id>` for an edit. */
        fun keyFor(args: FormArgs): String = args.entryId?.let { PREFIX + "edit." + it } ?: (PREFIX + "new." + args.mode)

        private val json = Json { ignoreUnknownKeys = true }

        internal fun encode(draft: RecordDraft): String = json.encodeToString(RecordDraft.serializer(), draft)

        internal fun decode(raw: String): RecordDraft? = runCatching { json.decodeFromString(RecordDraft.serializer(), raw) }.getOrNull()

        internal fun expired(draft: RecordDraft, now: Long): Boolean = now - draft.savedAt > KEEP_DAYS * Dates.DAY_MS
    }
}

/** What counts as the user's input when the record form asks before leaving and when it keeps a draft. */
internal object FormDrafts {
    /**
     * [state] without what only the screen uses: open panels and banners, the calculator (never saved), the id a new
     * record will get, errors and the saving flag. Unfolding a café's recipe is not input either: its values are.
     */
    fun content(state: FormState): FormState = state.copy(
        draftId = "",
        cuppingBeans = state.cuppingBeans.map { it.copy(evaluationOpen = false) },
        tempHint = "",
        cafeRecipeOpen = false,
        cafeRecipeUsed = false,
        calcOpen = false,
        calc = CalcForm(),
        autofillBanner = false,
        repeatBean = false,
        openLauncher = null,
        flavorWheelOpen = false,
        error = null,
        saving = false,
    )

    /** Whether [state] holds input that [opened] (the form as it was opened) did not. */
    fun changed(opened: FormState, state: FormState): Boolean = content(state) != content(opened)

    /** The draft to keep for [state]; a picked photo's bytes stay out ([PhotoSlot.pending] is not serialized). */
    fun draftOf(state: FormState, now: Long): RecordDraft =
        RecordDraft(savedAt = now, state = state, droppedPhotos = state.bagPhotos.count { it.pending != null })

    /** A kept draft as the form reopens with it: panels that were open while it was written start closed. */
    fun reopened(draft: RecordDraft, args: FormArgs): FormState =
        draft.state.copy(mode = args.mode, openLauncher = null, flavorWheelOpen = false, error = null, saving = false)
}
