package com.hiosdra.hreader.adapter.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.hiosdra.hreader.core.application.port.out.NetworkStatus

class NetworkMonitor(context: Context) : NetworkStatus {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val observedNetworks = mutableSetOf<Network>()
    private val _isOnline = MutableStateFlow(checkInitial())
    override val isOnline: StateFlow<Boolean> = _isOnline

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

    private fun NetworkCapabilities.reachesInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
}
