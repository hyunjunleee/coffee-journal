package com.coffeejournal.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Transaction
    @Query("SELECT * FROM entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<EntryWithBeans>>

    @Transaction
    @Query("SELECT * FROM entries ORDER BY createdAt DESC")
    suspend fun getAll(): List<EntryWithBeans>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: String): EntryWithBeans?

    @Query("SELECT COUNT(*) FROM entries")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsertEntry(entry: EntryEntity)

    @Query("DELETE FROM cupping_beans WHERE entryId = :entryId")
    suspend fun deleteBeansFor(entryId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeans(beans: List<CuppingBeanEntity>)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM entries")
    suspend fun deleteAll()

    @Transaction
    suspend fun upsertWithBeans(entry: EntryEntity, beans: List<CuppingBeanEntity>) {
        upsertEntry(entry)
        deleteBeansFor(entry.id)
        if (beans.isNotEmpty()) insertBeans(beans)
    }
}

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PantryItemEntity>>

    @Query("SELECT * FROM pantry_items ORDER BY createdAt DESC")
    suspend fun getAll(): List<PantryItemEntity>

    @Query("SELECT * FROM pantry_items WHERE id = :id")
    suspend fun getById(id: String): PantryItemEntity?

    @Upsert
    suspend fun upsert(item: PantryItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<PantryItemEntity>)

    @Query("DELETE FROM pantry_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pantry_items")
    suspend fun deleteAll()
}

@Dao
interface MiscDao {
    // auto-registration adds several items in the same millisecond; the id tie-break keeps their order stable
    // (otherwise it flips on every backup round trip)
    @Query("SELECT * FROM misc_items ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<MiscItemEntity>>

    @Query("SELECT * FROM misc_items ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<MiscItemEntity>

    @Query("SELECT * FROM misc_items WHERE id = :id")
    suspend fun getById(id: String): MiscItemEntity?

    @Upsert
    suspend fun upsert(item: MiscItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<MiscItemEntity>)

    @Query("DELETE FROM misc_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM misc_items")
    suspend fun deleteAll()
}

@Dao
interface CafePlaceDao {
    @Query("SELECT * FROM cafe_places ORDER BY name") fun observeAll(): Flow<List<CafePlaceEntity>>
    @Query("SELECT * FROM cafe_places ORDER BY name") suspend fun getAll(): List<CafePlaceEntity>
    @Upsert suspend fun upsert(item: CafePlaceEntity)
    @Upsert suspend fun upsertAll(items: List<CafePlaceEntity>)
    @Query("DELETE FROM cafe_places WHERE name = :name") suspend fun delete(name: String)
    @Query("DELETE FROM cafe_places") suspend fun deleteAll()
}

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY createdAt DESC") fun observeAll(): Flow<List<BookEntity>>
    @Query("SELECT * FROM books ORDER BY createdAt DESC") suspend fun getAll(): List<BookEntity>
    @Query("SELECT * FROM books WHERE id = :id") suspend fun getById(id: String): BookEntity?
    @Upsert suspend fun upsert(item: BookEntity)
    @Upsert suspend fun upsertAll(items: List<BookEntity>)
    @Query("DELETE FROM books WHERE id = :id") suspend fun deleteById(id: String)
    @Query("DELETE FROM books") suspend fun deleteAll()
}

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY createdAt DESC") fun observeAll(): Flow<List<VideoEntity>>
    @Query("SELECT * FROM videos ORDER BY createdAt DESC") suspend fun getAll(): List<VideoEntity>
    @Query("SELECT * FROM videos WHERE id = :id") suspend fun getById(id: String): VideoEntity?
    @Upsert suspend fun upsert(item: VideoEntity)
    @Upsert suspend fun upsertAll(items: List<VideoEntity>)
    @Query("DELETE FROM videos WHERE id = :id") suspend fun deleteById(id: String)
    @Query("DELETE FROM videos") suspend fun deleteAll()
}

