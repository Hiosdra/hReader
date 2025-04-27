package com.hiosdra.hreader.di

import androidx.room.Room
import com.hiosdra.hreader.data.local.AppDatabase
import com.hiosdra.hreader.data.local.ArticleRepository
import com.hiosdra.hreader.data.local.ContentSyncWorker
import com.hiosdra.hreader.ui.article.ArticleListViewModel
import com.hiosdra.hreader.ui.article.ArticleViewModel
import com.hiosdra.hreader.ui.feeds.AddFeedViewModel
import com.hiosdra.hreader.ui.feeds.FeedsViewModel
import com.hiosdra.hreader.ui.main.MainViewModel
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
        ).build()
    }
    single { get<AppDatabase>().articleDao() }
    single { ArticleRepository(get(), get()) }
    worker { ContentSyncWorker(get(), get()) }
    viewModel { MainViewModel(get()) }
    viewModel { FeedsViewModel(get()) }
    viewModel { ArticleViewModel(get()) }
    viewModel { AddFeedViewModel(get(), get()) }
    viewModel { ArticleListViewModel(get()) }
}
