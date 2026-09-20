package com.hiosdra.hreader.adapter.tts

import java.util.Locale

internal object EnglishTechnicalTtsTextNormalizer {
    private val dataRate = Regex(
        """(?i)\b(\d+(?:\.\d+)?)\s*(EFLOP|PFLOP|TFLOP|GFLOP|TB|GB)/s\b"""
    )
    private val binaryBytes = Regex("""(?i)\b(\d+(?:\.\d+)?)\s*(KiB|MiB|GiB|TiB)\b""")
    private val multiplier = Regex("""\b(\d+(?:\.\d+)?)\s*x\b""", RegexOption.IGNORE_CASE)
    private val tokenReplacements = listOf(
        Regex("""(?i)\bGPT-OSS\b""") to "G P T O S S",
        Regex("""(?i)\bMI355X\b""") to "M I three five five X",
        Regex("""(?i)\bGB200\b""") to "G B two hundred",
        Regex("""(?i)\bGB300\b""") to "G B three hundred",
        Regex("""(?i)\bHBM4\b""") to "H B M four",
        Regex("""(?i)\bMXFP4\b""") to "M X F P four",
        Regex("""(?i)\bASIC\b""") to "A S I C",
        Regex("""(?i)\bBF16\b""") to "B F sixteen",
        Regex("""(?i)\bFP16\b""") to "F P sixteen",
        Regex("""(?i)\bFP8\b""") to "F P eight"
    )

    fun normalize(text: String): String {
        var normalized = dataRate.replace(text) { match ->
            val number = match.groupValues[1]
            val unit = when (match.groupValues[2].lowercase(Locale.ROOT)) {
                "eflop" -> "exaflops"
                "pflop" -> "petaflops"
                "tflop" -> "teraflops"
                "gflop" -> "gigaflops"
                "tb" -> "terabytes"
                else -> "gigabytes"
            }
            "$number $unit per second"
        }
        normalized = binaryBytes.replace(normalized) { match ->
            val unit = when (match.groupValues[2].lowercase(Locale.ROOT)) {
                "kib" -> "kibibytes"
                "mib" -> "mebibytes"
                "gib" -> "gibibytes"
                else -> "tebibytes"
            }
            "${match.groupValues[1]} $unit"
        }
        normalized = multiplier.replace(normalized, "$1 times")
        tokenReplacements.forEach { (pattern, replacement) ->
            normalized = pattern.replace(normalized, replacement)
        }
        return normalized
    }
}
