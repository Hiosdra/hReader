package com.hiosdra.hreader.adapter.preferences

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretCipherTest {
    private val key: SecretKey = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val cipher = SecretCipher(existingKeyProvider = { key })

    @Test
    fun `round trip keeps secret value`() {
        val encrypted = cipher.encrypt("token with unicode żółć")

        assertEquals("token with unicode żółć", cipher.decrypt(encrypted))
    }

    @Test
    fun `decrypts v1 ciphertext with a caller-provided legacy IV`() {
        val iv = ByteArray(12) { (it + 1).toByte() }
        val legacyCipher = Cipher.getInstance("AES/GCM/NoPadding")
        legacyCipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = legacyCipher.doFinal("legacy secret".toByteArray(UTF_8))
        val encoded = listOf(
            "v1",
            Base64.getUrlEncoder().withoutPadding().encodeToString(iv),
            Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext)
        ).joinToString(".")

        assertEquals("legacy secret", cipher.decrypt(encoded))
    }

    @Test
    fun `each encryption gets a fresh initialization vector`() {
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"))
    }

    @Test
    fun `malformed ciphertext is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            cipher.decrypt("v1.invalid.invalid")
        }
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val encrypted = cipher.encrypt("secret")
        val parts = encrypted.split('.')
        val tampered = parts.dropLast(1).plus(parts.last() + "A").joinToString(".")

        assertThrows(Exception::class.java) { cipher.decrypt(tampered) }
    }

    @Test
    fun `encryption can create a key while decryption only uses the existing key`() {
        var generated = false
        val cipher = SecretCipher(
            existingKeyProvider = { key },
            keyGenerator = {
                generated = true
                key
            }
        )

        val encrypted = cipher.encrypt("secret")

        assertTrue(generated)
        assertEquals("secret", cipher.decrypt(encrypted))
    }

    @Test
    fun `decryption does not generate a replacement key`() {
        val encrypted = cipher.encrypt("secret")
        var generated = false
        val reader = SecretCipher(
            existingKeyProvider = {
                throw SecretKeyUnavailableException("missing")
            },
            keyGenerator = {
                generated = true
                key
            }
        )

        assertThrows(SecretKeyUnavailableException::class.java) {
            reader.decrypt(encrypted)
        }
        assertFalse(generated)
    }
}
