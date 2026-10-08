package com.example.streamingappzb.ui.base

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.example.streamingappzb.domain.repository.SettingsRepository

/**
 * POST_NOTIFICATIONS, asked the way the design's permission card specifies: **before the
 * first notification**, never at launch, and only once.
 *
 * Downloads work whether or not it is granted — the notification is the foreground
 * service's progress display, not a requirement — so a refusal must never block the
 * action the viewer actually asked for.
 */
object NotificationPermission {

    fun isRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun isGranted(context: Context): Boolean = !isRequired() ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Runs [action] straight away when the permission is already granted, already asked
     * once, or not applicable. Otherwise asks first — and runs [action] regardless of the
     * answer, because the download is what was requested.
     *
     * @return true when a prompt was shown, so the caller can defer [action] to the result
     *   callback instead of running it twice.
     */
    fun requestOnce(
        context: Context,
        settings: SettingsRepository,
        launcher: ActivityResultLauncher<String>,
    ): Boolean {
        if (isGranted(context) || settings.hasAskedNotifications()) return false
        settings.markAskedNotifications()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return true
    }
}
