package com.openrune.studio.companion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.openrune.studio.companion.cache.OpenRuneCacheReader
import com.openrune.studio.companion.openrune.OpenRuneProjectInspector
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
                        ),
                ),
            )
        }

        post("/api/v1/openrune/inspect") {
            val path = call.requiredPath() ?: return@post
            call.respond(HttpStatusCode.OK, openRuneProjectInspector.inspect(path))
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
