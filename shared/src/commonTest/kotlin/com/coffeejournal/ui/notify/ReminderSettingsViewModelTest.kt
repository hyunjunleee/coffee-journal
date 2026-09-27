package com.coffeejournal.ui.notify

import com.coffeejournal.data.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A platform that learns whether it may notify some time after it is asked, as iOS does. */
private class LateAnswerPlatform : ReminderPlatform {
    val allowed = MutableStateFlow(false)
    val scheduled = mutableListOf<ReminderTime>()

    override suspend fun schedule(time: ReminderTime) {
        scheduled += time
    }

    override suspend fun cancel() {}

    override fun canNotify(): Boolean = allowed.value

    override fun canNotifyChanges(): Flow<Boolean> = allowed

    override fun openNotificationSettings() {}
}

/** 알림 설정 follows a permission answer that arrives on its own (iOS reads it asynchronously). */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSettingsViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun theScreenFollowsTheAuthorization_whenItArrivesLater() = runTest {
        val platform = LateAnswerPlatform()
        val prefs = ReminderPrefs(SettingsRepository(MemorySettingsDao()))
        prefs.setEnabled(true)
        val vm = ReminderSettingsViewModel(prefs, platform)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        // on, but not allowed (yet): the "turned off in the phone's settings" hint shows
        assertTrue(vm.state.value.settings.enabled)
        assertFalse(vm.state.value.canNotify)
        assertTrue(vm.needsPermission())
        platform.allowed.value = true
        runCurrent()
        assertTrue(vm.state.value.canNotify)
        assertFalse(vm.needsPermission())
        // allowed after the prompt: the switch goes on and the reminders are scheduled
        vm.onPermissionResult(true)
        runCurrent()
        assertEquals(listOf(ReminderTime.DEFAULT), platform.scheduled)
        platform.allowed.value = false
        runCurrent()
        assertFalse(vm.state.value.canNotify)
    }
}
