package com.coffeejournal.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink

/** Web "먹어볼 원두" (renderBeansToTry): cupping records that carry an overall review. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CuppingReviewsSection(reviews: List<Entry>, onOpen: (Entry) -> Unit) {
    if (reviews.isEmpty()) {
        EmptyNote("전체 리뷰가 적힌 커핑 기록이 아직 없어요.\n커핑의 전체 경험을 기록하면 이곳에 연결돼요.")
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        reviews.forEach { en ->
            val beans = en.cuppingBeans.map { it.name }.filter { it.isNotBlank() }
            HairlineCard(onClick = { onOpen(en) }) {
                Text(Dates.koreanLong(Dates.toLocalDate(en.createdAt)), style = AppType.monoSmall)
                Spacer(Modifier.height(4.dp))
                Text(en.cuppingPlace.ifBlank { en.name.ifBlank { "커핑 기록" } }, style = AppType.cardTitle)
                Spacer(Modifier.height(6.dp))
                Text(CalendarGrid.reviewExcerpt(en.notes), style = AppType.bodyMuted)
                if (beans.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        beans.forEach { Chip(it) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("전체 커핑 리뷰에서 보기 →", style = AppType.small.copy(color = Ink.accent))
            }
        }
    }
}
