package com.hiosdra.hreader.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NetworkMonitor(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isOnline = MutableStateFlow(checkInitial())
    val isOnline: StateFlow<Boolean> = _isOnline

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { _isOnline.value = true }
        override fun onLost(network: Network) { _isOnline.value = checkAnyOnline() }
        override fun onUnavailable() { _isOnline.value = checkAnyOnline() }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try { connectivityManager.registerNetworkCallback(request, callback) } catch (_: Exception) {}
    }

    private fun checkInitial(): Boolean = try { checkAnyOnline() } catch (_: Exception) { true }

    private fun checkAnyOnline(): Boolean {
        val networks = connectivityManager.allNetworks
        if (networks.isEmpty()) return false
        networks.forEach { n ->
            val caps = connectivityManager.getNetworkCapabilities(n)
            if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return true
        }
        return false
    }
}

