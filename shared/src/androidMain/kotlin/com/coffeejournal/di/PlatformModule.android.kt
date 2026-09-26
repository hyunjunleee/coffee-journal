package com.coffeejournal.di

import com.coffeejournal.data.db.AppDatabase
import com.coffeejournal.data.db.androidDatabaseBuilder
import com.coffeejournal.data.db.buildAppDatabase
import com.coffeejournal.data.photo.AndroidPhotoStore
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.ui.notify.AndroidReminderPlatform
import com.coffeejournal.ui.notify.ReminderPlatform
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<AppDatabase> { androidDatabaseBuilder(androidContext()).buildAppDatabase() }
    single<PhotoStore> { AndroidPhotoStore(androidContext()) }
    single<ReminderPlatform> { AndroidReminderPlatform(androidContext()) }
}
