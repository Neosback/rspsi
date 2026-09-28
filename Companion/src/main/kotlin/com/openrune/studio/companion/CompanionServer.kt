package com.openrune.studio.companion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.openrune.studio.companion.openrune.OpenRuneProjectInspector
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
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
                    capabilities = listOf("openrune-project-inspection"),
                ),
            )
        }

        post("/api/v1/openrune/inspect") {
            val request = runCatching { call.receive<JsonNode>() }.getOrNull()
            val requestedPath = request?.path("path")?.asText()?.trim().orEmpty()
            if (requestedPath.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "path is required"))
                return@post
            }

            val path = try {
                Path.of(requestedPath)
            } catch (_: InvalidPathException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "path is invalid"))
                return@post
            }

            call.respond(HttpStatusCode.OK, openRuneProjectInspector.inspect(path))
        }
    }
}
