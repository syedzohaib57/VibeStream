package com.example.streamingappzb.data.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.streamingappzb.data.prefs.AppPrefs
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.PlayableSource
import com.example.streamingappzb.domain.model.StreamType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One video file found under a folder the viewer added. */
data class LocalVideo(
    /** A `content://` document URI, valid for as long as the tree permission is held. */
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    /** As the document provider reports it, or null when it is not a usable video type. */
    val mimeType: String?,
    val parsed: TitleMatch.Parsed,
) {
    val streamType: StreamType
        get() = when {
            displayName.endsWith(".m3u8", ignoreCase = true) -> StreamType.Hls
            displayName.endsWith(".mpd", ignoreCase = true) -> StreamType.Dash
            else -> StreamType.Progressive
        }
}

/**
 * Just the list of indexed files, so the matching rules can be tested without SAF.
 *
 * [LocalLibrary] is the only real implementation and it is unavoidably an Android class —
 * DocumentsContract has no JVM stand-in. The part worth testing is which file wins for a
 * given title, which is pure, so it goes behind this.
 */
fun interface LocalVideoIndex {
    suspend fun videos(): List<LocalVideo>
}

/**
 * The viewer's own media, indexed from folders they chose.
 *
 * This is the answer to "why does the play button hand me off to a provider for almost
 * everything?". The catalogue is TMDB's, which is every film ever made; what can actually
 * be played is whatever the device can open. Pointing the app at a folder makes those two
 * sets overlap, and for a personal build that overlap is the whole product.
 *
 * Storage Access Framework rather than `READ_MEDIA_VIDEO` and MediaStore, for three
 * reasons: it reaches an SD card and a USB drive, which MediaStore's external volumes do
 * not reliably; the permission is granted per folder rather than over all video on the
 * device; and the grant survives reboots once taken persistably, so a folder is added once
 * and not re-authorised every launch.
 */
