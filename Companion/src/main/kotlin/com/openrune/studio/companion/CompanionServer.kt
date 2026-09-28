package com.openrune.studio.companion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.openrune.studio.companion.cache.OpenRuneCacheReader
import com.openrune.studio.companion.openrune.OpenRuneContentIndexer
import com.openrune.studio.companion.openrune.OpenRuneContentResolver
import com.openrune.studio.companion.openrune.OpenRuneProjectInspector
import com.openrune.studio.companion.openrune.OpenRuneKotlinSourceIndexer
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.nio.file.InvalidPathException
import java.nio.file.Path

private const val API_VERSION = 1

data class CompanionStatus(
    val name: String,
    val apiVersion: Int,
    val status: String,
    val capabilities: List<String>,
)

fun Application.companionModule(
    openRuneProjectInspector: OpenRuneProjectInspector = OpenRuneProjectInspector(),
    openRuneCacheReader: OpenRuneCacheReader = OpenRuneCacheReader(),
    openRuneContentIndexer: OpenRuneContentIndexer = OpenRuneContentIndexer(),
    openRuneContentResolver: OpenRuneContentResolver = OpenRuneContentResolver(),
    openRuneKotlinSourceIndexer: OpenRuneKotlinSourceIndexer = OpenRuneKotlinSourceIndexer(),
) {
    install(ContentNegotiation) {
        jackson {
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        get("/api/v1/status") {
            call.respond(
                HttpStatusCode.OK,
                CompanionStatus(
                    name = "OpenRune Studio Companion",
                    apiVersion = API_VERSION,
                    status = "ready",
                    capabilities =
                        listOf(
                            "openrune-project-inspection",
                            "openrune-cache-read",
                            "openrune-content-index",
                            "openrune-content-resolve",
                            "openrune-kotlin-source-index",
                        ),
                ),
            )
        }

        post("/api/v1/openrune/inspect") {
            val path = call.requiredPath() ?: return@post
            call.respond(HttpStatusCode.OK, openRuneProjectInspector.inspect(path))
        }

        post("/api/v1/openrune/content/index") {
            val path = call.requiredPath() ?: return@post
            val index =
                try {
                    openRuneContentIndexer.index(path)
                } catch (failure: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        mapOf("error" to (failure.message ?: "content indexing failed")),
                    )
                    return@post
                }

            call.respond(HttpStatusCode.OK, index)
        }

        post("/api/v1/openrune/content/resolve") {
            val request = runCatching { call.receive<JsonNode>() }.getOrNull()
            val requestedPath = request?.path("path")?.asText()?.trim().orEmpty()
            val symbol = request?.path("symbol")?.asText()?.trim().orEmpty()
            if (requestedPath.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "path is required"))
                return@post
            }
            if (symbol.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "symbol is required"))
                return@post
            }

            val path =
                try {
                    Path.of(requestedPath)
                } catch (_: InvalidPathException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "path is invalid"))
                    return@post
                }

            val resolved =
                try {
                    openRuneContentResolver.resolve(path, symbol)
                } catch (failure: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        mapOf("error" to (failure.message ?: "content resolution failed")),
                    )
                    return@post
                }

            call.respond(HttpStatusCode.OK, resolved)
        }

        post("/api/v1/openrune/source/index") {
            val path = call.requiredPath() ?: return@post
            val index =
                try {
                    openRuneKotlinSourceIndexer.index(path)
                } catch (failure: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        mapOf("error" to (failure.message ?: "source indexing failed")),
                    )
                    return@post
                }

            call.respond(HttpStatusCode.OK, index)
        }

        post("/api/v1/cache/inspect") {
            val path = call.requiredPath() ?: return@post
            val inspection =
                try {
                    openRuneCacheReader.inspect(path)
                } catch (failure: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        mapOf("error" to (failure.message ?: "cache inspection failed")),
                    )
                    return@post
                }

            call.respond(HttpStatusCode.OK, inspection)
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.requiredPath(): Path? {
    val request = runCatching { receive<JsonNode>() }.getOrNull()
    val requestedPath = request?.path("path")?.asText()?.trim().orEmpty()
    if (requestedPath.isEmpty()) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "path is required"))
        return null
    }

    return try {
        Path.of(requestedPath)
    } catch (_: InvalidPathException) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "path is invalid"))
        null
    }
}
