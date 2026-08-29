package com.hiosdra.hreader.adapter.preferences

import kotlinx.coroutines.CancellationException

internal enum class SecretId {
    FRESHRSS_USERNAME,
    FRESHRSS_API_PASSWORD,
    MINIFLUX_API_TOKEN,
    OPENROUTER_API_KEY
}

internal data class SecretSlot(
    val id: SecretId,
    val legacyValue: String?,
    val encryptedValue: String?
)

internal data class SecretMigrationValue(
    val plaintext: String,
    val encrypted: String
)

internal data class SecretMigrationPlan(
    val valuesToWrite: Map<SecretId, SecretMigrationValue>,
    val legacyKeysWithReadableEncryptedValue: Set<SecretId>
)

internal fun readSecretValue(
    legacyValue: String?,
    encryptedValue: String?,
    decrypt: (String) -> String
): String = decryptSecretOrNull(encryptedValue, decrypt) ?: legacyValue.orEmpty()

private fun decryptSecretOrNull(
    encryptedValue: String?,
    decrypt: (String) -> String
): String? {
    val encoded = encryptedValue?.takeIf(String::isNotBlank) ?: return null
    return try {
        decrypt(encoded).takeIf(String::isNotBlank)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}

internal fun planSecretMigration(
    slots: List<SecretSlot>,
    decrypt: (String) -> String,
    encrypt: (String) -> String
): SecretMigrationPlan {
    val valuesToWrite = linkedMapOf<SecretId, SecretMigrationValue>()
    val legacyKeysWithReadableEncryptedValue = linkedSetOf<SecretId>()

    slots.forEach { slot ->
        val legacyValue = slot.legacyValue?.takeIf(String::isNotBlank)
        val decryptedValue = decryptSecretOrNull(slot.encryptedValue, decrypt)

        when {
            decryptedValue != null && legacyValue != null -> {
                legacyKeysWithReadableEncryptedValue += slot.id
            }
            legacyValue != null -> {
                valuesToWrite[slot.id] = SecretMigrationValue(
                    plaintext = legacyValue,
                    encrypted = encrypt(legacyValue)
                )
            }
        }
    }

    return SecretMigrationPlan(
        valuesToWrite = valuesToWrite,
        legacyKeysWithReadableEncryptedValue = legacyKeysWithReadableEncryptedValue
    )
}
