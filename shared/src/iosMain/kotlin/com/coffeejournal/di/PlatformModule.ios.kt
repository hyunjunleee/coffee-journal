package com.coffeejournal.di

import com.coffeejournal.data.db.AppDatabase
import com.coffeejournal.data.db.buildAppDatabase
import com.coffeejournal.data.db.iosDatabaseBuilder
import com.coffeejournal.data.photo.IosPhotoStore
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.ui.ai.AiHttp
import com.coffeejournal.ui.ai.IosAiHttp
import com.coffeejournal.ui.ai.IosSecretStore
import com.coffeejournal.ui.ai.SecretStore
import com.coffeejournal.ui.map.search.CurrentLocation
import com.coffeejournal.ui.map.search.DevicePlaceSearch
import com.coffeejournal.ui.map.search.IosCurrentLocation
import com.coffeejournal.ui.map.search.IosPlaceSearch
import com.coffeejournal.ui.notify.IosNotifications
import com.coffeejournal.ui.notify.IosReminderPlatform
import com.coffeejournal.ui.notify.ReminderCheck
import com.coffeejournal.ui.notify.ReminderPlatform
import com.coffeejournal.ui.notify.ReminderScheduleAhead
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<AppDatabase> { iosDatabaseBuilder().buildAppDatabase() }
    single<PhotoStore> { IosPhotoStore() }
    // reminders: scheduled ahead as local notifications (UNUserNotificationCenter), see IosReminderPlatform
    single { IosNotifications() }
    single { IosReminderPlatform(get(), ReminderScheduleAhead(get(), get<ReminderCheck>()::data, get<IosNotifications>())) } bind ReminderPlatform::class
    // AI 노트 도우미: HTTP on NSURLSession, keys in the Keychain (this device only)
    single<AiHttp> { IosAiHttp() }
    single<SecretStore> { IosSecretStore() }
    // 위치 지정: Apple Maps search (MKLocalSearch) and "현재 위치" (CLLocationManager, while using the app)
    single<DevicePlaceSearch> { IosPlaceSearch() }
    single { IosCurrentLocation() } bind CurrentLocation::class
}
