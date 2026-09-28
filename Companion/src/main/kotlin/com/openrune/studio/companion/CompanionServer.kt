package com.openrune.studio.companion

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

private const val API_VERSION = 1

data class CompanionStatus(
    val name: String,
    val apiVersion: Int,
    val status: String,
    val capabilities: List<String>,
)

fun Application.companionModule() {
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
                    capabilities = emptyList(),
                ),
            )
        }
    }
}
