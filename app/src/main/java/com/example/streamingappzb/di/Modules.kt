package com.example.streamingappzb.di

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.streamingappzb.BuildConfig
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.data.analytics.LogcatAnalytics
import com.example.streamingappzb.data.catalog.CatalogApi
import com.example.streamingappzb.data.catalog.CatalogRepositoryImpl
import com.example.streamingappzb.data.db.MovieHubDatabase
import com.example.streamingappzb.data.download.DownloadEngine
import com.example.streamingappzb.data.download.DownloadRepositoryImpl
import com.example.streamingappzb.data.network.HttpCachePolicy
import com.example.streamingappzb.data.network.NetworkMonitor
import com.example.streamingappzb.data.prefs.AppPrefs
import com.example.streamingappzb.data.prefs.SettingsRepositoryImpl
import com.example.streamingappzb.data.repository.MyListRepositoryImpl
import com.example.streamingappzb.data.repository.ProgressRepositoryImpl
import com.example.streamingappzb.data.session.SessionState
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.repository.DownloadRepository
import com.example.streamingappzb.domain.repository.MyListRepository
import com.example.streamingappzb.domain.repository.NetworkRepository
import com.example.streamingappzb.domain.repository.ProgressRepository
import com.example.streamingappzb.domain.repository.SettingsRepository
import com.example.streamingappzb.domain.usecase.EnqueueDownloadUseCase
import com.example.streamingappzb.domain.usecase.EstimateDownloadSizeUseCase
import com.example.streamingappzb.domain.usecase.GetHomeFeedUseCase
import com.example.streamingappzb.domain.usecase.GetTitleDetailUseCase
import com.example.streamingappzb.domain.usecase.ResolveQualityUseCase
import com.example.streamingappzb.domain.usecase.SaveProgressUseCase
import com.example.streamingappzb.data.media.ArchiveFreeSourceRepository
import com.example.streamingappzb.data.media.Clock
import com.example.streamingappzb.data.media.CompositeFreeSourceRepository
import com.example.streamingappzb.data.media.FreeSourceRepository
import com.example.streamingappzb.data.media.JellyfinConfig
import com.example.streamingappzb.data.media.JellyfinSourceRepository
import com.example.streamingappzb.data.media.LocalFreeSourceRepository
import com.example.streamingappzb.data.media.LocalLibrary
import com.example.streamingappzb.data.media.SampleFallbackSourceRepository
import com.example.streamingappzb.data.remote.jellyfin.JellyfinApi
import com.example.streamingappzb.data.media.GenreRepositoryImpl
import com.example.streamingappzb.data.media.MediaRepositoryImpl
import com.example.streamingappzb.data.media.MediaListRepositoryImpl
import com.example.streamingappzb.data.media.RegionRepositoryImpl
import com.example.streamingappzb.data.media.SystemClock
import com.example.streamingappzb.data.remote.anilist.AniListApi
import com.example.streamingappzb.data.remote.archive.ArchiveApi
import com.example.streamingappzb.data.remote.tmdb.TmdbApi
import com.example.streamingappzb.data.remote.tmdb.TmdbAuthInterceptor
import com.example.streamingappzb.data.remote.tmdb.TmdbCredentials
import com.example.streamingappzb.domain.repository.GenreRepository
import com.example.streamingappzb.domain.repository.MediaRepository
import com.example.streamingappzb.domain.repository.MediaListRepository
import com.example.streamingappzb.domain.repository.RegionRepository
import com.example.streamingappzb.domain.usecase.GetBrowseResultsUseCase
import com.example.streamingappzb.domain.usecase.GetPersonUseCase
import com.example.streamingappzb.domain.usecase.GetWatchOptionsUseCase
import com.example.streamingappzb.domain.usecase.ResolvePlaybackUseCase
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/** Koin qualifiers, so the strings live in one place. */
object Qualifiers {
    val AppScope = named("appScope")
    val Io = named("ioDispatcher")

    /** OkHttp with the TMDB key interceptor; the plain client serves the keyless hosts. */
    val TmdbClient = named("tmdbClient")
}