class LocalLibrary(
    context: Context,
    private val prefs: AppPrefs,
    private val io: CoroutineDispatcher,
) : LocalVideoIndex {

    private val resolver = context.contentResolver

    /** Guards the scan so two screens opening at once cannot walk the tree twice. */
    private val mutex = Mutex()

    /**
     * Volatile because the scan writes these from the IO dispatcher while [invalidate] is
     * called from the main thread after a folder is added or removed. Without it a UI
     * thread's write can sit in a core-local cache and the next lookup serves an index
     * built from folders that no longer exist.
     */
    @Volatile
    private var cached: List<LocalVideo>? = null

    /** The folder set the cache was built from — a change to it invalidates by itself. */
    @Volatile
    private var cachedFor: Set<String> = emptySet()

    /**
     * Every video under every added folder.
     *
     * Scanned once and held in memory. A re-scan on each lookup would walk the tree for
     * every title screen opened, and SAF traversal is a Binder round trip per directory —
     * on a library of any size that is seconds, not milliseconds.
     */
    override suspend fun videos(): List<LocalVideo> {
        val folders = prefs.libraryFolders
        if (folders.isEmpty()) {
            // Cheap, and it means removing the last folder frees the index rather than
            // leaving the app offering files it has been told to forget.
            cached = null
            cachedFor = emptySet()
            return emptyList()
        }

        cached?.takeIf { cachedFor == folders }?.let { return it }

        return mutex.withLock {
            // Re-checked under the lock: a caller that queued behind a scan for the same
            // folders wants its result, not a second walk of the same tree.
            cached?.takeIf { cachedFor == folders }?.let { return@withLock it }

            val scanned = withContext(io) { folders.flatMap(::scan) }
            cached = scanned
            cachedFor = folders
            scanned
        }
    }

    /** Drops the index so the next lookup re-walks the folders. */
    fun invalidate() {
        cached = null
        cachedFor = emptySet()
    }

    /**
     * The folder's own name, for the settings list.
     *
     * Derived from the tree id first and only queried as a fallback, because this is called
     * from the sheet's synchronous render pass. A tree id is typically `primary:Movies/Films`
     * and its tail *is* the folder name, so the common case costs no Binder call at all;
     * querying first put a content-provider round trip per row on the main thread for a
     * string the URI already contained.
     */
    fun folderName(treeUri: String): String {
        val uri = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return treeUri
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()

        documentId
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        // Exotic providers — a USB drive, a cloud document provider — use opaque ids with
        // no readable tail, and for those the display name is worth asking for.
        if (documentId != null) {
            queryName(DocumentsContract.buildDocumentUriUsingTree(uri, documentId))
                ?.let { return it }
        }
        return uri.lastPathSegment ?: treeUri
    }

    private fun queryName(document: Uri): String? = runCatching {
        resolver.query(
            document,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Walks one tree breadth-first.
     *
     * Iterative rather than recursive, and each directory's children are read fully before
     * any subdirectory is opened. Recursing while a cursor is still open holds a Binder
     * window per level of nesting, which a deep library exhausts.
     *
     * Every failure is swallowed per folder: a revoked permission, an unmounted SD card or
     * a provider that has gone away must cost that folder and not the whole library.
     */
    private fun scan(treeUri: String): List<LocalVideo> = runCatching {
        val tree = Uri.parse(treeUri)
        val found = mutableListOf<LocalVideo>()
        val queue = ArrayDeque<String>()
        val seen = mutableSetOf<String>()

        DocumentsContract.getTreeDocumentId(tree).let(queue::addLast)

        var directoriesVisited = 0
        while (queue.isNotEmpty() && found.size < MAX_FILES && directoriesVisited < MAX_DIRS) {
            val documentId = queue.removeFirst()
            // Document providers can present the same directory under two parents; without
            // this a symlinked or cross-linked tree never terminates.
            if (!seen.add(documentId)) continue
            directoriesVisited++
            queue.addAll(readDirectory(tree, documentId, found))
        }
        found
    }.getOrDefault(emptyList())

    /** Appends this directory's videos to [found] and returns its subdirectory ids. */
    private fun readDirectory(
        tree: Uri,
        documentId: String,
        found: MutableList<LocalVideo>,
    ): List<String> = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val directories = mutableListOf<String>()

        resolver.query(children, PROJECTION, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(COLUMN_ID) ?: continue
                val name = cursor.getString(COLUMN_NAME) ?: continue
                val mime = cursor.getString(COLUMN_MIME)
                val size = if (cursor.isNull(COLUMN_SIZE)) 0L else cursor.getLong(COLUMN_SIZE)

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    directories += id
                    continue
                }
                if (!isVideo(name, mime)) continue

                found += LocalVideo(
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                    displayName = name,
                    sizeBytes = size,
                    mimeType = mime?.takeIf(::isUsableMime),
                    parsed = TitleMatch.parse(name),
                )
            }
        }
        directories
    }.getOrDefault(emptyList())

    /**
     * The provider's MIME type is believed only when it says something.
     *
     * SAF routinely answers `application/octet-stream` for anything it does not recognise,
     * and passing that through would have ExoPlayer trust a type that carries no
     * information. Null instead means the container gets sniffed, which is reliable.
     */
    private fun isUsableMime(mime: String): Boolean =
        mime.startsWith("video/", ignoreCase = true) ||
            mime.equals("application/x-mpegurl", ignoreCase = true) ||
            mime.equals("application/vnd.apple.mpegurl", ignoreCase = true) ||
            mime.equals("application/dash+xml", ignoreCase = true)

    /**
     * Extension *or* declared type, because neither alone is enough: a provider may report
     * `application/octet-stream` for a perfectly ordinary `.mkv`, and a file exported from
     * a camera app may carry a correct `video/mp4` with no extension at all.
     */
    private fun isVideo(name: String, mime: String?): Boolean {
        if (mime != null && mime.startsWith("video/", ignoreCase = true)) return true
        return VIDEO_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }
    }

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        const val COLUMN_ID = 0
        const val COLUMN_NAME = 1
        const val COLUMN_MIME = 2
        const val COLUMN_SIZE = 3

        /**
         * What ExoPlayer can actually open. `.mkv`, `.webm`, `.mp4` and `.m4v` are
         * guaranteed; `.avi`, `.ts` and `.flv` have extractors in Media3 and work for the
         * common codecs inside them. `.wmv` is deliberately absent — there is no VC-1
         * extractor, so it would offer a play button that fails on the first frame.
         */
        val VIDEO_EXTENSIONS = listOf(
            ".mp4", ".m4v", ".mkv", ".webm", ".avi", ".ts", ".m2ts", ".mts", ".flv",
            ".3gp", ".mov", ".m3u8", ".mpd",
        )

        /** Bounds on a pathological tree, so a mis-chosen root cannot hang the scan. */
        const val MAX_FILES = 20_000
        const val MAX_DIRS = 2_000
    }
}

