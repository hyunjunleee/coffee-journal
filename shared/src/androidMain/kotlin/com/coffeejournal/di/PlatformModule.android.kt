package com.coffeejournal.di

import com.coffeejournal.data.db.AppDatabase
import com.coffeejournal.data.db.androidDatabaseBuilder
import com.coffeejournal.data.db.buildAppDatabase
import com.coffeejournal.data.photo.AndroidPhotoStore
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.ui.ai.AiHttp
import com.coffeejournal.ui.ai.AndroidAiHttp
import com.coffeejournal.ui.ai.AndroidSecretStore
import com.coffeejournal.ui.ai.SecretStore
import com.coffeejournal.ui.map.search.AndroidCurrentLocation
import com.coffeejournal.ui.map.search.AndroidPlaceSearch
import com.coffeejournal.ui.map.search.CurrentLocation
import com.coffeejournal.ui.map.search.DevicePlaceSearch
import com.coffeejournal.ui.notify.AndroidReminderPlatform
import com.coffeejournal.ui.notify.ReminderPlatform
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<AppDatabase> { androidDatabaseBuilder(androidContext()).buildAppDatabase() }
    single<PhotoStore> { AndroidPhotoStore(androidContext()) }
    single<ReminderPlatform> { AndroidReminderPlatform(androidContext()) }
    // AI 노트 도우미: the services over HttpURLConnection, the user's keys in the Android Keystore
    single<AiHttp> { AndroidAiHttp() }
    single<SecretStore> { AndroidSecretStore(androidContext()) }
    // 위치 지정: the phone's own place search (Geocoder) and "현재 위치" (LocationManager, no Play Services)
    single<DevicePlaceSearch> { AndroidPlaceSearch(androidContext()) }
    single<CurrentLocation> { AndroidCurrentLocation(androidContext()) }
}
