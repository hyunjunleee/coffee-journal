package com.coffeejournal.ui.theme

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Asks before a form with unsaved input is left by mistake: system back, the title-bar back and 취소 all go through
 * [request]. Nothing changed since the form was opened → it closes at once; otherwise [asking] turns on and the
 * form's dialog ([LeaveDialog], or the record form's own) asks first. While the form is busy (its save is running, or
 * it succeeded and the screen is closing) requests are ignored; [BlockBackWhile] keeps system back from leaving then.
 */
@Stable
class LeaveGuard internal constructor(private val askingState: MutableState<Boolean>) {
    /** The dialog is up. */
    val asking: Boolean get() = askingState.value

    internal var hasChanges: () -> Boolean = { false }
    internal var close: () -> Unit = {}
    internal var busy: Boolean = false
    internal var inFront: () -> Boolean = { true }

    /** Back or 취소. Only while the screen is in front: a second tap during the exit transition does nothing. */
    fun request() {
        if (busy || !inFront()) return
        if (hasChanges()) askingState.value = true else close()
    }

    /** 계속 쓰기, or the dialog dismissed (outside tap, back): the form stays as it is. */
    fun stay() { askingState.value = false }

    /** 나가기: closes the form without asking again (after whatever the chosen button does first). */
    fun leave() {
        askingState.value = false
        close()
    }
}

/**
 * A [LeaveGuard] for this screen. [hasChanges] compares the input with the form as it was opened (the loaded record,
 * or a new form's starting values), so backing out of an untouched form never asks. [leave] closes the form. With
 * [systemBack] the guard also takes the system back gesture; an inline form inside a screen leaves that to the screen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberLeaveGuard(hasChanges: () -> Boolean, busy: Boolean, leave: () -> Unit, systemBack: Boolean = true): LeaveGuard {
    val asking = rememberSaveable { mutableStateOf(false) }
    val guard = remember(asking) { LeaveGuard(asking) }
    val currentHasChanges by rememberUpdatedState(hasChanges)
    val currentLeave by rememberUpdatedState(leave)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    guard.hasChanges = { currentHasChanges() }
    guard.close = { currentLeave() }
    guard.busy = busy
    guard.inFront = { lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
    if (systemBack) BackHandler(enabled = !busy) { guard.request() }
    return guard
}

/**
 * The question for a form whose input is gone once it is left: [계속 쓰기] [나가기]. [text] and [leaveLabel] fit an
 * inline form that is closed rather than left.
 */
@Composable
fun LeaveDialog(guard: LeaveGuard, text: String = LeaveTexts.DISCARD_BODY, leaveLabel: String = LeaveTexts.LEAVE) {
    if (!guard.asking) return
    AlertDialog(
        onDismissRequest = guard::stay,
        shape = RectangleShape, containerColor = Ink.bg,
        modifier = Modifier.testTag("leave-dialog"),
        title = { Text(LeaveTexts.DISCARD_TITLE, style = AppType.title) },
        text = { Text(text, style = AppType.body) },
        confirmButton = { PrimaryButton(LeaveTexts.STAY, small = true, onClick = guard::stay) },
        dismissButton = { GhostButton(leaveLabel, small = true, danger = true, onClick = guard::leave) },
    )
}

/** Copy of the leave question, shared by every form and the tests. */
object LeaveTexts {
    const val DISCARD_TITLE = "저장하지 않은 내용이 있어요"
    const val DISCARD_BODY = "지금 나가면 입력한 내용은 사라져요."
    const val STAY = "계속 쓰기"
    const val LEAVE = "나가기"
}
