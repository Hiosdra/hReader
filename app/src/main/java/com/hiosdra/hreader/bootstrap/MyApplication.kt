package com.hiosdra.hreader.bootstrap

import android.app.Application
import com.hiosdra.hreader.bootstrap.di.appModule
import com.hiosdra.hreader.bootstrap.di.networkModule
import com.hiosdra.hreader.entrypoint.notification.NotificationChannels
import com.hiosdra.hreader.adapter.observability.ErrorReportingManager
import com.hiosdra.hreader.entrypoint.worker.SyncScheduler
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
        koin.get<ErrorReportingManager>().initialize()
        // Content prefetching is chained off each successful sync, not scheduled separately.
        koin.get<SyncScheduler>().schedulePeriodicSync()
        koin.get<SyncScheduler>().start()
    }
}