val appModule = module {
    single { AppPrefs(androidContext()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<NetworkRepository> { NetworkMonitor(androidContext()) }
    single<Analytics> { LogcatAnalytics() }

    /**
     * Process-lifetime scope for SessionState and the download listener. A SupervisorJob
     * so one failing collector cannot take the others down with it.
     */
    single(Qualifiers.AppScope) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    single<CoroutineDispatcher>(Qualifiers.Io) { Dispatchers.IO }
}

val dbModule = module {
    single { MovieHubDatabase.build(androidContext()) }
    single { get<MovieHubDatabase>().titleDao() }
    single { get<MovieHubDatabase>().homeRowDao() }
    single { get<MovieHubDatabase>().genreDao() }
    single { get<MovieHubDatabase>().progressDao() }
    single { get<MovieHubDatabase>().myListDao() }
    single { get<MovieHubDatabase>().downloadDao() }
    single { get<MovieHubDatabase>().mediaDao() }
    single { get<MovieHubDatabase>().mediaListDao() }
    single { get<MovieHubDatabase>().tmdbGenreDao() }
}

/**
 * The real catalogue: TMDB, AniList and the Internet Archive.
 *
 * Three separate Retrofit instances because they are three unrelated hosts with different
 * base URLs and only one of them takes a key — sharing one and rewriting URLs per call
 * would be worse, not cleverer. They do share the OkHttp client, so connection pooling
 * and the cache are common.
 */
val mediaModule = module {
    single { SystemClock() as Clock }
    single<RegionRepository> { RegionRepositoryImpl(androidContext(), get()) }

    single(Qualifiers.TmdbClient) {
        get<OkHttpClient>().newBuilder()
            .addInterceptor(
                TmdbAuthInterceptor(
                    token = TmdbCredentials.token,
                    language = { get<AppPrefs>().language },
                    region = { get<RegionRepository>().region() },
                ),
            )
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl(TmdbApi.BASE_URL)
            .client(get(Qualifiers.TmdbClient))
            .addConverterFactory(MoshiConverterFactory.create(get()))
            .build()
            .create(TmdbApi::class.java)
    }

    single {
        Retrofit.Builder()
            .baseUrl(AniListApi.BASE_URL)
            .client(get())
            .addConverterFactory(MoshiConverterFactory.create(get()))
            .build()
            .create(AniListApi::class.java)
    }

    single {
        Retrofit.Builder()
            .baseUrl(ArchiveApi.BASE_URL)
            .client(get())
            .addConverterFactory(MoshiConverterFactory.create(get()))
            .build()
            .create(ArchiveApi::class.java)
    }

    /**
     * A singleton because it holds the scanned index. A new instance per injection would
     * re-walk every added folder on each title screen opened.
     */
    single { LocalLibrary(androidContext(), get(), get(Qualifiers.Io)) }

    single {
        Retrofit.Builder()
            // Never used: every JellyfinApi call passes a full @Url, because the real
            // address is the viewer's and arrives at runtime. Retrofit still demands a
            // syntactically valid base ending in '/'.
            .baseUrl(JELLYFIN_PLACEHOLDER_BASE)
            .client(get())
            .addConverterFactory(MoshiConverterFactory.create(get()))
            .build()
            .create(JellyfinApi::class.java)
    }

    /**
     * The resolver chain. Order is load-bearing — see [CompositeFreeSourceRepository]:
     * own files (no network), then the viewer's server (exact TMDB-id join), then the
     * Archive, then the opt-in sample.
     *
     * Every setting is read through a lambda rather than captured, so connecting a server
     * or flipping a toggle in the library sheet takes effect on the next title screen
     * instead of the next process start.
     */
    single<FreeSourceRepository> {
        val prefs = get<AppPrefs>()
        CompositeFreeSourceRepository(
            listOf(
                // Explicitly the concrete type. `get()` would resolve the parameter's
                // declared type, `LocalVideoIndex`, which has no definition of its own —
                // the interface exists only so the matching rules can be tested without
                // SAF, and binding it as well would be a second name for one singleton.
                LocalFreeSourceRepository(get<LocalLibrary>()),
                JellyfinSourceRepository(
                    api = get(),
                    config = {
                        val url = prefs.jellyfinUrl
                        val token = prefs.jellyfinToken
                        if (url.isNullOrBlank() || token.isNullOrBlank()) {
                            null
                        } else {
                            JellyfinConfig(rawUrl = url, token = token)
                        }
                    },
                ),
                ArchiveFreeSourceRepository(
                    api = get(),
                    openSearch = { prefs.openArchiveSearch },
                ),
                SampleFallbackSourceRepository(enabled = { prefs.sampleFallback }),
            ),
        )
    }

    single<MediaListRepository> { MediaListRepositoryImpl(get(), get(), get(Qualifiers.Io)) }

    /**
     * A singleton, not a factory: it holds the genre name map in memory and `name()` is
     * called once per poster. A new instance per injection would mean an empty map and a
     * disk read on every row bind.
     */
    single<GenreRepository> {
        GenreRepositoryImpl(
            tmdb = get(),
            dao = get(),
            clock = get(),
            io = get(Qualifiers.Io),
        )
    }

    single<MediaRepository> {
        MediaRepositoryImpl(
            tmdb = get(),
            aniList = get(),
            dao = get(),
            region = get(),
            genres = get(),
            clock = get(),
            io = get(Qualifiers.Io),
        )
    }

    factory { ResolvePlaybackUseCase() }
    factory { GetWatchOptionsUseCase(get()) }
    factory { GetBrowseResultsUseCase(get(), get()) }
    factory { GetPersonUseCase(get()) }
}

val networkModule = module {
    single { Moshi.Builder().build() }

    /**
     * One client for every host, with a shared disk cache.
     *
     * Interceptor order is load-bearing. `offlineFallback` must run before the cache is
     * consulted, so it is an application interceptor; `storeResponses` must run after the
     * network answers but before the cache stores anything, so it is a network interceptor.
     * Swapping either one silently disables caching rather than failing.
     */
    single {
        OkHttpClient.Builder()
            .cache(
                Cache(
                    File(androidContext().cacheDir, HttpCachePolicy.CACHE_DIR),
                    HttpCachePolicy.CACHE_BYTES,
                ),
            )
            .addInterceptor(HttpCachePolicy.offlineFallback(get()))
            .addInterceptor(HttpCachePolicy.retryTransient)
            .addNetworkInterceptor(HttpCachePolicy.storeResponses)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .build()
    }

    // Only registered when a host is configured. While API_BASE_URL is empty the
    // catalogue comes from the bundled asset and Room, and the repository takes null.
    if (BuildConfig.API_BASE_URL.isNotBlank()) {
        single {
            Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(get())
                .addConverterFactory(MoshiConverterFactory.create(get()))
                .build()
                .create(CatalogApi::class.java)
        }
    }
}

@OptIn(UnstableApi::class)
val dataModule = module {
    single<CatalogRepository> {
        CatalogRepositoryImpl(
            assets = androidContext().assets,
            moshi = get(),
            titleDao = get(),
            homeRowDao = get(),
            genreDao = get(),
            progressDao = get(),
            api = getOrNull(),
            io = get(Qualifiers.Io),
        )
    }

    single<ProgressRepository> { ProgressRepositoryImpl(get(), get(Qualifiers.Io)) }

    single<MyListRepository> { MyListRepositoryImpl(get(), get(Qualifiers.Io)) }

    single { DownloadEngine(androidContext(), get()) }

    single<DownloadRepository> {
        DownloadRepositoryImpl(
            context = androidContext(),
            engine = get(),
            dao = get(),
            catalog = get(),
            settings = get(),
            analytics = get(),
            scope = get(Qualifiers.AppScope),
            io = get(Qualifiers.Io),
        )
    }

    single {
        SessionState(
            settings = get(),
            network = get(),
            progress = get(),
            myList = get(),
            downloads = get(),
            resolveQuality = get(),
            scope = get(Qualifiers.AppScope),
        )
    }
}

val domainModule = module {
    single { ResolveQualityUseCase(get()) }
    factory { GetHomeFeedUseCase(get(), get()) }
    factory { GetTitleDetailUseCase(get(), get(), get(), get()) }
    factory { SaveProgressUseCase(get(), get()) }
    factory { EstimateDownloadSizeUseCase(get()) }
    factory { EnqueueDownloadUseCase(get(), get(), get(), get()) }
}

val movieHubModules = listOf(
    appModule,
    dbModule,
    networkModule,
    // After networkModule: the TMDB client is built from the shared OkHttp instance.
    mediaModule,
    dataModule,
    domainModule,
    viewModelModule,
)

private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_TIMEOUT_SECONDS = 30L

/** See the JellyfinApi single — syntactically required, semantically never used. */
private const val JELLYFIN_PLACEHOLDER_BASE = "http://jellyfin.invalid/"
