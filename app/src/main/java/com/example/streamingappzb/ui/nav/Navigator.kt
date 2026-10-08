package com.example.streamingappzb.ui.nav

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.streamingappzb.domain.model.PlayableSource
import com.example.streamingappzb.ui.player.PlayerActivity
import com.example.streamingappzb.ui.title.TitleActivity

/**
 * Routing (PRD §4). Explicit Intents rather than a nav graph, because Title detail and the
 * Player are separate Activities — which is what makes "the bottom nav shows on the four
 * tab routes only" a structural fact.
 *
 * Deep links resolve to the same two Activities:
 * ```
 * moviehub://title/{id}
 * moviehub://play/{id}/{ep}?t={s}
 * ```
 * plus the `https://` App Link that Share produces (FR-108).
 */
object Navigator {

    const val EXTRA_TITLE_ID = "titleId"
    const val EXTRA_EPISODE = "episode"
    const val EXTRA_POSITION_SECONDS = "positionSeconds"

    /**
     * A free source, passed as its four fields rather than a parcelled model.
     *
     * Deliberately not deep-linkable: a `moviehub://` link carrying an arbitrary media URL
     * would let anything on the device hand this player a stream to play.
     */
    const val EXTRA_SOURCE_URL = "sourceUrl"
    const val EXTRA_SOURCE_TYPE = "sourceType"
    const val EXTRA_SOURCE_LABEL = "sourceLabel"
    const val EXTRA_SOURCE_ATTRIBUTION = "sourceAttribution"

    const val SCHEME = "moviehub"
    const val PATH_TITLE = "title"
    const val PATH_PLAY = "play"
    const val QUERY_TIME = "t"

    /**
     * Share links point at a real host so they open in a browser for anyone without the
     * app. Verifying them as App Links needs an `assetlinks.json` on that host, which is
     * why the manifest filter is not `autoVerify` yet.
     */
    const val WEB_HOST = "moviehub.app"

    fun title(context: Context, titleId: Int) = context.startActivity(titleIntent(context, titleId))

    fun titleIntent(context: Context, titleId: Int): Intent =
        Intent(context, TitleActivity::class.java).putExtra(EXTRA_TITLE_ID, titleId)

    fun play(context: Context, titleId: Int, episode: Int, positionSeconds: Int = 0) =
        context.startActivity(playIntent(context, titleId, episode, positionSeconds))

    fun playIntent(
        context: Context,
        titleId: Int,
        episode: Int,
        positionSeconds: Int = 0,
    ): Intent = Intent(context, PlayerActivity::class.java)
        .putExtra(EXTRA_TITLE_ID, titleId)
        .putExtra(EXTRA_EPISODE, episode)
        .putExtra(EXTRA_POSITION_SECONDS, positionSeconds)

    /**
     * Full playback of a source the free-source layer resolved, in the real player.
     *
     * This previously went to `TrailerActivity`, which plays a bare URL but has none of the
     * quality ladder, download cache, MediaSession or progress machinery — so the one
     * category of title the app may stream in full was the one that bypassed its player.
     */
    fun playSourceIntent(
        context: Context,
        source: PlayableSource,
        positionSeconds: Int = 0,
    ): Intent = Intent(context, PlayerActivity::class.java)
        .putExtra(EXTRA_SOURCE_URL, source.url)
        .putExtra(EXTRA_SOURCE_TYPE, source.type.name)
        .putExtra(EXTRA_SOURCE_LABEL, source.label)
        .putExtra(EXTRA_SOURCE_ATTRIBUTION, source.attribution)
        .putExtra(EXTRA_POSITION_SECONDS, positionSeconds)

    /** The link Share copies (FR-108). Resolves to the same routes as the custom scheme. */
    fun shareUrl(titleId: Int, episode: Int? = null, positionSeconds: Int? = null): String =
        if (episode == null) {
            "https://$WEB_HOST/$PATH_TITLE/$titleId"
        } else {
            buildString {
                append("https://$WEB_HOST/$PATH_PLAY/$titleId/$episode")
                if (positionSeconds != null && positionSeconds > 0) {
                    append("?$QUERY_TIME=$positionSeconds")
                }
            }
        }

    /**
     * Reads a deep link into the extras the Activities already understand, so an incoming
     * link and an in-app tap take exactly the same path.
     *
     * @return null when the Uri is not one of ours.
     */
    fun parse(uri: Uri?): Destination? {
        val segments = uri?.pathSegments ?: return null
        // A custom-scheme link puts the route in the authority, a web link in the path.
        val route = uri.host?.takeIf { uri.scheme == SCHEME } ?: segments.firstOrNull()
        val rest = if (uri.scheme == SCHEME) segments else segments.drop(1)

        return when (route) {
            PATH_TITLE -> rest.firstOrNull()?.toIntOrNull()?.let { Destination.Title(it) }

            PATH_PLAY -> {
                val id = rest.getOrNull(0)?.toIntOrNull() ?: return null
                val episode = rest.getOrNull(1)?.toIntOrNull() ?: 1
                val seconds = uri.getQueryParameter(QUERY_TIME)?.toIntOrNull() ?: 0
                Destination.Play(id, episode, seconds)
            }

            else -> null
        }
    }

    sealed interface Destination {
        data class Title(val titleId: Int) : Destination

        data class Play(
            val titleId: Int,
            val episode: Int,
            val positionSeconds: Int,
        ) : Destination
    }
}
