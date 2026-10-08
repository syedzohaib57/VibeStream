package com.example.streamingappzb.data.remote.tmdb

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Authenticates every TMDB request and adds the viewer's language and region.
 *
 * Doing it here rather than per call means no endpoint can be added later that forgets
 * the credential, and it never appears in a Retrofit signature where it could be logged.
 *
 * Both of TMDB's schemes are supported, because the token page hands out both and which
 * one you copied is not obvious: a v4 read token goes in an `Authorization: Bearer` header
 * (what the reference app sends), a v3 key goes in an `api_key` query parameter. Sending
 * either one the other way returns a 401 that reads as "bad key" rather than "wrong
 * scheme", so [TmdbCredentials.isBearerToken] picks for you.
 *
 * @param token from [TmdbCredentials]: the in-source constant, else `local.properties` or
 *   the environment. Blank in a checkout nobody has configured — see
 *   [MissingTmdbKeyException].
 * @param language IETF tag, e.g. "en-US". TMDB falls back to English per field, so a
 *   partially translated title still renders.
 */
class TmdbAuthInterceptor(
    private val token: String,
    private val language: () -> String,
    private val region: () -> String,
    private val useBearer: Boolean = TmdbCredentials.isBearerToken,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (token.isBlank()) throw MissingTmdbKeyException()

        val original = chain.request()
        val url = original.url.newBuilder()
            .apply {
                // A v4 token authenticates by header, so it must not also be sent as a
                // query parameter — TMDB rejects the request outright when both are set.
                if (!useBearer) addQueryParameter("api_key", token)
                if (original.url.queryParameter("language") == null) {
                    addQueryParameter("language", language())
                }
                // `watch_region` narrows provider data; harmless on endpoints that ignore it.
                if (original.url.queryParameter("watch_region") == null) {
                    addQueryParameter("watch_region", region())
                }
            }
            .build()

        val request = original.newBuilder()
            .url(url)
            .apply { if (useBearer) header("Authorization", "Bearer $token") }
            .build()

        return chain.proceed(request)
    }
}

/**
 * Thrown when the app is built without a TMDB key.
 *
 * It is a distinct type so the UI can say *"add a TMDB key to local.properties"* instead
 * of the generic "couldn't connect", which would send someone debugging their Wi-Fi.
 *
 * ### Why it must be an IOException
 *
 * It is thrown from inside an OkHttp interceptor, and OkHttp only treats `IOException` as a
 * call failure. Anything else is rethrown on the dispatcher thread *after* the failure
 * callback runs — which no coroutine is in a position to catch, so it reaches the default
 * handler and kills the process. A keyless build has to degrade to the setup notice, not
 * crash, so the supertype here is load-bearing rather than cosmetic.
 */
class MissingTmdbKeyException : IOException(
    "No TMDB token. Paste one into TmdbCredentials.BUILT_IN_TOKEN and rebuild. " +
        "It is free from https://www.themoviedb.org/settings/api — either the v4 read " +
        "token or the v3 API key works.",
)
