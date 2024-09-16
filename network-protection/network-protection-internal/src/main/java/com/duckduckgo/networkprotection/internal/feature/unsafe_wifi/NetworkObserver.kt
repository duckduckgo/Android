package com.duckduckgo.networkprotection.internal.feature.unsafe_wifi

import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.NetworkObserver.Listener
import logcat.asLog
import logcat.logcat

private const val TAG = "NetworkObserver"

private fun Context.isPermissionGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PERMISSION_GRANTED
}

/** Create a new [NetworkObserver]. */
internal fun NetworkObserver(
    context: Context,
    listener: Listener,
): NetworkObserver {
    val connectivityManager: ConnectivityManager? = context.getSystemService()
    if (connectivityManager == null || !context.isPermissionGranted(ACCESS_NETWORK_STATE)) {
        logcat(TAG) { "Unable to register network observer." }
        return EmptyNetworkObserver()
    }

    return try {
        RealNetworkObserver(connectivityManager, listener)
    } catch (e: Exception) {
        logcat(TAG) { e.asLog() }
        EmptyNetworkObserver()
    }
}

/**
 * Observes the device's network state and calls [Listener] if any state changes occur.
 *
 * This class provides a raw stream of updates from the network APIs. The [Listener] can be
 * called multiple times for the same network state.
 */
internal interface NetworkObserver {

    /** Synchronously checks if the device is online. */
    val isOnline: Boolean

    /** Stop observing network changes. */
    fun shutdown()

    /** Calls [onConnectivityChange] when a connectivity change event occurs. */
    fun interface Listener {

        @MainThread
        fun onConnectivityChange(isOnline: Boolean)
    }
}

internal class EmptyNetworkObserver : NetworkObserver {

    override val isOnline get() = true

    override fun shutdown() {}
}

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION") // TODO: Remove uses of 'allNetworks'.
private class RealNetworkObserver(
    private val connectivityManager: ConnectivityManager,
    private val listener: Listener,
) : NetworkObserver {

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onConnectivityChange(network, true)
        override fun onLost(network: Network) = onConnectivityChange(network, false)
    }

    override val isOnline: Boolean
        get() = connectivityManager.allNetworks.any { it.isOnline() }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    override fun shutdown() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private fun onConnectivityChange(network: Network, isOnline: Boolean) {
        val isAnyOnline = connectivityManager.allNetworks.any {
            if (it == network) {
                // Don't trust the network capabilities for the network that just changed.
                isOnline
            } else {
                it.isOnline()
            }
        }
        listener.onConnectivityChange(isAnyOnline)
    }

    private fun Network.isOnline(): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(this)
        return capabilities != null && capabilities.hasCapability(NET_CAPABILITY_INTERNET)
    }
}
