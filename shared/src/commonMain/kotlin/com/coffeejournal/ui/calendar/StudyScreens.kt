package com.coffeejournal.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.Book
import com.coffeejournal.domain.model.Video
import com.coffeejournal.ui.calendar.components.ConfirmDialog
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.platform.openUrl
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Badge
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.SubTabs
import org.koin.compose.viewmodel.koinViewModel

/** 스터디 chip: [책][동영상] lists with add / edit / confirmed delete (web #study-view). */
@Composable
internal fun StudyView(nav: NavHostController) {
    val vm = koinViewModel<StudyViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val books = state.tab == StudyViews.BOOKS
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.gutter)) {
        item(key = "tabs") { SubTabs(StudyViews.all, selected = state.tab, onSelect = vm::setTab, modifier = Modifier.padding(bottom = 16.dp)) }
        item(key = "add-${state.tab}") {
            PrimaryButton(
                if (books) "+ 책 추가" else "+ 동영상 추가",
                modifier = Modifier.fillMaxWidth(),
                onClick = { nav.navigate(if (books) Route.BookForm() else Route.VideoForm()) },
            )
            SectionLabel(if (books) "읽은 · 읽는 책" else "본 · 볼 동영상")
        }
        if (books) {
            if (state.books.isEmpty()) item(key = "empty-books") { EmptyNote("아직 기록한 책이 없습니다.") }
            items(state.books, key = { "book-" + it.id }) { b ->
                BookCard(b, onEdit = { nav.navigate(Route.BookForm(b.id)) }, onDelete = { vm.deleteBook(b.id) }, modifier = Modifier.padding(bottom = 8.dp))
            }
        } else {
            if (state.videos.isEmpty()) item(key = "empty-videos") { EmptyNote("아직 기록한 동영상이 없습니다.") }
            items(state.videos, key = { "video-" + it.id }) { v ->
                VideoCard(v, onEdit = { nav.navigate(Route.VideoForm(v.id)) }, onDelete = { vm.deleteVideo(v.id) }, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(96.dp)) }
    }
}

/** Web bookHtml: title, author, ★ rating, status badge, reading dates, memo. */
@Composable
private fun BookCard(book: Book, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    HairlineCard(modifier) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(book.title.ifBlank { "제목 없음" }, style = AppType.cardTitle)
                if (book.author.isNotBlank()) Text(book.author, style = AppType.small)
            }
            if (book.rating > 0) {
                val n = book.rating.coerceIn(0, 5)
                Text("★".repeat(n) + "☆".repeat(5 - n), style = AppType.monoValue.copy(color = Ink.accent))
            }
        }
        if (book.status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row { Badge(book.status) }
        }
        val dates = listOfNotNull(
            book.startDate.takeIf { it.isNotBlank() }?.let { "시작 $it" },
            book.endDate.takeIf { it.isNotBlank() }?.let { "읽음 $it" },
        ).joinToString(" · ")
        if (dates.isNotEmpty()) Text(dates, style = AppType.faint, modifier = Modifier.padding(top = 6.dp))
        if (book.notes.isNotBlank()) Text(book.notes, style = AppType.bodyMuted, modifier = Modifier.padding(top = 8.dp))
        CardActions(onEdit = onEdit, onDelete = onDelete, confirmText = "\"${book.title.ifBlank { "제목 없음" }}\" 책 기록을 삭제할까요?")
    }
}

/** Web video card: linked title (opens the URL), channel, memo. */
@Composable
private fun VideoCard(video: Video, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    HairlineCard(modifier) {
        val hasUrl = video.url.isNotBlank()
        Text(
            video.title,
            style = if (hasUrl) AppType.cardTitle.copy(color = Ink.accent, textDecoration = TextDecoration.Underline) else AppType.cardTitle,
            modifier = if (hasUrl) Modifier.clickable { openUrl(video.url) } else Modifier,
        )
        if (video.channel.isNotBlank()) Text(video.channel, style = AppType.small)
        if (video.notes.isNotBlank()) Text(video.notes, style = AppType.bodyMuted, modifier = Modifier.padding(top = 8.dp))
        CardActions(onEdit = onEdit, onDelete = onDelete, confirmText = "\"${video.title}\" 동영상 기록을 삭제할까요?")
    }
}

/** [수정][삭제] row shared by the study and class cards; delete asks first. */
@Composable
internal fun CardActions(onEdit: () -> Unit, onDelete: () -> Unit, confirmText: String) {
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.padding(top = 10.dp)) {
        GhostButton("수정", onClick = onEdit, small = true)
        Spacer(Modifier.width(8.dp))
        GhostButton("삭제", onClick = { confirm = true }, small = true, danger = true)
    }
    if (confirm) ConfirmDialog(title = "삭제", text = confirmText, onConfirm = onDelete, onDismiss = { confirm = false })
}
