package com.hiosdra.hreader.core.application.content

import java.security.MessageDigest

internal class WeightedStringLruCache<K : Any>(
    private val maximumWeight: Long
) {
    private val lock = Any()
    private val entries = LinkedHashMap<K, String>(8, 0.75f, true)
    private var retainedWeight = 0L

    operator fun get(key: K): String? = synchronized(lock) { entries[key] }

    fun put(key: K, value: String) {
        val weight = weightOf(value)
        if (weight > maximumWeight) return
        synchronized(lock) {
            entries.remove(key)?.let { retainedWeight -= weightOf(it) }
            while (retainedWeight + weight > maximumWeight) {
                val eldest = entries.entries.iterator()
                if (!eldest.hasNext()) break
                retainedWeight -= weightOf(eldest.next().value)
                eldest.remove()
            }
            entries[key] = value
            retainedWeight += weight
        }
    }

    private fun weightOf(value: String): Long = value.length.toLong() + ENTRY_OVERHEAD

    private companion object {
        const val ENTRY_OVERHEAD = 128L
    }
}

internal fun String.contentFingerprint(): String = contentFingerprint(this)

internal fun contentFingerprint(vararg values: String?): String {
    val digest = MessageDigest.getInstance("SHA-256")
    values.forEach { value ->
        val bytes = value?.toByteArray(Charsets.UTF_8)
        digest.update((bytes?.size ?: -1).toString().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        if (bytes != null) digest.update(bytes)
    }
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}
