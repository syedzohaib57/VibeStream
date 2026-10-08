package com.example.streamingappzb.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.domain.repository.NetworkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The live connection, from a ConnectivityManager callback.
 *
 * A callback rather than a poll because the app-bar DataPill has to reflect a change
 * within one second (acceptance item 6).
 *
 * "Metered" is decided by `NET_CAPABILITY_NOT_METERED`, not by transport type: a metered
 * Wi-Fi hotspot must behave like mobile data, since the whole point of the app is not
 * spending the viewer's data unexpectedly.
 */
class NetworkMonitor(context: Context) : NetworkRepository {

    private val connectivity = context.getSystemService<ConnectivityManager>()

    private val _state = MutableStateFlow(read())
    override val state: StateFlow<NetworkState> = _state.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()
        override fun onLost(network: Network) = publish()
        override fun onUnavailable() = publish()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = publish()
    }

    init {
        runCatching {
            connectivity?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
        }
    }

    override fun current(): NetworkState = _state.value

    /** Only for tests and a clean shutdown; the monitor lives as long as the process. */
    fun stop() {
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
    }

    private fun publish() {
        _state.value = read()
    }

    private fun read(): NetworkState {
        val cm = connectivity ?: return NetworkState.Offline
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            ?: return NetworkState.Offline
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkState.Offline
        }
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return if (unmetered) NetworkState.Wifi else NetworkState.Cellular
    }
}
