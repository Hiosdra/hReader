package com.hiosdra.hreader.data.local

private val NON_SEARCHABLE = Regex("[^\\p{L}\\p{N}]+")

/**
 * Turns what the reader typed into an FTS4 MATCH expression. Passing the raw text through would
 * hand SQLite its own query language — a stray quote or a leading `-` is a syntax error that takes
 * the whole list down, and `AND` or `OR` would be read as operators rather than as words.
 *
 * Every token gets a trailing `*` so results narrow while the query is still being typed.
 * Returns null when nothing searchable is left, which the caller reads as "no search".
 */
fun buildFtsMatchQuery(rawQuery: String): String? {
    val tokens = rawQuery.split(NON_SEARCHABLE).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null
    return tokens.joinToString(" ") { "$it*" }
}

/**
 * The same text as a LIKE pattern, for the columns that are not in the index. `%` and `_` are left
 * as they are: without an ESCAPE clause they widen the match rather than break it, and a feed name
 * is not somewhere either character carries meaning worth defending.
 */
fun buildLikePattern(rawQuery: String): String = "%${rawQuery.trim().lowercase()}%"
