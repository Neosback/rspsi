package com.openrune.studio.companion

import io.ktor.client.call.body
import io.ktor.client.request.contentType
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompanionServerTest {
    @Test
    fun statusEndpointExposesVersionedCompanionIdentity() = testApplication {
        application {
            companionModule()
        }

        val response = client.get("/api/v1/status")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<String>()
        assertTrue(body.contains("\"name\":\"OpenRune Studio Companion\""))
        assertTrue(body.contains("\"apiVersion\":1"))
        assertTrue(body.contains("\"status\":\"ready\""))
        assertTrue(body.contains("\"openrune-project-inspection\""))
    }

    @Test
    fun inspectEndpointReturnsPassiveProjectDiscovery() = testApplication {
        val root = Files.createTempDirectory("openrune-api")
        try {
            root.resolve("settings.gradle.kts").writeText(
                """
                rootProject.name = "OpenRune-Server"
                include("content", "engine", "server", "or-cache")
                """.trimIndent(),
            )
            root.resolve("gradlew").writeText("#!/bin/sh")
            root.resolve("or-cache").createDirectories()
            root.resolve("or-cache/build.gradle.kts").writeText("plugins {}")
            root.resolve("content").createDirectories()

            application {
                companionModule()
            }

            val jsonPath = root.toString().replace("\\", "\\\\")
            val response = client.post("/api/v1/openrune/inspect") {
                contentType(ContentType.Application.Json)
                setBody("{\"path\":\"$jsonPath\"}")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<String>()
            assertTrue(body.contains("\"matched\":true"))
            assertTrue(body.contains("\"cache-build-module\""))
            assertTrue(body.contains("\"content-source\""))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun inspectEndpointRejectsMissingPath() = testApplication {
        application {
            companionModule()
        }

        val response = client.post("/api/v1/openrune/inspect") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
