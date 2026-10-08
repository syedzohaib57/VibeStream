package com.example.streamingappzb.domain.repository

import com.example.streamingappzb.domain.model.NetworkState
import kotlinx.coroutines.flow.StateFlow

/**
 * The live connection.
 *
 * The app-bar DataPill must reflect a change within one second (acceptance item 6), so
 * this is a ConnectivityManager callback rather than anything polled.
 */
interface NetworkRepository {
    val state: StateFlow<NetworkState>

    /** Read synchronously for the very first frame, before a callback has fired. */
    fun current(): NetworkState
}
