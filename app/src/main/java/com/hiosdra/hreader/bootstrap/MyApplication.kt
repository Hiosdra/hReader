package com.hiosdra.hreader.bootstrap

import android.app.Application
import com.hiosdra.hreader.BuildConfig
import com.hiosdra.hreader.bootstrap.di.appModule
import com.hiosdra.hreader.bootstrap.di.networkModule
import com.hiosdra.hreader.entrypoint.notification.NotificationChannels
import com.hiosdra.hreader.adapter.observability.ErrorReportingManager
import com.hiosdra.hreader.entrypoint.worker.SyncScheduler
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)

        val koin = startKoin {
            if (BuildConfig.DEBUG) {
                androidLogger()
            }
            androidContext(this@MyApplication)
            workManagerFactory()
            modules(appModule, networkModule)
        }.koin
        koin.get<ErrorReportingManager>().initialize()
        val syncScheduler = koin.get<SyncScheduler>()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                syncScheduler.enqueueBackgroundSyncChain()
            }
        })
        // Content prefetching is chained off each successful sync, not scheduled separately.
        syncScheduler.schedulePeriodicSync()
        syncScheduler.start()
    }
}
