package com.example.streamingappzb.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.streamingappzb.domain.repository.DownloadRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Drops downloads past their licence window and deletes the media (PRD §6.5).
 *
 * Runs daily rather than on a timer in the app, because a licence can lapse while the app
 * has not been opened for weeks — which is exactly when a stale offline copy would be
 * played.
 */
class LicenceExpiryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val downloads: DownloadRepository by inject()

    override suspend fun doWork(): Result {
        val removed = runCatching { downloads.purgeExpired(System.currentTimeMillis()) }
        // A failure here is never worth retrying aggressively: tomorrow's run will catch it.
        return if (removed.isSuccess) Result.success() else Result.failure()
    }

    companion object {
        private const val WORK_NAME = "moviehub-licence-expiry"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LicenceExpiryWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
