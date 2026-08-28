package com.hiosdra.hreader.adapter.preferences

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "hreader.secrets.v1"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS = 128
private const val WIRE_VERSION = "v1"
private const val IV_BYTES = 12

internal class SecretCipher(
    private val keyProvider: () -> SecretKey,
    private val random: SecureRandom = SecureRandom()
) {
    fun encrypt(value: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(value.toByteArray(UTF_8))
        return listOf(WIRE_VERSION, encode(iv), encode(ciphertext)).joinToString(".")
    }

    fun decrypt(value: String): String {
        val parts = value.split('.')
        require(parts.size == 3 && parts[0] == WIRE_VERSION) { "Unsupported secret format" }
        val iv = decode(parts[1]).also { require(it.size == IV_BYTES) { "Invalid secret IV" } }
        val ciphertext = decode(parts[2]).also { require(it.size > GCM_TAG_BITS / 8) { "Invalid secret payload" } }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).toString(UTF_8)
    }

    private fun encode(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
}

internal class AndroidKeystoreSecretKeyProvider {
    operator fun invoke(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }
}
