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
import com.coffeejournal.ui.notify.IosReminderPlatform
import com.coffeejournal.ui.notify.ReminderPlatform
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<AppDatabase> { iosDatabaseBuilder().buildAppDatabase() }
    single<PhotoStore> { IosPhotoStore() }
    single<ReminderPlatform> { IosReminderPlatform() }
    // AI 노트 도우미: HTTP on NSURLSession, keys in the Keychain (this device only)
    single<AiHttp> { IosAiHttp() }
    single<SecretStore> { IosSecretStore() }
}
