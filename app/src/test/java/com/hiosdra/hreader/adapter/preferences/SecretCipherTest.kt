package com.hiosdra.hreader.adapter.preferences

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SecretCipherTest {
    private val key: SecretKey = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val cipher = SecretCipher(keyProvider = { key })

    @Test
    fun `round trip keeps secret value`() {
        val encrypted = cipher.encrypt("token with unicode żółć")

        assertEquals("token with unicode żółć", cipher.decrypt(encrypted))
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
}
