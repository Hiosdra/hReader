package com.hiosdra.hreader.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Online means a usable network, which is not the same as a validated one.
 *
 * A marina hotspot behind a captive portal offers a network that claims `NET_CAPABILITY_INTERNET`
 * with nothing behind it, so that one is excluded outright. Validation is not required though:
 * the backend this app talks to is self-hosted and frequently on the same LAN, where the platform's
 * check against a Google endpoint fails while the server is perfectly reachable. Demanding it
 * declared exactly that setup offline and refused to sync it.
 */
class NetworkMonitor(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val observedNetworks = mutableSetOf<Network>()
    private val _isOnline = MutableStateFlow(checkInitial())
    val isOnline: StateFlow<Boolean> = _isOnline

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(observedNetworks) { observedNetworks.add(network) }
            updateOnline()
        }
        override fun onLost(network: Network) {
            synchronized(observedNetworks) { observedNetworks.remove(network) }
            updateOnline()
        }
        override fun onUnavailable() { updateOnline() }
        // Validation lands after the network becomes available, so without this a portal that
        // never lets a request through would stay marked online.
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            synchronized(observedNetworks) { observedNetworks.add(network) }
            updateOnline()
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try { connectivityManager.registerNetworkCallback(request, callback) } catch (_: Exception) {}
    }

    private fun checkInitial(): Boolean = try {
        connectivityManager.activeNetwork?.let { network ->
            synchronized(observedNetworks) { observedNetworks.add(network) }
        }
        checkAnyOnline()
    } catch (_: Exception) {
        false
    }

    private fun checkAnyOnline(): Boolean {
        val networks = synchronized(observedNetworks) { observedNetworks.toList() }
        return networks.any { network ->
            connectivityManager.getNetworkCapabilities(network)?.reachesInternet() == true
        }
    }

    private fun updateOnline() {
        _isOnline.value = checkAnyOnline()
    }

    // Only the two things worth asserting. Every further capability required here is another way to
    // report a working connection as offline, which is the expensive direction to be wrong in:
    // it hides the reader's articles and stops the sync, while being wrong the other way costs a
    // failed request and a message saying so.
    private fun NetworkCapabilities.reachesInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
}
