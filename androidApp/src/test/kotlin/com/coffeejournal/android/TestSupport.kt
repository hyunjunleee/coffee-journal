package com.coffeejournal.android

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.coffeejournal.data.db.AppDatabase
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.di.dataModule
import com.coffeejournal.ui.ai.AiHttp
import com.coffeejournal.ui.ai.SecretStore
import com.coffeejournal.ui.nav.Features
import com.coffeejournal.ui.notify.AndroidReminderPlatform
import com.coffeejournal.ui.notify.ReminderPlatform
import com.coffeejournal.ui.theme.Display
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File

/** Test application that does not start Koin, so each test can install its own modules. */
class TestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Display (설정 › 화면) is process-wide state: an earlier test's typeface or text size must not carry over
        Display.reset()
    }
}

/** In-memory Room database on the framework SQLite (Robolectric), a photo store in a temp dir, fake AI HTTP and key store. */
fun testPlatformModule(context: Context) = module {
    single<AppDatabase> {
        Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .allowMainThreadQueries()
            .build()
    }
    single<PhotoStore> { TempPhotoStore(File(context.cacheDir, "photos")) }
    // the production reminder platform: it touches WorkManager only when scheduling (tests that do install a test WorkManager)
    single<ReminderPlatform> { AndroidReminderPlatform(context) }
    // AI 노트 도우미: no test reaches the network, and Robolectric has no AndroidKeyStore
    single<AiHttp> { FakeAiHttp() }
    single<SecretStore> { MemorySecretStore() }
}

class TempPhotoStore(private val dir: File) : PhotoStore {
    init { dir.mkdirs() }
    override fun pathFor(fileName: String): String = File(dir, fileName).absolutePath
    override suspend fun save(bytes: ByteArray): String {
        // unique like the real store (UUID names); a count-based name would overwrite files after a delete
        val name = "p" + java.util.UUID.randomUUID().toString().replace("-", "") + ".jpg"
        File(dir, name).writeBytes(bytes)
        return name
    }
    override suspend fun readBytes(fileName: String): ByteArray? = File(dir, fileName).takeIf { it.exists() }?.readBytes()
    override suspend fun delete(fileName: String) { File(dir, fileName).delete() }
    override suspend fun exists(fileName: String): Boolean = File(dir, fileName).exists()
}

fun startTestKoin(context: Context) {
    stopKoin()
    startKoin {
        androidContext(context)
        modules(listOf(testPlatformModule(context), dataModule) + Features.all.map { it.module })
    }
}
