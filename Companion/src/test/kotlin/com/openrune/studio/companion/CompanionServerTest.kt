package com.openrune.studio.companion

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
        assertTrue(body.contains("\"project.inspect\""))
        assertTrue(body.contains("\"cache.read\""))
        assertTrue(body.contains("\"content.index\""))
        assertTrue(body.contains("\"content.resolve\""))
        assertTrue(body.contains("\"source.index\""))
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

            val response = client.post("/api/v1/openrune/inspect") {
                contentType(ContentType.Application.Json)
                setBody(pathBody(root))
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
    fun contentIndexEndpointReturnsNeutralGameVals() = testApplication {
        val root = Files.createTempDirectory("openrune-content-api")
        try {
            val module = root.resolve("content/skills/mining")
            module.createDirectories()
            module.resolve("build.gradle.kts").writeText("plugins {}")
            val resources = module.resolve("src/main/resources")
            resources.createDirectories()
            resources.resolve("gamevals.toml").writeText(
                """
                [gamevals.content]
                rock = 52
                """.trimIndent(),
            )

            application {
                companionModule()
            }

            val response = client.post("/api/v1/openrune/content/index") {
                contentType(ContentType.Application.Json)
                setBody(pathBody(root))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<String>()
            assertTrue(body.contains("\"qualifiedName\":\"content.rock\""))
            assertTrue(body.contains("\"modulePath\":\"skills/mining\""))
        } finally {
            root.toFile().deleteRecursively()
        }
    }


    @Test
    fun contentResolveEndpointJoinsGameValAndKotlinHandler() = testApplication {
        val root = Files.createTempDirectory("openrune-content-resolve-api")
        try {
            val module = root.resolve("content/skills/mining")
            module.createDirectories()
            module.resolve("build.gradle.kts").writeText("plugins {}")

            val resources = module.resolve("src/main/resources")
            resources.createDirectories()
            resources.resolve("gamevals.toml").writeText(
                """
                [gamevals.content]
                rock = 52
                """.trimIndent(),
            )

            val source = module.resolve("src/main/kotlin/org/example")
            source.createDirectories()
            source.resolve("Mining.kt").writeText(
                """
                package org.example

                class Mining : PluginScript() {
                    fun ScriptContext.startup() {
                        onOpContentLoc1("content.rock") { mine() }
                    }

                    private fun mine() = Unit
                }
                """.trimIndent(),
            )

            application {
                companionModule()
            }

            val jsonPath = root.toString().replace("\\", "\\\\")
            val response = client.post("/api/v1/openrune/content/resolve") {
                contentType(ContentType.Application.Json)
                setBody("{\"path\":\"$jsonPath\",\"symbol\":\"content.rock\"}")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<String>()
            assertTrue(body.contains("\"found\":true"))
            assertTrue(body.contains("\"qualifiedName\":\"content.rock\""))
            assertTrue(body.contains("\"name\":\"onOpContentLoc1\""))
            assertTrue(body.contains("\"name\":\"Mining\""))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun cacheInspectRejectsMissingDirectoryWithoutOpeningFileStore() = testApplication {
        val missing = Files.createTempDirectory("openrune-cache-api").resolve("missing")

        application {
            companionModule()
        }

        val response = client.post("/api/v1/cache/inspect") {
            contentType(ContentType.Application.Json)
            setBody(pathBody(missing))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.body<String>().contains("not a directory"))
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

    private fun pathBody(path: java.nio.file.Path): String {
        val jsonPath = path.toString().replace("\\", "\\\\")
        return "{\"path\":\"$jsonPath\"}"
    }
}
