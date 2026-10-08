package com.example.streamingappzb.di

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.streamingappzb.data.player.PlaybackController
import com.example.streamingappzb.ui.downloads.DownloadsViewModel
import com.example.streamingappzb.ui.home.HomeViewModel
import com.example.streamingappzb.ui.mylist.MyListViewModel
import com.example.streamingappzb.ui.player.PlayerArgs
import com.example.streamingappzb.ui.player.PlayerViewModel
import com.example.streamingappzb.ui.search.SearchViewModel
import com.example.streamingappzb.ui.title.TitleViewModel
import org.koin.android.ext.koin.androidContext
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.ui.browse.BrowseViewModel
import com.example.streamingappzb.ui.discover.DiscoverViewModel
import com.example.streamingappzb.ui.media.MediaDetailViewModel
import com.example.streamingappzb.ui.person.PersonViewModel
import com.example.streamingappzb.ui.search.MediaSearchViewModel
import com.example.streamingappzb.ui.upcoming.UpcomingViewModel
import com.example.streamingappzb.ui.mylist.MediaListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * ViewModels. Kept separate from [dataModule] so screen wiring and infrastructure wiring
 * stay independently readable.
 *
 * [TitleViewModel] and [PlayerViewModel] take route arguments, so they are resolved with
 * `parametersOf(...)` from their Activities rather than reading a SavedStateHandle — the
 * arguments come from an Intent or a deep link, and the Activity has already normalised
 * both into the same shape.
 */
@OptIn(UnstableApi::class)
val viewModelModule = module {

    // Real catalogue (TMDB / AniList).
    viewModel { DiscoverViewModel(get(), get(), get(), get()) }

    viewModel { (id: Int, type: MediaType) ->
        MediaDetailViewModel(
            mediaId = id,
            mediaType = type,
            media = get(),
            freeSources = get(),
            myList = get(),
            resolvePlayback = get(),
            watchOptions = get(),
            session = get(),
            analytics = get(),
        )
    }

    /**
     * Browse takes four discriminators plus a heading. Destructured rather than
     * `parameters.get()` per field: three of the five are Int?, so type-based lookup would
     * hand back the same value three times.
     */
    viewModel { (genreId: Int?, companyId: Int?, networkId: Int?, type: MediaType, heading: String) ->
        BrowseViewModel(
            genreId = genreId,
            companyId = companyId,
            networkId = networkId,
            type = type,
            heading = heading,
            browse = get(),
            myList = get(),
            analytics = get(),
        )
    }

    viewModel { (personId: Int) -> PersonViewModel(personId, get(), get()) }

    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }

    viewModel { SearchViewModel(get(), get()) }

    viewModel { MediaSearchViewModel(get(), get(), get()) }

    viewModel { UpcomingViewModel(get(), get()) }

    viewModel { MediaListViewModel(get(), get(), get()) }

    viewModel { MyListViewModel(get(), get(), get()) }

    viewModel { DownloadsViewModel(get(), get(), get(), get()) }

    viewModel { (titleId: Int) ->
        TitleViewModel(
            titleId = titleId,
            getTitleDetail = get(),
            estimateSize = get(),
            enqueueDownload = get(),
            myList = get(),
            downloads = get(),
            session = get(),
            analytics = get(),
        )
    }

    // A fresh controller per player: it owns an ExoPlayer, which must not outlive the
    // screen or be shared between two of them.
    factory { PlaybackController(androidContext(), get(), get()) }

    // One typed argument rather than three loose Ints. The player has two modes carrying
    // different fields, and since `parametersOf` resolves by type, a nullable source
    // alongside them would be a trap — see [PlayerArgs].
    viewModel { (args: PlayerArgs) ->
        PlayerViewModel(
            args = args,
            controller = get(),
            catalog = get(),
            downloads = get(),
            saveProgress = get(),
            resolveQuality = get(),
            settings = get(),
            session = get(),
            analytics = get(),
        )
    }
}
