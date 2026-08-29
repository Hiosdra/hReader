package com.hiosdra.hreader.adapter.preferences

import kotlinx.coroutines.CancellationException

internal enum class SecretId {
    FRESHRSS_USERNAME,
    FRESHRSS_API_PASSWORD,
    MINIFLUX_API_TOKEN,
    OPENROUTER_API_KEY
}

internal enum class SecretReadSource {
    NONE,
    ENCRYPTED,
    LEGACY,
    LEGACY_FALLBACK,
    UNREADABLE_ENCRYPTED
}

internal data class SecretReadResult(
    val value: String,
    val source: SecretReadSource,
    val legacyEntryPresent: Boolean = false,
    val encryptedEntryPresent: Boolean = false,
    val encryptedReadError: Exception? = null
)

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
    val legacyKeysWithReadableEncryptedValue: Set<SecretId>,
    val readResults: Map<SecretId, SecretReadResult>,
    val encryptionErrors: Map<SecretId, Exception>
)

internal fun readSecretValue(
    legacyValue: String?,
    encryptedValue: String?,
    decrypt: (String) -> String
): String = readSecretValueWithDiagnostics(legacyValue, encryptedValue, decrypt).value

internal fun readSecretValueWithDiagnostics(
    legacyValue: String?,
    encryptedValue: String?,
    decrypt: (String) -> String
): SecretReadResult {
    val legacyEntryPresent = legacyValue != null
    val encryptedEntryPresent = encryptedValue != null
    val legacy = legacyValue?.takeIf(String::isNotBlank)
    val encoded = encryptedValue?.takeIf(String::isNotBlank)
    if (encoded == null) {
        val error = encryptedValue?.let { IllegalArgumentException("Encrypted secret is blank") }
        return if (legacy == null) {
            SecretReadResult(
                value = "",
                source = if (error == null) SecretReadSource.NONE else SecretReadSource.UNREADABLE_ENCRYPTED,
                legacyEntryPresent = legacyEntryPresent,
                encryptedEntryPresent = encryptedEntryPresent,
                encryptedReadError = error
            )
        } else {
            SecretReadResult(
                value = legacy,
                source = if (error == null) SecretReadSource.LEGACY else SecretReadSource.LEGACY_FALLBACK,
                legacyEntryPresent = legacyEntryPresent,
                encryptedEntryPresent = encryptedEntryPresent,
                encryptedReadError = error
            )
        }
    }

    val decrypted = try {
        DecryptionAttempt(decrypt(encoded).takeIf(String::isNotBlank))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        DecryptionAttempt(error = error)
    }

    decrypted.value?.let { value ->
        return SecretReadResult(
            value = value,
            source = SecretReadSource.ENCRYPTED,
            legacyEntryPresent = legacyEntryPresent,
            encryptedEntryPresent = encryptedEntryPresent
        )
    }
    return if (legacy == null) {
        SecretReadResult(
            value = "",
            source = SecretReadSource.UNREADABLE_ENCRYPTED,
            legacyEntryPresent = legacyEntryPresent,
            encryptedEntryPresent = encryptedEntryPresent,
            encryptedReadError = decrypted.error
        )
    } else {
        SecretReadResult(
            value = legacy,
            source = SecretReadSource.LEGACY_FALLBACK,
            legacyEntryPresent = legacyEntryPresent,
            encryptedEntryPresent = encryptedEntryPresent,
            encryptedReadError = decrypted.error
        )
    }
}

internal fun planSecretMigration(
    slots: List<SecretSlot>,
    decrypt: (String) -> String,
    encrypt: (String) -> String
): SecretMigrationPlan {
    val valuesToWrite = linkedMapOf<SecretId, SecretMigrationValue>()
    val legacyKeysWithReadableEncryptedValue = linkedSetOf<SecretId>()
    val readResults = linkedMapOf<SecretId, SecretReadResult>()
    val encryptionErrors = linkedMapOf<SecretId, Exception>()

    slots.forEach { slot ->
        val legacyValue = slot.legacyValue?.takeIf(String::isNotBlank)
        val readResult = readSecretValueWithDiagnostics(
            legacyValue = slot.legacyValue,
            encryptedValue = slot.encryptedValue,
            decrypt = decrypt
        )
        readResults[slot.id] = readResult

        when (readResult.source) {
            SecretReadSource.ENCRYPTED -> if (legacyValue != null) {
                legacyKeysWithReadableEncryptedValue += slot.id
            }
            SecretReadSource.LEGACY, SecretReadSource.LEGACY_FALLBACK -> {
                try {
                    valuesToWrite[slot.id] = SecretMigrationValue(
                        plaintext = readResult.value,
                        encrypted = encrypt(readResult.value)
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    encryptionErrors[slot.id] = error
                }
            }
            SecretReadSource.NONE, SecretReadSource.UNREADABLE_ENCRYPTED -> Unit
        }
    }

    return SecretMigrationPlan(
        valuesToWrite = valuesToWrite,
        legacyKeysWithReadableEncryptedValue = legacyKeysWithReadableEncryptedValue,
        readResults = readResults,
        encryptionErrors = encryptionErrors
    )
}

private data class DecryptionAttempt(
    val value: String? = null,
    val error: Exception? = null
)
