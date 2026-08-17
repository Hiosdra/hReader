package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.core.application.port.out.AppPreferences
import okhttp3.Dns
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

class RemoteResourcePolicy(
    private val allowedHosts: () -> Set<String>,
    private val resolveHost: (String) -> List<InetAddress> = { host ->
        InetAddress.getAllByName(host).toList()
    }
) {
    constructor(preferences: AppPreferences) : this(
        allowedHosts = {
            runCatching {
                val backendType = preferences.getBackendType()
                URI(preferences.getServerUrl(backendType)).host
                    ?.lowercase()
                    ?.let(::setOf)
                    ?: emptySet()
            }.getOrDefault(emptySet())
        }
    )

    fun allows(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in HTTP_SCHEMES || uri.userInfo != null) return false
        val host = uri.host?.let(::normalizeHost)?.takeIf { it.isNotBlank() } ?: return false
        val addresses = runCatching { resolveHost(host) }.getOrNull() ?: return false
        return isAllowedHost(host, addresses)
    }

    fun dns(): Dns = Dns { host ->
        val normalizedHost = normalizeHost(host)
        val addresses = runCatching { resolveHost(normalizedHost) }
            .getOrElse { throw UnknownHostException(normalizedHost) }
        if (!isAllowedHost(normalizedHost, addresses)) {
            throw UnknownHostException("Blocked remote resource host: $normalizedHost")
        }
        addresses
    }

    private fun isAllowedHost(host: String, addresses: List<InetAddress>): Boolean {
        if (addresses.isEmpty()) return false
        val configuredHosts = allowedHosts().map(::normalizeHost).toSet()
        if (host in configuredHosts) return true
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
            return false
        }
        return addresses.isNotEmpty() && addresses.all { it.isPublicAddress() }
    }

    private fun normalizeHost(host: String): String = host.trim().trim('[', ']').lowercase()

    private fun InetAddress.isPublicAddress(): Boolean {
        if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) {
            return false
        }
        val bytes = address
        if (bytes.size != 16) return true
        val mappedIpv4 = bytes.take(10).all { it == 0.toByte() } &&
            bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()
        if (mappedIpv4) {
            return InetAddress.getByAddress(bytes.copyOfRange(12, 16)).isPublicAddress()
        }
        return bytes[0].toInt() and 0xfe != 0xfc
    }

    private companion object {
        val HTTP_SCHEMES = setOf("http", "https")
    }
}
