package com.example.streamingappzb.data.remote.tmdb

import com.example.streamingappzb.data.remote.tmdb.dto.TmdbCastDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbCollectionDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbCompanyDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbCrewDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbEpisodeDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbGenreListDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbImagesDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbMovieDetailDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbNetworkDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbPersonCreditDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbPersonDetailDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbRegionProvidersDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbSeasonSummaryDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbStillImagesDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbSummaryDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbTvDetailDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbVideoDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbWatchProvidersDto
import com.example.streamingappzb.domain.model.CastMember
import com.example.streamingappzb.domain.model.MovieCollection
import com.example.streamingappzb.domain.model.Credit
import com.example.streamingappzb.domain.model.CrewMember
import com.example.streamingappzb.domain.model.MediaGenre
import com.example.streamingappzb.domain.model.GenreScope
import com.example.streamingappzb.domain.model.MediaDetail
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.OfferType
import com.example.streamingappzb.domain.model.PersonProfile
import com.example.streamingappzb.domain.model.SeasonSummary
import com.example.streamingappzb.domain.model.EpisodeSummary
import com.example.streamingappzb.domain.model.Studio
import com.example.streamingappzb.domain.model.TitleImages
import com.example.streamingappzb.domain.model.Trailer
import com.example.streamingappzb.domain.model.WatchOffer
import com.example.streamingappzb.domain.model.WatchOptions

/**
 * Resolves `genre_ids` to names.
 *
 * Row endpoints return ids only. This defaults to the built-in list so existing call sites
 * and tests keep working without threading a repository through; the real one is TMDB's own
 * cached vocabulary, passed in by
 * [com.example.streamingappzb.data.media.MediaRepositoryImpl].
 */
typealias GenreNamer = (List<Int>) -> List<String>

/** TMDB DTOs to domain models. Anything unusable is dropped rather than faked. */
object TmdbMappers {

    /**
     * @param fallbackType used by the typed endpoints, which do not set `media_type`.
     *   Returns null for a person result, which `/search/multi` also returns.
     */
    fun toItem(
        dto: TmdbSummaryDto,
        fallbackType: MediaType?,
        genreNames: GenreNamer = DEFAULT_GENRE_NAMER,
    ): MediaItem? {
        val type = MediaType.fromTmdb(dto.mediaType) ?: fallbackType ?: return null
        val title = dto.displayTitle?.takeIf { it.isNotBlank() } ?: return null
        if (dto.id <= 0) return null
        return MediaItem(
            id = dto.id,
            type = type,
            title = title,
            overview = dto.overview.orEmpty(),
            posterPath = dto.posterPath,
            backdropPath = dto.backdropPath,
            year = yearOf(dto.displayDate),
            rating = dto.voteAverage?.takeIf { dto.voteCount != null && dto.voteCount > 0 },
            genres = genreNames(dto.genreIds),
            releaseDate = dto.displayDate?.takeIf { it.isNotBlank() },
        )
    }

    fun toItems(
        dtos: List<TmdbSummaryDto>,
        fallbackType: MediaType?,
        genreNames: GenreNamer = DEFAULT_GENRE_NAMER,
    ): List<MediaItem> =
        dtos.mapNotNull { toItem(it, fallbackType, genreNames) }.distinctBy { it.key }

    fun toDetail(
        dto: TmdbMovieDetailDto,
        region: String,
        genreNames: GenreNamer = DEFAULT_GENRE_NAMER,
    ): MediaDetail? {
        val title = dto.title?.takeIf { it.isNotBlank() } ?: return null
        val item = MediaItem(
            id = dto.id,
            type = MediaType.Movie,
            title = title,
            overview = dto.overview.orEmpty(),
            posterPath = dto.posterPath,
            backdropPath = dto.backdropPath,
            year = yearOf(dto.releaseDate),
            rating = dto.voteAverage?.takeIf { it > 0 },
            genres = dto.genres.map { it.name }.filter { it.isNotBlank() },
            releaseDate = dto.releaseDate?.takeIf { it.isNotBlank() },
        )
        return MediaDetail(
            item = item,
            tagline = dto.tagline?.takeIf { it.isNotBlank() },
            runtimeMinutes = dto.runtime?.takeIf { it > 0 },
            seasonCount = null,
            episodeCount = null,
            status = dto.status?.takeIf { it.isNotBlank() },
            cast = toCast(dto.credits?.cast.orEmpty()),
            trailers = toTrailers(dto.videos?.results.orEmpty()),
            seasons = emptyList(),
            similar = toItems(dto.similar?.results.orEmpty(), MediaType.Movie, genreNames),
            watch = toWatchOptions(dto.watchProviders, region),
            recommendations =
                toItems(dto.recommendations?.results.orEmpty(), MediaType.Movie, genreNames),
            crew = toCrew(dto.credits?.crew.orEmpty()),
            studios = dto.productionCompanies.map { toStudio(it) },
            collectionId = dto.belongsToCollection?.id?.takeIf { it > 0 },
            collectionName = dto.belongsToCollection?.name?.takeIf { it.isNotBlank() },
            images = toImages(dto.images),
            imdbId = dto.externalIds?.imdbId?.takeIf { it.isNotBlank() },
            originalTitle = dto.originalTitle?.takeIf { it.isNotBlank() },
            homepage = dto.homepage?.takeIf { it.isNotBlank() },
        )
    }

