package com.openrune.studio.companion

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanionServerTest {
    @Test
    fun apiRequiresSessionToken() = testApplication {
        application {
            companionModule(security = TEST_SECURITY)
        }

        val response = client.get("/api/v1/status") {
            loopbackHost()
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.body<String>().contains("\"code\":\"UNAUTHORIZED\""))
    }

    @Test
    fun apiRejectsNonLoopbackHostEvenWithToken() = testApplication {
        application {
            companionModule(security = TEST_SECURITY)
        }

        val response = client.get("/api/v1/status") {
            companionAuth()
            header(HttpHeaders.Host, "attacker.example")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.body<String>().contains("\"code\":\"HOST_NOT_ALLOWED\""))
    }

    @Test
    fun apiRejectsNonLoopbackBrowserOrigin() = testApplication {
        application {
            companionModule(security = TEST_SECURITY)
        }

        val response = client.get("/api/v1/status") {
            companionAuth()
            header(HttpHeaders.Origin, "https://attacker.example")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.body<String>().contains("\"code\":\"ORIGIN_NOT_ALLOWED\""))
    }

    @Test
    fun statusOnlyAdvertisesProjectOpenBeforeAProjectSessionExists() = testApplication {
        application {
            companionModule(security = TEST_SECURITY)
        }

        val response = client.get("/api/v1/status") {
            companionAuth()
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<String>()
        assertTrue(body.contains("\"project.open\""))
        assertFalse(body.contains("\"source.index\""))
    }

    @Test
    fun projectOpenCreatesOpaqueSessionWithProjectCapabilities() = testApplication {
        val root = openRuneProject()
        try {
            application {
                companionModule(security = TEST_SECURITY)
            }

            val response = client.post("/api/v1/project/open") {
                companionAuth()
                contentType(ContentType.Application.Json)
                setBody(pathBody(root))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<String>()
            assertTrue(body.contains("\"projectId\""))
            assertTrue(body.contains("\"project.inspect\""))
            assertTrue(body.contains("\"content.index\""))
            assertTrue(body.contains("\"content.resolve\""))
            assertTrue(body.contains("\"source.index\""))
            assertFalse(body.contains("\"cache.read\""))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun contentCallsUseProjectIdInsteadOfFilesystemPath() = testApplication {
        val root = openRuneProject()
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
                companionModule(security = TEST_SECURITY)
            }

            val openResponse = client.post("/api/v1/project/open") {
                companionAuth()
                contentType(ContentType.Application.Json)
                setBody(pathBody(root))
            }
            val projectId =
                Regex("\\\"projectId\\\":\\\"([^\\\"]+)\\\"")
                    .find(openResponse.body<String>())
                    ?.groupValues
                    ?.get(1)
                    ?: error("projectId missing")

            val response = client.post("/api/v1/project/$projectId/content/index") {
                companionAuth()
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.body<String>().contains("\"qualifiedName\":\"content.rock\""))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun unknownProjectIdUsesStableErrorCode() = testApplication {
        application {
            companionModule(security = TEST_SECURITY)
        }

        val response = client.post("/api/v1/project/missing/source/index") {
            companionAuth()
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.body<String>().contains("\"code\":\"PROJECT_NOT_OPEN\""))
    }

    @Test
    fun legacyArbitraryPathEndpointIsRemoved() = testApplication {
        application {
            companionModule(security = TEST_SECURITY)
        }

        val response = client.post("/api/v1/openrune/source/index") {
            companionAuth()
            contentType(ContentType.Application.Json)
            setBody("{\"path\":\"/tmp\"}")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private fun openRuneProject(): java.nio.file.Path {
        val root = Files.createTempDirectory("openrune-project-session")
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
        root.resolve("engine").createDirectories()
        root.resolve("server").createDirectories()
        return root
    }

    private fun io.ktor.client.request.HttpRequestBuilder.companionAuth() {
        loopbackHost()
        header(CompanionSecurity.TOKEN_HEADER, TEST_TOKEN)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.loopbackHost() {
        header(HttpHeaders.Host, "localhost")
    }

    private fun pathBody(path: java.nio.file.Path): String {
        val jsonPath = path.toString().replace("\\", "\\\\")
        return "{\"path\":\"$jsonPath\"}"
    }

    private companion object {
        const val TEST_TOKEN = "test-token-with-at-least-32-characters"
        val TEST_SECURITY = CompanionSecurity(TEST_TOKEN)
    }
}
