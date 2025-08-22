package com.hiosdra.hreader.util

import java.net.URI

private val trackingParamsPrefixes = listOf(
    "utm_", "mc_", "pk_"
)

private val trackingParamsExact = setOf(
    "gclid", "fbclid", "yclid", "msclkid", "ref", "ref_src", "aff", "aff_id", "campid", "adid", "adgroupid"
)

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
