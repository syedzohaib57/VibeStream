package com.example.streamingappzb.data.network

import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.domain.repository.NetworkRepository
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * The HTTP caching policy for the catalogue APIs.
 *
 * Room already caches the *shaped* feed, but that only covers rows and titles the app has
 * modelled. Everything else — genre lists, images, a person's filmography, a collection —
 * had no cache at all and re-fetched on every visit. These interceptors give all of it a
 * disk cache, so a second visit to a title screen is free and an offline one still draws.
 *
 * TMDB sends `Cache-Control: public, max-age=…` on most endpoints but not all of them, and
 * AniList sends none, so the age is rewritten here rather than trusted.
 */
object HttpCachePolicy {

    /** 10 MB. Metadata JSON is small; this holds thousands of responses. */
    const val CACHE_BYTES = 10L * 1024 * 1024

    const val CACHE_DIR = "api_http_cache"

    /** Fresh enough that browsing never re-fetches, short enough to see a new release. */
    private const val MAX_AGE_MINUTES = 5

    /** How stale a response may be before an offline read gives up on it. */
    private const val MAX_STALE_DAYS = 7

    /**
     * Rewrites the response's cache headers on the way back from the network.
     *
     * A *network* interceptor, not an application one: the directive has to be stamped on
     * the response before OkHttp's cache decides whether to store it. `Pragma: no-cache`
     * is stripped because when present it overrides `Cache-Control` entirely and would
     * leave the cache permanently empty.
     */
    val storeResponses = Interceptor { chain ->
        chain.proceed(chain.request()).newBuilder()
            .header("Cache-Control", "public, max-age=${MAX_AGE_MINUTES * 60}")
            .removeHeader("Pragma")
            .build()
    }

    /**
     * Serves a stale cached response when there is no usable connection.
     *
     * Without this an offline request fails outright even with a perfectly good response
     * on disk, because the stored `max-age` has passed. With it, a cold offline open draws
     * the last week's data instead of an error — the behaviour the reference app relies on
     * and the reason the feed survives a flight.
     *
     * Only applied while offline. Doing it unconditionally would let a week-old response
     * satisfy a request made on a good connection.
     */
    fun offlineFallback(network: NetworkRepository) = Interceptor { chain ->
        val request = chain.request()
        if (network.current() != NetworkState.Offline) return@Interceptor chain.proceed(request)

        chain.proceed(
            request.newBuilder()
                .cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(MAX_STALE_DAYS, TimeUnit.DAYS)
                        .build(),
                )
                .build(),
        )
    }

    /**
     * Retries the failures that are worth retrying, and only those.
     *
     * TMDB rate-limits with 429 and its edge occasionally answers 503/504; both clear
     * within a second or two. 4xx other than 429 never clears by asking again, so it is
     * returned immediately rather than burning three attempts and the viewer's time.
     *
     * Backoff is exponential from [RETRY_BASE_DELAY_MS]. The reference app slept a flat
     * three seconds, which on a 429 means three requests inside the same rate-limit window.
     */
    val retryTransient = Interceptor { chain ->
        var attempt = 0
        while (true) {
            val isLastAttempt = attempt == MAX_RETRIES

            if (attempt > 0) {
                // Interceptors run on OkHttp's own thread, never the main one, so sleeping
                // here is how backoff is expressed at this layer.
                try {
                    Thread.sleep(RETRY_BASE_DELAY_MS shl (attempt - 1))
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Retry interrupted", interrupted)
                }
            }

            val response = try {
                chain.proceed(chain.request())
            } catch (e: IOException) {
                // A cancelled call must not be retried: the caller's coroutine is already
                // gone, and retrying would outlive the scope that owns the request.
                if (isLastAttempt || chain.call().isCanceled() || !isTransient(e)) throw e
                attempt++
                continue
            }

            if (isLastAttempt || response.code !in RETRYABLE_CODES) return@Interceptor response

            // The body holds the connection; a response being discarded must be closed.
            response.close()
            attempt++
        }
        // Not reached: every path above either returns or throws.
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    /** A timeout or a dropped connection clears on a retry; a DNS or TLS failure does not. */
    private fun isTransient(e: IOException): Boolean =
        e is SocketTimeoutException || e is java.net.ConnectException

    private const val MAX_RETRIES = 2
    private const val RETRY_BASE_DELAY_MS = 800L

    /** 429 is TMDB's rate limit; 502/503/504 are transient edge failures. */
    private val RETRYABLE_CODES = setOf(429, 502, 503, 504)
}
