package com.hiosdra.hreader.adapter.paywall

import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.port.out.PaywallBypass
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Bypass services supported by the app, mirroring the set offered by ShareToBypass.
 *
 * A method either appends the article URL to [baseUrl] as a path segment ([queryParam] is null)
 * or passes it as the [queryParam] query parameter.
 */
class PaywallBypassService : PaywallBypass {
    override fun getBypassUrl(originalUrl: String, method: PaywallBypassMethod): String {
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

    override fun isPaywallBypassUrl(url: String): Boolean = extractOriginalUrl(url) != null

    override fun extractOriginalUrl(bypassUrl: String): String? {
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
