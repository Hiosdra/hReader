package com.hiosdra.hreader.data.paywall

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
        val expected = "https://www.removepaywall.com/search?url=https://www.wsj.com/articles/test"
        
        val result = paywallBypassService.getBypassUrl(originalUrl, PaywallBypassMethod.REMOVE_PAYWALL)
        
        assertEquals(expected, result)
    }

    @Test
    fun testIsPaywallBypassUrl() {
        assertTrue(paywallBypassService.isPaywallBypassUrl("https://smry.ai/test"))
        assertTrue(paywallBypassService.isPaywallBypassUrl("https://www.removepaywall.com/search?url=test"))
        assertFalse(paywallBypassService.isPaywallBypassUrl("https://www.nytimes.com/article"))
    }

    @Test
    fun testExtractOriginalUrl() {
        val bypassUrl1 = "https://smry.ai/https://www.nytimes.com/article"
        val bypassUrl2 = "https://www.removepaywall.com/search?url=https://www.wsj.com/test"
        val normalUrl = "https://www.nytimes.com/article"
        
        assertEquals("https://www.nytimes.com/article", paywallBypassService.extractOriginalUrl(bypassUrl1))
        assertEquals("https://www.wsj.com/test", paywallBypassService.extractOriginalUrl(bypassUrl2))
        assertNull(paywallBypassService.extractOriginalUrl(normalUrl))
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