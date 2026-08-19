package com.hiosdra.hreader.bootstrap

import android.app.Application
import com.hiosdra.hreader.bootstrap.di.appModule
import com.hiosdra.hreader.bootstrap.di.networkModule
import com.hiosdra.hreader.entrypoint.notification.NotificationChannels
import com.hiosdra.hreader.adapter.observability.ErrorReportingManager
import com.hiosdra.hreader.entrypoint.worker.SyncScheduler
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.hiosdra.hreader.adapter.preferences.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class MyApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)

        val koin = startKoin {
            androidLogger()
            androidContext(this@MyApplication)
            workManagerFactory()
            modules(appModule, networkModule)
        }.koin
        val preferences = koin.get<PreferencesManager>()
        applicationScope.launch {
            preferences.awaitReady()
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
}
