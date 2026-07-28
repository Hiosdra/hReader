package com.hiosdra.hreader.di

import com.hiosdra.hreader.BuildConfig
import com.hiosdra.hreader.data.ai.AiModelRepository
import com.hiosdra.hreader.data.ai.ArticleAiService
import com.hiosdra.hreader.data.ai.CredibilityPromptBuilder
import com.hiosdra.hreader.data.ai.CredibilityResponseParser
import com.hiosdra.hreader.data.ai.OpenRouterApiService
import com.hiosdra.hreader.data.remote.BackendUrlInterceptor
import com.hiosdra.hreader.data.remote.DelegatingFeedBackend
import com.hiosdra.hreader.data.remote.FRESHRSS_PLACEHOLDER_BASE_URL
import com.hiosdra.hreader.data.remote.FeedBackend
import com.hiosdra.hreader.data.remote.FeedDiscoveryService
import com.hiosdra.hreader.data.remote.MINIFLUX_PLACEHOLDER_BASE_URL
import com.hiosdra.hreader.data.remote.ServerConfig
import com.hiosdra.hreader.data.remote.freshrss.FreshRssApiService
import com.hiosdra.hreader.data.remote.freshrss.FreshRssBackend
import com.hiosdra.hreader.data.remote.freshrss.GoogleReaderAuthInterceptor
import com.hiosdra.hreader.data.remote.freshrss.GoogleReaderAuthenticator
import com.hiosdra.hreader.data.remote.miniflux.MinifluxApiService
import com.hiosdra.hreader.data.remote.miniflux.MinifluxAuthInterceptor
import com.hiosdra.hreader.data.remote.miniflux.MinifluxBackend
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.qualifier.named
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
    single { GoogleReaderAuthenticator(get()) { get<OkHttpClient>() } }
    single { GoogleReaderAuthInterceptor(get()) }
    single { MinifluxAuthInterceptor(get()) }
    single { BackendUrlInterceptor(get()) }
    single<HttpLoggingInterceptor> {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
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
    single<Retrofit>(named(OPENROUTER_RETROFIT)) { retrofitFor("https://openrouter.ai/api/v1/", get(), get()) }

    single<FreshRssApiService> { get<Retrofit>(named(FRESHRSS_RETROFIT)).create(FreshRssApiService::class.java) }
    single<MinifluxApiService> { get<Retrofit>(named(MINIFLUX_RETROFIT)).create(MinifluxApiService::class.java) }

    single { FeedDiscoveryService(get()) }

    single<FeedBackend>(named(FRESHRSS_RETROFIT)) { FreshRssBackend(get(), get()) }
    single<FeedBackend>(named(MINIFLUX_RETROFIT)) { MinifluxBackend(get()) }
    single<FeedBackend> {
        DelegatingFeedBackend(
            config = get(),
            freshRssBackend = get(named(FRESHRSS_RETROFIT)),
            minifluxBackend = get(named(MINIFLUX_RETROFIT))
        )
    }

    single<OpenRouterApiService> { get<Retrofit>(named(OPENROUTER_RETROFIT)).create(OpenRouterApiService::class.java) }
    single<Clock> { Clock.systemDefaultZone() }
    single { CredibilityPromptBuilder(get()) }
    single { CredibilityResponseParser(get()) }
    single<ArticleAiService> { ArticleAiService(get(), get(), get(), get()) }
    single { AiModelRepository(get(), get()) }
}

private fun retrofitFor(baseUrl: String, client: OkHttpClient, moshi: Moshi): Retrofit =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
