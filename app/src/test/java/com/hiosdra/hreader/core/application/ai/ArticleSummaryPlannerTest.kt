package com.hiosdra.hreader.core.application.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleSummaryPlannerTest {
    @Test
    fun plansShortContentAsOnePart() {
        val plan = ArticleSummaryPlanner.plan("One short article.", contextLength = 8_192)

        assertEquals(listOf("One short article."), plan.chunks)
        assertTrue(plan.maxOutputTokens in 96..500)
    }

    @Test
    fun keepsLongUnbrokenTokensWithinChunkLimit() {
        val plan = ArticleSummaryPlanner.plan("x".repeat(10_000), contextLength = 1_024)

        assertTrue(plan.chunks.size > 1)
        assertTrue(plan.chunks.all { it.length <= 1_229 })
    }
}
