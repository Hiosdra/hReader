package com.hiosdra.hreader.adapter.tts

internal object PolishTtsTextNormalizer {
    private val abbreviationReplacements = listOf(
        Regex("(?<![\\p{L}])m\\.in\\.(?![\\p{L}])", RegexOption.IGNORE_CASE) to "między innymi",
        Regex("(?<![\\p{L}])np\\.(?![\\p{L}])", RegexOption.IGNORE_CASE) to "na przykład",
        Regex("(?<![\\p{L}])tj\\.(?![\\p{L}])", RegexOption.IGNORE_CASE) to "to jest",
        Regex("(?<![\\p{L}])itd\\.(?![\\p{L}])", RegexOption.IGNORE_CASE) to "i tak dalej",
        Regex("(?<![\\p{L}])itp\\.(?![\\p{L}])", RegexOption.IGNORE_CASE) to "i tym podobne",
        Regex("(?<![\\p{L}])dr\\.(?![\\p{L}])", RegexOption.IGNORE_CASE) to "doktor",
        Regex("(?<![\\p{L}])prof\\.(?![\\p{L}])", RegexOption.IGNORE_CASE) to "profesor"
    )
    private val isoDatePattern = Regex(
        "(?<![\\p{L}\\d])(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})(?![\\p{L}\\d])"
    )
    private val europeanDatePattern = Regex(
        "(?<![\\p{L}\\d])(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})(?![\\p{L}\\d])"
    )
    private val percentagePattern = Regex("(\\d+(?:[,.]\\d+)?)\\s*%")
    private val timePattern = Regex("(?<![\\p{L}\\d])(\\d{1,2}):(\\d{2})(?![\\p{L}\\d])")
    private val decimalPattern = Regex("(?<![\\p{L}\\d])(\\d+)[,.](\\d+)(?![\\p{L}\\d])")
    private val rangePattern = Regex("(?<![\\p{L}\\d])(\\d+)\\s*[-–—]\\s*(\\d+)(?![\\p{L}\\d])")
    private val integerPattern = Regex("(?<![\\p{L}\\d])\\d+(?![\\p{L}\\d])")
    private val protectedPattern = Regex(
        "(?i)(?:https?://|www\\.)\\S+|[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}|" +
            "(?<![\\p{L}\\d])(?:[a-z]+)?\\d+(?:\\.\\d+){2,}(?![\\p{L}\\d])"
    )

    fun normalize(text: String): String {
        val protectedValues = mutableListOf<String>()
        val masked = protectedPattern.replace(text) {
            val index = protectedValues.size
            protectedValues += it.value
            protectedPlaceholder(index)
        }
        var normalized = masked
        abbreviationReplacements.forEach { (pattern, replacement) ->
            normalized = normalized.replace(pattern) { match ->
                replacementFor(match.value, replacement)
            }
        }
        normalized = normalized.replace(percentagePattern) { match ->
            "${spokenNumber(match.groupValues[1])} procent"
        }
        normalized = normalized.replace(isoDatePattern) { match ->
            dateWords(
                year = match.groupValues[1],
                month = match.groupValues[2],
                day = match.groupValues[3]
            )
        }
        normalized = normalized.replace(europeanDatePattern) { match ->
            dateWords(
                year = match.groupValues[3],
                month = match.groupValues[2],
                day = match.groupValues[1]
            )
        }
        normalized = normalized.replace(timePattern) { match ->
            "${spokenNumber(match.groupValues[1])} ${spokenNumber(match.groupValues[2])}"
        }
        normalized = normalized.replace(decimalPattern) { match ->
            "${spokenNumber(match.groupValues[1])} przecinek ${spokenNumber(match.groupValues[2])}"
        }
        normalized = normalized.replace(rangePattern) { match ->
            "${spokenNumber(match.groupValues[1])} do ${spokenNumber(match.groupValues[2])}"
        }
        normalized = normalized.replace(integerPattern) { match -> spokenNumber(match.value) }
        protectedValues.forEachIndexed { index, value ->
            normalized = normalized.replace(protectedPlaceholder(index), value)
        }
        return normalized
    }

