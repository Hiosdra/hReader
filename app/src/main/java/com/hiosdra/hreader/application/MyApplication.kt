package com.hiosdra.hreader.application

import android.app.Application
import com.hiosdra.hreader.di.appModule
import com.hiosdra.hreader.di.networkModule
import com.hiosdra.hreader.worker.setupContentSyncWorker
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@MyApplication)
            workManagerFactory()
            modules(appModule, networkModule)
        }
        // Content prefetching is chained off each successful sync, not scheduled separately.
        setupContentSyncWorker(this)
    }
}
