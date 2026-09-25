package com.coffeejournal.di

import com.coffeejournal.data.db.AppDatabase
import com.coffeejournal.data.db.buildAppDatabase
import com.coffeejournal.data.db.iosDatabaseBuilder
import com.coffeejournal.data.photo.IosPhotoStore
import com.coffeejournal.data.photo.PhotoStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<AppDatabase> { iosDatabaseBuilder().buildAppDatabase() }
    single<PhotoStore> { IosPhotoStore() }
}
