package com.coffeejournal.ui.calendar.forms

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ----- 책 -----

internal data class BookFormState(
    val existing: Book? = null,
    val title: String = "",
    val author: String = "",
    val status: String = BookStatus.READING,
    val startDate: String = "",
    val endDate: String = "",
    val rating: Int = 0,
    val notes: String = "",
    val titleError: Boolean = false,
) {
    val isEdit: Boolean get() = existing != null
    /** Web updateBookDateFields: dates only while reading or done, the end date only when done. */
    val showDates: Boolean get() = status == BookStatus.READING || status == BookStatus.DONE
    val showEndDate: Boolean get() = status == BookStatus.DONE
}

class BookFormViewModel(private val bookId: String?, private val repo: StudyRepository) : ViewModel() {
    private val _state = MutableStateFlow(BookFormState())
    internal val state: StateFlow<BookFormState> = _state.asStateFlow()

    init {
        bookId?.let { id ->
            viewModelScope.launch {
                repo.getBook(id)?.let { b ->
                    _state.value = BookFormState(
                        existing = b, title = b.title, author = b.author, status = b.status.ifBlank { BookStatus.READING },
                        startDate = b.startDate, endDate = b.endDate, rating = b.rating, notes = b.notes,
                    )
                }
            }
        }
    }

    internal fun update(block: BookFormState.() -> BookFormState) = _state.update(block)

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (s.title.isBlank()) { _state.update { it.copy(titleError = true) }; return }
        val book = Book(
            id = s.existing?.id ?: Ids.newId(),
            createdAt = s.existing?.createdAt ?: Dates.nowMillis(),
            title = s.title.trim(),
            author = s.author.trim(),
            status = s.status.ifBlank { BookStatus.READING },
            startDate = s.startDate,
            endDate = if (s.status == BookStatus.DONE) s.endDate else "",
            rating = s.rating,
            notes = s.notes.trim(),
        )
        viewModelScope.launch { repo.upsertBook(book); onSaved() }
    }
}

// ----- 동영상 -----

internal data class VideoFormState(
    val existing: Video? = null,
    val title: String = "",
    val channel: String = "",
    val url: String = "",
    val notes: String = "",
    val titleError: Boolean = false,
) {
    val isEdit: Boolean get() = existing != null
}

class VideoFormViewModel(private val videoId: String?, private val repo: StudyRepository) : ViewModel() {
    private val _state = MutableStateFlow(VideoFormState())
    internal val state: StateFlow<VideoFormState> = _state.asStateFlow()

    init {
        videoId?.let { id ->
            viewModelScope.launch {
                repo.getVideo(id)?.let { v -> _state.value = VideoFormState(existing = v, title = v.title, channel = v.channel, url = v.url, notes = v.notes) }
            }
        }
    }

    internal fun update(block: VideoFormState.() -> VideoFormState) = _state.update(block)

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (s.title.isBlank()) { _state.update { it.copy(titleError = true) }; return }
        val video = Video(
            id = s.existing?.id ?: Ids.newId(),
            createdAt = s.existing?.createdAt ?: Dates.nowMillis(),
            title = s.title.trim(), channel = s.channel.trim(), url = s.url.trim(), notes = s.notes.trim(),
        )
        viewModelScope.launch { repo.upsertVideo(video); onSaved() }
    }
}

// ----- 클래스 -----

internal data class ClassFormState(
    val existing: CoffeeClass? = null,
    val title: String = "",
    val classType: String = ClassType.ONEDAY,
    val date: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val notes: String = "",
    val titleError: Boolean = false,
) {
    val isEdit: Boolean get() = existing != null
    val isRecurring: Boolean get() = classType == ClassType.RECURRING
}

class ClassFormViewModel(private val classId: String?, private val repo: StudyRepository) : ViewModel() {
    private val _state = MutableStateFlow(ClassFormState())
    internal val state: StateFlow<ClassFormState> = _state.asStateFlow()

    init {
        classId?.let { id ->
            viewModelScope.launch {
                repo.getClass(id)?.let { c ->
                    _state.value = ClassFormState(
                        existing = c, title = c.title,
                        classType = if (c.classType == ClassType.RECURRING) ClassType.RECURRING else ClassType.ONEDAY,
                        date = c.date, startDate = c.startDate, endDate = c.endDate, notes = c.notes,
                    )
                }
            }
        }
    }

    internal fun update(block: ClassFormState.() -> ClassFormState) = _state.update(block)

    /** Web save-class: one-day keeps only `date`, recurring keeps only the start/end pair. */
    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (s.title.isBlank()) { _state.update { it.copy(titleError = true) }; return }
        val cls = CoffeeClass(
            id = s.existing?.id ?: Ids.newId(),
            createdAt = s.existing?.createdAt ?: Dates.nowMillis(),
            title = s.title.trim(),
            classType = s.classType,
            date = if (s.isRecurring) "" else s.date,
            startDate = if (s.isRecurring) s.startDate else "",
            endDate = if (s.isRecurring) s.endDate else "",
            notes = s.notes.trim(),
        )
        viewModelScope.launch { repo.upsertClass(cls); onSaved() }
    }
}
