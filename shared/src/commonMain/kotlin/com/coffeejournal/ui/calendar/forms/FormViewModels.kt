package com.coffeejournal.ui.calendar.forms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.repo.StudyRepository
import com.coffeejournal.domain.model.Book
import com.coffeejournal.domain.model.BookStatus
import com.coffeejournal.domain.model.ClassType
import com.coffeejournal.domain.model.CoffeeClass
import com.coffeejournal.domain.model.Video
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Ids
import com.coffeejournal.ui.form.SavedFormState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * What the three study forms share: the typed input is kept in the SavedStateHandle (it survives process death), and
 * a save runs once — taps while it runs, or after it succeeded while the screen is closing, are ignored. A new item
 * keeps one id for the life of the form, so even a repeated save could only upsert the same row.
 */
abstract class StudyFormViewModel<S : Any>(savedState: SavedStateHandle?, key: String, serializer: KSerializer<S>, initial: S) : ViewModel() {
    private val kept = SavedFormState(savedState, key, serializer)
    protected val restored: S? = kept.restore()
    protected val mutableState = MutableStateFlow(restored ?: initial)
    val state: StateFlow<S> = mutableState.asStateFlow()

    init {
        kept.keep(viewModelScope, mutableState)
    }

    fun update(block: S.() -> S) = mutableState.update(block)

    /** Marks the state as saving and runs [write]; the mark is undone only when [write] fails, so the user can retry. */
    protected fun saveOnce(setSaving: (S, Boolean) -> S, write: suspend () -> Unit) {
        mutableState.update { setSaving(it, true) }
        viewModelScope.launch {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { setSaving(it, false) }
            }
        }
    }
}

// ----- 책 -----

@Serializable
data class BookFormState(
    /** The edited book's id, or the id this new book will be stored under. */
    val id: String = "",
    val isEdit: Boolean = false,
    val createdAt: Long = 0L,
    val title: String = "",
    val author: String = "",
    val status: String = BookStatus.READING,
    val startDate: String = "",
    val endDate: String = "",
    val rating: Int = 0,
    val notes: String = "",
    @Transient val titleError: Boolean = false,
    @Transient val saving: Boolean = false,
) {
    /** Web updateBookDateFields: dates only while reading or done, the end date only when done. */
    val showDates: Boolean get() = status == BookStatus.READING || status == BookStatus.DONE
    val showEndDate: Boolean get() = status == BookStatus.DONE
}

class BookFormViewModel(bookId: String?, private val repo: StudyRepository, savedState: SavedStateHandle? = null) : StudyFormViewModel<BookFormState>(
    savedState, "bookForm", BookFormState.serializer(), BookFormState(id = bookId ?: Ids.newId(), isEdit = bookId != null),
) {
    init {
        if (bookId != null && restored == null) viewModelScope.launch {
            repo.getBook(bookId)?.let { b ->
                mutableState.value = BookFormState(
                    id = b.id, isEdit = true, createdAt = b.createdAt, title = b.title, author = b.author,
                    status = b.status.ifBlank { BookStatus.READING }, startDate = b.startDate, endDate = b.endDate, rating = b.rating, notes = b.notes,
                )
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val s = state.value
        if (s.saving) return
        if (s.title.isBlank()) { update { copy(titleError = true) }; return }
        val book = Book(
            id = s.id.ifBlank { Ids.newId() },
            createdAt = s.createdAt.takeIf { it > 0 } ?: Dates.nowMillis(),
            title = s.title.trim(),
            author = s.author.trim(),
            status = s.status.ifBlank { BookStatus.READING },
            startDate = s.startDate,
            endDate = if (s.status == BookStatus.DONE) s.endDate else "",
            rating = s.rating,
            notes = s.notes.trim(),
        )
        saveOnce({ st, v -> st.copy(saving = v) }) { repo.upsertBook(book); onSaved() }
    }
}

// ----- 동영상 -----

@Serializable
data class VideoFormState(
    val id: String = "",
    val isEdit: Boolean = false,
    val createdAt: Long = 0L,
    val title: String = "",
    val channel: String = "",
    val url: String = "",
    val notes: String = "",
    @Transient val titleError: Boolean = false,
    @Transient val saving: Boolean = false,
)

class VideoFormViewModel(videoId: String?, private val repo: StudyRepository, savedState: SavedStateHandle? = null) : StudyFormViewModel<VideoFormState>(
    savedState, "videoForm", VideoFormState.serializer(), VideoFormState(id = videoId ?: Ids.newId(), isEdit = videoId != null),
) {
    init {
        if (videoId != null && restored == null) viewModelScope.launch {
            repo.getVideo(videoId)?.let { v ->
                mutableState.value = VideoFormState(id = v.id, isEdit = true, createdAt = v.createdAt, title = v.title, channel = v.channel, url = v.url, notes = v.notes)
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val s = state.value
        if (s.saving) return
        if (s.title.isBlank()) { update { copy(titleError = true) }; return }
        val video = Video(
            id = s.id.ifBlank { Ids.newId() },
            createdAt = s.createdAt.takeIf { it > 0 } ?: Dates.nowMillis(),
            title = s.title.trim(), channel = s.channel.trim(), url = s.url.trim(), notes = s.notes.trim(),
        )
        saveOnce({ st, v -> st.copy(saving = v) }) { repo.upsertVideo(video); onSaved() }
    }
}

// ----- 클래스 -----

@Serializable
data class ClassFormState(
    val id: String = "",
    val isEdit: Boolean = false,
    val createdAt: Long = 0L,
    val title: String = "",
    val classType: String = ClassType.ONEDAY,
    val date: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val notes: String = "",
    @Transient val titleError: Boolean = false,
    @Transient val saving: Boolean = false,
) {
    val isRecurring: Boolean get() = classType == ClassType.RECURRING
}

class ClassFormViewModel(classId: String?, private val repo: StudyRepository, savedState: SavedStateHandle? = null) : StudyFormViewModel<ClassFormState>(
    savedState, "classForm", ClassFormState.serializer(), ClassFormState(id = classId ?: Ids.newId(), isEdit = classId != null),
) {
    init {
        if (classId != null && restored == null) viewModelScope.launch {
            repo.getClass(classId)?.let { c ->
                mutableState.value = ClassFormState(
                    id = c.id, isEdit = true, createdAt = c.createdAt, title = c.title,
                    classType = if (c.classType == ClassType.RECURRING) ClassType.RECURRING else ClassType.ONEDAY,
                    date = c.date, startDate = c.startDate, endDate = c.endDate, notes = c.notes,
                )
            }
        }
    }

    /** Web save-class: one-day keeps only `date`, recurring keeps only the start/end pair. */
    fun save(onSaved: () -> Unit) {
        val s = state.value
        if (s.saving) return
        if (s.title.isBlank()) { update { copy(titleError = true) }; return }
        val cls = CoffeeClass(
            id = s.id.ifBlank { Ids.newId() },
            createdAt = s.createdAt.takeIf { it > 0 } ?: Dates.nowMillis(),
            title = s.title.trim(),
            classType = s.classType,
            date = if (s.isRecurring) "" else s.date,
            startDate = if (s.isRecurring) s.startDate else "",
            endDate = if (s.isRecurring) s.endDate else "",
            notes = s.notes.trim(),
        )
        saveOnce({ st, v -> st.copy(saving = v) }) { repo.upsertClass(cls); onSaved() }
    }
}
