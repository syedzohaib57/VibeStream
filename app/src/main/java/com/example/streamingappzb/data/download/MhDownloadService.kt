package com.example.streamingappzb.data.download

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.workmanager.WorkManagerScheduler
import com.example.streamingappzb.R
import org.koin.android.ext.android.inject

/**
 * The foreground service that actually fetches episodes.
 *
 * Declared `foregroundServiceType="dataSync"`, which API 34+ requires, and paired with
 * `FOREGROUND_SERVICE_DATA_SYNC` in the manifest.
 *
 * Uses [WorkManagerScheduler] rather than `PlatformScheduler` so queued work resumes in
 * the background without the app needing `RECEIVE_BOOT_COMPLETED` — the permission set
 * stays at exactly what the features require.
 */
@OptIn(UnstableApi::class)
class MhDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description,
) {

    private val engine: DownloadEngine by inject()

    private val notificationHelper: DownloadNotificationHelper by lazy {
        DownloadNotificationHelper(this, CHANNEL_ID)
    }

    override fun getDownloadManager(): DownloadManager = engine.downloadManager

    override fun getScheduler(): Scheduler = WorkManagerScheduler(this, WORK_NAME)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification = notificationHelper.buildProgressNotification(
        /* context = */ this,
        /* smallIcon = */ R.drawable.ic_download,
        /* contentIntent = */ null,
        /* message = */ null,
        /* downloads = */ downloads,
        /* notMetRequirements = */ notMetRequirements,
    )

    companion object {
        const val CHANNEL_ID = "moviehub_downloads"
        const val FOREGROUND_NOTIFICATION_ID = 4201
        const val NOTIFICATION_IMPORTANCE = NotificationUtil.IMPORTANCE_LOW

        private const val WORK_NAME = "moviehub-downloads"
    }
}
