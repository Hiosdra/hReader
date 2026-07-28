package com.hiosdra.hreader.data.ai

import com.hiosdra.hreader.data.model.CredibilityConfidence
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredibilityResponseParserTest {
    private val parser = CredibilityResponseParser(
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    )

    @Test
    fun parsesPlainJsonVerdict() {
        val result = parser.parse(
            """
            {"score": 0.72, "confidence": "high", "summary": "Well sourced.",
             "reasons": ["Names three sources"], "red_flags": [],
             "factors": {"sourcing": 0.8, "tone": 0.6}}
            """.trimIndent()
        )

        assertEquals(0.72f, result.score, 0.0001f)
        assertEquals(CredibilityConfidence.HIGH, result.confidence)
        assertEquals("Well sourced.", result.summary)
        assertEquals(listOf("Names three sources"), result.reasons)
        assertTrue(result.redFlags.isEmpty())
        assertEquals(2, result.factors.size)
        assertEquals("sourcing", result.factors.first().name)
    }

    @Test
    fun parsesVerdictWrappedInProseAndCodeFence() {
        val result = parser.parse(
            """
            Sure! Here is my assessment:

            ```json
            {"score": 0.35, "summary": "Sensational.", "red_flags": ["Clickbait headline"]}
            ```

            Let me know if you need more detail.
            """.trimIndent()
        )

        assertEquals(0.35f, result.score, 0.0001f)
        assertEquals(listOf("Clickbait headline"), result.redFlags)
        assertEquals(CredibilityConfidence.MEDIUM, result.confidence)
    }

    @Test
    fun normalizesPercentScaleAndStringScores() {
        assertEquals(0.85f, parser.parse("""{"score": 85}""").score, 0.0001f)
        assertEquals(0.85f, parser.parse("""{"score": "85%"}""").score, 0.0001f)
        assertEquals(0.85f, parser.parse("""{"score": "0.85"}""").score, 0.0001f)
    }

    @Test
    fun clampsOutOfRangeScores() {
        assertEquals(1f, parser.parse("""{"score": 400}""").score, 0.0001f)
        assertEquals(0f, parser.parse("""{"score": -3}""").score, 0.0001f)
    }

    @Test
    fun keepsBracesInsideStrings() {
        val result = parser.parse("""{"score": 0.5, "summary": "Uses {curly} braces"}""")
        assertEquals("Uses {curly} braces", result.summary)
    }

    @Test
    fun acceptsFactorsGivenAsList() {
        val result = parser.parse(
            """{"score": 0.5, "factors": [{"name": "tone", "score": 0.4}]}"""
        )
        assertEquals(1, result.factors.size)
        assertEquals(0.4f, result.factors.first().score, 0.0001f)
    }

    @Test
    fun skipsLeadingJsonThatIsNotTheVerdict() {
        val result = parser.parse(
            """
            {"thinking": "let me weigh the sources"}
            {"score": 0.44, "summary": "Thin sourcing."}
            """.trimIndent()
        )

        assertEquals(0.44f, result.score, 0.0001f)
        assertEquals("Thin sourcing.", result.summary)
    }

    @Test
    fun findsVerdictNestedUnderAWrapperKey() {
        val result = parser.parse("""{"result": {"score": 0.9, "summary": "Solid."}}""")

        assertEquals(0.9f, result.score, 0.0001f)
        assertEquals("Solid.", result.summary)
    }

    @Test
    fun acceptsCapitalizedKeys() {
        val result = parser.parse("""{"Score": 0.5, "Summary": "Ok.", "Red_Flags": ["Vague"]}""")

        assertEquals(0.5f, result.score, 0.0001f)
        assertEquals("Ok.", result.summary)
        assertEquals(listOf("Vague"), result.redFlags)
    }

    @Test
    fun readsVerdictWrappedInAnArray() {
        val result = parser.parse("""[{"score": 0.25, "summary": "Weak."}]""")

        assertEquals(0.25f, result.score, 0.0001f)
    }

    @Test(expected = CredibilityParseException::class)
    fun rejectsAnswerWithoutJson() {
        parser.parse("I think this article is fairly trustworthy.")
    }

    @Test(expected = CredibilityParseException::class)
    fun rejectsJsonWithoutScore() {
        parser.parse("""{"summary": "no score here"}""")
    }

    @Test(expected = CredibilityParseException::class)
    fun rejectsUnparseableScore() {
        parser.parse("""{"score": "very high"}""")
    }
}
