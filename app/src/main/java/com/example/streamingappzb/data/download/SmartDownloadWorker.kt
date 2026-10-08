package com.example.streamingappzb.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.streamingappzb.domain.model.DownloadItem
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.repository.DownloadRepository
import com.example.streamingappzb.domain.repository.SettingsRepository
import com.example.streamingappzb.domain.usecase.EnqueueDownloadUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Smart downloads (PRD §6.5): when the viewer finishes an episode and the setting is on,
 * fetch the next one and delete the one just watched.
 *
 * Constrained to an unmetered connection, so it can never spend mobile data — the setting
 * says "on Wi-Fi", and this is what enforces it regardless of the Wi-Fi-only download
 * toggle.
 */
class SmartDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val settings: SettingsRepository by inject()
    private val catalog: CatalogRepository by inject()
    private val downloads: DownloadRepository by inject()
    private val enqueue: EnqueueDownloadUseCase by inject()

    override suspend fun doWork(): Result {
        if (!settings.smartDownloads.value) return Result.success()

        val titleId = inputData.getInt(KEY_TITLE_ID, -1)
        val finished = inputData.getInt(KEY_EPISODE, -1)
        if (titleId < 0 || finished < 0) return Result.failure()

        val detail = catalog.titleDetail(titleId) ?: return Result.success()
        val next = detail.next(finished)

        // Match the rung the viewer already chose for this title rather than guessing.
        val previous = downloads.get(titleId, finished)
        val rungId = previous?.rungId ?: Rung.DEFAULT_DOWNLOAD

        if (next != null) {
            // This worker only runs while unmetered, so nothing needs parking.
            enqueue(
                titleId = titleId,
                episodes = listOf(next.number),
                rungId = rungId,
                useMobileDataNow = false,
            )
        }

        // Only remove the watched copy once its replacement is on the way (or there is no
        // replacement), so the viewer is never left with nothing offline.
        if (previous != null) {
            downloads.remove(DownloadItem.key(titleId, finished))
        }
        return Result.success()
    }

    companion object {
        private const val KEY_TITLE_ID = "titleId"
        private const val KEY_EPISODE = "episode"

        fun enqueue(context: Context, titleId: Int, finishedEpisode: Int) {
            val request = OneTimeWorkRequestBuilder<SmartDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build(),
                )
                .setInputData(
                    Data.Builder()
                        .putInt(KEY_TITLE_ID, titleId)
                        .putInt(KEY_EPISODE, finishedEpisode)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "moviehub-smart-$titleId-$finishedEpisode",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