    fun toDetail(
        dto: TmdbTvDetailDto,
        region: String,
        genreNames: GenreNamer = DEFAULT_GENRE_NAMER,
    ): MediaDetail? {
        val title = dto.name?.takeIf { it.isNotBlank() } ?: return null
        val item = MediaItem(
            id = dto.id,
            type = MediaType.Tv,
            title = title,
            overview = dto.overview.orEmpty(),
            posterPath = dto.posterPath,
            backdropPath = dto.backdropPath,
            year = yearOf(dto.firstAirDate),
            rating = dto.voteAverage?.takeIf { it > 0 },
            genres = dto.genres.map { it.name }.filter { it.isNotBlank() },
            releaseDate = dto.firstAirDate?.takeIf { it.isNotBlank() },
        )
        return MediaDetail(
            item = item,
            tagline = dto.tagline?.takeIf { it.isNotBlank() },
            runtimeMinutes = dto.episodeRunTime.firstOrNull { it > 0 },
            seasonCount = dto.numberOfSeasons?.takeIf { it > 0 },
            episodeCount = dto.numberOfEpisodes?.takeIf { it > 0 },
            status = dto.status?.takeIf { it.isNotBlank() },
            cast = toCast(dto.credits?.cast.orEmpty()),
            trailers = toTrailers(dto.videos?.results.orEmpty()),
            // Season 0 is "Specials" on TMDB; it is not part of the run.
            seasons = dto.seasons.filter { it.seasonNumber > 0 }.map(::toSeason),
            similar = toItems(dto.similar?.results.orEmpty(), MediaType.Tv, genreNames),
            watch = toWatchOptions(dto.watchProviders, region),
            recommendations =
                toItems(dto.recommendations?.results.orEmpty(), MediaType.Tv, genreNames),
            crew = toCrew(dto.credits?.crew.orEmpty()),
            studios = dto.networks.map { toStudio(it) },
            images = toImages(dto.images),
            imdbId = dto.externalIds?.imdbId?.takeIf { it.isNotBlank() },
            originalTitle = dto.originalName?.takeIf { it.isNotBlank() },
            homepage = dto.homepage?.takeIf { it.isNotBlank() },
        )
    }

    private fun toSeason(dto: TmdbSeasonSummaryDto) = SeasonSummary(
        seasonNumber = dto.seasonNumber,
        name = dto.name?.takeIf { it.isNotBlank() } ?: "Season ${dto.seasonNumber}",
        episodeCount = dto.episodeCount,
        posterPath = dto.posterPath,
        airDate = dto.airDate?.takeIf { it.isNotBlank() },
    )

    fun toEpisode(dto: TmdbEpisodeDto) = EpisodeSummary(
        seasonNumber = dto.seasonNumber,
        episodeNumber = dto.episodeNumber,
        name = dto.name?.takeIf { it.isNotBlank() } ?: "Episode ${dto.episodeNumber}",
        overview = dto.overview.orEmpty(),
        stillPath = dto.stillPath,
        airDate = dto.airDate?.takeIf { it.isNotBlank() },
        runtimeMinutes = dto.runtime?.takeIf { it > 0 },
    )

    private fun toCast(dtos: List<TmdbCastDto>): List<CastMember> = dtos
        .sortedBy { it.order }
        .take(MAX_CAST)
        .filter { it.name.isNotBlank() }
        .map {
            CastMember(
                id = it.id,
                name = it.name,
                character = it.character?.takeIf { c -> c.isNotBlank() },
                profilePath = it.profilePath,
            )
        }

    /**
     * Crew, narrowed to the jobs a viewer actually looks for.
     *
     * A film's full crew runs to hundreds of entries — gaffers, caterers, second assistant
     * editors. Listing all of them is not "complete", it is unreadable, so this keeps the
     * handful of credits people search by and drops the rest.
     */
    private fun toCrew(dtos: List<TmdbCrewDto>): List<CrewMember> = dtos
        .filter { it.name.isNotBlank() && it.job?.isNotBlank() == true }
        .filter { it.job in BILLED_JOBS }
        .distinctBy { it.id to it.job }
        .sortedBy { BILLED_JOBS.indexOf(it.job) }
        .take(MAX_CREW)
        .map {
            CrewMember(
                id = it.id,
                name = it.name,
                job = it.job!!,
                profilePath = it.profilePath,
            )
        }

