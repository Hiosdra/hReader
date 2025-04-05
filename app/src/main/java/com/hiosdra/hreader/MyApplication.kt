package com.hiosdra.hreader

import android.app.Application
import com.hiosdra.hreader.di.appModule
import com.hiosdra.hreader.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger() // Use Android logger (optional)
            androidContext(this@MyApplication)
            modules(appModule, networkModule) // List your Koin modules here
        }
    }
}