@Dao
interface ClassDao {
    @Query("SELECT * FROM classes ORDER BY createdAt DESC") fun observeAll(): Flow<List<ClassEntity>>
    @Query("SELECT * FROM classes ORDER BY createdAt DESC") suspend fun getAll(): List<ClassEntity>
    @Query("SELECT * FROM classes WHERE id = :id") suspend fun getById(id: String): ClassEntity?
    @Upsert suspend fun upsert(item: ClassEntity)
    @Upsert suspend fun upsertAll(items: List<ClassEntity>)
    @Query("DELETE FROM classes WHERE id = :id") suspend fun deleteById(id: String)
    @Query("DELETE FROM classes") suspend fun deleteAll()
}

@Dao
interface BlendDao {
    @Query("SELECT * FROM blends ORDER BY createdAt DESC") fun observeAll(): Flow<List<BlendEntity>>
    @Query("SELECT * FROM blends ORDER BY createdAt DESC") suspend fun getAll(): List<BlendEntity>
    @Query("SELECT * FROM blends WHERE id = :id") suspend fun getById(id: String): BlendEntity?
    @Upsert suspend fun upsert(item: BlendEntity)
    @Upsert suspend fun upsertAll(items: List<BlendEntity>)
    @Query("DELETE FROM blends WHERE id = :id") suspend fun deleteById(id: String)
    @Query("DELETE FROM blends") suspend fun deleteAll()
}

@Dao
interface MyRecipeDao {
    @Query("SELECT * FROM my_recipes ORDER BY createdAt DESC") fun observeAll(): Flow<List<MyRecipeEntity>>
    @Query("SELECT * FROM my_recipes ORDER BY createdAt DESC") suspend fun getAll(): List<MyRecipeEntity>
    @Query("SELECT * FROM my_recipes WHERE id = :id") suspend fun getById(id: String): MyRecipeEntity?
    @Upsert suspend fun upsert(item: MyRecipeEntity)
    @Upsert suspend fun upsertAll(items: List<MyRecipeEntity>)
    @Query("DELETE FROM my_recipes WHERE id = :id") suspend fun deleteById(id: String)
    @Query("DELETE FROM my_recipes") suspend fun deleteAll()
}

@Dao
interface RoadmapDao {
    @Query("SELECT * FROM roadmap_phases ORDER BY position ASC") fun observeAll(): Flow<List<RoadmapPhaseEntity>>
    @Query("SELECT * FROM roadmap_phases ORDER BY position ASC") suspend fun getAll(): List<RoadmapPhaseEntity>
    @Query("SELECT COUNT(*) FROM roadmap_phases") suspend fun count(): Int
    @Upsert suspend fun upsert(item: RoadmapPhaseEntity)
    @Upsert suspend fun upsertAll(items: List<RoadmapPhaseEntity>)
    @Query("DELETE FROM roadmap_phases") suspend fun deleteAll()
}

@Dao
interface BeanMetaDao {
    @Query("SELECT * FROM bean_summaries") fun observeSummaries(): Flow<List<BeanSummaryEntity>>
    @Query("SELECT * FROM bean_summaries") suspend fun getSummaries(): List<BeanSummaryEntity>
    @Upsert suspend fun upsertSummary(item: BeanSummaryEntity)
    @Upsert suspend fun upsertSummaries(items: List<BeanSummaryEntity>)
    @Query("DELETE FROM bean_summaries WHERE beanKey = :key") suspend fun deleteSummary(key: String)
    @Query("DELETE FROM bean_summaries") suspend fun deleteAllSummaries()

    @Query("SELECT * FROM best_recipes") fun observeBest(): Flow<List<BestRecipeEntity>>
    @Query("SELECT * FROM best_recipes") suspend fun getBest(): List<BestRecipeEntity>
    @Upsert suspend fun upsertBest(item: BestRecipeEntity)
    @Upsert suspend fun upsertBestAll(items: List<BestRecipeEntity>)
    @Query("DELETE FROM best_recipes WHERE beanKey = :key") suspend fun deleteBest(key: String)
    @Query("DELETE FROM best_recipes") suspend fun deleteAllBest()
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key") fun observe(key: String): Flow<String?>
    @Query("SELECT value FROM settings WHERE `key` = :key") suspend fun get(key: String): String?
    @Query("SELECT * FROM settings") suspend fun getAll(): List<SettingEntity>
    @Upsert suspend fun put(entity: SettingEntity)
    @Query("DELETE FROM settings WHERE `key` = :key") suspend fun delete(key: String)
    @Query("DELETE FROM settings") suspend fun deleteAll()
}
