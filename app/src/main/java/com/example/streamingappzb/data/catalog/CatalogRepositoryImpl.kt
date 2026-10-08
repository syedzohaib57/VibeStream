package com.example.streamingappzb.data.catalog

import android.content.res.AssetManager
import com.example.streamingappzb.data.catalog.dto.CatalogDto
import com.example.streamingappzb.data.db.GenreDao
import com.example.streamingappzb.data.db.HomeRowDao
import com.example.streamingappzb.data.db.ProgressDao
import com.example.streamingappzb.data.db.ProgressEntity
import com.example.streamingappzb.data.db.TitleDao
import com.example.streamingappzb.domain.model.AdCreative
import com.example.streamingappzb.domain.model.AdSchedule
import com.example.streamingappzb.domain.model.AdSlot
import com.example.streamingappzb.domain.model.AdSpot
import com.example.streamingappzb.domain.model.Genre
import com.example.streamingappzb.domain.model.HomeRow
import com.example.streamingappzb.domain.model.Kind
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.model.Stream
import com.example.streamingappzb.domain.model.StreamType
import com.example.streamingappzb.domain.model.Title
import com.example.streamingappzb.domain.model.TitleDetail
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.search.FuzzyMatcher
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source

/** Config the client needs but that is not per-title. */
data class CatalogConfig(
    val rungs: List<Rung>,
    val heroByKind: Map<String, Int>,
    val ads: AdConfig,
    val episodeNames: List<String>,
    /** The public Widevine test asset, debug builds only. */
    val drmTestVector: Stream?,
    val drmTestVectorName: String?,
)

data class AdConfig(
    val preRollSeconds: Int,
    val skipAfterSeconds: Int,
    val creatives: List<AdCreative>,
)

/**
 * Room-first catalogue.
 *
 * Reads never touch the network, which is what makes Home, Search and My List work
 * offline (PRD §8). [api] is wired but unused while `BuildConfig.API_BASE_URL` is empty;
 * a remote payload parses into the same DTOs and lands in the same tables.
 */
