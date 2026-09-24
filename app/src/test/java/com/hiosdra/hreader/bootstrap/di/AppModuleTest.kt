package com.hiosdra.hreader.bootstrap.di

import android.app.Application
import com.hiosdra.hreader.core.application.port.out.StorageDatabaseStatsStore
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = AppModuleTestApplication::class, sdk = [35])
class AppModuleTest {
    @Test
    fun storageDatabaseStatsStoreIsBoundToApplicationPort() {
        val application = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(appModule)
        }

        try {
            assertNotNull(application.koin.get<StorageDatabaseStatsStore>())
        } finally {
            application.close()
        }
    }
}

internal class AppModuleTestApplication : Application()
