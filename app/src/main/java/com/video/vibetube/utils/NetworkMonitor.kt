package com.video.vibetube.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.video.vibetube.BuildConfig

class NetworkMonitor(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isMonitoring = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun isConnected(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Exception) {
            if (BuildConfig.IS_DEBUG) {
                Log.e("NetworkMonitor", "Error checking connectivity", e)
            }
            false
        }
    }

    fun isWiFiConnected(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            false
        }
    }

    fun isMobileDataConnected(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (e: Exception) {
            false
        }
    }

    fun getConnectionType(): ConnectionType {
        return try {
            val network = connectivityManager.activeNetwork ?: return ConnectionType.NONE
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return ConnectionType.NONE

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.MOBILE
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
                else -> ConnectionType.OTHER
            }
        } catch (e: Exception) {
            ConnectionType.NONE
        }
    }

    fun startMonitoring() {
        if (isMonitoring) return

        try {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    if (BuildConfig.IS_DEBUG) {
                        Log.d("NetworkMonitor", "Network available: ${getConnectionType()}")
                    }
                }

                override fun onLost(network: android.net.Network) {
                    if (BuildConfig.IS_DEBUG) {
                        Log.d("NetworkMonitor", "Network lost")
                    }
                }

                override fun onCapabilitiesChanged(
                    network: android.net.Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    if (BuildConfig.IS_DEBUG) {
                        Log.d("NetworkMonitor", "Network capabilities changed: ${getConnectionType()}")
                    }
                }
            }

            networkCallback?.let { callback ->
                connectivityManager.registerDefaultNetworkCallback(callback)
                isMonitoring = true
            }
        } catch (e: Exception) {
            if (BuildConfig.IS_DEBUG) {
                Log.e("NetworkMonitor", "Failed to start network monitoring", e)
            }
        }
    }

    fun stopMonitoring() {
        if (!isMonitoring) return

        try {
            networkCallback?.let { callback ->
                connectivityManager.unregisterNetworkCallback(callback)
            }
            networkCallback = null
            isMonitoring = false

            if (BuildConfig.IS_DEBUG) {
                Log.d("NetworkMonitor", "Network monitoring stopped")
            }
        } catch (e: Exception) {
            if (BuildConfig.IS_DEBUG) {
                Log.e("NetworkMonitor", "Failed to stop network monitoring", e)
            }
        }
    }

    enum class ConnectionType {
        NONE, WIFI, MOBILE, ETHERNET, OTHER
    }
}