class CatalogRepositoryImpl(
    private val assets: AssetManager,
    private val moshi: Moshi,
    private val titleDao: TitleDao,
    private val homeRowDao: HomeRowDao,
    private val genreDao: GenreDao,
    private val progressDao: ProgressDao,
    private val api: CatalogApi?,
    private val io: CoroutineDispatcher,
) : CatalogRepository {

    private val configLock = Mutex()
    private var cachedConfig: CatalogConfig? = null
    private var cachedEpisodeFactory: EpisodeFactory? = null

    @Volatile
    private var cachedCatalog: CatalogDto? = null

    override suspend fun ensureSeeded() = withContext(io) {
        val config = config()
        if (titleDao.count() > 0) return@withContext

        val dto = readCatalog()
        titleDao.insertAll(
            dto.titles.mapIndexed { i, t -> CatalogMapper.toEntity(t, dto.streams, i) },
        )
        homeRowDao.insertAll(dto.rows.mapIndexed { i, r -> CatalogMapper.toEntity(r, i) })
        genreDao.insertAll(dto.genres.mapIndexed { i, g -> CatalogMapper.toEntity(g, i) })

        // The design ships a preset Continue watching row so the feature is visible on a
        // fresh install rather than only after something has been watched. Seeded once,
        // on the same first run that seeds the catalogue, so clearing a card keeps it
        // cleared.
        if (progressDao.all().isEmpty()) {
            val now = System.currentTimeMillis()
            dto.continueWatching.forEachIndexed { i, c ->
                progressDao.put(
                    ProgressEntity(
                        titleId = c.titleId,
                        episode = c.ep,
                        positionSeconds = c.posSec,
                        // Descending so the seeded order survives the "most recent first"
                        // sort the row renders in.
                        updatedAt = now - i * 1_000L,
                    ),
                )
            }
        }
        check(config.rungs.isNotEmpty()) { "catalogue has no bitrate ladder" }
    }

    override suspend fun titles(): List<Title> = withContext(io) {
        titleDao.all().map(CatalogMapper::toDomain)
    }

    override fun observeTitles(): Flow<List<Title>> =
        titleDao.observeAll().map { list -> list.map(CatalogMapper::toDomain) }

    override suspend fun title(id: Int): Title? = withContext(io) {
        titleDao.byId(id)?.let(CatalogMapper::toDomain)
    }

    override suspend fun titleDetail(id: Int): TitleDetail? = withContext(io) {
        val title = title(id) ?: return@withContext null
        TitleDetail(title = title, episodes = episodeFactory().episodesFor(title))
    }

    override suspend fun homeRows(): List<HomeRow> = withContext(io) {
        val byId = titleDao.all().associate { it.id to CatalogMapper.toDomain(it) }
        homeRowDao.all().map { row ->
            HomeRow(
                key = row.key,
                fallbackTitle = row.title,
                titles = CatalogMapper.rowTitleIds(row).mapNotNull(byId::get),
            )
        }
    }

    override suspend fun heroTitle(kind: Kind?): Title? {
        val key = kind?.name ?: KEY_ALL
        val config = config()
        val id = config.heroByKind[key]
            ?: config.heroByKind[KEY_ALL]
            // Nothing configured: fall back to the first title of the right kind rather
            // than showing no hero at all.
            ?: return titles().firstOrNull { kind == null || it.kind == kind }
        return title(id) ?: titles().firstOrNull { kind == null || it.kind == kind }
    }

    override suspend fun genres(): List<Genre> = withContext(io) {
        genreDao.all().map(CatalogMapper::toDomain)
    }

    /**
     * Synchronous by design: the player and the quality sheet need a rung on the first
     * frame. Falls back to [Rung.LADDER], the §6.7 ladder in code, until the asset has
     * been read.
     */
    override fun rungs(): List<Rung> = cachedConfig?.rungs ?: Rung.LADDER

    override fun rung(id: String): Rung =
        rungs().firstOrNull { it.id == id }
            ?: rungs().firstOrNull { it.id == Rung.DEFAULT_CELLULAR_SAVER }
            ?: Rung.LADDER[1]

    override suspend fun adSchedule(titleId: Int, adFree: Boolean): AdSchedule {
        // A downloaded episode was monetised at download time (FR-505).
        if (adFree) return AdSchedule.NONE
        val config = config()
        val creatives = config.ads.creatives
        if (creatives.isEmpty()) return AdSchedule.NONE
        val title = title(titleId) ?: return AdSchedule.NONE

        val preRoll = config.ads.preRollSeconds
            .coerceAtMost(AdSchedule.MAX_PRE_ROLL_SECONDS)
        val skipAfter = config.ads.skipAfterSeconds

        val spots = buildList {
            if (preRoll > 0) {
                add(
                    AdSpot(
                        id = "pre",
                        slot = AdSlot.PreRoll,
                        positionFraction = 0f,
                        // Indexed, not random, so the same title yields the same plan
                        // across process death and rotation.
                        creative = creatives[titleId % creatives.size],
                        skipAfterSeconds = skipAfter,
                        maxDurationSeconds = preRoll,
                    ),
                )
            }
            title.adBreaks.forEachIndexed { i, fraction ->
                add(
                    AdSpot(
                        id = "mid$i",
                        slot = AdSlot.MidRoll,
                        positionFraction = fraction,
                        creative = creatives[(titleId + i + 1) % creatives.size],
                        skipAfterSeconds = skipAfter,
                        maxDurationSeconds = preRoll,
                    ),
                )
            }
        }
        return AdSchedule(spots)
    }

    override suspend fun search(query: String): List<Title> =
        FuzzyMatcher.filter(titles(), query)

    /** The debug-only Widevine test asset, for exercising the offline-licence path. */
    suspend fun drmTestVector(): Pair<String, Stream>? {
        val config = config()
        val stream = config.drmTestVector ?: return null
        return (config.drmTestVectorName ?: "DRM test vector") to stream
    }

    // ---------- Config ----------

    private suspend fun config(): CatalogConfig =
        cachedConfig ?: configLock.withLock {
            cachedConfig ?: withContext(io) { loadConfig(readCatalog()) }.also { cachedConfig = it }
        }

    private suspend fun episodeFactory(): EpisodeFactory =
        cachedEpisodeFactory ?: EpisodeFactory(config().episodeNames)
            .also { cachedEpisodeFactory = it }

    private fun loadConfig(dto: CatalogDto): CatalogConfig {
        return CatalogConfig(
            rungs = dto.rungs
                .map { Rung(it.id, it.subKey, it.mbPerHour, it.maxHeight) }
                .ifEmpty { Rung.LADDER },
            heroByKind = dto.heroByKind,
            ads = AdConfig(
                preRollSeconds = dto.ads.preRollSeconds,
                skipAfterSeconds = dto.ads.skipAfterSeconds,
                creatives = dto.ads.creatives.map {
                    AdCreative(url = it.url, type = StreamType.from(it.type))
                },
            ),
            episodeNames = dto.episodeNames,
            drmTestVector = dto.drmTestVector?.let { v ->
                Stream(
                    url = v.url,
                    type = StreamType.from(v.type),
                    drm = v.drm?.let {
                        com.example.streamingappzb.domain.model.Drm(
                            scheme = it.scheme,
                            licenseUrl = it.licenseUrl,
                            headers = it.headers,
                        )
                    },
                )
            },
            drmTestVectorName = dto.drmTestVector?.name,
        )
    }

    private fun readAsset(): CatalogDto {
        val adapter = moshi.adapter(CatalogDto::class.java)
        return assets.open(ASSET_NAME).use { input ->
            requireNotNull(adapter.fromJson(input.source().buffer())) {
                "$ASSET_NAME parsed to null"
            }
        }
    }

    /**
     * The catalogue payload, fetched once and reused.
     *
     * Identical to [readAsset] while [api] is null — the default, since `CatalogApi` is only
     * registered when `BuildConfig.API_BASE_URL` is set. When a host *is* configured the
     * catalogue is pulled from `GET /home`; any failure falls back to the bundled asset, so
     * first-run seeding (and therefore offline Home, Search and My List — PRD §8) can never
     * be blocked by the network. Reads after seeding stay Room-first, exactly as before.
     */
    private suspend fun readCatalog(): CatalogDto {
        cachedCatalog?.let { return it }
        val dto = if (api != null) {
            try {
                api.home()
            } catch (e: Exception) {
                readAsset()
            }
        } else {
            readAsset()
        }
        cachedCatalog = dto
        return dto
    }

    private companion object {
        const val ASSET_NAME = "catalog.json"
        const val KEY_ALL = "all"
    }
}
