package com.hiosdra.hreader.bootstrap

import android.app.Application
import android.util.Log
import com.hiosdra.hreader.BuildConfig
import com.hiosdra.hreader.bootstrap.di.appModule
import com.hiosdra.hreader.bootstrap.di.networkModule
import com.hiosdra.hreader.entrypoint.notification.NotificationChannels
import com.hiosdra.hreader.adapter.observability.ErrorReportingManager
import com.hiosdra.hreader.core.application.port.out.PreferenceWriteBarrier
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.entrypoint.worker.SyncScheduler
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "MyApplication"

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
        val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val preferenceWrites = koin.get<PreferenceWriteBarrier>()
        val syncScheduler = koin.get<SyncScheduler>()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                startupScope.launch {
                    val result = runCatchingCancellable { preferenceWrites.awaitWrites() }
                    result.exceptionOrNull()?.let { error ->
                        Log.e(TAG, "Could not flush preference writes on app stop", error)
                    }
                }
                syncScheduler.enqueueBackgroundSyncChain()
            }
        })
        startupScope.launch {
            preferenceWrites.awaitReady()
            koin.get<ErrorReportingManager>().initialize()
            // Content prefetching is chained off each successful sync, not scheduled separately.
            syncScheduler.schedulePeriodicSync()
            syncScheduler.start()
        }
    }
}
