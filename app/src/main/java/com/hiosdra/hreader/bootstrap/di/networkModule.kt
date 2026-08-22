package com.hiosdra.hreader.bootstrap.di

import com.hiosdra.hreader.BuildConfig
import com.hiosdra.hreader.adapter.ai.ArticleAiGatewayRouter
import com.hiosdra.hreader.adapter.ai.CompositeAiModelCatalog
import com.hiosdra.hreader.adapter.ai.common.CredibilityPromptBuilder
import com.hiosdra.hreader.adapter.ai.common.CredibilityReportFactory
import com.hiosdra.hreader.adapter.ai.common.CredibilityResponseParser
import com.hiosdra.hreader.adapter.ai.gemma.GemmaArticleAiService
import com.hiosdra.hreader.adapter.ai.gemma.GemmaInferenceEngine
import com.hiosdra.hreader.adapter.ai.gemma.GemmaModelManager
import com.hiosdra.hreader.adapter.ai.openrouter.ArticleAiService
import com.hiosdra.hreader.adapter.ai.openrouter.AiModelRepository
import com.hiosdra.hreader.adapter.ai.openrouter.OpenRouterApiService
import com.hiosdra.hreader.adapter.ai.openrouter.OPENROUTER_API_BASE_URL
import com.hiosdra.hreader.adapter.ai.openrouter.OkHttpOpenRouterStreamingClient
import com.hiosdra.hreader.adapter.ai.openrouter.OpenRouterStreamingClient
import com.hiosdra.hreader.adapter.backend.common.BackendUrlInterceptor
import com.hiosdra.hreader.adapter.backend.common.DelegatingFeedBackend
import com.hiosdra.hreader.adapter.backend.common.FRESHRSS_PLACEHOLDER_BASE_URL
import com.hiosdra.hreader.adapter.backend.common.FeedDiscoveryService
import com.hiosdra.hreader.adapter.backend.common.MINIFLUX_PLACEHOLDER_BASE_URL
import com.hiosdra.hreader.adapter.backend.common.ServerConfig
import com.hiosdra.hreader.adapter.backend.freshrss.FreshRssApiService
import com.hiosdra.hreader.adapter.backend.freshrss.FreshRssBackend
import com.hiosdra.hreader.adapter.backend.freshrss.GoogleReaderAuthInterceptor
import com.hiosdra.hreader.adapter.backend.freshrss.GoogleReaderAuthenticator
import com.hiosdra.hreader.adapter.backend.miniflux.MinifluxApiService
import com.hiosdra.hreader.adapter.backend.miniflux.MinifluxAuthInterceptor
import com.hiosdra.hreader.adapter.backend.miniflux.MinifluxBackend
import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.ArticleAiGateway
import com.hiosdra.hreader.core.application.port.out.GemmaModelGateway
import com.hiosdra.hreader.core.application.port.out.GemmaModelLifecycle
import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.qualifier.named
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Clock
import java.util.concurrent.TimeUnit

private const val FRESHRSS_RETROFIT = "freshrss"
private const val MINIFLUX_RETROFIT = "miniflux"
private const val OPENROUTER_RETROFIT = "openrouter"

private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_TIMEOUT_SECONDS = 60L
private const val WRITE_TIMEOUT_SECONDS = 30L

val networkModule = module {
    single { ServerConfig(get()) }
    single<BackendIdentity> { get<ServerConfig>() }
    // The login call carries credentials in its form body and gets the auth token back in the
    // response body, so it uses a client without the app's auth and logging interceptors. The
    // connection pool and dispatcher are still shared, since newBuilder keeps them.
    single {
        GoogleReaderAuthenticator(get()) {
            get<OkHttpClient>().newBuilder().apply { interceptors().clear() }.build()
        }
    }
    single { GoogleReaderAuthInterceptor(get()) }
    single { MinifluxAuthInterceptor(get(), androidApplication()) }
    single { BackendUrlInterceptor(get(), androidApplication()) }
    single<HttpLoggingInterceptor> {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
            redactHeader("X-Auth-Token")
        }
    }
    single<OkHttpClient> {
        OkHttpClient.Builder()
            // OkHttp defaults to a 10s read timeout, which a self-hosted backend serving a page of
            // 200 entries with full content routinely exceeds. No callTimeout on purpose: it also
            // counts time spent queued in the dispatcher, and content prefetching submits every
            // unread article at once, so a whole-call deadline would fail the tail of that queue.
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(get<GoogleReaderAuthInterceptor>())
            .addInterceptor(get<MinifluxAuthInterceptor>())
            .addInterceptor(get<BackendUrlInterceptor>())
            .addInterceptor(get<HttpLoggingInterceptor>())
            .build()
    }
    single<Moshi> {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    single<Retrofit>(named(FRESHRSS_RETROFIT)) { retrofitFor(FRESHRSS_PLACEHOLDER_BASE_URL, get(), get()) }
    single<Retrofit>(named(MINIFLUX_RETROFIT)) { retrofitFor(MINIFLUX_PLACEHOLDER_BASE_URL, get(), get()) }
    single<Retrofit>(named(OPENROUTER_RETROFIT)) { retrofitFor(OPENROUTER_API_BASE_URL, get(), get()) }

    single<FreshRssApiService> { get<Retrofit>(named(FRESHRSS_RETROFIT)).create(FreshRssApiService::class.java) }
    single<MinifluxApiService> { get<Retrofit>(named(MINIFLUX_RETROFIT)).create(MinifluxApiService::class.java) }

    single { FeedDiscoveryService(get()) }

    single<FeedBackend>(named(FRESHRSS_RETROFIT)) { FreshRssBackend(get(), get(), get()) }
    single<FeedBackend>(named(MINIFLUX_RETROFIT)) { MinifluxBackend(get()) }
    single<FeedBackend> {
        DelegatingFeedBackend(
            config = get(),
            freshRssBackend = get(named(FRESHRSS_RETROFIT)),
            minifluxBackend = get(named(MINIFLUX_RETROFIT))
        )
    }

    single<OpenRouterApiService> { get<Retrofit>(named(OPENROUTER_RETROFIT)).create(OpenRouterApiService::class.java) }
    single<OpenRouterStreamingClient> { OkHttpOpenRouterStreamingClient(get(), get()) }
    single<Clock> { Clock.systemDefaultZone() }
    single { CredibilityPromptBuilder(get()) }
    single { CredibilityReportFactory(get()) }
    single { CredibilityResponseParser(get()) }
    single<ArticleAiService> {
        ArticleAiService(
            openRouterApiService = get(),
            streamingClient = get(),
            preferencesManager = get(),
            credibilityPromptBuilder = get(),
            credibilityResponseParser = get(),
            credibilityReportFactory = get(),
            aiModelCatalog = get(),
            moshi = get()
        )
    }
    single { GemmaModelManager(androidApplication(), get()) }
    single<GemmaModelGateway> { get<GemmaModelManager>() }
    single { GemmaInferenceEngine(androidApplication(), get(), get()) }
    single<GemmaModelLifecycle> { get<GemmaInferenceEngine>() }
    single { GemmaArticleAiService(get(), get(), get(), get()) }
    single<ArticleAiGateway> {
        ArticleAiGatewayRouter(
            openRouter = get(),
            gemma = get(),
            errorReporter = get()
        )
    }
    single { AiModelRepository(get(), get()) }
    single<AiModelCatalog> {
        CompositeAiModelCatalog(androidApplication(), get<AiModelRepository>(), get(), get())
    }
}

private fun retrofitFor(baseUrl: String, client: OkHttpClient, moshi: Moshi): Retrofit =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
