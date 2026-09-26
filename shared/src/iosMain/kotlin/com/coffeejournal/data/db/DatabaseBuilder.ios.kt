package com.coffeejournal.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** iOS database lives in the app's Documents directory (backed up by iCloud/iTunes by default). */
@OptIn(ExperimentalForeignApi::class)
fun iosDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory, inDomain = NSUserDomainMask, appropriateForURL = null, create = true, error = null,
    )
    val path = requireNotNull(documents?.path) { "Documents directory unavailable" } + "/coffee_journal.db"
    return Room.databaseBuilder<AppDatabase>(name = path)
}
