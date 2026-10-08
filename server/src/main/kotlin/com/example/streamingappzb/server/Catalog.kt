package com.example.streamingappzb.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Loads `catalog.json` once and exposes exactly what the four endpoints need.
 *
 * The document is parsed into a generic JSON tree rather than typed models on purpose: the
 * client owns the schema (`CatalogDto`), and the server's job is only to split one document
 * into `/home`, `/titles/{id}`, `/genres` and `/search` responses. Re-declaring every field
 * here would just be a second copy of the schema to keep in step.
 *
 * Data source, in order: the `CATALOG_PATH` env var if it points at a real file, otherwise
 * the `catalog.json` copied onto the classpath from the app's bundled asset at build time.
 */
object Catalog {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var loaded = false

    /** The whole catalogue, served verbatim for `GET /home`. */
    lateinit var homeJson: String
        private set

    /** The `genres` array, for `GET /genres`. */
    lateinit var genresJson: String
        private set

    private val titlesById = LinkedHashMap<Int, JsonObject>()
    private val index = ArrayList<Row>()

    private class Row(val title: String, val genre: String, val cast: String, val obj: JsonObject)

    @Synchronized
    fun load() {
        if (loaded) return
        val text = readSource()
        val root = json.parseToJsonElement(text).jsonObject

        // Served as-is: the same bytes the app bundles, so the server and the offline asset
        // are the identical payload.
        homeJson = text

        genresJson = (root["genres"] ?: JsonArray(emptyList())).toString()

        val titles = root["titles"]?.jsonArray ?: JsonArray(emptyList())
        for (element in titles) {
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: continue
            val title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val genre = obj["genre"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val cast = (obj["cast"] as? JsonArray)
                ?.joinToString(" ") { it.jsonPrimitive.content }
                .orEmpty()
            titlesById[id] = obj
            index += Row(title, genre, cast, obj)
        }
        loaded = true
    }

    fun titleCount(): Int = index.size

    /** The single title object for `GET /titles/{id}`, or null if there is no such id. */
    fun titleJson(id: Int): String? = titlesById[id]?.toString()

    /** A JSON array of the titles matching [query], in catalogue (editorial) order. */
    fun search(query: String): String {
        if (query.isBlank()) return "[]"
        return index
            .filter { Search.matches(it.title, it.genre, it.cast, query) }
            .joinToString(prefix = "[", postfix = "]", separator = ",") { it.obj.toString() }
    }

    private fun readSource(): String {
        System.getenv("CATALOG_PATH")?.let { path ->
            val file = File(path)
            if (file.isFile) return file.readText()
        }
        val stream = Catalog::class.java.getResourceAsStream("/catalog.json")
            ?: error(
                "catalog.json is not on the classpath and CATALOG_PATH is unset or invalid. " +
                    "Run via ':server:run' so the build copies the app's asset in.",
            )
        return stream.bufferedReader().use { it.readText() }
    }
}
