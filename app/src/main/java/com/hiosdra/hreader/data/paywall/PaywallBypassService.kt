package com.hiosdra.hreader.data.paywall

enum class PaywallBypassMethod(val displayName: String, val baseUrl: String) {
    SMRY_AI("Smry.ai", "https://smry.ai"),
    REMOVE_PAYWALL("RemovePaywall.com", "https://www.removepaywall.com/search")
}

class PaywallBypassService {
    fun getBypassUrl(originalUrl: String, method: PaywallBypassMethod): String {
        return when (method) {
            PaywallBypassMethod.SMRY_AI -> "${method.baseUrl}/${originalUrl}"
            PaywallBypassMethod.REMOVE_PAYWALL -> "${method.baseUrl}?url=${originalUrl}"
        }
    }
    
    fun isPaywallBypassUrl(url: String): Boolean {
        return PaywallBypassMethod.entries.any { method ->
            url.startsWith(method.baseUrl)
        }
    }
    
    fun extractOriginalUrl(bypassUrl: String): String? {
        return PaywallBypassMethod.entries.firstNotNullOfOrNull { method ->
            when (method) {
                PaywallBypassMethod.SMRY_AI -> {
                    if (bypassUrl.startsWith(method.baseUrl + "/")) {
                        bypassUrl.substringAfter(method.baseUrl + "/")
                    } else null
                }
                PaywallBypassMethod.REMOVE_PAYWALL -> {
                    if (bypassUrl.startsWith(method.baseUrl)) {
                        val urlParam = bypassUrl.substringAfter("?url=")
                        if (urlParam != bypassUrl) urlParam else null
                    } else null
                }
            }
        }
    }
}