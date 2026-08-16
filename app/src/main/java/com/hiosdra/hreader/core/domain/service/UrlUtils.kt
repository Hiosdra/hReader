package com.hiosdra.hreader.core.domain.service

import java.net.URI

private val trackingParamsPrefixes = listOf(
    "utm_", "mc_", "pk_"
)

/**
 * `ref` is deliberately absent: plenty of sites route on it — a documentation anchor, a store
 * listing, a paginated archive — and stripping it turned a working link into a different page.
 * `ref_src` is unambiguous and stays.
 */
private val trackingParamsExact = setOf(
    "gclid", "fbclid", "yclid", "msclkid", "ref_src", "aff", "aff_id", "campid", "adid", "adgroupid"
)

fun displayUrl(raw: String): String = raw.trim()
    .substringAfter("://")
    .removePrefix("www.")
    .trimEnd('/')
    .ifBlank { raw.trim() }

fun cleanUrl(raw: String): String = runCatching {
    val uri = URI(raw)
    val query = uri.rawQuery ?: return raw
    val kept = query.split('&')
        .filter { it.isNotBlank() }
        .filter { param ->
            val key = param.substringBefore('=')
            trackingParamsPrefixes.none { key.startsWith(it) } && key !in trackingParamsExact
        }
        .joinToString("&")
    val newQuery = if (kept.isBlank()) null else kept
    URI(
        uri.scheme,
        uri.authority,
        uri.path,
        newQuery,
        uri.fragment
    ).toString()
}.getOrElse { raw }
