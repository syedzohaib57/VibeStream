package com.example.streamingappzb.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.streamingappzb.R
import com.example.streamingappzb.data.media.JellyfinConfig
import com.example.streamingappzb.data.media.JellyfinSourceRepository
import com.example.streamingappzb.data.prefs.AppPrefs
import com.example.streamingappzb.data.remote.jellyfin.JellyfinApi
import com.example.streamingappzb.data.remote.jellyfin.JfAuthRequestDto
import com.example.streamingappzb.databinding.SheetJellyfinConnectBinding
import com.example.streamingappzb.ui.base.BaseBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * Signs in to a Jellyfin server and stores the result.
 *
 * The password's whole lifetime is this sheet: it is read from the field, exchanged for
 * Jellyfin's long-lived access token, and never written anywhere. What persists is the
 * token, the normalised address, and the username for the settings row.
 */
class JellyfinConnectSheet : BaseBottomSheet<SheetJellyfinConnectBinding>() {

    private val prefs: AppPrefs by inject()
    private val api: JellyfinApi by inject()

    /** So the library sheet behind this one can re-render its server row on success. */
    var onConnected: (() -> Unit)? = null

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): SheetJellyfinConnectBinding = SheetJellyfinConnectBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        // Pre-fill the last address: re-connecting after a password change is the common
        // repeat visit, and the address is the field least worth retyping on a phone.
        prefs.jellyfinUrl?.let(binding.serverUrl::setText)
        prefs.jellyfinUserName?.let(binding.userName::setText)

        binding.btnConnect.setOnClickListener { connect() }
    }

    private fun connect() {
        val url = binding.serverUrl.text.toString().trim()
        val user = binding.userName.text.toString().trim()
        val password = binding.password.text.toString()

        if (url.isEmpty() || user.isEmpty() || password.isEmpty()) {
            showError(R.string.jellyfin_error_fields)
            return
        }

        binding.btnConnect.isEnabled = false
        binding.btnConnect.setText(R.string.jellyfin_connecting)
        binding.connectError.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            val base = JellyfinConfig.normalise(url)

            val response = withContext(Dispatchers.IO) {
                runCatching {
                    api.authenticate(
                        url = "$base/Users/AuthenticateByName",
                        authHeader = JellyfinSourceRepository.authHeader(token = null),
                        body = JfAuthRequestDto(username = user, password = password),
                    )
                }.getOrNull()
            }

            val token = response?.accessToken
            if (token.isNullOrBlank()) {
                binding.btnConnect.isEnabled = true
                binding.btnConnect.setText(R.string.jellyfin_connect)
                showError(R.string.jellyfin_error_connect)
                return@launch
            }

            prefs.jellyfinUrl = base
            prefs.jellyfinToken = token
            prefs.jellyfinUserName = response.user?.name ?: user

            showToast(getString(R.string.jellyfin_connected_toast, JellyfinConfig(base, token).displayHost))
            onConnected?.invoke()
            dismiss()
        }
    }

    private fun showError(messageRes: Int) {
        binding.connectError.isVisible = true
        binding.connectError.setText(messageRes)
    }

    companion object {
        const val TAG = "jellyfin-connect"
    }
}
