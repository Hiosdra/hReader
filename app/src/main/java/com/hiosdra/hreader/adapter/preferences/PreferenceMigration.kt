package com.hiosdra.hreader.adapter.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal class PreferenceMigration(
    context: Context,
    private val secretCipher: SecretCipher = defaultSecretCipher()
) {
    private val applicationContext = context.applicationContext
    private val legacyPreferences = applicationContext.getSharedPreferences(
        PREFERENCES_FILE,
        Context.MODE_PRIVATE
    )
    private val legacySecretPreferences = applicationContext.getSharedPreferences(
        SECRETS_FILE,
        Context.MODE_PRIVATE
    )

    suspend fun migrate(
        preferencesDataStore: DataStore<Preferences>,
        secretDataStore: DataStore<Preferences>
    ) {
        migrateLegacySecretsIntoDataStore(secretDataStore)
        migrateLegacyRegularKeys(preferencesDataStore)
        migrateSecrets(secretDataStore)
        clearMigratedLegacySecrets(secretDataStore)
    }

    private suspend fun migrateLegacySecretsIntoDataStore(
        secretDataStore: DataStore<Preferences>
    ) {
        val current = secretDataStore.data.first()
        val valuesToCopy = secretDefinitions.mapNotNull { definition ->
            if (!current[definition.legacyKey].isNullOrBlank() ||
                !current[definition.encryptedKey].isNullOrBlank()
            ) {
                return@mapNotNull null
            }
            val value = legacySecretValue(definition.legacyName) ?: return@mapNotNull null
            definition to value
        }
        if (valuesToCopy.isEmpty()) return

        secretDataStore.edit { preferences ->
            valuesToCopy.forEach { (definition, value) ->
                preferences[definition.legacyKey] = value
            }
        }
    }

    private suspend fun migrateLegacyRegularKeys(
        preferencesDataStore: DataStore<Preferences>
    ) {
        preferencesDataStore.edit { preferences ->
            migrateLegacyPreferenceKeys(preferences)
        }
    }

    private suspend fun migrateSecrets(secretDataStore: DataStore<Preferences>) {
        val loaded = secretDataStore.data.first()
        val plan = planSecretMigration(
            slots = secretDefinitions.map { definition ->
                SecretSlot(
                    id = definition.id,
                    legacyValue = loaded[definition.legacyKey],
                    encryptedValue = loaded[definition.encryptedKey]
                )
            },
            decrypt = secretCipher::decrypt,
            encrypt = secretCipher::encrypt
        )
        if (plan.valuesToWrite.isEmpty() && plan.legacyKeysWithReadableEncryptedValue.isEmpty()) return

        secretDataStore.edit { preferences ->
            plan.valuesToWrite.forEach { (id, value) ->
                preferences[definitionFor(id).encryptedKey] = value.encrypted
            }
            preferences[SecretPreferenceKeys.migrationState] =
                if (plan.valuesToWrite.isEmpty()) "complete" else "prepared"
        }

        val verified = try {
            val prepared = secretDataStore.data.first()
            plan.valuesToWrite.all { (id, value) ->
                prepared[definitionFor(id).encryptedKey]?.let(secretCipher::decrypt) == value.plaintext
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        if (!verified) return

        secretDataStore.edit { preferences ->
            (plan.valuesToWrite.keys + plan.legacyKeysWithReadableEncryptedValue).forEach { id ->
                preferences.remove(definitionFor(id).legacyKey)
            }
            preferences[SecretPreferenceKeys.migrationState] = "complete"
        }
    }

    private suspend fun clearMigratedLegacySecrets(
        secretDataStore: DataStore<Preferences>
    ) {
        val stored = secretDataStore.data.first()
        val readableIds = secretDefinitions.mapNotNull { definition ->
            val result = readSecretValueWithDiagnostics(
                legacyValue = stored[definition.legacyKey],
                encryptedValue = stored[definition.encryptedKey],
                decrypt = secretCipher::decrypt
            )
            definition.id.takeIf { result.source == SecretReadSource.ENCRYPTED }
        }
        if (readableIds.isEmpty()) return

        listOf(legacySecretPreferences, legacyPreferences).forEach { preferences ->
            val editor = preferences.edit()
            readableIds.forEach { id -> editor.remove(definitionFor(id).legacyName) }
            editor.commit()
        }
    }

    private fun legacySecretValue(name: String): String? = sequenceOf(
        legacySecretPreferences,
        legacyPreferences
    ).mapNotNull { preferences ->
        runCatching { preferences.getString(name, null) }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
    }.firstOrNull()

    private companion object {
        const val PREFERENCES_FILE = "hreader_prefs"
        const val SECRETS_FILE = "hreader_secrets"
    }
}

internal fun migrateLegacyPreferenceKeys(preferences: MutablePreferences) {
    if (preferences[TtsPreferenceKeys.ttsVitsNoiseScale] == null) {
        preferences[TtsPreferenceKeys.legacyTtsVitsNoiseScale]?.let { value ->
            preferences[TtsPreferenceKeys.ttsVitsNoiseScale] = value
        }
    }
    if (preferences[TtsPreferenceKeys.ttsVitsDurationNoiseScale] == null) {
        preferences[TtsPreferenceKeys.legacyTtsVitsDurationNoiseScale]?.let { value ->
            preferences[TtsPreferenceKeys.ttsVitsDurationNoiseScale] = value
        }
    }
    preferences.remove(TtsPreferenceKeys.legacyTtsVitsNoiseScale)
    preferences.remove(TtsPreferenceKeys.legacyTtsVitsDurationNoiseScale)
}

internal fun TtsAdvancedSettings.normalizedForStorage() = copy(
    numThreads = numThreads.coerceIn(1, 4),
    silenceScale = silenceScale.coerceIn(0f, 1f),
    supertonicSpeaker = supertonicSpeaker.coerceIn(0, 9),
    supertonicSteps = supertonicSteps.coerceIn(4, 12),
    kokoroSpeaker = kokoroSpeaker.coerceIn(0, 102),
    kittenSpeaker = kittenSpeaker.coerceIn(0, 7),
    vitsNoiseScale = vitsNoiseScale.coerceIn(0f, 1f),
    vitsDurationNoiseScale = vitsDurationNoiseScale.coerceIn(0f, 1f)
)