    /** A production company. [isNetwork] is false — see [Studio] for why one type serves both. */
    fun toStudio(dto: TmdbCompanyDto) = Studio(
        id = dto.id,
        name = dto.name,
        logoPath = dto.logoPath,
        originCountry = dto.originCountry?.takeIf { it.isNotBlank() },
        isNetwork = false,
    )

    /** A broadcaster. Same shape as a company, different endpoint and different browse axis. */
    fun toStudio(dto: TmdbNetworkDto) = Studio(
        id = dto.id,
        name = dto.name,
        logoPath = dto.logoPath,
        originCountry = dto.originCountry?.takeIf { it.isNotBlank() },
        isNetwork = true,
    )

    /**
     * Picks the images worth keeping out of what is often a hundred near-duplicates.
     *
     * Backdrops are filtered to the textless ones (`iso_639_1` null) because those are the
     * only ones a title can safely be drawn over; a backdrop with the title already burned
     * in gives a hero two titles. Within that, TMDB's own vote order is a good proxy for
     * quality, and the first is reliably the best.
     */
    fun toImages(dto: TmdbImagesDto?): TitleImages {
        if (dto == null) return TitleImages.EMPTY
        return TitleImages(
            // PNG over SVG: Coil renders SVG only with an extra decoder, and TMDB serves
            // the same logo in both.
            logoPath = dto.logos
                .filter { it.filePath.isNotBlank() && !it.filePath.endsWith(".svg", true) }
                .maxByOrNull { it.voteAverage ?: 0.0 }
                ?.filePath,
            backdrops = dto.backdrops
                .filter { it.language == null && it.filePath.isNotBlank() }
                .sortedByDescending { it.voteAverage ?: 0.0 }
                .take(MAX_IMAGES)
                .map { it.filePath },
            posters = dto.posters
                .filter { it.filePath.isNotBlank() }
                .sortedByDescending { it.voteAverage ?: 0.0 }
                .take(MAX_IMAGES)
                .map { it.filePath },
        )
    }

    /** Episode stills, best first. Falls back to the season listing's own `still_path`. */
    fun toStills(dto: TmdbStillImagesDto?): List<String> = dto?.stills.orEmpty()
        .filter { it.filePath.isNotBlank() }
        .sortedByDescending { it.voteAverage ?: 0.0 }
        .take(MAX_IMAGES)
        .map { it.filePath }

    fun toCollection(
        dto: TmdbCollectionDto,
        genreNames: GenreNamer = DEFAULT_GENRE_NAMER,
    ): MovieCollection? {
        val name = dto.name?.takeIf { it.isNotBlank() } ?: return null
        return MovieCollection(
            id = dto.id,
            name = name,
            overview = dto.overview.orEmpty(),
            posterPath = dto.posterPath,
            backdropPath = dto.backdropPath,
            // A collection's parts are always films, and TMDB does not set media_type here.
            // Release order, not the API's order, which is effectively arbitrary.
            parts = toItems(dto.parts, MediaType.Movie, genreNames)
                .sortedBy { it.releaseDate ?: "9999" },
        )
    }

    fun toPerson(
        dto: TmdbPersonDetailDto,
        genreNames: GenreNamer = DEFAULT_GENRE_NAMER,
    ): PersonProfile? {
        val name = dto.name.takeIf { it.isNotBlank() } ?: return null
        return PersonProfile(
            id = dto.id,
            name = name,
            biography = dto.biography.orEmpty(),
            knownFor = dto.knownForDepartment?.takeIf { it.isNotBlank() },
            profilePath = dto.profilePath
                ?: dto.images?.profiles?.firstOrNull()?.filePath,
            birthday = dto.birthday?.takeIf { it.isNotBlank() },
            deathday = dto.deathday?.takeIf { it.isNotBlank() },
            placeOfBirth = dto.placeOfBirth?.takeIf { it.isNotBlank() },
            asCast = toCredits(dto.movieCredits?.cast, dto.tvCredits?.cast, genreNames),
            asCrew = toCredits(dto.movieCredits?.crew, dto.tvCredits?.crew, genreNames),
        )
    }

