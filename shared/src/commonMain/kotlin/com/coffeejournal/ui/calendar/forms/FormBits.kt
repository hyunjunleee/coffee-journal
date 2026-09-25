package com.coffeejournal.ui.calendar.forms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coffeejournal.ui.form.imeOverlapPadding
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar

/**
 * Title bar, scrolling fields, then [저장][취소] (web .form-actions). The scroll area ends at the keyboard, so the
 * focused field stays above it; 저장 is disabled while [saving].
 */
@Composable
internal fun FormScaffold(title: String, onBack: () -> Unit, onSave: () -> Unit, saving: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitleBar(title = title, onBack = onBack)
        Column(Modifier.weight(1f).imeOverlapPadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter).padding(top = 12.dp)) {
            content()
            Spacer(Modifier.height(24.dp))
            Row {
                PrimaryButton("저장", onClick = onSave, enabled = !saving, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                GhostButton("취소", onClick = onBack, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
internal fun RequiredHint(show: Boolean, text: String) {
    if (show) Text(text, style = AppType.faint.copy(color = Ink.bad), modifier = Modifier.padding(top = 4.dp))
}

/** Five tappable stars, whole numbers only; tapping the current value again clears it (web #bk-rating-row). */
@Composable
internal fun StarRating(rating: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier) {
        (1..5).forEach { v ->
            Text(
                "★",
                style = AppType.body.copy(fontSize = 26.sp, color = if (v <= rating) Ink.accent else Ink.line),
                modifier = Modifier.clickable { onChange(if (v == rating) 0 else v) }.padding(end = 6.dp),
            )
        }
    }
    HintText("별을 다시 누르면 해제돼요")
}
