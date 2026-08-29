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
        assertEquals(
            "legacy-token",
            readSecretValue(
                legacyValue = "legacy-token",
                encryptedValue = "unreadable",
                decrypt = { throw IllegalArgumentException("cannot decrypt") }
            )
        )
    }

    @Test
    fun `readable encrypted value takes precedence over legacy value`() {
        assertEquals(
            "encrypted-value",
            readSecretValue(
                legacyValue = "legacy-value",
                encryptedValue = "readable",
                decrypt = { "encrypted-value" }
            )
        )
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
    }
}
