package com.hiosdra.hreader.adapter.image

import java.util.Locale

internal fun normalizedRasterImageContentType(value: String?): String? {
    val type = value?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) ?: return null
    return when (type) {
        "image/jpg" -> "image/jpeg"
        "image/png",
        "image/jpeg",
        "image/gif",
        "image/webp",
        "image/bmp" -> type
        else -> null
    }
}

internal fun isSafeRasterImage(contentType: String, bytes: ByteArray): Boolean = when (contentType) {
    "image/png" -> bytes.startsWithBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
    "image/jpeg" -> bytes.startsWithBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))
    "image/gif" -> bytes.startsWithAscii("GIF87a") || bytes.startsWithAscii("GIF89a")
    "image/webp" -> bytes.startsWithAscii("RIFF") && bytes.startsWithAscii("WEBP", offset = 8)
    "image/bmp" -> bytes.startsWithAscii("BM")
    else -> false
}

private fun ByteArray.startsWithBytes(value: ByteArray): Boolean =
    size >= value.size && value.indices.all { index -> this[index] == value[index] }

private fun ByteArray.startsWithAscii(value: String, offset: Int = 0): Boolean {
    if (size < offset + value.length) return false
    return value.indices.all { index -> this[offset + index] == value[index].code.toByte() }
}
