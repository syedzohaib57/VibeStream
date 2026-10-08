package com.example.streamingappzb.media

import com.example.streamingappzb.data.remote.tmdb.MissingTmdbKeyException
import com.example.streamingappzb.data.remote.tmdb.TmdbAuthInterceptor
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The TMDB auth interceptor.
 *
 * The first test is a regression guard, not a behavioural one, and it is the important one
 * here: [MissingTmdbKeyException] being an [IOException] is what stops a keyless build from
 * taking the process down. OkHttp routes `IOException` to the call's failure path, where the
 * repositories' `runCatching` sees it; anything else is rethrown on the dispatcher thread
 * after the callback, reaching the default handler as a fatal exception. `IllegalStateException`
 * is the natural-looking choice and is exactly the wrong one.
 */
class TmdbAuthInterceptorTest {

    @Test
    fun `the missing-key exception is an IOException, so OkHttp can fail the call`() {
        assertTrue(
            "MissingTmdbKeyException must extend IOException — see the class doc. " +
                "Any other supertype crashes the app on a keyless build.",
            MissingTmdbKeyException() is IOException,
        )
    }

    @Test
    fun `a blank token fails the call rather than sending an unauthenticated request`() {
        val chain = chain()
        val interceptor = TmdbAuthInterceptor(token = "", language = { "en-US" }, region = { "US" })

        val thrown = assertThrows(MissingTmdbKeyException::class.java) {
            interceptor.intercept(chain)
        }

        // The message is what the setup notice paraphrases, so it has to name the fix.
        assertTrue(thrown.message!!.contains("TmdbCredentials"))
    }

    @Test
    fun `a v4 read token authenticates by header, never as a query parameter`() {
        val sent = slot<Request>()
        val interceptor = TmdbAuthInterceptor(
            token = V4_TOKEN,
            language = { "en-US" },
            region = { "US" },
            useBearer = true,
        )

        interceptor.intercept(chain(captureInto = sent))

        assertEquals("Bearer $V4_TOKEN", sent.captured.header("Authorization"))
        // Sending both is not belt-and-braces: TMDB rejects the request when it sees both.
        assertNull(sent.captured.url.queryParameter("api_key"))
        // Language and region still apply — they are orthogonal to the auth scheme.
        assertEquals("en-US", sent.captured.url.queryParameter("language"))
    }

    @Test
    fun `a v3 key authenticates by query parameter, with no auth header`() {
        val sent = slot<Request>()
        val interceptor = TmdbAuthInterceptor(
            token = V3_KEY,
            language = { "en-US" },
            region = { "US" },
            useBearer = false,
        )

        interceptor.intercept(chain(captureInto = sent))

        assertEquals(V3_KEY, sent.captured.url.queryParameter("api_key"))
        assertNull(sent.captured.header("Authorization"))
    }

    @Test
    fun `the scheme is detected from the token's own shape`() {
        // v4 tokens are JWTs; v3 keys are hex. Picking the wrong scheme returns a 401 that
        // reads as "bad key", which is the confusing failure this detection exists to avoid.
        assertTrue(V4_TOKEN.startsWith("eyJ"))
        assertTrue(!V3_KEY.startsWith("eyJ"))
    }

    @Test
    fun `the key, language and watch region are appended to every request`() {
        val sent = slot<Request>()
        // Pinned to the v3 scheme: this test asserts `api_key` is on the URL, which only
        // happens when the token is a v3 key. Left to the default it would follow
        // `TmdbCredentials.isBearerToken` and so depend on whichever token the build
        // happens to carry.
        val interceptor = TmdbAuthInterceptor(
            token = "abc123",
            language = { "en-GB" },
            region = { "PK" },
            useBearer = false,
        )

        interceptor.intercept(chain(captureInto = sent))

        val url = sent.captured.url
        assertEquals("abc123", url.queryParameter("api_key"))
        assertEquals("en-GB", url.queryParameter("language"))
        assertEquals("PK", url.queryParameter("watch_region"))
    }

    @Test
    fun `a language already on the request is not overwritten`() {
        val sent = slot<Request>()
        val interceptor =
            TmdbAuthInterceptor(token = "abc123", language = { "en-GB" }, region = { "PK" })

        interceptor.intercept(
            chain(
                request = Request.Builder()
                    .url("https://api.themoviedb.org/3/movie/popular?language=ja-JP")
                    .build(),
                captureInto = sent,
            ),
        )

        // A caller that asked for a specific language meant it — appending a second
        // `language` parameter would make TMDB pick one of them arbitrarily.
        assertEquals("ja-JP", sent.captured.url.queryParameter("language"))
        assertEquals(1, sent.captured.url.queryParameterValues("language").size)
    }

    @Test
    fun `the key never appears in the path, only as a query parameter`() {
        val sent = slot<Request>()
        // v3 scheme, for the same reason as above: the "no auth header" assertion below is
        // only true of a query-parameter key.
        val interceptor = TmdbAuthInterceptor(
            token = "secret",
            language = { "en-US" },
            region = { "US" },
            useBearer = false,
        )

        interceptor.intercept(chain(captureInto = sent))

        assertTrue(sent.captured.url.encodedPath.contains("movie/popular"))
        assertNull(sent.captured.header("Authorization"))
    }

    // ------------------------------------------------------------------ fake

    private companion object {
        /** Shape only — a JWT header and nothing else. Not a working credential. */
        const val V4_TOKEN = "eyJhbGciOiJIUzI1NiJ9.test-payload.test-signature"

        const val V3_KEY = "0123456789abcdef0123456789abcdef"
    }

    /** `capture` only resolves inside an `every` block, so the slot is always bound. */
    private fun chain(
        request: Request = Request.Builder()
            .url("https://api.themoviedb.org/3/movie/popular")
            .build(),
        captureInto: CapturingSlot<Request> = slot(),
    ): Interceptor.Chain = mockk<Interceptor.Chain>().apply {
        every { request() } returns request
        every { proceed(capture(captureInto)) } answers {
            Response.Builder()
                .request(firstArg())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody())
                .build()
        }
    }
}