    /**
     * A filmography, newest first.
     *
     * The two typed endpoints are merged here rather than using `/combined_credits`,
     * because only the typed ones tell us reliably which space an id is in — and an id in
     * the wrong space opens the wrong title.
     */
    private fun toCredits(
        movies: List<TmdbPersonCreditDto>?,
        series: List<TmdbPersonCreditDto>?,
        genreNames: GenreNamer,
    ): List<Credit> {
        val fromMovies = movies.orEmpty().mapNotNull { it.toCredit(MediaType.Movie, genreNames) }
        val fromSeries = series.orEmpty().mapNotNull { it.toCredit(MediaType.Tv, genreNames) }
        return (fromMovies + fromSeries)
            // The same title can appear twice when someone had two jobs on it.
            .distinctBy { it.item.key }
            // Undated entries are announced-but-unscheduled work; they belong at the top.
            .sortedByDescending { it.item.releaseDate ?: "9999" }
            .take(MAX_CREDITS)
    }

    private fun TmdbPersonCreditDto.toCredit(type: MediaType, genreNames: GenreNamer): Credit? {
        val item = toItem(toSummary(), type, genreNames) ?: return null
        return Credit(
            item = item,
            role = character?.takeIf { it.isNotBlank() } ?: job?.takeIf { it.isNotBlank() },
        )
    }

    fun toGenres(dto: TmdbGenreListDto, scope: GenreScope): List<MediaGenre> = dto.genres
        .filter { it.id > 0 && it.name.isNotBlank() }
        .map { MediaGenre(id = it.id, name = it.name, scope = scope) }

    /**
     * YouTube only, and official first. Other sites appear occasionally and we have no
     * player for them, so offering one would be a dead button.
     */
    private fun toTrailers(dtos: List<TmdbVideoDto>): List<Trailer> = dtos
        .filter { it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank() }
        .sortedWith(compareByDescending<TmdbVideoDto> { it.official }.thenBy { rank(it.type) })
        .map { Trailer(key = it.key, name = it.name, official = it.official, type = it.type) }

    private fun rank(type: String): Int = when (type.lowercase()) {
        "trailer" -> 0
        "teaser" -> 1
        "clip" -> 2
        else -> 3
    }

    /**
     * Provider data for one region.
     *
     * Falls back to US when the viewer's own region has no entry, because "nobody carries
     * this" and "TMDB has no data for your country" look identical to a viewer and only
     * one of them is true. The returned [WatchOptions.region] says which was used.
     */
    fun toWatchOptions(dto: TmdbWatchProvidersDto?, region: String): WatchOptions {
        val results = dto?.results.orEmpty()
        val chosen = results[region] ?: results[FALLBACK_REGION] ?: return WatchOptions.empty(region)
        val usedRegion = if (results.containsKey(region)) region else FALLBACK_REGION
        return WatchOptions(
            region = usedRegion,
            offers = offersOf(chosen),
            justWatchLink = chosen.link?.takeIf { it.isNotBlank() },
        )
    }

    private fun offersOf(dto: TmdbRegionProvidersDto): List<WatchOffer> = buildList {
        addAll(dto.free.map { it.toOffer(OfferType.Free) })
        addAll(dto.ads.map { it.toOffer(OfferType.Ads) })
        addAll(dto.flatrate.map { it.toOffer(OfferType.Subscription) })
        addAll(dto.rent.map { it.toOffer(OfferType.Rent) })
        addAll(dto.buy.map { it.toOffer(OfferType.Buy) })
    }.filter { it.providerName.isNotBlank() }

    private fun com.example.streamingappzb.data.remote.tmdb.dto.TmdbProviderDto.toOffer(
        type: OfferType,
    ) = WatchOffer(
        providerId = providerId,
        providerName = providerName,
        logoPath = logoPath,
        offerType = type,
    )

    /** TMDB dates are `yyyy-MM-dd`; anything shorter is unusable rather than parsed. */
    fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()?.takeIf { it > 1800 }

    /** The built-in genre list — see [GenreNamer] for when the live one is used instead. */
    private val DEFAULT_GENRE_NAMER: GenreNamer = { ids -> TmdbGenres.names(ids) }

    private const val MAX_CAST = 12
    private const val MAX_CREW = 6
    private const val MAX_IMAGES = 10

    /** A filmography long enough to be representative without becoming a scroll test. */
    private const val MAX_CREDITS = 40

    private const val FALLBACK_REGION = "US"

    /**
     * The crew jobs worth billing, in the order a viewer scans for them. Anything not on
     * this list is dropped — see [toCrew] for why "everything" is the wrong answer here.
     */
    private val BILLED_JOBS = listOf(
        "Director",
        "Screenplay",
        "Writer",
        "Story",
        "Creator",
        "Novel",
        "Composer",
        "Original Music Composer",
        "Director of Photography",
    )
}
