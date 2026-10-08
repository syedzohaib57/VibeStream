package com.example.streamingappzb

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import com.example.streamingappzb.data.download.DownloadEngine
import com.example.streamingappzb.data.download.DownloadRepositoryImpl
import com.example.streamingappzb.data.download.LicenceExpiryWorker
import com.example.streamingappzb.data.download.MhDownloadService
import com.example.streamingappzb.data.media.LocalLibrary
import com.example.streamingappzb.data.session.SessionState
import com.example.streamingappzb.di.Qualifiers
import com.example.streamingappzb.di.movieHubModules
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.repository.DownloadRepository
import com.example.streamingappzb.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

@OptIn(UnstableApi::class)
class MovieHubApp : Application() {

    private val appScope: CoroutineScope by inject(Qualifiers.AppScope)

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@MovieHubApp)
            modules(movieHubModules)
        }

        // Resolve SessionState eagerly: its collectors own the FR-204 quality rules and
        // the waiting-to-queued promotion, so they have to be live from process start
        // rather than from whenever the first screen happens to inject it.
        get<SessionState>()

        // Match the engine's requirement to the stored Wi-Fi-only setting before anything
        // can be enqueued.
        val settings: SettingsRepository = get()
        get<DownloadEngine>().setWifiOnly(settings.wifiOnly.value)

        // Media3 keeps the queue but does not run it without its service, so anything left
        // pending by a previous process needs restarting here.
        (get<DownloadRepository>() as? DownloadRepositoryImpl)?.resumePendingDownloads()

        NotificationUtil.createNotificationChannel(
            this,
            MhDownloadService.CHANNEL_ID,
            R.string.download_channel_name,
            R.string.download_channel_description,
            MhDownloadService.NOTIFICATION_IMPORTANCE,
        )

        LicenceExpiryWorker.schedule(this)

        // Drop the library index every time the app comes back to the foreground.
        //
        // The index is scanned once and held in memory, because a tree walk over SAF is a
        // Binder round trip per directory and re-doing it on every title screen would be
        // seconds, not milliseconds. The cost of that is a cache with no way to learn that
        // the folder changed — and the realistic way it changes is the viewer leaving the
        // app, copying a film across, and coming back, which is exactly this signal.
        //
        // Invalidating is cheap: it only clears the cache. Nothing is re-walked until a
        // title screen actually asks for a source, and that happens on the IO dispatcher.
        val library: LocalLibrary = get()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = library.invalidate()
            },
        )

        // Seeding reads an asset and writes Room, so it never runs on the main thread.
        // Home renders from Room, so it simply fills in a frame or two later — which is
        // what keeps cold start inside the PRD §8 budget.
        appScope.launch {
            get<CatalogRepository>().ensureSeeded()
            settings.markSeeded()
        }
    }
}
