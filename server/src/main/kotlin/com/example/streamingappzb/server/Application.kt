package com.example.streamingappzb.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/**
 * The MovieHub reference catalogue API (PRD §7): a read-only, CDN-cacheable v1 API that
 * serves the sample catalogue the app otherwise reads from its bundled asset.
 *
 *   GET /v1/home            -> the whole catalogue document (CatalogDto)
 *   GET /v1/titles/{id}     -> one title (TitleDto)
 *   GET /v1/search?q=       -> matching titles (List<TitleDto>), server-side FuzzyMatcher
 *   GET /v1/genres          -> the genre list (List<GenreDto>)
 *   GET /health             -> liveness
 *
 * Config via env: PORT (default 8080), API_PREFIX (default "/v1"), CATALOG_PATH (default:
 * the asset copied onto the classpath at build time).
 */
fun main(args: Array<String>) {
    Catalog.load()

    val port = (System.getenv("PORT") ?: args.getOrNull(0))?.toIntOrNull() ?: 8080
    val prefix = (System.getenv("API_PREFIX") ?: "/v1").let { if (it.startsWith("/")) it else "/$it" }

    println("MovieHub reference catalogue API")
    println("  titles loaded : ${Catalog.titleCount()}")
    println("  listening on  : http://localhost:$port$prefix")
    println("  point the app : API_BASE_URL = \"http://10.0.2.2:$port$prefix/\"  (Android emulator -> host machine)")

    embeddedServer(CIO, port = port, host = "0.0.0.0") {
        routing {
            get("/") {
                call.respondJson(
                    """{"service":"moviehub-reference-api","version":1,""" +
                        """"endpoints":["$prefix/home","$prefix/titles/{id}","$prefix/search?q=","$prefix/genres","/health"]}""",
                )
            }
            get("/health") {
                call.respondJson("""{"status":"ok","titles":${Catalog.titleCount()}}""")
            }

            route(prefix) {
                // The full catalogue document. `kind` is accepted for forward-compatibility,
                // but hero selection lives in `heroByKind` and the client resolves it, so the
                // payload is identical regardless of `kind`.
                get("/home") {
                    call.respondJson(Catalog.homeJson)
                }

                get("/titles/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                    when {
                        id == null ->
                            call.respondJson("""{"error":"id must be an integer"}""", HttpStatusCode.BadRequest)
                        else -> {
                            val body = Catalog.titleJson(id)
                            if (body == null) {
                                call.respondJson("""{"error":"no title with id $id"}""", HttpStatusCode.NotFound)
                            } else {
                                call.respondJson(body)
                            }
                        }
                    }
                }

                get("/search") {
                    val query = call.request.queryParameters["q"].orEmpty()
                    call.respondJson(Catalog.search(query))
                }

                get("/genres") {
                    call.respondJson(Catalog.genresJson)
                }
            }
        }
    }.start(wait = true)
}

/**
 * Every response is JSON, open to any origin (so the endpoints are browser-testable), and
 * cacheable — the API is read-only, which is what makes it CDN-friendly (PRD §7).
 */
private suspend fun ApplicationCall.respondJson(text: String, status: HttpStatusCode = HttpStatusCode.OK) {
    response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
    response.headers.append(HttpHeaders.CacheControl, "public, max-age=300")
    // The catalogue has non-ASCII text (·, em dashes, regional names); declare UTF-8 so
    // Moshi on the client decodes it correctly rather than guessing a charset.
    respondText(text, ContentType.Application.Json.withCharset(Charsets.UTF_8), status)
}
