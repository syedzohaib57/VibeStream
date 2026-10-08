package com.example.streamingappzb.data.remote.tmdb

import com.example.streamingappzb.BuildConfig

/**
 * The TMDB credential, resolved the same way the reference app resolves it.
 *
 * ## Paste your token on the line below and rebuild. That is the whole setup.
 *
 * KiduyuTv works on a fresh checkout because its token is a constant in the source
 * (`ApiClient.BEARER_TOKEN`) rather than something each machine has to configure. This is
 * that same arrangement: fill [BUILT_IN_TOKEN] in and the app needs no `local.properties`,
 * no environment variable, and nothing set up on a second machine or on CI.
 *
 * A token is free and takes about two minutes:
 *   1. https://www.themoviedb.org/signup
 *   2. https://www.themoviedb.org/settings/api  →  request an API key (choose "Developer")
 *   3. Copy **either** the "API Read Access Token" (v4, long, starts `eyJ`) **or** the
 *      "API Key" (v3, 32 hex characters) — [isBearerToken] detects which and authenticates
 *      the right way, so either one works.
 *
 * ### Use your own, not one copied from another project
 *
 * A v4 token encodes the account that issued it. Traffic from this app counts against that
 * account's rate limit and its standing with TMDB, so a token lifted from someone else's
 * repository makes them responsible for what this app does. Yours costs nothing.
 *
 * ### Not a secret worth protecting
 *
 * A read-only TMDB token grants nothing but public catalogue data, which is why the
 * reference app ships one in plain source. It is still worth keeping out of a public
 * repository — [BuildConfig.TMDB_API_KEY] remains as the fallback for exactly that, reading
 * `tmdb.apiKey` from the git-ignored `local.properties` or the `TMDB_API_KEY` environment
 * variable.
 */
object TmdbCredentials {

    /**
     * Paste a v4 read token or a v3 API key between the quotes.
     *
     * The value below was taken from the KiduyuTv reference project, where it is committed
     * in plain source at `ApiClient.BEARER_TOKEN`. It is read-only (`scopes: [api_read]`)
     * and reaches nothing but public catalogue data — but it identifies *that* project's
     * TMDB account, so every request this app makes counts against their rate limit and
     * their standing with TMDB. Replace it with your own before shipping; it is free from
     * https://www.themoviedb.org/settings/api and nothing else has to change.
     */
    private const val BUILT_IN_TOKEN =
        "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0MTAzZmMzMDY1YzEyMmViNWRiNmJkY2ZmNzQ5ZmRlNyIsIm5iZiI6" +
            "MTY2ODA2NDAzNC4yNDk5OTk4LCJzdWIiOiI2MzZjYTMyMjA0OTlmMjAwN2ZlYjA4MWEiLCJzY29wZXMiOlsi" +
            "YXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.tjvtYPTPfLOyMdOouQ14GGgOzmfnZRW4RgvOzfoq19w"

    /**
     * The built-in token when there is one, otherwise whatever the build supplied. In that
     * order so pasting one in always wins, without having to clear `local.properties` too.
     */
    val token: String
        get() = BUILT_IN_TOKEN.trim().ifBlank { BuildConfig.TMDB_API_KEY.trim() }

    val isPresent: Boolean get() = token.isNotBlank()

    /**
     * v4 read tokens are JWTs and go in an `Authorization: Bearer` header; v3 keys are hex
     * and go in an `api_key` query parameter. TMDB accepts both, but not interchangeably —
     * sending a v4 token as `api_key` returns 401, which reads as "wrong key" rather than
     * "wrong scheme" and is a genuinely confusing hour to lose.
     */
    val isBearerToken: Boolean get() = token.startsWith(JWT_PREFIX)

    /** Base64 of `{"alg"` — every JWT header starts this way. */
    private const val JWT_PREFIX = "eyJ"
}
