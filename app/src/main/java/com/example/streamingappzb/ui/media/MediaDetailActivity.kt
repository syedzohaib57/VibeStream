package com.example.streamingappzb.ui.media

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.streamingappzb.R
import com.example.streamingappzb.data.remote.tmdb.TmdbImages
import com.example.streamingappzb.databinding.ActivityMediaDetailBinding
import com.example.streamingappzb.databinding.ItemWatchOfferBinding
import com.example.streamingappzb.domain.model.CastMember
import com.example.streamingappzb.domain.model.CrewMember
import com.example.streamingappzb.domain.model.MediaDetail
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.OfferType
import com.example.streamingappzb.domain.model.WatchOffer
import com.example.streamingappzb.domain.usecase.GetWatchOptionsUseCase
import com.example.streamingappzb.domain.usecase.ResolvePlaybackUseCase
import com.example.streamingappzb.ui.base.BaseActivity
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.browse.BrowseActivity
import com.example.streamingappzb.ui.discover.MediaPosterAdapter
import com.example.streamingappzb.ui.nav.Navigator
import com.example.streamingappzb.ui.person.PersonActivity
import coil.load
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.util.Locale

/**
 * The title screen for a real catalogue item.
 *
 * Its centre of gravity is "where to watch", not "play": for a licensed title the honest
 * primary action is to open the service that carries it, and the button says so. Playback
 * in-app happens only for sources we may legally serve, which
 * [ResolvePlaybackUseCase] decides.
 */
class MediaDetailActivity : BaseActivity<ActivityMediaDetailBinding>() {

    private val mediaId by lazy { intent.getIntExtra(EXTRA_ID, 0) }
    private val mediaType by lazy {
        runCatching { MediaType.valueOf(intent.getStringExtra(EXTRA_TYPE).orEmpty()) }
            .getOrDefault(MediaType.Movie)
    }

    private val viewModel: MediaDetailViewModel by viewModel { parametersOf(mediaId, mediaType) }

    private val castAdapter = CastAdapter(onOpen = ::openPerson)
    private val crewAdapter = CrewAdapter(onOpen = ::openPerson)
    private val episodeAdapter = MediaEpisodeAdapter(::todayIso, onClick = { episode ->
        viewModel.playEpisode(episode)
    })

    /** Recommendations and the collection are both poster rows, so they share the cell. */
    private val relatedAdapter = MediaPosterAdapter(onOpen = ::openTitle, onLongPress = {})
    private val collectionAdapter = MediaPosterAdapter(onOpen = ::openTitle, onLongPress = {})

    override fun inflateBinding(inflater: LayoutInflater) =
        ActivityMediaDetailBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.btnBack.onSingleClick { finish() }
        binding.btnRetry.onSingleClick { viewModel.retry() }
        binding.btnMyList.onSingleClick { viewModel.toggleMyList() }

