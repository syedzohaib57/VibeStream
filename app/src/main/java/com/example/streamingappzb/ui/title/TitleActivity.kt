package com.example.streamingappzb.ui.title

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.ActivityTitleBinding
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.repository.SettingsRepository
import com.example.streamingappzb.domain.usecase.TitleScreenData
import com.example.streamingappzb.ui.base.BaseActivity
import com.example.streamingappzb.ui.base.NotificationPermission
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.base.padTopForStatusBar
import com.example.streamingappzb.ui.main.MainActivity
import com.example.streamingappzb.ui.nav.Navigator
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * Title detail (PRD §6.2).
 *
 * A separate Activity from the tab host, which is what keeps the bottom nav off this
 * screen without any per-screen flag. Also the target for `moviehub://title/{id}` and the
 * `https://` Share link (PRD §4).
 */
class TitleActivity : BaseActivity<ActivityTitleBinding>(), DownloadSheetFragment.Host {

    private val titleId: Int by lazy { resolveTitleId() }

    private val viewModel: TitleViewModel by viewModel { parametersOf(titleId) }

    private val catalog: CatalogRepository by inject()
    private val settings: SettingsRepository by inject()

    private lateinit var adapter: TitleAdapter

    /** Set when a download is waiting on the notification prompt's answer. */
    private var pendingDownload: (() -> Unit)? = null

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // The answer does not gate the download — only whether progress is visible.
        pendingDownload?.invoke()
        pendingDownload = null
    }

    override val downloadSheetState: StateFlow<DownloadSheetState?> get() = viewModel.sheet

    override fun inflateBinding(inflater: LayoutInflater): ActivityTitleBinding =
        ActivityTitleBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        if (titleId <= 0) {
            finish()
            return
        }

        binding.actions.padTopForStatusBar()
        binding.btnBack.onSingleClick { finish() }
        binding.btnShare.onSingleClick { viewModel.onShare() }

        adapter = TitleAdapter(
            callbacks = TitleCallbacks(
                onPrimaryPlay = { playPrimary() },
                onToggleMyList = viewModel::toggleMyList,
                onDownloadTitle = {
                    val film = viewModel.state.value.data?.detail?.title?.isFilm == true
                    // A film is one episode; a series offers the whole season.
                    viewModel.openDownloadSheet(if (film) 1 else null)
                },
                onPlayEpisode = { episode, position ->
                    Navigator.play(this, titleId, episode, position)
                },
                onEpisodeGlyph = viewModel::onEpisodeDownloadGlyph,
            ),
            estimateRung = catalog.rung(Rung.DEFAULT_DOWNLOAD),
        )

        binding.content.layoutManager = LinearLayoutManager(this)
        binding.content.adapter = adapter

        observe()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the player: progress and downloads may both have moved on.
        viewModel.refresh()
    }

    private fun observe() {
        lifecycleScope.launch {
            viewModel.state
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { state -> state.data?.let { adapter.submitList(it.toItems()) } }
        }

        lifecycleScope.launch {
            viewModel.sheet
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { sheet ->
                    val existing = supportFragmentManager.findFragmentByTag(DownloadSheetFragment.TAG)
                    if (sheet != null && existing == null) {
                        DownloadSheetFragment().show(supportFragmentManager, DownloadSheetFragment.TAG)
                    }
                }
        }

        lifecycleScope.launch {
            viewModel.messages
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest(::handle)
        }
    }

    private fun handle(message: TitleViewModel.TitleMessage) {
        when (message) {
            is TitleViewModel.TitleMessage.Toast -> showToast(message.messageRes)

            is TitleViewModel.TitleMessage.ToastFormatted ->
                showToast(getString(message.messageRes, message.arg))

            is TitleViewModel.TitleMessage.ToastPlural ->
                showToast(resources.getQuantityString(message.pluralRes, message.count, message.count))

            is TitleViewModel.TitleMessage.Play ->
                Navigator.play(this, titleId, message.episode, message.positionSeconds)

            is TitleViewModel.TitleMessage.Share -> share(message.data)

            TitleViewModel.TitleMessage.OpenDownloads -> openDownloadsTab()
        }
    }

    private fun playPrimary() {
        val data = viewModel.state.value.data ?: return
        val progress = data.progress
        Navigator.play(
            context = this,
            titleId = titleId,
            episode = progress?.episode ?: 1,
            positionSeconds = progress?.positionSeconds ?: 0,
        )
    }

    /** Share produces a link that resolves back to this screen, or to the exact moment. */
    private fun share(data: TitleScreenData) {
        val title = data.detail.title
        val progress = data.progress
        val url = if (progress != null && !title.isFilm) {
            Navigator.shareUrl(title.id, progress.episode, progress.positionSeconds)
        } else {
            Navigator.shareUrl(title.id)
        }
        val body = if (progress != null && !title.isFilm) {
            getString(R.string.share_body_episode, title.title, progress.episode, url)
        } else {
            getString(R.string.share_body_film, title.title, url)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, title.title))
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser, title.title)))
    }

    private fun openDownloadsTab() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true),
        )
    }

    // ---------------------------------------------------------------- sheet host

    override fun onRungSelected(rungId: String) = viewModel.selectRung(rungId)

    override fun onConfirmDownload(useMobileDataNow: Boolean) {
        val enqueue = { viewModel.confirmDownload(useMobileDataNow) }
        // Ask about notifications once, right before the first download would post one.
        if (NotificationPermission.requestOnce(this, settings, notificationLauncher)) {
            pendingDownload = enqueue
        } else {
            enqueue()
        }
    }

    override fun onDownloadSheetDismissed() = viewModel.dismissSheet()

    // ---------------------------------------------------------------- helpers

    private fun resolveTitleId(): Int {
        intent?.getIntExtra(Navigator.EXTRA_TITLE_ID, -1)
            ?.takeIf { it > 0 }
            ?.let { return it }
        return when (val destination = Navigator.parse(intent?.data)) {
            is Navigator.Destination.Title -> destination.titleId
            is Navigator.Destination.Play -> destination.titleId
            null -> -1
        }
    }

    private fun TitleScreenData.toItems(): List<TitleItem> = buildList {
        add(TitleItem.Header(detail.title, progress, inMyList))
        if (!detail.title.isFilm) {
            add(TitleItem.EpisodesHeader)
            for (episode in detail.episodes) {
                add(
                    TitleItem.EpisodeRow(
                        title = detail.title,
                        episode = episode,
                        download = downloadsByEpisode[episode.number],
                        watchedFraction = watchedFraction(episode.number),
                        resumePositionSeconds = if (progress?.episode == episode.number) {
                            progress.positionSeconds
                        } else {
                            0
                        },
                    ),
                )
            }
        }
    }
}
