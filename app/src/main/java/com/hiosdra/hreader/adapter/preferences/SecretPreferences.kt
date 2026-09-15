package com.hiosdra.hreader.adapter.preferences

import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences

internal data class SecretDefinition(
    val id: SecretId,
    val legacyName: String,
    val legacyKey: androidx.datastore.preferences.core.Preferences.Key<String>,
    val encryptedKey: androidx.datastore.preferences.core.Preferences.Key<String>
)

internal val secretDefinitions = listOf(
    SecretDefinition(
        SecretId.FRESHRSS_USERNAME,
        SecretPreferenceKeys.FRESHRSS_USERNAME,
        SecretPreferenceKeys.freshRssUsername,
        SecretPreferenceKeys.freshRssUsernameEncrypted
    ),
    SecretDefinition(
        SecretId.FRESHRSS_API_PASSWORD,
        SecretPreferenceKeys.FRESHRSS_API_PASSWORD,
        SecretPreferenceKeys.freshRssApiPassword,
        SecretPreferenceKeys.freshRssApiPasswordEncrypted
    ),
    SecretDefinition(
        SecretId.MINIFLUX_API_TOKEN,
        SecretPreferenceKeys.MINIFLUX_API_TOKEN,
        SecretPreferenceKeys.minifluxApiToken,
        SecretPreferenceKeys.minifluxApiTokenEncrypted
    ),
    SecretDefinition(
        SecretId.OPENROUTER_API_KEY,
        SecretPreferenceKeys.OPENROUTER_API_KEY,
        SecretPreferenceKeys.openRouterApiKey,
        SecretPreferenceKeys.openRouterApiKeyEncrypted
    )
)

internal class SecretPreferences(
    private val storage: PreferenceStorage,
    private val cipher: SecretCipher = defaultSecretCipher()
) {
    fun get(id: SecretId): String {
        val definition = definitionFor(id)
        val result = readSecretValueWithDiagnostics(
            legacyValue = storage.getSecret(definition.legacyKey),
            encryptedValue = storage.getSecret(definition.encryptedKey),
            decrypt = cipher::decrypt
        )
        if (result.source != SecretReadSource.ENCRYPTED && result.source != SecretReadSource.NONE) {
            Log.w(TAG, "Secret $id is read from ${result.source}")
        }
        return result.value
    }

    fun set(id: SecretId, value: String) {
        storage.updateSecrets { writeSecretValue(definitionFor(id), value, cipher) }
    }

    internal fun update(transform: MutablePreferences.() -> Unit) {
        storage.updateSecrets(transform)
    }

    internal fun writeSecretValue(
        preferences: MutablePreferences,
        id: SecretId,
        value: String
    ) {
        preferences.writeSecretValue(definitionFor(id), value, cipher)
    }

    private companion object {
        const val TAG = "SecretPreferences"
    }
}

internal fun MutablePreferences.writeSecretValue(
    definition: SecretDefinition,
    value: String,
    cipher: SecretCipher
) {
    if (value.isBlank()) {
        remove(definition.encryptedKey)
        remove(definition.legacyKey)
        return
    }
    this[definition.encryptedKey] = cipher.encrypt(value)
    remove(definition.legacyKey)
    this[SecretPreferenceKeys.migrationState] = "complete"
}

internal fun definitionFor(id: SecretId): SecretDefinition =
    secretDefinitions.first { it.id == id }

internal fun defaultSecretCipher(): SecretCipher {
    val keyProvider = AndroidKeystoreSecretKeyProvider()
    return SecretCipher(
        existingKeyProvider = keyProvider::getExisting,
        keyGenerator = keyProvider::getOrCreate
    )
}
