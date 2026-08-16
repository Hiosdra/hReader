package com.hiosdra.hreader.adapter.paywall

import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test

class PaywallBypassServiceTest {

    private val paywallBypassService = PaywallBypassService()

    @Test
    fun testGetBypassUrl_SmryAi() {
        val originalUrl = "https://www.nytimes.com/2023/article"
        val expected = "https://smry.ai/https://www.nytimes.com/2023/article"

        val result = paywallBypassService.getBypassUrl(originalUrl, PaywallBypassMethod.SMRY_AI)

        assertEquals(expected, result)
    }

    @Test
    fun testGetBypassUrl_RemovePaywall() {
        val originalUrl = "https://www.wsj.com/articles/test"
        val expected = "https://www.removepaywall.com/search?url=" +
            "https%3A%2F%2Fwww.wsj.com%2Farticles%2Ftest"

        val result = paywallBypassService.getBypassUrl(originalUrl, PaywallBypassMethod.REMOVE_PAYWALL)

        assertEquals(expected, result)
    }

    @Test
    fun testGetBypassUrl_RemovePaywalls() {
        val originalUrl = "https://www.wsj.com/articles/test"
        val expected = "https://removepaywalls.com/https://www.wsj.com/articles/test"

        val result = paywallBypassService.getBypassUrl(originalUrl, PaywallBypassMethod.REMOVE_PAYWALLS)

        assertEquals(expected, result)
    }

    @Test
    fun testGetBypassUrl_PaywallBuster() {
        val originalUrl = "https://www.wsj.com/articles/test"
        val expected = "https://paywallbuster.com/articles?article=" +
            "https%3A%2F%2Fwww.wsj.com%2Farticles%2Ftest"

        val result = paywallBypassService.getBypassUrl(originalUrl, PaywallBypassMethod.PAYWALL_BUSTER)

        assertEquals(expected, result)
    }

    @Test
    fun testGetBypassUrl_ArchivePh() {
        val originalUrl = "https://www.wsj.com/articles/test"
        val expected = "https://archive.ph/newest/https://www.wsj.com/articles/test"

        val result = paywallBypassService.getBypassUrl(originalUrl, PaywallBypassMethod.ARCHIVE_PH)

        assertEquals(expected, result)
    }

    @Test
    fun testGetBypassUrl_WaybackMachine() {
        val originalUrl = "https://www.wsj.com/articles/test"
        val expected = "https://web.archive.org/web/2/https://www.wsj.com/articles/test"

        val result = paywallBypassService.getBypassUrl(originalUrl, PaywallBypassMethod.WAYBACK_MACHINE)

        assertEquals(expected, result)
    }

    @Test
    fun testGetBypassUrl_ArchiveButtons() {
        val originalUrl = "https://www.wsj.com/articles/test"
        val expected = "https://www.archivebuttons.com/articles?article=" +
            "https%3A%2F%2Fwww.wsj.com%2Farticles%2Ftest"

        val result = paywallBypassService.getBypassUrl(originalUrl, PaywallBypassMethod.ARCHIVE_BUTTONS)

        assertEquals(expected, result)
    }

    @Test
    fun testGetBypassUrl_BypassPaywallReader() {
        val originalUrl = "https://www.wsj.com/articles/test"
        val expected = "https://www.bypasspaywallreader.com/?url=" +
            "https%3A%2F%2Fwww.wsj.com%2Farticles%2Ftest"

        val result = paywallBypassService.getBypassUrl(
            originalUrl,
            PaywallBypassMethod.BYPASS_PAYWALL_READER
        )

        assertEquals(expected, result)
    }

    @Test
    fun testIsPaywallBypassUrl() {
        assertTrue(paywallBypassService.isPaywallBypassUrl("https://smry.ai/test"))
        assertTrue(paywallBypassService.isPaywallBypassUrl("https://www.removepaywall.com/search?url=test"))
        assertTrue(paywallBypassService.isPaywallBypassUrl("https://removepaywalls.com/test"))
        assertTrue(paywallBypassService.isPaywallBypassUrl("https://paywallbuster.com/articles?article=test"))
        assertTrue(paywallBypassService.isPaywallBypassUrl("https://archive.ph/newest/test"))
        assertTrue(paywallBypassService.isPaywallBypassUrl("https://web.archive.org/web/2/test"))
        assertTrue(paywallBypassService.isPaywallBypassUrl("https://www.archivebuttons.com/articles?article=test"))
        assertTrue(paywallBypassService.isPaywallBypassUrl("https://www.bypasspaywallreader.com/?url=test"))
        assertFalse(paywallBypassService.isPaywallBypassUrl("https://www.nytimes.com/article"))
    }

    @Test
    fun testIsPaywallBypassUrl_rejectsLookalikeHostsAndEmptyPayloads() {
        assertFalse(paywallBypassService.isPaywallBypassUrl("https://smry.ai.example.com/article"))
        assertFalse(paywallBypassService.isPaywallBypassUrl("https://smry.ai/"))
        assertFalse(paywallBypassService.isPaywallBypassUrl("https://www.removepaywall.com/search?url="))
    }

    @Test
    fun testGetBypassUrl_trimsSurroundingWhitespace() {
        val expected = "https://smry.ai/https://www.nytimes.com/article"

        val result = paywallBypassService.getBypassUrl(
            "  https://www.nytimes.com/article\n",
            PaywallBypassMethod.SMRY_AI
        )

        assertEquals(expected, result)
    }

    @Test
    fun testExtractOriginalUrl_roundTripsEveryMethod() {
        val originalUrl = "https://www.nytimes.com/2023/article?id=1"

        PaywallBypassMethod.entries.forEach { method ->
            val bypassUrl = paywallBypassService.getBypassUrl(originalUrl, method)

            assertEquals(
                "round trip failed for $method",
                originalUrl,
                paywallBypassService.extractOriginalUrl(bypassUrl)
            )
        }
    }

    @Test
    fun testExtractOriginalUrl_returnsNullForPlainUrl() {
        assertNull(paywallBypassService.extractOriginalUrl("https://www.nytimes.com/article"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetBypassUrl_EmptyUrl() {
        paywallBypassService.getBypassUrl("", PaywallBypassMethod.SMRY_AI)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetBypassUrl_BlankUrl() {
        paywallBypassService.getBypassUrl("   ", PaywallBypassMethod.SMRY_AI)
    }
}
