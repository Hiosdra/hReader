package com.hiosdra.hreader.core.application.paywall

enum class PaywallBypassMethod(
    val host: String,
    private val path: String,
    val queryParam: String? = null
) {
    SMRY_AI("smry.ai", ""),
    REMOVE_PAYWALL("www.removepaywall.com", "/search", "url"),
    REMOVE_PAYWALLS("removepaywalls.com", ""),
    PAYWALL_BUSTER("paywallbuster.com", "/articles", "article"),
    ARCHIVE_PH("archive.ph", "/newest"),
    WAYBACK_MACHINE("web.archive.org", "/web/2"),
    ARCHIVE_BUTTONS("www.archivebuttons.com", "/articles", "article"),
    BYPASS_PAYWALL_READER("www.bypasspaywallreader.com", "/", "url");

    val baseUrl: String get() = "https://$host$path"
}