    private fun protectedPlaceholder(index: Int): String {
        var remaining = index
        return buildString {
            append('\uE000')
            do {
                append(('A'.code + remaining % 26).toChar())
                remaining = remaining / 26 - 1
            } while (remaining >= 0)
        }
    }

    private fun replacementFor(matched: String, replacement: String): String =
        if (matched.firstOrNull()?.isUpperCase() == true) {
            replacement.replaceRange(0, 1, replacement[0].uppercase())
        } else {
            replacement
        }

    private fun dateWords(year: String, month: String, day: String): String {
        val monthNumber = month.toIntOrNull()
        val monthName = monthNumber
            ?.takeIf { it in 1..MONTHS.size }
            ?.let { MONTHS[it - 1] }
            ?: spokenNumber(month)
        return "${spokenNumber(day)} $monthName ${spokenNumber(year)}"
    }

    private fun spokenNumber(value: String): String {
        val decimalSeparator = value.indexOfFirst { it == ',' || it == '.' }
        if (decimalSeparator >= 0) {
            return "${spokenNumber(value.substring(0, decimalSeparator))} przecinek " +
                spokenNumber(value.substring(decimalSeparator + 1))
        }
        val number = value.toLongOrNull()
        return if (number != null && number <= MAX_SUPPORTED_NUMBER) {
            numberToWords(number)
        } else {
            value.map { DIGIT_WORDS[it.digitToInt()] }.joinToString(" ")
        }
    }

    private fun numberToWords(number: Long): String {
        if (number == 0L) return "zero"
        var remaining = number
        val result = mutableListOf<String>()
        SCALES.forEach { (scale, forms) ->
            val group = (remaining / scale).toInt()
            if (group > 0) {
                if (group != 1 || scale != 1_000L) result += belowThousand(group)
                result += scaleWord(group, forms)
                remaining %= scale
            }
        }
        if (remaining > 0) result += belowThousand(remaining.toInt())
        return result.joinToString(" ")
    }

    private fun belowThousand(number: Int): String {
        val words = mutableListOf<String>()
        val hundreds = number / 100
        val remainder = number % 100
        if (hundreds > 0) words += HUNDREDS[hundreds]
        when {
            remainder in 10..19 -> words += TEENS[remainder - 10]
            remainder >= 20 -> {
                words += TENS[remainder / 10]
                if (remainder % 10 > 0) words += UNITS[remainder % 10]
            }
            remainder > 0 -> words += UNITS[remainder]
        }
        return words.joinToString(" ")
    }

    private fun scaleWord(group: Int, forms: List<String>): String {
        if (group == 1) return forms.first()
        val lastTwo = group % 100
        return if (group % 10 in 2..4 && lastTwo !in 12..14) forms[1] else forms[2]
    }

    private val SCALES = listOf(
        1_000_000_000L to listOf("miliard", "miliardy", "miliardów"),
        1_000_000L to listOf("milion", "miliony", "milionów"),
        1_000L to listOf("tysiąc", "tysiące", "tysięcy")
    )

    private val UNITS = listOf(
        "zero", "jeden", "dwa", "trzy", "cztery", "pięć", "sześć", "siedem", "osiem", "dziewięć"
    )
    private val TEENS = listOf(
        "dziesięć", "jedenaście", "dwanaście", "trzynaście", "czternaście",
        "piętnaście", "szesnaście", "siedemnaście", "osiemnaście", "dziewiętnaście"
    )
    private val TENS = listOf(
        "", "", "dwadzieścia", "trzydzieści", "czterdzieści", "pięćdziesiąt",
        "sześćdziesiąt", "siedemdziesiąt", "osiemdziesiąt", "dziewięćdziesiąt"
    )
    private val HUNDREDS = listOf(
        "", "sto", "dwieście", "trzysta", "czterysta", "pięćset",
        "sześćset", "siedemset", "osiemset", "dziewięćset"
    )
    private val MONTHS = listOf(
        "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
        "lipca", "sierpnia", "września", "października", "listopada", "grudnia"
    )
    private val DIGIT_WORDS = UNITS
    private const val MAX_SUPPORTED_NUMBER = 999_999_999_999L
}
