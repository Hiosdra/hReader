package com.hiosdra.hreader.application

import android.app.Application
import com.hiosdra.hreader.di.appModule
import com.hiosdra.hreader.di.networkModule
import com.hiosdra.hreader.notification.NotificationChannels
import com.hiosdra.hreader.worker.SyncScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)

        val koin = startKoin {
            androidLogger()
            androidContext(this@MyApplication)
            workManagerFactory()
            modules(appModule, networkModule)
        }.koin
        // Content prefetching is chained off each successful sync, not scheduled separately.
        koin.get<SyncScheduler>().schedulePeriodicSync()
        koin.get<SyncScheduler>().start()
    }
}
