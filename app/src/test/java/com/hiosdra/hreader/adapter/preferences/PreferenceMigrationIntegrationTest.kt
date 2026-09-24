package com.hiosdra.hreader.adapter.preferences

import android.app.Application
import androidx.datastore.preferences.preferencesDataStoreFile
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = PreferenceMigrationIntegrationTestApplication::class, sdk = [35])
class PreferenceMigrationIntegrationTest {
    private val secretKey = SecretKeySpec(ByteArray(16) { 9 }, "AES")

    @Before
    fun clearStorage() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteSharedPreferences(PREFERENCES_FILE)
        context.deleteSharedPreferences(SECRETS_FILE)
        context.preferencesDataStoreFile(PREFERENCES_FILE).delete()
        context.preferencesDataStoreFile(SECRETS_FILE).delete()
    }

    @Test
    fun `legacy shared preferences are migrated once into encrypted storage`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(SECRETS_FILE, 0).edit()
            .putString(SecretPreferenceKeys.MINIFLUX_API_TOKEN, "legacy-token")
            .commit()

        val firstStorage = newStorage(context)
        try {
            firstStorage.awaitReady()
            val secrets = SecretPreferences(firstStorage, newCipher())

            assertEquals("legacy-token", secrets.get(SecretId.MINIFLUX_API_TOKEN))
            assertNull(firstStorage.getSecret(SecretPreferenceKeys.minifluxApiToken))
            assertNotNull(firstStorage.getSecret(SecretPreferenceKeys.minifluxApiTokenEncrypted))
            assertNull(
                context.getSharedPreferences(SECRETS_FILE, 0)
                    .getString(SecretPreferenceKeys.MINIFLUX_API_TOKEN, null)
            )
            firstStorage.awaitWrites()
        } finally {
            firstStorage.close()
        }

        val restartedStorage = newStorage(context)
        try {
            restartedStorage.awaitReady()
            assertEquals(
                "legacy-token",
                SecretPreferences(restartedStorage, newCipher()).get(SecretId.MINIFLUX_API_TOKEN)
            )
        } finally {
            restartedStorage.close()
        }
    }

    private fun newStorage(context: android.content.Context): PreferenceStorage =
        PreferenceStorage(context, PreferenceMigration(context, newCipher()))

    private fun newCipher() = SecretCipher(existingKeyProvider = { secretKey })

    private companion object {
        const val PREFERENCES_FILE = "hreader_prefs"
        const val SECRETS_FILE = "hreader_secrets"
    }
}

internal class PreferenceMigrationIntegrationTestApplication : Application()
