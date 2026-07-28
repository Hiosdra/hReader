package com.hiosdra.hreader.data.ai

import com.hiosdra.hreader.data.model.CredibilityConfidence
import com.hiosdra.hreader.data.model.CredibilityFactor
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

private const val MAX_JSON_CANDIDATES = 4
private const val MAX_NESTING_DEPTH = 2

class CredibilityParseException(message: String) : Exception(message)

data class ParsedCredibility(
    val score: Float,
    val confidence: CredibilityConfidence,
    val summary: String,
    val reasons: List<String>,
    val redFlags: List<String>,
    val factors: List<CredibilityFactor>
)

class CredibilityResponseParser(moshi: Moshi) {
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    ).lenient()

    fun parse(raw: String): ParsedCredibility {
        val candidates = jsonObjectCandidates(raw)
        if (candidates.isEmpty()) {
            throw CredibilityParseException("The model did not return a JSON verdict.")
        }

        val verdict = candidates.firstNotNullOfOrNull { candidate ->
            val root = runCatching { mapAdapter.fromJson(candidate) }.getOrNull()
            root?.let { verdictFrom(it, depth = 0) }
        }

        return verdict
            ?: throw CredibilityParseException("The model answer contains no usable credibility score.")
    }

    private fun verdictFrom(root: Map<*, *>, depth: Int): ParsedCredibility? {
        val fields = root.entries.associate { (key, value) -> key.toString().lowercase() to value }
        val score = coerceScore(fields["score"])
        if (score == null) {
            if (depth >= MAX_NESTING_DEPTH) return null
            return root.values.filterIsInstance<Map<*, *>>()
                .firstNotNullOfOrNull { verdictFrom(it, depth + 1) }
        }

        return ParsedCredibility(
            score = score,
            confidence = coerceConfidence(fields["confidence"]),
            summary = coerceString(fields["summary"]).orEmpty(),
            reasons = coerceStringList(fields["reasons"]),
            redFlags = coerceStringList(fields["red_flags"] ?: fields["redflags"]),
            factors = coerceFactors(fields["factors"])
        )
    }

    private fun jsonObjectCandidates(raw: String): List<String> {
        val candidates = mutableListOf<String>()
        var searchFrom = 0
        while (candidates.size < MAX_JSON_CANDIDATES) {
            val start = raw.indexOf('{', searchFrom)
            if (start < 0) break
            val end = matchingBraceIndex(raw, start)
            if (end < 0) break
            candidates.add(raw.substring(start, end + 1))
            searchFrom = end + 1
        }
        return candidates
    }

    private fun matchingBraceIndex(raw: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val character = raw[i]
            when {
                escaped -> escaped = false
                character == '\\' && inString -> escaped = true
                character == '"' -> inString = !inString
                inString -> Unit
                character == '{' -> depth++
                character == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun coerceScore(value: Any?): Float? {
        val number = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().removeSuffix("%").trim().toDoubleOrNull()
            else -> null
        } ?: return null
        if (number.isNaN()) return null
        val onUnitScale = if (number > 1.0) number / 100.0 else number
        return onUnitScale.coerceIn(0.0, 1.0).toFloat()
    }

    private fun coerceConfidence(value: Any?): CredibilityConfidence =
        when (coerceString(value)?.trim()?.lowercase()) {
            "high" -> CredibilityConfidence.HIGH
            "low" -> CredibilityConfidence.LOW
            else -> CredibilityConfidence.MEDIUM
        }

    private fun coerceString(value: Any?): String? = when (value) {
        null -> null
        is String -> value.trim().takeIf { it.isNotEmpty() }
        is Number, is Boolean -> value.toString()
        else -> null
    }

    private fun coerceStringList(value: Any?): List<String> = when (value) {
        null -> emptyList()
        is List<*> -> value.mapNotNull { coerceString(it) }
        is String -> listOfNotNull(coerceString(value))
        else -> emptyList()
    }

    private fun coerceFactors(value: Any?): List<CredibilityFactor> = when (value) {
        is Map<*, *> -> value.entries.mapNotNull { (key, factorScore) ->
            val name = coerceString(key) ?: return@mapNotNull null
            val score = coerceScore(factorScore) ?: return@mapNotNull null
            CredibilityFactor(name = name, score = score)
        }

        is List<*> -> value.mapNotNull { item ->
            val factor = item as? Map<*, *> ?: return@mapNotNull null
            val name = coerceString(factor["name"]) ?: return@mapNotNull null
            val score = coerceScore(factor["score"]) ?: return@mapNotNull null
            CredibilityFactor(name = name, score = score)
        }

        else -> emptyList()
    }
}
