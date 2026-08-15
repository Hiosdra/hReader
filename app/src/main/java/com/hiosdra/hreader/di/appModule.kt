package com.hiosdra.hreader.di

import androidx.room.Room
import com.hiosdra.hreader.R
import com.hiosdra.hreader.data.local.ALL_MIGRATIONS
import com.hiosdra.hreader.data.local.AppDatabase
import com.hiosdra.hreader.data.local.repository.ArticleContentRepository
import com.hiosdra.hreader.data.local.repository.ArticleAiOverviewRepository
import com.hiosdra.hreader.data.local.repository.ArticleImageRepository
import com.hiosdra.hreader.data.local.repository.ArticlePageRepository
import com.hiosdra.hreader.data.local.repository.ArticleReadingPositionRepository
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.local.repository.CredibilityRepository
import com.hiosdra.hreader.data.local.repository.OfflineReadinessRepository
import com.hiosdra.hreader.data.paywall.PaywallBypassService
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.repository.FeedRepository
import com.hiosdra.hreader.data.repository.LocalCacheRepository
import com.hiosdra.hreader.data.tts.ArticleTtsController
import com.hiosdra.hreader.data.tts.TtsModelManager
import com.hiosdra.hreader.ui.article.ArticleViewModel
import com.hiosdra.hreader.ui.feeds.FeedsViewModel
import com.hiosdra.hreader.ui.feeds.add.AddFeedViewModel
import com.hiosdra.hreader.ui.main.MainViewModel
import com.hiosdra.hreader.ui.settings.SettingsViewModel
import com.hiosdra.hreader.util.ErrorReportingManager
import com.hiosdra.hreader.util.ImageLoader
import com.hiosdra.hreader.util.NetworkMonitor
import com.hiosdra.hreader.util.SyncPerformanceLogger
import com.hiosdra.hreader.worker.ArticleContentSyncWorker
import com.hiosdra.hreader.worker.ContentSyncWorker
import com.hiosdra.hreader.worker.FullPageSyncWorker
import com.hiosdra.hreader.worker.SyncScheduler
import com.hiosdra.hreader.worker.TtsModelDownloadWorker
import com.hiosdra.hreader.worker.TtsModelDownloadScheduler
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.io.File

val appModule = module {
    single {
        Room.databaseBuilder(
                androidApplication(),
                AppDatabase::class.java,
                "hreader-db"
            ).addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigration(false)
            .build()
    }
    single { get<AppDatabase>().articleDao() }
    single { get<AppDatabase>().feedDao() }
    single { get<AppDatabase>().articleContentDao() }
    single { get<AppDatabase>().articleImageDao() }
    single { get<AppDatabase>().articleCredibilityDao() }
    single { get<AppDatabase>().articleAiOverviewDao() }
    single { get<AppDatabase>().articlePageSnapshotDao() }
    single { get<AppDatabase>().articleReadingPositionDao() }
    single { ArticleRepository(get(), get(), get(), get(), get(), get(), get()) }
    single { ArticleImageRepository(androidApplication(), get(), get(), get(), get()) }
    single { CredibilityRepository(get(), get()) }
    single {
        ArticleContentRepository(
            { androidApplication().getString(R.string.article_open_embedded_media) },
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
    single { ArticlePageRepository(androidApplication(), get(), get(), get()) }
    single { ArticleReadingPositionRepository(get()) }
    single { OfflineReadinessRepository(get(), get(), get(), get(), get()) }
    single<FeedRepository> { FeedRepository(get(), get(), get(), get()) }
    single {
        LocalCacheRepository(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            File(androidApplication().filesDir, "article_images"),
            File(androidApplication().filesDir, "article_pages"),
            get()
        )
    }
    single { ArticleAiOverviewRepository(get()) }
    single { PaywallBypassService() }
    single { PreferencesManager(androidApplication()) }
    single { ErrorReportingManager(androidApplication(), get()) }
    single { TtsModelManager(androidApplication(), get()) }
    single { TtsModelDownloadScheduler(androidApplication(), get()) }
    single { ArticleTtsController(androidApplication(), get(), get()) }
    single { SyncPerformanceLogger(get()) }
    single { ImageLoader(get()) }
    single { NetworkMonitor(androidApplication()) }
    single { SyncScheduler(androidApplication(), get(), get()) }
    worker { ContentSyncWorker(get(), get(), get(), get(), get(), get(), get()) }
    worker { ArticleContentSyncWorker(get(), get(), get(), get(), get(), get(), get()) }
    worker { FullPageSyncWorker(get(), get(), get(), get(), get(), get(), get()) }
    worker { TtsModelDownloadWorker(get(), get(), get(), get()) }
    viewModel { MainViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { FeedsViewModel(get(), get()) }
    viewModel { ArticleViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { AddFeedViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
}
