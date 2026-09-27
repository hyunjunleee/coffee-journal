package com.coffeejournal.ui.notify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.domain.rules.ReminderKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ReminderSettingsUi(
    val loaded: Boolean = false,
    val settings: ReminderSettings = ReminderSettings(),
    /** Notifications can be shown right now (permission granted, app notifications on). */
    val canNotify: Boolean = true,
    /** The user just refused the permission, so the switch stayed off. */
    val refused: Boolean = false,
)

/**
 * 알림 설정. Turning reminders on needs notifications to be allowed first: the screen asks the platform
 * ([needsPermission] → the permission prompt → [onPermissionResult]); a refusal leaves the switch off with a hint.
 * Every change is saved and the daily check is (re)scheduled in the order the taps came.
 */
class ReminderSettingsViewModel(private val prefs: ReminderPrefs, private val platform: ReminderPlatform) : ViewModel() {
    private val canNotify = MutableStateFlow(platform.canNotify())
    private val refused = MutableStateFlow(false)
    private val writes = Mutex()

    val state: StateFlow<ReminderSettingsUi> = combine(prefs.observe(), canNotify, refused) { s, can, no ->
        ReminderSettingsUi(loaded = true, settings = s, canNotify = can, refused = no && !can)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSettingsUi())

    init {
        // iOS learns the authorization asynchronously: the screen follows it when it arrives
        viewModelScope.launch { platform.canNotifyChanges().collect { canNotify.value = it } }
    }

    /** Turning reminders on has to go through the permission prompt first. */
    fun needsPermission(): Boolean = !platform.canNotify()

    fun turnOn() {
        refused.value = false
        write {
            prefs.setEnabled(true)
            platform.schedule(prefs.load().time)
        }
    }

    fun turnOff() = write {
        prefs.setEnabled(false)
        platform.cancel()
    }

    fun onPermissionResult(granted: Boolean) {
        refreshPermission()
        if (granted && platform.canNotify()) turnOn() else refused.value = true
    }

    fun setKind(kind: ReminderKind, on: Boolean) = write { prefs.setKind(kind, on) }

    /** A new time replaces the running schedule; while reminders are off it is only saved. */
    fun setTime(time: ReminderTime) = write {
        prefs.setTime(time)
        if (prefs.load().enabled) platform.schedule(time)
    }

    /** Called when the screen comes back (the user may have changed the permission in the phone's settings). */
    fun refreshPermission() {
        canNotify.value = platform.canNotify()
    }

    fun openNotificationSettings() = platform.openNotificationSettings()

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch { writes.withLock { block() } }
    }
}
