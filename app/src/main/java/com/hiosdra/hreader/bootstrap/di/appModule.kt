package com.hiosdra.hreader.bootstrap.di

import androidx.room.Room
import com.hiosdra.hreader.R
import com.hiosdra.hreader.adapter.persistence.room.ALL_MIGRATIONS
import com.hiosdra.hreader.adapter.persistence.room.AppDatabase
import com.hiosdra.hreader.adapter.persistence.ArticleContentRepository
import com.hiosdra.hreader.adapter.persistence.ArticleAiOverviewRepository
import com.hiosdra.hreader.adapter.persistence.ArticleImageRepository
import com.hiosdra.hreader.adapter.persistence.ArticlePageRepository
import com.hiosdra.hreader.adapter.persistence.RemoteResourcePolicy
import com.hiosdra.hreader.adapter.persistence.ArticleReadingPositionRepository
import com.hiosdra.hreader.adapter.persistence.ArticleRepository
import com.hiosdra.hreader.adapter.persistence.CredibilityRepository
import com.hiosdra.hreader.adapter.persistence.OfflineReadinessRepository
import com.hiosdra.hreader.adapter.paywall.PaywallBypassService
import com.hiosdra.hreader.adapter.preferences.PreferencesManager
import com.hiosdra.hreader.adapter.image.ArticleImageShareService
import com.hiosdra.hreader.adapter.persistence.FeedRepository
import com.hiosdra.hreader.adapter.persistence.LocalCacheRepository
import com.hiosdra.hreader.adapter.tts.ArticleTtsController
import com.hiosdra.hreader.adapter.tts.TtsModelManager
import com.hiosdra.hreader.entrypoint.tts.ArticleTtsPlaybackServiceLauncher
import com.hiosdra.hreader.presentation.article.ArticleViewModel
import com.hiosdra.hreader.presentation.feeds.FeedsViewModel
import com.hiosdra.hreader.presentation.feeds.add.AddFeedViewModel
import com.hiosdra.hreader.presentation.main.MainViewModel
import com.hiosdra.hreader.presentation.settings.SettingsViewModel
import com.hiosdra.hreader.adapter.observability.ErrorReportingManager
import com.hiosdra.hreader.adapter.image.ImageLoader
import com.hiosdra.hreader.adapter.system.NetworkMonitor
import com.hiosdra.hreader.adapter.observability.SyncPerformanceLogger
import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.AppPreferences
import com.hiosdra.hreader.core.application.port.out.ArticleAiGateway
import com.hiosdra.hreader.core.application.port.out.ArticleAiOverviewStore
import com.hiosdra.hreader.core.application.port.out.ArticleContentStore
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.ArticleImageSharer
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import com.hiosdra.hreader.core.application.port.out.ArticlePageStore
import com.hiosdra.hreader.core.application.port.out.ArticleReadingPositionStore
import com.hiosdra.hreader.core.application.port.out.ArticleStore
import com.hiosdra.hreader.core.application.port.out.ArticleTtsPlayer
import com.hiosdra.hreader.core.application.port.out.ArticleTtsPlaybackServiceControl
import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.CacheStore
import com.hiosdra.hreader.core.application.port.out.CredibilityStore
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.FeedStore
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.core.application.port.out.OfflineReadinessStore
import com.hiosdra.hreader.core.application.port.out.PaywallBypass
import com.hiosdra.hreader.core.application.port.out.TtsModelDownloadRequester
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import com.hiosdra.hreader.core.application.port.out.SyncRequester
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.usecase.article.ArticleReaderUseCase
import com.hiosdra.hreader.core.application.usecase.feeds.FeedUseCase
import com.hiosdra.hreader.core.application.usecase.main.MainReaderUseCase
import com.hiosdra.hreader.core.application.usecase.settings.SettingsUseCase
import com.hiosdra.hreader.entrypoint.worker.ArticleContentSyncWorker
import com.hiosdra.hreader.entrypoint.worker.ContentSyncWorker
import com.hiosdra.hreader.entrypoint.worker.FullPageSyncWorker
import com.hiosdra.hreader.entrypoint.worker.SyncScheduler
import com.hiosdra.hreader.entrypoint.worker.TtsModelDownloadWorker
import com.hiosdra.hreader.entrypoint.worker.TtsModelDownloadScheduler
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.qualifier.named
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

