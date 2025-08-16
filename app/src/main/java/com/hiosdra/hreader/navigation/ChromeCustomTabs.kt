package com.hiosdra.hreader.navigation

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun openChromeCustomTab(context: Context, url: String) {
    val builder = CustomTabsIntent.Builder()
    val customTabsIntent = builder.build()
    customTabsIntent.launchUrl(context, url.toUri())
}

/**
 * Supported paywall bypass providers.
 */
enum class PaywallBypassProvider {
    SMRY_AI,
    REMOVE_PAYWALL
}

/**
 * Builds a URL that routes the given article URL through a selected paywall-bypass service.
 * Note: Service endpoints may change over time; adjust here if needed.
 */
fun buildPaywallBypassUrl(provider: PaywallBypassProvider, articleUrl: String): String {
    val encoded = URLEncoder.encode(articleUrl, StandardCharsets.UTF_8.name())
    return when (provider) {
        PaywallBypassProvider.SMRY_AI -> "https://www.smry.ai/go?url=$encoded"
        PaywallBypassProvider.REMOVE_PAYWALL -> "https://www.removepaywall.com/?url=$encoded"
    }
}

/**
 * Opens the given article URL via the selected paywall-bypass provider in a Chrome Custom Tab.
 */
fun openWithPaywallBypass(context: Context, provider: PaywallBypassProvider, articleUrl: String) {
    val bypassUrl = buildPaywallBypassUrl(provider, articleUrl)
    openChromeCustomTab(context, bypassUrl)
}
