package com.hiosdra.hreader.di

import com.hiosdra.hreader.BuildConfig
import com.hiosdra.hreader.data.ai.ArticleAiService
import com.hiosdra.hreader.data.ai.OpenRouterApiService
import com.hiosdra.hreader.data.remote.ArticleContentFetcher
import com.hiosdra.hreader.data.remote.FRESHRSS_PLACEHOLDER_BASE_URL
import com.hiosdra.hreader.data.remote.FeedDiscoveryService
import com.hiosdra.hreader.data.remote.FreshRssApiRepository
import com.hiosdra.hreader.data.remote.FreshRssApiService
import com.hiosdra.hreader.data.remote.FreshRssServerConfig
import com.hiosdra.hreader.data.remote.FreshRssUrlInterceptor
import com.hiosdra.hreader.data.remote.GoogleReaderAuthInterceptor
import com.hiosdra.hreader.data.remote.GoogleReaderAuthenticator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

val networkModule = module {
    single { FreshRssServerConfig(get()) }
    single { GoogleReaderAuthenticator(get()) { get<OkHttpClient>() } }
    single { GoogleReaderAuthInterceptor(get()) }
    single { FreshRssUrlInterceptor(get()) }
    single<HttpLoggingInterceptor> {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }
    }
    single<OkHttpClient> {
        OkHttpClient.Builder()
            .addInterceptor(get<GoogleReaderAuthInterceptor>())
            .addInterceptor(get<FreshRssUrlInterceptor>())
            .addInterceptor(get<HttpLoggingInterceptor>())
            .build()
    }
    single<Moshi> {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    single<Retrofit> {
        Retrofit.Builder()
            .baseUrl(FRESHRSS_PLACEHOLDER_BASE_URL)
            .client(get<OkHttpClient>())
            .addConverterFactory(MoshiConverterFactory.create(get<Moshi>()))
            .build()
    }

    single<FreshRssApiService> { get<Retrofit>().create(FreshRssApiService::class.java) }
    single<FreshRssApiRepository> { FreshRssApiRepository(get()) }
    single { ArticleContentFetcher(get()) }
    single { FeedDiscoveryService(get()) }

    // OpenRouter AI Services
    single<Retrofit>(named("openrouter")) {
        Retrofit.Builder()
            .baseUrl("https://openrouter.ai/api/v1/")
            .client(get<OkHttpClient>())
            .addConverterFactory(MoshiConverterFactory.create(get<Moshi>()))
            .build()
    }
    single<OpenRouterApiService> { get<Retrofit>(named("openrouter")).create(OpenRouterApiService::class.java) }
    single<ArticleAiService> { ArticleAiService(get()) }
}
