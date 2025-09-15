package com.hiosdra.hreader

import android.app.Application
import com.hiosdra.hreader.di.appModule
import com.hiosdra.hreader.di.networkModule
import com.hiosdra.hreader.config.setupArticleContentSyncWorker
import com.hiosdra.hreader.config.setupContentSyncWorker
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
        setupContentSyncWorker(this)
        setupArticleContentSyncWorker(this)
    }
}
