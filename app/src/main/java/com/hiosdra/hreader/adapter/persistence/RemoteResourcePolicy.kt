package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.core.application.port.out.AppPreferences
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy as RemoteResourcePolicyPort
import okhttp3.Dns
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.Locale

class RemoteResourcePolicyAdapter(
    private val allowedHosts: () -> Set<String>,
    private val resolveHost: (String) -> List<InetAddress> = { host ->
        InetAddress.getAllByName(host).toList()
    }
) : RemoteResourcePolicyPort {
    constructor(preferences: AppPreferences) : this(
        allowedHosts = {
            runCatching {
                val backendType = preferences.getBackendType()
                normalizedConfiguredHost(preferences.getServerUrl(backendType))
                    ?.let(::setOf)
                    ?: emptySet()
            }.getOrDefault(emptySet())
        }
    )

    override fun allows(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.ROOT) !in HTTP_SCHEMES || uri.userInfo != null) return false
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

    private fun normalizeHost(host: String): String =
        host.trim().trim('[', ']').lowercase(Locale.ROOT)

    private fun InetAddress.isPublicAddress(): Boolean {
        if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) {
            return false
        }
        val bytes = address
        if (bytes.size == 4) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            val third = bytes[2].toInt() and 0xff
            return when {
                first == 0 || first == 10 || first == 127 || first >= 224 -> false
                first == 100 && second in 64..127 -> false
                first == 169 && second == 254 -> false
                first == 172 && second in 16..31 -> false
                first == 192 && second == 0 -> false
                first == 192 && second == 2 -> false
                first == 192 && second == 168 -> false
                first == 198 && second in 18..19 -> false
                first == 198 && second == 51 && third == 100 -> false
                first == 203 && second == 0 && third == 113 -> false
                else -> true
            }
        }
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

internal fun normalizedConfiguredHost(serverUrl: String): String? {
    val trimmed = serverUrl.trim()
    if (trimmed.isEmpty()) return null
    val absolute = if (
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }
    return runCatching { URI(absolute).host?.lowercase(Locale.ROOT) }.getOrNull()
}
