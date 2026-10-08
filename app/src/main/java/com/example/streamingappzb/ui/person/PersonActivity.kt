package com.example.streamingappzb.ui.person

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.streamingappzb.R
import com.example.streamingappzb.data.remote.tmdb.TmdbImages
import com.example.streamingappzb.databinding.ActivityPersonBinding
import com.example.streamingappzb.domain.model.CastMember
import com.example.streamingappzb.domain.model.Credit
import com.example.streamingappzb.domain.model.CrewMember
import com.example.streamingappzb.ui.base.BaseActivity
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.base.padTopForStatusBar
import com.example.streamingappzb.ui.media.MediaDetailActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * A person's page, reached by tapping a face on a title screen.
 *
 * It exists because "what else is this actor in" is one of the questions a browsing app is
 * actually asked, and TMDB answers it in the same call as the person's own detail — so the
 * whole screen is one round trip.
 */
class PersonActivity : BaseActivity<ActivityPersonBinding>() {

    private val personId by lazy { intent.getIntExtra(EXTRA_ID, 0) }

    private val viewModel: PersonViewModel by viewModel { parametersOf(personId) }

    private val filmsAdapter = CreditAdapter(::open)
    private val seriesAdapter = CreditAdapter(::open)

    override fun inflateBinding(inflater: LayoutInflater) = ActivityPersonBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.header.padTopForStatusBar()
        binding.btnBack.onSingleClick { finish() }

        binding.films.adapter = filmsAdapter
        binding.series.adapter = seriesAdapter

        lifecycleScope.launch {
            viewModel.state
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest(::render)
        }
    }

    private fun render(state: PersonUiState) {
        binding.loading.isVisible = state.loading
        binding.errorState.isVisible = state.failed
        val person = state.person ?: return

        binding.name.text = person.name
        binding.knownFor.text = person.knownFor.orEmpty()
        binding.knownFor.isVisible = !person.knownFor.isNullOrBlank()

        // Lifespan and birthplace, whichever TMDB has. Both missing hides the line rather
        // than leaving a stray separator.
        val lifespan = person.lifespan
        val place = person.placeOfBirth
        binding.born.text = when {
            lifespan != null && place != null -> getString(R.string.person_born, lifespan, place)
            lifespan != null -> lifespan
            else -> place.orEmpty()
        }
        binding.born.isVisible = binding.born.text.isNotBlank()

        binding.biography.text = person.biography
        binding.biography.isVisible = person.biography.isNotBlank()

        val photo = TmdbImages.profile(person.profilePath)
        if (photo == null) {
            binding.photo.setImageDrawable(null)
        } else {
            binding.photo.load(photo) { crossfade(true) }
        }
        binding.photo.contentDescription = getString(R.string.cd_person, person.name)

        filmsAdapter.submitList(state.films)
        binding.filmsHeading.isVisible = state.films.isNotEmpty()
        binding.films.isVisible = state.films.isNotEmpty()

        seriesAdapter.submitList(state.series)
        binding.seriesHeading.isVisible = state.series.isNotEmpty()
        binding.series.isVisible = state.series.isNotEmpty()
    }

    private fun open(credit: Credit) {
        viewModel.onOpened(credit.item)
        startActivity(MediaDetailActivity.intent(this, credit.item.id, credit.item.type))
    }

    companion object {
        private const val EXTRA_ID = "person_id"

        fun intent(context: Context, personId: Int): Intent =
            Intent(context, PersonActivity::class.java).putExtra(EXTRA_ID, personId)

        /**
         * Null when the person cannot be opened — AniList characters carry no TMDB id, so
         * their faces are shown but not tappable. See `AniListMappers.toDetail`.
         */
        fun intentFor(context: Context, member: CastMember): Intent? =
            member.id.takeIf { it > 0 }?.let { intent(context, it) }

        fun intentFor(context: Context, member: CrewMember): Intent? =
            member.id.takeIf { it > 0 }?.let { intent(context, it) }
    }
}
