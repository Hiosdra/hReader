package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod

interface PaywallBypass {
    fun getBypassUrl(originalUrl: String, method: PaywallBypassMethod): String
    fun isPaywallBypassUrl(url: String): Boolean
    fun extractOriginalUrl(bypassUrl: String): String?
}
