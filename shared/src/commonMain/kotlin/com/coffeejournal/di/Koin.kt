package com.coffeejournal.di

import com.coffeejournal.data.db.AppDatabase
import com.coffeejournal.data.repo.BeanMetaRepository
import com.coffeejournal.data.repo.BlendRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.MyRecipeRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.RoadmapRepository
import com.coffeejournal.data.repo.SaveEntryPipeline
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.data.repo.StudyRepository
import com.coffeejournal.ui.nav.Features
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

/** Platform supplies the database builder and the photo store. */
expect val platformModule: Module

val dataModule = module {
    single { get<AppDatabase>().entryDao() }
    single { get<AppDatabase>().pantryDao() }
    single { get<AppDatabase>().miscDao() }
    single { get<AppDatabase>().bookDao() }
    single { get<AppDatabase>().videoDao() }
    single { get<AppDatabase>().classDao() }
    single { get<AppDatabase>().blendDao() }
    single { get<AppDatabase>().myRecipeDao() }
    single { get<AppDatabase>().roadmapDao() }
    single { get<AppDatabase>().beanMetaDao() }
    single { get<AppDatabase>().settingsDao() }

    single { EntryRepository(get(), get()) }
    single { PantryRepository(get()) }
    single { MiscRepository(get(), get()) }
    single { StudyRepository(get(), get(), get()) }
    single { BlendRepository(get()) }
    single { MyRecipeRepository(get()) }
    single { RoadmapRepository(get()) }
    single { BeanMetaRepository(get()) }
    single { SettingsRepository(get()) }
    single { SaveEntryPipeline(get(), get(), get()) }
}

fun initKoin(config: KoinApplication.() -> Unit = {}) {
    startKoin {
        config()
        modules(listOf(platformModule, dataModule) + Features.all.map { it.module })
    }
}
