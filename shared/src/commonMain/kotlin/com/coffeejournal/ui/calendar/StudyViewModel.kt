package com.coffeejournal.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.repo.StudyRepository
import com.coffeejournal.domain.model.Book
import com.coffeejournal.domain.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal object StudyViews {
    const val BOOKS = "책"
    const val VIDEOS = "동영상"
    val all = listOf(BOOKS, VIDEOS)
}

internal data class StudyUiState(
    val tab: String = StudyViews.BOOKS,
    val books: List<Book> = emptyList(),
    val videos: List<Video> = emptyList(),
)

/** 스터디 view: books and videos, newest first (web renderBooks / renderVideos). */
class StudyViewModel(private val repo: StudyRepository) : ViewModel() {
    private val tab = MutableStateFlow(StudyViews.BOOKS)

    internal val state: StateFlow<StudyUiState> = combine(tab, repo.observeBooks(), repo.observeVideos()) { t, books, videos ->
        StudyUiState(t, books.sortedByDescending { it.createdAt }, videos.sortedByDescending { it.createdAt })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyUiState())

    fun setTab(value: String) { tab.value = value }
    fun deleteBook(id: String) { viewModelScope.launch { repo.deleteBook(id) } }
    fun deleteVideo(id: String) { viewModelScope.launch { repo.deleteVideo(id) } }
}
