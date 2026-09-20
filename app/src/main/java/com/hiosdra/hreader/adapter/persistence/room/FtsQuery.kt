package com.hiosdra.hreader.adapter.persistence.room

import java.util.Locale

private val NON_SEARCHABLE = Regex("[^\\p{L}\\p{N}]+")

fun buildFtsMatchQuery(rawQuery: String): String? {
    val tokens = rawQuery.split(NON_SEARCHABLE).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null
    return tokens.joinToString(" ") { "$it*" }
}

fun buildLikePattern(rawQuery: String): String = "%${rawQuery.trim().lowercase(Locale.ROOT)}%"
