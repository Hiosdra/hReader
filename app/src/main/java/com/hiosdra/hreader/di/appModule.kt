package com.hiosdra.hreader.di

import androidx.room.Room
import com.hiosdra.hreader.data.local.AppDatabase
import com.hiosdra.hreader.data.local.repository.ArticleContentRepository
import com.hiosdra.hreader.data.local.repository.ArticleImageRepository
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.paywall.PaywallBypassService
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.ui.article.ArticleViewModel
import com.hiosdra.hreader.ui.feeds.FeedsViewModel
import com.hiosdra.hreader.ui.feeds.add.AddFeedViewModel
import com.hiosdra.hreader.ui.main.MainViewModel
import com.hiosdra.hreader.util.SyncPerformanceLogger
import com.hiosdra.hreader.util.ImageLoader
import com.hiosdra.hreader.worker.ArticleContentSyncWorker
import com.hiosdra.hreader.worker.ContentSyncWorker
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        Room.databaseBuilder(
                androidApplication(),
                AppDatabase::class.java,
                "hreader-db"
            ).fallbackToDestructiveMigration(false)
            .build()
    }
    single { get<AppDatabase>().articleDao() }
    single { get<AppDatabase>().feedDao() }
    single { get<AppDatabase>().articleContentDao() }
    single { get<AppDatabase>().articleImageDao() }
    single { ArticleRepository(get(), get(), get(), get(), get(), get()) }
    single { ArticleImageRepository(androidApplication(), get(), get(), get()) }
    single { ArticleContentRepository(get(), get(), get(), get()) }
    single { PaywallBypassService() }
    single { PreferencesManager(androidApplication()) }
    single { SyncPerformanceLogger(get()) }
    single { ImageLoader(get()) }
    worker { ContentSyncWorker(get(), get()) }
    worker { ArticleContentSyncWorker(get(), get(), get(), get(), get()) }
    viewModel { MainViewModel(get()) }
    viewModel { FeedsViewModel(get()) }
    viewModel { ArticleViewModel(get(), get(), get(), get()) }
    viewModel { AddFeedViewModel(get(), get()) }
}