/**
 * Plays the viewer's own file for a catalogue title, when they have one.
 *
 * First in the resolver chain by design: a local copy beats a remote one on quality,
 * latency and data every time, and it is the only source that works with no network.
 */
class LocalFreeSourceRepository(
    private val index: LocalVideoIndex,
) : FreeSourceRepository {

    override suspend fun sourceFor(item: MediaItem): PlayableSource? =
        sourceFor(item, episode = null)

    override suspend fun sourceFor(item: MediaItem, episode: EpisodeRef?): PlayableSource? {
        // Normalised once, not once per file: this filter runs over the whole library.
        val target = TitleMatch.target(item)
        val candidates = index.videos().filter { TitleMatch.matches(it.parsed, target) }
        if (candidates.isEmpty()) return null

        // A specific episode was asked for, so only that episode will do. Falling back to
        // "whatever episode is on disk" would play S01E01 under a header claiming S02E05,
        // which is strictly worse than the chain moving on to a source that has it.
        if (episode != null && item.type.isSeries) {
            val wanted = candidates
                .filter {
                    it.parsed.episode == episode.episode &&
                        // A file with no season marker ("Firefly - E05") is accepted for
                        // season 1, which is how single-season shows are usually named.
                        (it.parsed.season ?: 1) == episode.season
                }
                .maxByOrNull { it.sizeBytes }
                ?: return null
            return wanted.toSource(item)
        }

        val pick = if (item.type.isSeries) {
            // The detail screen's play button means "start this show", so it starts at the
            // earliest episode on disk. Files with no episode code sort last rather than
            // first: on a series they are extras and featurettes, not the premiere.
            candidates.minWithOrNull(
                compareBy<LocalVideo> { it.parsed.season ?: Int.MAX_VALUE }
                    .thenBy { it.parsed.episode ?: Int.MAX_VALUE }
                    .thenByDescending { it.sizeBytes },
            )
        } else {
            // Largest wins. A film folder commonly holds the feature next to a trailer, a
            // deleted-scenes reel and a sample — all of which match the title, and only one
            // of which is the film.
            candidates.maxByOrNull { it.sizeBytes }
        } ?: return null

        return pick.toSource(item)
    }

    private fun LocalVideo.toSource(item: MediaItem): PlayableSource {
        val code = parsed.episodeCode.takeIf { item.type.isSeries }
        return PlayableSource(
            url = uri,
            type = streamType,
            // The catalogue's title, not the filename's — the viewer tapped a poster
            // saying "The Matrix" and should not land on a header reading
            // "The.Matrix.1999.1080p.BluRay.x264-AMIABLE".
            label = if (code == null) item.title else "${item.title} · $code",
            attribution = "Your library · $displayName",
            mimeType = mimeType,
        )
    }
}
