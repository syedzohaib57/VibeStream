package com.example.streamingappzb.ui.media

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.ActivityTrailerBinding
import com.example.streamingappzb.domain.model.PlayableSource
import com.example.streamingappzb.domain.model.Trailer
import com.example.streamingappzb.ui.base.BaseActivity
import com.example.streamingappzb.ui.base.onSingleClick

/**
 * Plays a trailer, or a title we may legally serve in full.
 *
 * ### Two players, and why
 *
 * **Trailers use a WebView running YouTube's official IFrame embed.** They are not fed to
 * ExoPlayer, and that is not a technical shortcut — extracting YouTube's underlying media
 * URLs to play them in your own player is a plain breach of their terms, and it is exactly
 * what the apps that get pulled from Play do. The embed is the supported, permitted route:
 * YouTube serves it, counts the view, and shows whatever advertising it chooses.
 *
 * **Public-domain and openly-licensed video uses ExoPlayer**, because that we may serve
 * directly.
 *
 * One screen rather than two because from the viewer's side it is one thing — press play,
 * watch — and the distinction that matters is legal, not visual.
 */
@OptIn(UnstableApi::class)
class TrailerActivity : BaseActivity<ActivityTrailerBinding>() {

    private var player: ExoPlayer? = null

    override fun inflateBinding(inflater: LayoutInflater) =
        ActivityTrailerBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.title.text = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        binding.btnClose.onSingleClick { finish() }

        val embed = intent.getStringExtra(EXTRA_EMBED_URL)
        val sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL)

        when {
            embed != null -> showEmbed(embed)
            sourceUrl != null -> showVideo(sourceUrl)
            else -> finish()
        }

        binding.attribution.text = intent.getStringExtra(EXTRA_ATTRIBUTION).orEmpty()
        binding.attribution.isVisible = !intent.getStringExtra(EXTRA_ATTRIBUTION).isNullOrBlank()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showEmbed(url: String) {
        binding.web.isVisible = true
        binding.playerView.isVisible = false

        with(binding.web.settings) {
            // The IFrame player is JavaScript; without this it renders a blank rectangle.
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
        }
        binding.web.webChromeClient = WebChromeClient()
        binding.web.webViewClient = object : WebViewClient() {
            /**
             * Keeps the WebView on the embed. Any other destination — a "watch on YouTube"
             * tap, an advertiser link — leaves for a real browser or the YouTube app rather
             * than turning this screen into an unmanaged browser.
             */
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val target = request.url.toString()
                if (target.startsWith(EMBED_PREFIX)) return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }
        }
        binding.web.loadUrl(url)
    }

    private fun showVideo(url: String) {
        binding.web.isVisible = false
        binding.playerView.isVisible = true

        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
        // Pausing the WebView stops audio continuing after the screen is gone, which a
        // plain finish() does not.
        binding.web.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.web.onResume()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        binding.web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_EMBED_URL = "embed_url"
        private const val EXTRA_SOURCE_URL = "source_url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ATTRIBUTION = "attribution"
        private const val EMBED_PREFIX = "https://www.youtube.com/embed/"

        fun intent(context: Context, trailer: Trailer, title: String): Intent =
            Intent(context, TrailerActivity::class.java)
                .putExtra(EXTRA_EMBED_URL, trailer.embedUrl)
                .putExtra(EXTRA_TITLE, title)

        fun intentForSource(context: Context, source: PlayableSource, title: String): Intent =
            Intent(context, TrailerActivity::class.java)
                .putExtra(EXTRA_SOURCE_URL, source.url)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ATTRIBUTION, source.attribution)
    }
}
