package com.example.streamingappzb.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.streamingappzb.R
import com.example.streamingappzb.data.session.SessionState
import com.example.streamingappzb.databinding.ActivityMainBinding
import com.example.streamingappzb.ui.base.BaseActivity
import com.example.streamingappzb.ui.upcoming.UpcomingFragment
import com.example.streamingappzb.ui.discover.DiscoverFragment
import com.example.streamingappzb.ui.mylist.MediaListFragment
import com.example.streamingappzb.ui.search.MediaSearchFragment
import com.example.streamingappzb.ui.widget.MhTab
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Hosts the four tab routes (PRD §4).
 *
 * Title detail and the Player are separate Activities, which is what makes "the bottom nav
 * shows on the four tab routes only" structural rather than a flag every screen has to
 * remember.
 *
 * Tabs are added once and then shown/hidden rather than replaced, so a poster row's scroll
 * position and a search query survive switching away and back.
 */
class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val session: SessionState by inject()

    private var current: MhTab = MhTab.Home

    override fun inflateBinding(inflater: LayoutInflater): ActivityMainBinding =
        ActivityMainBinding.inflate(inflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must precede setContentView, which BaseActivity does in its own onCreate.
        installSplashScreen()
        super.onCreate(savedInstanceState)
    }

    override fun onViewReady(savedInstanceState: Bundle?) {
        current = savedInstanceState?.getString(KEY_TAB)
            ?.let { route -> MhTab.entries.firstOrNull { it.route == route } }
            // Title detail sends the viewer here when a download is already in flight.
            ?: if (intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true) {
                MhTab.Downloads
            } else {
                MhTab.Home
            }

        binding.bottomNav.onTabSelected = { tab -> show(tab) }
        binding.bottomNav.selected = current
        show(current)

        if (savedInstanceState == null) playBrandReveal()

        lifecycleScope.launch {
            session.activeDownloadCount
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { binding.bottomNav.setDownloadCount(it) }
        }
    }

    /**
     * The ember brand reveal, over the first frame.
     *
     * An overlay that fades itself out rather than a blocking splash: Home is already
     * behind it and rendering, so this cannot cost anything against the cold-start budget
     * (PRD §8 — interactive Home in under 2 s on a 2 GB device).
     */
    private fun playBrandReveal() {
        binding.splashOverlay.isVisible = true
        binding.splashAnimation.playAnimation()
        binding.splashOverlay.animate()
            .alpha(0f)
            .setStartDelay(REVEAL_HOLD_MS)
            .setDuration(REVEAL_FADE_MS)
            .withEndAction {
                binding.splashOverlay.isVisible = false
                binding.splashAnimation.cancelAnimation()
            }
            .start()
    }

    /** Lets a tab screen jump to another one — Home's app-bar Search and Downloads icons. */
    fun selectTab(tab: MhTab) {
        binding.bottomNav.selected = tab
        show(tab)
    }

    private fun show(tab: MhTab) {
        current = tab
        val fm = supportFragmentManager
        fm.commitNow {
            setReorderingAllowed(true)
            for (candidate in MhTab.entries) {
                val existing = fm.findFragmentByTag(candidate.route)
                when {
                    candidate != tab -> existing?.let { hide(it) }
                    existing == null -> add(R.id.tabContainer, newFragment(candidate), candidate.route)
                    else -> show(existing)
                }
            }
        }
    }

    private fun newFragment(tab: MhTab): Fragment = when (tab) {
        // Discover replaces the sample-catalogue Home: same design, real data.
        MhTab.Home -> DiscoverFragment()
        MhTab.Search -> MediaSearchFragment()
        // Downloads made sense for our own sample video; a licensed catalogue has nothing
        // we may store, so the slot answers "what is coming" instead.
        MhTab.Downloads -> UpcomingFragment()
        MhTab.MyList -> MediaListFragment()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, current.route)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)) selectTab(MhTab.Downloads)
    }

    companion object {
        /** Opens straight onto the Downloads tab. */
        const val EXTRA_OPEN_DOWNLOADS = "openDownloads"

        private const val KEY_TAB = "tab"

        /** Long enough to read the wordmark, short enough not to be in the way. */
        private const val REVEAL_HOLD_MS = 700L
        private const val REVEAL_FADE_MS = 280L
    }
}
