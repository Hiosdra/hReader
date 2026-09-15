package com.hiosdra.hreader.adapter.preferences

import android.app.Application
import androidx.datastore.preferences.preferencesDataStoreFile
import com.hiosdra.hreader.core.application.ai.GemmaBackend
import com.hiosdra.hreader.core.application.sync.SyncCheckpoint
import com.hiosdra.hreader.core.application.sync.SyncCheckpointMode
import com.hiosdra.hreader.core.domain.model.BackendType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(application = PreferenceStoresPersistenceTestApplication::class, sdk = [35])
class PreferenceStoresPersistenceTest {
    private val secretKey = SecretKeySpec(ByteArray(16) { 7 }, "AES")

    @Before
    fun clearStorage() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteSharedPreferences(PREFERENCES_FILE)
        context.deleteSharedPreferences(SECRETS_FILE)
        context.preferencesDataStoreFile(PREFERENCES_FILE).delete()
        context.preferencesDataStoreFile(SECRETS_FILE).delete()
    }

    @Test
    fun `capability stores persist concurrent unrelated updates and keep secrets separate`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val firstStorage = newStorage(context)
        try {
            firstStorage.awaitReady()
            val secrets = SecretPreferences(firstStorage, newCipher())
            val backend = BackendPreferencesStore(firstStorage, secrets)
            val ai = AiPreferencesStore(firstStorage, secrets)
            val sync = SyncPreferencesStore(firstStorage)

            listOf(
                async(Dispatchers.Default) { backend.setBackendType(BackendType.MINIFLUX) },
                async(Dispatchers.Default) {
                    backend.setServerUrl(BackendType.MINIFLUX, "https://reader.example")
                },
                async(Dispatchers.Default) {
                    backend.setBackendSecret(BackendType.MINIFLUX, "miniflux-token")
                },
                async(Dispatchers.Default) { ai.setOpenRouterApiKey("openrouter-key") },
                async(Dispatchers.Default) { ai.setAiModelId("test/model") },
                async(Dispatchers.Default) { ai.setGemmaBackend(GemmaBackend.CPU) },
                async(Dispatchers.Default) { sync.setSyncIntervalMinutes(45) }
            ).awaitAll()
            firstStorage.awaitWrites()
        } finally {
            firstStorage.close()
        }

        val restartedStorage = newStorage(context)
        try {
            restartedStorage.awaitReady()
            val secrets = SecretPreferences(restartedStorage, newCipher())
            val backend = BackendPreferencesStore(restartedStorage, secrets)
            val ai = AiPreferencesStore(restartedStorage, secrets)
            val sync = SyncPreferencesStore(restartedStorage)

            assertEquals(BackendType.MINIFLUX, backend.getBackendType())
            assertEquals("https://reader.example", backend.getServerUrl(BackendType.MINIFLUX))
            assertEquals("miniflux-token", backend.getBackendSecret(BackendType.MINIFLUX))
            assertEquals("openrouter-key", ai.getOpenRouterApiKey())
            assertEquals("test/model", ai.getAiModelId())
            assertEquals(GemmaBackend.CPU, ai.getGemmaBackend())
            assertEquals(45, sync.getSyncIntervalMinutes())
            assertTrue(restartedStorage.get(SecretPreferenceKeys.openRouterApiKey) == null)
        } finally {
            restartedStorage.close()
        }
    }

    @Test
    fun `sync recovery state survives a capability store restart`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val checkpoint = SyncCheckpoint(
            ownerKey = "miniflux|https://reader.example",
            mode = SyncCheckpointMode.INCREMENTAL,
            startedAt = 1_000L,
            changedAfter = 900L,
            cursor = "cursor.with.separators",
            fullSyncRunId = "full-run"
        )
        val firstStorage = newStorage(context)
        try {
            firstStorage.awaitReady()
            val sync = SyncPreferencesStore(firstStorage)
            val syncHealth = SyncHealthPreferencesStore(firstStorage, newMoshi())

            sync.setCacheOwnerKey(checkpoint.ownerKey)
            sync.setCacheCleanupPending(true)
            sync.setLastSyncTimestamp(2_000L)
            sync.setLastFullSyncTimestamp(1_500L)
            sync.setSyncCheckpoint(checkpoint)
            syncHealth.recordSyncStarted(1_000L, "run-1")
            firstStorage.awaitWrites()
        } finally {
            firstStorage.close()
        }

        val restartedStorage = newStorage(context)
        try {
            restartedStorage.awaitReady()
            val sync = SyncPreferencesStore(restartedStorage)
            val syncHealth = SyncHealthPreferencesStore(restartedStorage, newMoshi())

            assertEquals(checkpoint.ownerKey, sync.getCacheOwnerKey())
            assertTrue(sync.isCacheCleanupPending())
            assertEquals(2_000L, sync.getLastSyncTimestamp())
            assertEquals(1_500L, sync.getLastFullSyncTimestamp())
            assertEquals(checkpoint, sync.getSyncCheckpoint())
            assertEquals("run-1", syncHealth.getSnapshot().lastRun?.runId)
        } finally {
            restartedStorage.close()
        }
    }

    private fun newStorage(context: android.content.Context): PreferenceStorage =
        PreferenceStorage(context, PreferenceMigration(context, newCipher()))

    private fun newCipher() = SecretCipher(existingKeyProvider = { secretKey })

    private fun newMoshi() = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private companion object {
        const val PREFERENCES_FILE = "hreader_prefs"
        const val SECRETS_FILE = "hreader_secrets"
    }
}

private class PreferenceStoresPersistenceTestApplication : Application()
