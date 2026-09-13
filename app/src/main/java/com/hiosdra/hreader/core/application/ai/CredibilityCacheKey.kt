package com.hiosdra.hreader.core.application.ai

import com.hiosdra.hreader.core.domain.model.CredibilitySource
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate

const val CREDIBILITY_PROMPT_VERSION = "credibility-v1"

private const val FIELD_SEPARATOR = "\u001f"

fun credibilityInputFingerprint(
    source: CredibilitySource,
    currentDate: LocalDate = LocalDate.now()
): String {
    val values = listOf(
        CREDIBILITY_PROMPT_VERSION,
        currentDate.toString(),
        source.title,
        source.content,
        source.author.orEmpty(),
        source.feedTitle.orEmpty(),
        source.url,
        source.publishedAt?.toString().orEmpty()
    )
    val canonical = values.joinToString(FIELD_SEPARATOR) { value -> "${value.length}:$value" }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
