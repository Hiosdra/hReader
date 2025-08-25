package com.hiosdra.hreader.di

import com.hiosdra.hreader.BuildConfig
import com.hiosdra.hreader.data.ai.ArticleAiService
import com.hiosdra.hreader.data.ai.OpenRouterApiService
import com.hiosdra.hreader.data.remote.AuthInterceptor
import com.hiosdra.hreader.data.remote.MinifluxApiRepository
import com.hiosdra.hreader.data.remote.MinifluxApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

val networkModule = module {
    single<AuthInterceptor> { AuthInterceptor() }
    single<HttpLoggingInterceptor> {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            redactHeader("X-Auth-Token")
            redactHeader("CF-Access-Client-Id")
            redactHeader("CF-Access-Client-Secret")
            redactHeader("Authorization")
        }
    }
    single<OkHttpClient> {
        OkHttpClient.Builder()
            .addInterceptor(get<AuthInterceptor>())
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
            .baseUrl(BuildConfig.MINIFLUX_BASE_URL)
            .client(get<OkHttpClient>())
            .addConverterFactory(MoshiConverterFactory.create(get<Moshi>()))
            .build()
    }

    single<MinifluxApiService> { get<Retrofit>().create(MinifluxApiService::class.java) }
    single<MinifluxApiRepository> { MinifluxApiRepository(get()) }
    
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