        binding.cast.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.cast.adapter = castAdapter
        binding.crew.adapter = crewAdapter
        binding.related.adapter = relatedAdapter
        binding.collection.adapter = collectionAdapter
        binding.episodes.layoutManager = LinearLayoutManager(this)
        binding.episodes.adapter = episodeAdapter
        binding.episodes.isNestedScrollingEnabled = false

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            viewModel.state
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest(::render)
        }
        lifecycleScope.launch {
            viewModel.events
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { event ->
                    when (event) {
                        is MediaDetailViewModel.Event.Toast -> showToast(event.messageRes)
                        is MediaDetailViewModel.Event.OpenHandoff -> open(event.handoff)
                        is MediaDetailViewModel.Event.PlaySource ->
                            startActivity(Navigator.playSourceIntent(this@MediaDetailActivity, event.source))
                    }
                }
        }
    }

    private fun render(state: MediaDetailUiState) {
        binding.loading.isVisible = state.loading && state.detail == null
        binding.errorState.isVisible = state.failed && state.detail == null
        val detail = state.detail ?: return

        binding.backdrop.bind(detail.item, showLabel = false, backdrop = true)
        binding.title.text = detail.item.title
        binding.overview.text = detail.item.overview
        binding.overview.isVisible = detail.item.overview.isNotBlank()

        // The tagline, or the original-language title when there is no tagline and the two
        // titles differ — one line, whichever of the two is actually informative.
        val subtitle = detail.tagline?.takeIf { it.isNotBlank() }
            ?: detail.alternateTitle?.let { getString(R.string.detail_original_title, it) }
        binding.tagline.text = subtitle.orEmpty()
        binding.tagline.isVisible = subtitle != null

        fillMeta(detail)
        renderPrimary(state)
        renderWatch(detail)

        castAdapter.submitList(detail.cast)
        binding.castHeading.isVisible = detail.cast.isNotEmpty()
        binding.cast.isVisible = detail.cast.isNotEmpty()

        crewAdapter.submitList(detail.crew)
        binding.crewHeading.isVisible = detail.crew.isNotEmpty()
        binding.crew.isVisible = detail.crew.isNotEmpty()

        renderRelated(detail)
        renderCollection(state)
        renderStudios(detail)

        binding.btnMyList.setIconResource(
            if (state.inMyList) R.drawable.ic_check else R.drawable.ic_add,
        )
        binding.btnMyList.contentDescription = getString(
            if (state.inMyList) R.string.cd_remove_from_list else R.string.cd_add_to_list,
        )

        binding.btnShare.onSingleClick { share(detail) }

        renderEpisodes(state, detail)
    }

    /** Year · runtime or episode count · rating · genres, as chips that scroll. */
    private fun fillMeta(detail: MediaDetail) {
        binding.meta.removeAllViews()
        MediaFormat.fillHeroMeta(binding.meta, detail.item)

        detail.runtimeMinutes?.let { addMeta(MediaFormat.runtime(it)) }
        detail.seasonCount?.let { addMeta(getString(R.string.seasons_count, it)) }
        detail.episodeCount?.takeIf { detail.seasonCount == null }
            ?.let { addMeta(getString(R.string.episodes_count, it)) }
        detail.status?.let { addMeta(it) }
        detail.nextAiring?.let { airing ->
            val date = java.text.SimpleDateFormat("d MMM", Locale.getDefault())
                .format(java.util.Date(airing.airingAtEpochSeconds * 1000))
            addMeta(getString(R.string.next_episode_airs, airing.episode, date))
        }
    }

    private fun addMeta(text: String) {
        binding.meta.addView(
            TextView(this).apply {
                setTextAppearance(R.style.TextAppearance_Mh_Num_Meta)
                this.text = text
                setPadding(0, 0, dimen(R.dimen.gap_button_tight), 0)
            },
        )
    }

    /** The primary button's label and action both come from the resolved action. */
    private fun renderPrimary(state: MediaDetailUiState) {
        val detail = state.detail ?: return
        when (val action = state.primary) {
            is ResolvePlaybackUseCase.Action.Play -> {
                // "Play", not "Watch free": the source may now be the viewer's own file,
                // for which "free" is meaningless, or a sample, for which it is
                // misleading. The attribution line immediately below says which it is, so
                // the button only has to say what it does.
                binding.btnPrimary.setText(R.string.action_play)
                binding.btnPrimary.setIconResource(R.drawable.ic_play_arrow)
                binding.btnPrimary.isEnabled = true
                binding.btnPrimary.onSingleClick { playFree(detail) }
                binding.playableNote.isVisible = true
                binding.playableNote.text = action.source.attribution
            }

            is ResolvePlaybackUseCase.Action.OpenProvider -> {
                binding.btnPrimary.text =
                    getString(R.string.action_open_on, action.offer.providerName)
                binding.btnPrimary.setIconResource(R.drawable.ic_play_arrow)
                binding.btnPrimary.isEnabled = true
                binding.btnPrimary.onSingleClick {
                    viewModel.openProvider(action.offer, ::isInstalled)
                }
                binding.playableNote.isVisible = false
            }

            is ResolvePlaybackUseCase.Action.PlayTrailer -> {
                binding.btnPrimary.setText(R.string.action_trailer)
                binding.btnPrimary.setIconResource(R.drawable.ic_play_circle_fill)
                binding.btnPrimary.isEnabled = true
                binding.btnPrimary.onSingleClick {
                    startActivity(TrailerActivity.intent(this, action.trailer, detail.item.title))
                }
                binding.playableNote.isVisible = false
            }

            ResolvePlaybackUseCase.Action.Unavailable, null -> {
                binding.btnPrimary.setText(R.string.unavailable_body)
                binding.btnPrimary.isEnabled = false
                binding.playableNote.isVisible = false
            }
        }

        val secondary = state.secondary
        binding.btnSecondary.isVisible = secondary != null
        if (secondary is ResolvePlaybackUseCase.Action.PlayTrailer) {
            binding.btnSecondary.setText(R.string.action_trailer)
            binding.btnSecondary.onSingleClick {
                startActivity(TrailerActivity.intent(this, secondary.trailer, detail.item.title))
            }
        }
    }

    /** One group per offer type, cheapest first, each a row of tappable provider chips. */
    private fun renderWatch(detail: MediaDetail) {
        val watch = detail.watch
        binding.watchRegion.text =
            getString(R.string.watch_region, displayRegion(watch.region))
        binding.watchEmpty.isVisible = watch.isEmpty
        binding.watchEmpty.text =
            getString(R.string.watch_none, displayRegion(watch.region))
        binding.attribution.isVisible = !watch.isEmpty

        binding.watchGroups.removeAllViews()
        for ((type, offers) in watch.byType) {
            binding.watchGroups.addView(groupHeading(type))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val scroller = android.widget.HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }
            offers.forEach { offer -> row.addView(offerChip(offer, detail)) }
            binding.watchGroups.addView(scroller)
        }
    }

    private fun groupHeading(type: OfferType): TextView = TextView(this).apply {
        setTextAppearance(R.style.TextAppearance_Mh_Caption_Semibold)
        // Free and ad-supported are cyan — the app's colour for "this costs you nothing".
        setTextColor(color(if (type.costsNothing) R.color.cyan400 else R.color.text_mid))
        setText(
            when (type) {
                OfferType.Free -> R.string.offer_free
                OfferType.Ads -> R.string.offer_ads
                OfferType.Subscription -> R.string.offer_subscription
                OfferType.Rent -> R.string.offer_rent
                OfferType.Buy -> R.string.offer_buy
            },
        )
        setPadding(0, dimen(R.dimen.gap_button_tight), 0, 4)
    }

    private fun offerChip(offer: WatchOffer, detail: MediaDetail): View {
        val chip = ItemWatchOfferBinding.inflate(layoutInflater, binding.watchGroups, false)
        chip.name.text = offer.providerName
        TmdbImages.logo(offer.logoPath)?.let { url ->
            chip.logo.load(url) { crossfade(true) }
        }
        chip.root.contentDescription = getString(R.string.action_open_on, offer.providerName)
        chip.root.onSingleClick { viewModel.openProvider(offer, ::isInstalled) }
        return chip.root
    }

    /**
     * "More like this": TMDB's recommendations, falling back to `similar` when it has none.
     * [MediaDetail.related] decides which — never both, since the lists overlap heavily and
     * a merged row reads as padding.
     */
    private fun renderRelated(detail: MediaDetail) {
        // The title being viewed turns up in its own recommendations often enough to notice.
        val related = detail.related.filterNot { it.key == detail.item.key }
        relatedAdapter.submitList(related)
        binding.relatedHeading.isVisible = related.isNotEmpty()
        binding.related.isVisible = related.isNotEmpty()
    }

    /** The film series this title belongs to, when it is part of one. */
    private fun renderCollection(state: MediaDetailUiState) {
        val collection = state.collection
        val parts = collection?.parts.orEmpty()
        collectionAdapter.submitList(parts)
        binding.collectionHeading.isVisible = collection != null && parts.isNotEmpty()
        binding.collection.isVisible = collection != null && parts.isNotEmpty()
        if (collection != null) {
            // TMDB already names these "The X Collection" about half the time, so the
            // wrapper is only applied when it does not.
            binding.collectionHeading.text =
                if (collection.name.contains(COLLECTION_WORD, ignoreCase = true)) {
                    collection.name
                } else {
                    getString(R.string.detail_collection, collection.name)
                }
        }
    }

    /** Studios for a film, networks for a series; each chip browses that catalogue. */
    private fun renderStudios(detail: MediaDetail) {
        val studios = detail.studios.filter { it.name.isNotBlank() }.take(MAX_STUDIOS)
        binding.studiosHeading.isVisible = studios.isNotEmpty()
        binding.studiosScroller.isVisible = studios.isNotEmpty()
        binding.studiosHeading.setText(
            if (studios.firstOrNull()?.isNetwork == true) {
                R.string.detail_networks
            } else {
                R.string.detail_studios
            },
        )

        binding.studios.removeAllViews()
        for (studio in studios) {
            val chip = ItemWatchOfferBinding.inflate(layoutInflater, binding.studios, false)
            chip.name.text = studio.name
            TmdbImages.logo(studio.logoPath)?.let { url -> chip.logo.load(url) { crossfade(true) } }
            chip.root.onSingleClick {
                startActivity(BrowseActivity.forStudio(this, studio))
            }
            binding.studios.addView(chip.root)
        }
    }

    private fun renderEpisodes(state: MediaDetailUiState, detail: MediaDetail) {
        val show = detail.type.isSeries && detail.seasons.isNotEmpty()
        binding.episodesSection.isVisible = show
        if (!show) return

        binding.seasonChips.removeAllViews()
        for (season in detail.seasons) {
            val selected = season.seasonNumber == state.selectedSeason
            val chip = TextView(this, null, 0, R.style.Widget_Mh_Chip).apply {
                text = season.name
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dimen(R.dimen.chip_touch_height),
                ).apply { marginEnd = dimen(R.dimen.gap_poster) }
                setBackgroundResource(
                    if (selected) {
                        R.drawable.bg_chip_selected_inset
                    } else {
                        R.drawable.bg_chip_unselected_inset
                    },
                )
                setTextColor(color(if (selected) R.color.chip_selected_text else R.color.text_hi))
                isSelected = selected
                onSingleClick { viewModel.selectSeason(season.seasonNumber) }
            }
            binding.seasonChips.addView(chip)
        }
        episodeAdapter.submitList(state.episodes)
    }

    // ------------------------------------------------------------- hand-off

    private fun open(handoff: GetWatchOptionsUseCase.Handoff) {
        val launched = handoff.packageName?.let { pkg ->
            packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
                runCatching { startActivity(intent) }.isSuccess
            }
        } ?: false

        if (!launched) {
            // The app was not installed, or refused to start: the web destination always
            // resolves, so the viewer is never left on a dead tap.
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(handoff.url)))
            }.onFailure { showToast(R.string.error_generic) }
        }
    }

    private fun isInstalled(packageName: String): Boolean = runCatching {
        packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun playFree(detail: MediaDetail) {
        val source = detail.playable ?: return
        // The real player, not the trailer's. A free source is the one category this app
        // streams in full, so it gets the full playback stack rather than a bare surface.
        startActivity(Navigator.playSourceIntent(this, source))
    }

    /** A tapped face. Null for an AniList character, which has no TMDB person behind it. */
    private fun openPerson(member: CastMember) {
        PersonActivity.intentFor(this, member)?.let(::startActivity)
    }

    private fun openPerson(member: CrewMember) {
        PersonActivity.intentFor(this, member)?.let(::startActivity)
    }

    /**
     * Another title, from a recommendation or a collection. A new Activity rather than
     * reloading this one, so Back walks the trail the viewer actually followed.
     */
    private fun openTitle(item: MediaItem) {
        startActivity(intent(this, item.id, item.type))
    }

    private fun share(detail: MediaDetail) {
        val link = detail.watch.justWatchLink
            ?: "https://www.themoviedb.org/${tmdbPath()}/${detail.item.id}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${detail.item.title}\n$link")
        }
        startActivity(Intent.createChooser(intent, detail.item.title))
    }

    private fun tmdbPath(): String = if (mediaType == MediaType.Movie) "movie" else "tv"

    private fun displayRegion(code: String): String =
        Locale("", code).displayCountry.takeIf { it.isNotBlank() } ?: code

    private fun todayIso(): String = with(java.util.Calendar.getInstance()) {
        "%04d-%02d-%02d".format(
            get(java.util.Calendar.YEAR),
            get(java.util.Calendar.MONTH) + 1,
            get(java.util.Calendar.DAY_OF_MONTH),
        )
    }

    companion object {
        private const val EXTRA_ID = "media_id"
        private const val EXTRA_TYPE = "media_type"

        /** More than this and the chip row is a scroll test rather than a signal. */
        private const val MAX_STUDIOS = 6

        /** TMDB names about half of its collections "The X Collection" already. */
        private const val COLLECTION_WORD = "collection"

        fun intent(context: Context, id: Int, type: MediaType): Intent =
            Intent(context, MediaDetailActivity::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TYPE, type.name)
    }
}