val appModule = module {
    single(named("applicationScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
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
    single { RemoteResourcePolicy(get<AppPreferences>()) }
    single { get<AppDatabase>().articleReadingPositionDao() }
    single { ArticleRepository(get(), get(), get(), get(), get(), get(), get()) }
    single<ArticleStore> { get<ArticleRepository>() }
    single { ArticleImageRepository(androidApplication(), get(), get(), get(), get(), get()) }
    single<ArticleImageStore> { get<ArticleImageRepository>() }
    single { CredibilityRepository(get(), get()) }
    single<CredibilityStore> { get<CredibilityRepository>() }
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
    single<ArticleContentStore> { get<ArticleContentRepository>() }
    single { ArticlePageRepository(androidApplication(), get(), get(), get(), get()) }
    single<ArticlePageStore> { get<ArticlePageRepository>() }
    single { ArticleReadingPositionRepository(get()) }
    single<ArticleReadingPositionStore> { get<ArticleReadingPositionRepository>() }
    single { OfflineReadinessRepository(get(), get(), get(), get(), get()) }
    single<OfflineReadinessStore> { get<OfflineReadinessRepository>() }
    single<FeedRepository> { FeedRepository(get(), get(), get(), get()) }
    single<FeedStore> { get<FeedRepository>() }
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
    single<CacheStore> { get<LocalCacheRepository>() }
    single { ArticleAiOverviewRepository(get()) }
    single<ArticleAiOverviewStore> { get<ArticleAiOverviewRepository>() }
    single { PaywallBypassService() }
    single<PaywallBypass> { get<PaywallBypassService>() }
    single { PreferencesManager(androidApplication()) }
    single<AppPreferences> { get<PreferencesManager>() }
    single { ErrorReportingManager(androidApplication(), get()) }
    single<ErrorReporter> { get<ErrorReportingManager>() }
    single { TtsModelManager(androidApplication(), get()) }
    single<TtsModelGateway> { get<TtsModelManager>() }
    single<ArticleTtsPlaybackServiceControl> { ArticleTtsPlaybackServiceLauncher(androidApplication()) }
    single { TtsModelDownloadScheduler(androidApplication(), get()) }
    single<TtsModelDownloadRequester> { get<TtsModelDownloadScheduler>() }
    single { ArticleTtsController(androidApplication(), get(), get(), get()) }
    single<ArticleTtsPlayer> { get<ArticleTtsController>() }
    single { SyncPerformanceLogger(get()) }
    single<SyncPerformanceTracker> { get<SyncPerformanceLogger>() }
    single { ImageLoader(get<ArticleImageStore>()) }
    single<ArticleImageLoader> { get<ImageLoader>() }
    single<ArticleImageSharer> { ArticleImageShareService(androidApplication()) }
    single { NetworkMonitor(androidApplication()) }
    single<NetworkStatus> { get<NetworkMonitor>() }
    single { SyncScheduler(androidApplication(), get(), get(), scope = get(named("applicationScope"))) }
    single<SyncRequester> { get<SyncScheduler>() }
    single {
        ArticleReaderUseCase(
            articles = get<ArticleStore>(),
            positions = get<ArticleReadingPositionStore>(),
            content = get<ArticleContentStore>(),
            pages = get<ArticlePageStore>(),
            ai = get<ArticleAiGateway>(),
            overviews = get<ArticleAiOverviewStore>(),
            credibility = get<CredibilityStore>(),
            preferences = get<AppPreferences>(),
            images = get<ArticleImageLoader>(),
            network = get<NetworkStatus>()
        )
    }
    single {
        MainReaderUseCase(
            articles = get<ArticleStore>(),
            cache = get<CacheStore>(),
            aiModels = get<AiModelCatalog>(),
            sync = get<SyncRequester>(),
            network = get<NetworkStatus>()
        )
    }
    single { FeedUseCase(get<FeedStore>(), get<NetworkStatus>()) }
    single {
        SettingsUseCase(
            preferences = get<AppPreferences>(),
            feeds = get<FeedStore>(),
            aiModels = get<AiModelCatalog>(),
            cache = get<CacheStore>(),
            offlineReadiness = get<OfflineReadinessStore>(),
            sync = get<SyncRequester>()
        )
    }
    worker { ContentSyncWorker(get(), get(), get(), get(), get(), get(), get()) }
    worker { ArticleContentSyncWorker(get(), get(), get(), get(), get(), get(), get()) }
    worker { FullPageSyncWorker(get(), get(), get(), get(), get(), get(), get()) }
    worker { TtsModelDownloadWorker(get(), get(), get(), get()) }
    viewModel { MainViewModel(get(), get()) }
    viewModel { FeedsViewModel(get()) }
    viewModel { ArticleViewModel(get()) }
    viewModel { AddFeedViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
}
