package com.hiosdra.hreader.data.paywall

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Bypass services supported by the app, mirroring the set offered by ShareToBypass.
 *
 * A method either appends the article URL to [baseUrl] as a path segment ([queryParam] is null)
 * or passes it as the [queryParam] query parameter.
 */
enum class PaywallBypassMethod(
    val displayName: String,
    val host: String,
    private val path: String,
    val queryParam: String? = null
) {
    SMRY_AI("Smry.ai", "smry.ai", ""),
    REMOVE_PAYWALL("RemovePaywall.com", "www.removepaywall.com", "/search", "url"),
    REMOVE_PAYWALLS("RemovePaywalls.com", "removepaywalls.com", ""),
    PAYWALL_BUSTER("PaywallBuster", "paywallbuster.com", "/articles", "article"),
    ARCHIVE_PH("Archive.ph", "archive.ph", "/newest"),
    WAYBACK_MACHINE("Wayback Machine", "web.archive.org", "/web/2"),
    ARCHIVE_BUTTONS("Archive Buttons", "www.archivebuttons.com", "/articles", "article"),
    BYPASS_PAYWALL_READER("Bypass Paywall Reader", "www.bypasspaywallreader.com", "/", "url");

    val baseUrl: String get() = "https://$host$path"
}

class PaywallBypassService {
    fun getBypassUrl(originalUrl: String, method: PaywallBypassMethod): String {
        val target = originalUrl.trim()
        if (target.isEmpty()) {
            throw IllegalArgumentException("Original URL cannot be blank")
        }
        val param = method.queryParam
        return if (param == null) {
            "${method.baseUrl}/$target"
        } else {
            "${method.baseUrl}?$param=${encode(target)}"
        }
    }

    fun isPaywallBypassUrl(url: String): Boolean = extractOriginalUrl(url) != null

    fun extractOriginalUrl(bypassUrl: String): String? {
        return PaywallBypassMethod.entries.firstNotNullOfOrNull { method ->
            val param = method.queryParam
            val prefix = if (param == null) "${method.baseUrl}/" else "${method.baseUrl}?$param="
            if (!bypassUrl.startsWith(prefix)) return@firstNotNullOfOrNull null

            val value = bypassUrl.removePrefix(prefix).let {
                if (param == null) it else it.substringBefore('&')
            }
            if (value.isEmpty()) null else if (param == null) value else decode(value)
        }
    }

    private fun encode(url: String): String = URLEncoder.encode(url, StandardCharsets.UTF_8.name())

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
