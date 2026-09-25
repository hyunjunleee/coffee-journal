package com.coffeejournal.android

import android.app.Application
import com.coffeejournal.di.initKoin
import com.coffeejournal.ui.platform.installUrlOpener
import org.koin.android.ext.koin.androidContext

class CoffeeJournalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installUrlOpener(this)
        initKoin { androidContext(this@CoffeeJournalApplication) }
    }
}
