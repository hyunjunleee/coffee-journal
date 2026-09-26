package com.coffeejournal.ui.notify

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.domain.rules.ReminderKind
import com.coffeejournal.domain.rules.Reminders
import com.coffeejournal.ui.form.TextLink
import com.coffeejournal.ui.platform.rememberNotificationPermissionRequest
import com.coffeejournal.ui.theme.AppTimePickerDialog
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.MinTouchTarget
import com.coffeejournal.ui.theme.PickerBox
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.SectionLabel
import org.koin.compose.viewmodel.koinViewModel

/** 알림 설정: the master switch, one switch per reminder kind and the time of the daily check. */
@Composable
fun ReminderSettingsScreen(nav: NavHostController) {
    val vm = koinViewModel<ReminderSettingsViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val askPermission = rememberNotificationPermissionRequest(vm::onPermissionResult)
    // back from the phone's settings, the permission may have changed
    LifecycleResumeEffect(vm) {
        vm.refreshPermission()
        onPauseOrDispose { }
    }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val settings = state.settings

    Column(Modifier.fillMaxSize()) {
        ScreenTitleBar("알림 설정", onBack = { nav.popBackStack() })
        // nothing until the saved settings are read: the switches would flash their defaults first
        if (!state.loaded) return@Column
        Column(
            Modifier
                .fillMaxSize()
                .testTag("reminder-settings")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter)
                .padding(top = 14.dp, bottom = 96.dp),
        ) {
            Text(ReminderTexts.INTRO, style = AppType.bodyMuted)
            Spacer(Modifier.height(10.dp))
            SettingSwitch(
                title = "알림 받기",
                hint = if (settings.enabled) "매일 ${settings.time}쯤 확인해요." else null,
                checked = settings.enabled,
                onChange = { on ->
                    when {
                        !on -> vm.turnOff()
                        vm.needsPermission() -> askPermission()
                        else -> vm.turnOn()
                    }
                },
            )
            when {
                state.refused -> PermissionHint(ReminderTexts.REFUSED, vm::openNotificationSettings)
                settings.enabled && !state.canNotify -> PermissionHint(ReminderTexts.BLOCKED, vm::openNotificationSettings)
            }

            SectionLabel("알림 종류")
            SettingSwitch("피크 시작", ReminderTexts.PEAK, settings.kinds.peak, { vm.setKind(ReminderKind.PEAK, it) })
            SettingSwitch("원두 소진 임박", ReminderTexts.LOW_STOCK, settings.kinds.lowStock, { vm.setKind(ReminderKind.LOW_STOCK, it) })
            SettingSwitch("D-day 마일스톤", ReminderTexts.DDAY, settings.kinds.dday, { vm.setKind(ReminderKind.DDAY, it) })

            SectionLabel("알림 시각")
            PickerBox(settings.time.toString(), onClick = { pickingTime = true })
            HintText(ReminderTexts.TIME_HINT)
        }
    }

    if (pickingTime) {
        AppTimePickerDialog(
            hour = settings.time.hour,
            minute = settings.time.minute,
            onDismiss = { pickingTime = false },
            onPick = { hour, minute ->
                vm.setTime(ReminderTime(hour, minute))
                pickingTime = false
            },
        )
    }
}

/** The screen's copy, in the app's tone (also read by the tests). */
object ReminderTexts {
    const val INTRO = "하루 한 번 정한 시각에 보관함과 마시는 중인 원두, Coffee D-day를 살펴보고 알려드려요. 같은 알림은 두 번 보내지 않아요."
    const val PEAK = "보관함 원두의 예상 피크가 시작되는 날"
    val LOW_STOCK = "마시는 중인 원두가 평소 원두량으로 ${Reminders.LOW_STOCK_CUPS}잔 이하 남았을 때"
    const val DDAY = "Coffee D-day 30일·100일 단위 기념일"
    const val TIME_HINT = "휴대폰이 절전 중이면 조금 늦게 올 수 있어요."
    const val REFUSED = "알림 권한을 허용하지 않아서 켜지 않았어요. 휴대폰 설정에서 이 앱의 알림을 허용한 뒤 다시 켜 주세요."
    const val BLOCKED = "휴대폰 설정에서 이 앱의 알림이 꺼져 있어서 지금은 알림이 오지 않아요."
    const val OPEN_SETTINGS = "알림 설정 열기 →"
}

@Composable
private fun PermissionHint(text: String, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(text, style = AppType.small.copy(color = Ink.bad))
        TextLink(ReminderTexts.OPEN_SETTINGS, Ink.text, onOpenSettings)
    }
}

/** A setting that is on or off: the whole row toggles (TalkBack reads it as a switch with its state). */
@Composable
internal fun SettingSwitch(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = AppType.body)
                if (hint != null) Text(hint, style = AppType.small)
            }
            Spacer(Modifier.width(12.dp))
            SquareSwitch(checked)
        }
        Hairline()
    }
}

/** The archive look of a switch: square, hairline, an ink track when on. */
@Composable
private fun SquareSwitch(on: Boolean) {
    Box(
        Modifier
            .size(width = 40.dp, height = 22.dp)
            .background(if (on) Ink.accent else Ink.surface)
            .border(BorderStroke(Dimens.hairline, if (on) Ink.accent else Ink.line), RectangleShape)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(16.dp).background(if (on) Ink.bg else Ink.line))
    }
}
