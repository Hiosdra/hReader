package com.hiosdra.hreader.adapter.preferences

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SecretMigrationTest {
    @Test
    fun `cancellation while decrypting is propagated`() {
        val cancellation = CancellationException("cancelled")

        try {
            readSecretValue(
                legacyValue = null,
                encryptedValue = "encrypted",
                decrypt = { throw cancellation }
            )
            fail("Expected cancellation")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
    }

    @Test
    fun `unreadable encrypted value falls back to legacy value`() {
        val error = IllegalArgumentException("cannot decrypt")
        val result = readSecretValueWithDiagnostics(
            legacyValue = "legacy-token",
            encryptedValue = "unreadable",
            decrypt = { throw error }
        )

        assertEquals(
            "legacy-token",
            readSecretValue(
                legacyValue = "legacy-token",
                encryptedValue = "unreadable",
                decrypt = { throw error }
            )
        )
        assertEquals(SecretReadSource.LEGACY_FALLBACK, result.source)
        assertSame(error, result.encryptedReadError)
    }

    @Test
    fun `readable encrypted value takes precedence over legacy value`() {
        val result = readSecretValueWithDiagnostics(
            legacyValue = "legacy-value",
            encryptedValue = "readable",
            decrypt = { "encrypted-value" }
        )

        assertEquals(
            "encrypted-value",
            readSecretValue(
                legacyValue = "legacy-value",
                encryptedValue = "readable",
                decrypt = { "encrypted-value" }
            )
        )
        assertEquals(SecretReadSource.ENCRYPTED, result.source)
    }

    @Test
    fun `missing values are reported without attempting decryption`() {
        val result = readSecretValueWithDiagnostics(
            legacyValue = null,
            encryptedValue = null,
            decrypt = { throw AssertionError("Decryption should not be attempted") }
        )

        assertEquals("", result.value)
        assertEquals(SecretReadSource.NONE, result.source)
        assertEquals(false, result.legacyEntryPresent)
        assertEquals(false, result.encryptedEntryPresent)
    }

    @Test
    fun `blank legacy value is distinguished from a missing entry`() {
        val result = readSecretValueWithDiagnostics(
            legacyValue = " ",
            encryptedValue = null,
            decrypt = { throw AssertionError("Decryption should not be attempted") }
        )

        assertEquals(SecretReadSource.NONE, result.source)
        assertEquals(true, result.legacyEntryPresent)
        assertEquals(false, result.encryptedEntryPresent)
    }

    @Test
    fun `unreadable encrypted value without legacy data is reported`() {
        val error = IllegalStateException("keystore key is unavailable")
        val result = readSecretValueWithDiagnostics(
            legacyValue = null,
            encryptedValue = "unreadable",
            decrypt = { throw error }
        )

        assertEquals("", result.value)
        assertEquals(SecretReadSource.UNREADABLE_ENCRYPTED, result.source)
        assertEquals(false, result.legacyEntryPresent)
        assertEquals(true, result.encryptedEntryPresent)
        assertSame(error, result.encryptedReadError)
    }

    @Test
    fun `blank encrypted value is reported separately from a missing value`() {
        val result = readSecretValueWithDiagnostics(
            legacyValue = null,
            encryptedValue = " ",
            decrypt = { throw AssertionError("Decryption should not be attempted") }
        )

        assertEquals(SecretReadSource.UNREADABLE_ENCRYPTED, result.source)
        assertEquals(false, result.legacyEntryPresent)
        assertEquals(true, result.encryptedEntryPresent)
        assertEquals(IllegalArgumentException::class.java, result.encryptedReadError?.javaClass)
    }

    @Test
    fun `unreadable encrypted value without legacy data remains untouched`() {
        val plan = planSecretMigration(
            slots = listOf(
                SecretSlot(
                    id = SecretId.MINIFLUX_API_TOKEN,
                    legacyValue = null,
                    encryptedValue = "unreadable"
                )
            ),
            decrypt = { throw IllegalArgumentException("cannot decrypt") },
            encrypt = { "encrypted:$it" }
        )

        assertTrue(plan.valuesToWrite.isEmpty())
        assertTrue(plan.legacyKeysWithReadableEncryptedValue.isEmpty())
    }

    @Test
    fun `one unreadable value does not discard other legacy values`() {
        val plan = planSecretMigration(
            slots = listOf(
                SecretSlot(
                    id = SecretId.MINIFLUX_API_TOKEN,
                    legacyValue = "miniflux-token",
                    encryptedValue = "unreadable"
                ),
                SecretSlot(
                    id = SecretId.OPENROUTER_API_KEY,
                    legacyValue = "openrouter-key",
                    encryptedValue = null
                ),
                SecretSlot(
                    id = SecretId.FRESHRSS_API_PASSWORD,
                    legacyValue = "old-password",
                    encryptedValue = "readable"
                )
            ),
            decrypt = { encoded ->
                if (encoded == "readable") "stored-password" else throw IllegalArgumentException()
            },
            encrypt = { "encrypted:$it" }
        )

        assertEquals(
            mapOf(
                SecretId.MINIFLUX_API_TOKEN to SecretMigrationValue(
                    plaintext = "miniflux-token",
                    encrypted = "encrypted:miniflux-token"
                ),
                SecretId.OPENROUTER_API_KEY to SecretMigrationValue(
                    plaintext = "openrouter-key",
                    encrypted = "encrypted:openrouter-key"
                )
            ),
            plan.valuesToWrite
        )
        assertEquals(
            setOf(SecretId.FRESHRSS_API_PASSWORD),
            plan.legacyKeysWithReadableEncryptedValue
        )
        assertEquals(
            SecretReadSource.LEGACY_FALLBACK,
            plan.readResults[SecretId.MINIFLUX_API_TOKEN]?.source
        )
        assertEquals(
            SecretReadSource.LEGACY,
            plan.readResults[SecretId.OPENROUTER_API_KEY]?.source
        )
        assertEquals(
            SecretReadSource.ENCRYPTED,
            plan.readResults[SecretId.FRESHRSS_API_PASSWORD]?.source
        )
    }

    @Test
    fun `encryption failure is isolated and reported per secret`() {
        val error = IllegalStateException("key generation failed")
        val plan = planSecretMigration(
            slots = listOf(
                SecretSlot(
                    id = SecretId.MINIFLUX_API_TOKEN,
                    legacyValue = "miniflux-token",
                    encryptedValue = null
                ),
                SecretSlot(
                    id = SecretId.OPENROUTER_API_KEY,
                    legacyValue = "openrouter-key",
                    encryptedValue = null
                )
            ),
            decrypt = { throw AssertionError("Decryption should not be attempted") },
            encrypt = { value ->
                if (value == "miniflux-token") throw error
                "encrypted:$value"
            }
        )

        assertEquals(
            mapOf(
                SecretId.OPENROUTER_API_KEY to SecretMigrationValue(
                    plaintext = "openrouter-key",
                    encrypted = "encrypted:openrouter-key"
                )
            ),
            plan.valuesToWrite
        )
        assertSame(error, plan.encryptionErrors[SecretId.MINIFLUX_API_TOKEN])
    }
}
