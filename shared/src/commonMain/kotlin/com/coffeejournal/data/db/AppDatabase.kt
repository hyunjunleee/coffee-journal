package com.coffeejournal.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        EntryEntity::class, CuppingBeanEntity::class, PantryItemEntity::class, MiscItemEntity::class,
        BookEntity::class, VideoEntity::class, ClassEntity::class, BlendEntity::class, MyRecipeEntity::class,
        RoadmapPhaseEntity::class, BeanSummaryEntity::class, BestRecipeEntity::class, SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun pantryDao(): PantryDao
    abstract fun miscDao(): MiscDao
    abstract fun bookDao(): BookDao
    abstract fun videoDao(): VideoDao
    abstract fun classDao(): ClassDao
    abstract fun blendDao(): BlendDao
    abstract fun myRecipeDao(): MyRecipeDao
    abstract fun roadmapDao(): RoadmapDao
    abstract fun beanMetaDao(): BeanMetaDao
    abstract fun settingsDao(): SettingsDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun RoomDatabase.Builder<AppDatabase>.buildAppDatabase(): AppDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
