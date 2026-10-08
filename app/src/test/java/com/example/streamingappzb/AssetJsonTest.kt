package com.example.streamingappzb

import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Lottie files in res/raw and the catalogue in assets/ are hand-authored, and both
 * fail silently at runtime when malformed — Lottie renders a blank view, a bad catalogue
 * leaves an empty Home. This turns both into build failures instead.
 */
class AssetJsonTest {

    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
    )

    /** Unit tests run with the module directory as cwd; tolerate being run from the root. */
    private fun moduleFile(relative: String): File =
        listOf(File(relative), File("app/$relative"))
            .firstOrNull { it.exists() }
            ?: error("Not found from ${File(".").absolutePath}: $relative")

    private fun parse(file: File): Map<String, Any?> {
        val reader = JsonReader.of(file.source().buffer())
        // Lottie/bodymovin emits repeated keys in some groups; tolerate them here since
        // the only thing under test is that the document is well-formed and complete.
        reader.setLenient(true)
        return requireNotNull(mapAdapter.fromJson(reader)) { "${file.name} parsed to null" }
    }

    @Test
    fun `every lottie animation is well formed`() {
        val raw = moduleFile("src/main/res/raw")
        val files = raw.listFiles { f: File -> f.extension == "json" }?.sortedBy { it.name }
        assertNotNull("res/raw has no JSON files", files)
        assertTrue("expected the authored Lottie set in res/raw", files!!.size >= 6)

        for (file in files) {
            val json = parse(file)
            // The keys lottie-android's LottieCompositionParser requires.
            for (key in listOf("v", "fr", "ip", "op", "w", "h", "layers")) {
                assertTrue("${file.name}: missing \"$key\"", json.containsKey(key))
            }

            @Suppress("UNCHECKED_CAST")
            val layers = json["layers"] as? List<Map<String, Any?>>
            assertNotNull("${file.name}: \"layers\" is not an array", layers)
            assertFalse("${file.name}: no layers, would render blank", layers!!.isEmpty())

            val op = (json["op"] as Number).toDouble()
            val ip = (json["ip"] as Number).toDouble()
            assertTrue("${file.name}: op ($op) must be after ip ($ip)", op > ip)
            assertTrue("${file.name}: frame rate must be positive", (json["fr"] as Number).toDouble() > 0)

            layers.forEachIndexed { i, layer ->
                assertTrue("${file.name} layer $i: missing \"ty\"", layer.containsKey("ty"))
                assertTrue("${file.name} layer $i: missing \"ks\" transform", layer.containsKey("ks"))
                // ty 4 is a shape layer; every animation here is shape-only, so a shape
                // layer with no shapes is a silent blank.
                if ((layer["ty"] as Number).toInt() == SHAPE_LAYER) {
                    @Suppress("UNCHECKED_CAST")
                    val shapes = layer["shapes"] as? List<Any?>
                    assertTrue(
                        "${file.name} layer $i: shape layer with no shapes",
                        shapes != null && shapes.isNotEmpty(),
                    )
                }
            }
        }
    }

    @Test
    fun `catalogue parses and matches the design`() {
        val json = parse(moduleFile("src/main/assets/catalog.json"))

        @Suppress("UNCHECKED_CAST")
        val titles = json["titles"] as List<Map<String, Any?>>
        assertEquals("the design ships exactly 14 sample titles", 14, titles.size)

        @Suppress("UNCHECKED_CAST")
        val streams = json["streams"] as Map<String, Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val rungs = json["rungs"] as List<Map<String, Any?>>
        assertEquals("the §6.7 ladder has six rungs", 6, rungs.size)

        val ids = titles.map { (it["id"] as Number).toInt() }
        assertEquals("title ids must be 1..14 with no gaps", (1..14).toList(), ids.sorted())

        for (t in titles) {
            val name = t["title"] as String
            for (key in listOf("id", "title", "kind", "year", "genre", "c1", "c2", "motif", "downloadable", "synopsis", "cast", "stream", "adBreaks")) {
                assertTrue("$name: missing \"$key\"", t.containsKey(key))
            }
            val kind = t["kind"] as String
            assertTrue("$name: unknown kind $kind", kind in setOf("Drama", "Film", "Documentary"))
            // A Film has a runtime, a series has an episode count — never both, never neither.
            assertEquals(
                "$name: a Film needs mins, a series needs eps",
                kind == "Film",
                t.containsKey("mins"),
            )
            assertEquals("$name: eps/mins mismatch", kind != "Film", t.containsKey("eps"))

            val stream = t["stream"] as String
            assertTrue("$name: stream \"$stream\" is not in the pool", streams.containsKey(stream))

            @Suppress("UNCHECKED_CAST")
            val breaks = t["adBreaks"] as List<Number>
            assertTrue(
                "$name: ad breaks must be 0..1 fractions of runtime, ascending",
                breaks.all { it.toDouble() > 0.0 && it.toDouble() < 1.0 } &&
                    breaks.map { it.toDouble() } == breaks.map { it.toDouble() }.sorted(),
            )
        }

        // Every id referenced by a row or the hero map must exist (PRD §6.1: a row that
        // filters to nothing is dropped, but a dangling id is a data bug).
        @Suppress("UNCHECKED_CAST")
        val rows = json["rows"] as List<Map<String, Any?>>
        assertTrue("Home ships at most 7 rows so Continue watching keeps it under 8 (§8.5)", rows.size <= 7)
        for (row in rows) {
            @Suppress("UNCHECKED_CAST")
            val rowIds = row["ids"] as List<Number>
            for (id in rowIds) {
                assertTrue("row ${row["key"]} references unknown title $id", id.toInt() in ids)
            }
        }

        @Suppress("UNCHECKED_CAST")
        val hero = json["heroByKind"] as Map<String, Number>
        assertEquals(setOf("all", "Drama", "Film", "Documentary"), hero.keys)
        for ((chip, id) in hero) {
            assertTrue("hero for $chip references unknown title $id", id.toInt() in ids)
        }
    }

    private companion object {
        const val SHAPE_LAYER = 4
    }
}
