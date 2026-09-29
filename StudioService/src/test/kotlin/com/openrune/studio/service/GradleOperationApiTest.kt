package com.openrune.studio.service

import com.openrune.studio.service.gradle.GradleOperationDescriptor
import com.openrune.studio.service.gradle.GradleOperationService
import com.openrune.studio.service.gradle.GradleOperationSnapshot
import com.openrune.studio.service.gradle.GradleOperationState
import com.openrune.studio.service.project.ProjectSession
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradleOperationApiTest {
    @Test
    fun startsLooksUpAndCancelsOperation() = testApplication {
        val root = openRuneProject()
        val operations = FakeOperations()

        try {
            application {
                studioServiceModule(
                    security = TEST_SECURITY,
                    gradleOperations = operations,
                )
            }

            val projectId = openProject(root)

            val catalog = client.get("/api/v1/project/$projectId/gradle/operations") {
                auth()
            }
            assertEquals(HttpStatusCode.OK, catalog.status)
            assertTrue(catalog.body<String>().contains("\"id\":\"assemble\""))

            val start = client.post("/api/v1/project/$projectId/gradle/operations") {
                auth()
                contentType(ContentType.Application.Json)
                setBody("{\"operation\":\"assemble\"}")
            }
            assertEquals(HttpStatusCode.Accepted, start.status)
            val startBody = start.body<String>()
            assertTrue(startBody.contains("\"operationId\":\"operation-1\""))
            assertTrue(startBody.contains("\"state\":\"RUNNING\""))

            val lookup =
                client.get("/api/v1/project/$projectId/gradle/operations/operation-1") {
                    auth()
                }
            assertEquals(HttpStatusCode.OK, lookup.status)
            assertTrue(lookup.body<String>().contains("\"state\":\"RUNNING\""))

            val cancel =
                client.post(
                    "/api/v1/project/$projectId/gradle/operations/operation-1/cancel",
                ) {
                    auth()
                }
            assertEquals(HttpStatusCode.Accepted, cancel.status)
            assertTrue(cancel.body<String>().contains("\"state\":\"CANCELLED\""))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun streamsTerminalSnapshotOverSse() = testApplication {
        val root = openRuneProject()
        val operations = FakeOperations()

        try {
            application {
                studioServiceModule(
                    security = TEST_SECURITY,
                    gradleOperations = operations,
                )
            }

            val projectId = openProject(root)
            val start = client.post("/api/v1/project/$projectId/gradle/operations") {
                auth()
                contentType(ContentType.Application.Json)
                setBody("{\"operation\":\"assemble\"}")
            }
            assertEquals(HttpStatusCode.Accepted, start.status)

            operations.complete()

            val events =
                client.get(
                    "/api/v1/project/$projectId/gradle/operations/operation-1/events",
                ) {
                    auth()
                }

            assertEquals(HttpStatusCode.OK, events.status)
            val body = events.body<String>()
            assertTrue(body.contains("event: snapshot"))
            assertTrue(body.contains("\"state\":\"SUCCEEDED\""))
            assertTrue(body.contains("\"stdoutTail\":\"done\\n\""))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.openProject(
        root: java.nio.file.Path,
    ): String {
        val response = client.post("/api/v1/project/open") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(pathBody(root))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<String>()
        assertTrue(body.contains("\"gradle.operations\""))
        return projectId(body)
    }

    private class FakeOperations : GradleOperationService {
        private var projectId: String? = null
        private val state =
            MutableStateFlow(
                snapshot(
                    projectId = "not-started",
                    state = GradleOperationState.RUNNING,
                    sequence = 0,
                ),
            )

        override fun catalog(): List<GradleOperationDescriptor> =
            listOf(
                GradleOperationDescriptor(
                    id = "assemble",
                    task = "assemble",
                    timeoutMillis = 1000,
                ),
            )

        override fun start(
            project: ProjectSession,
            operationId: String,
        ): GradleOperationSnapshot {
            projectId = project.id
            val running =
                snapshot(
                    projectId = project.id,
                    state = GradleOperationState.RUNNING,
                    sequence = 0,
                )
            state.value = running
            return running
        }

        override fun requireSnapshot(
            project: ProjectSession,
            operationId: String,
        ): GradleOperationSnapshot {
            checkProject(project, operationId)
            return state.value
        }

        override fun cancel(
            project: ProjectSession,
            operationId: String,
        ): GradleOperationSnapshot {
            checkProject(project, operationId)
            val cancelled =
                state.value.copy(
                    state = GradleOperationState.CANCELLED,
                    completedAtEpochMillis = 2,
                    durationMillis = 1,
                    cancelRequested = true,
                    sequence = state.value.sequence + 1,
                )
            state.value = cancelled
            return cancelled
        }

        override fun snapshots(
            project: ProjectSession,
            operationId: String,
        ): StateFlow<GradleOperationSnapshot> {
            checkProject(project, operationId)
            return state.asStateFlow()
        }

        fun complete() {
            state.value =
                state.value.copy(
                    state = GradleOperationState.SUCCEEDED,
                    completedAtEpochMillis = 2,
                    durationMillis = 1,
                    exitCode = 0,
                    stdoutTail = "done\n",
                    sequence = state.value.sequence + 1,
                )
        }

        private fun checkProject(
            project: ProjectSession,
            operationId: String,
        ) {
            check(operationId == "operation-1")
            check(project.id == projectId)
        }

        private companion object {
            fun snapshot(
                projectId: String,
                state: GradleOperationState,
                sequence: Long,
            ): GradleOperationSnapshot =
                GradleOperationSnapshot(
                    operationId = "operation-1",
                    projectId = projectId,
                    operation = "assemble",
                    task = "assemble",
                    state = state,
                    startedAtEpochMillis = 1,
                    completedAtEpochMillis = null,
                    durationMillis = 0,
                    exitCode = null,
                    stdoutTail = "",
                    stderrTail = "",
                    stdoutTruncated = false,
                    stderrTruncated = false,
                    cancelRequested = false,
                    sequence = sequence,
                    errorCode = null,
                    errorMessage = null,
                )
        }
    }

    private fun openRuneProject(): java.nio.file.Path {
        val root = Files.createTempDirectory("openrune-operation-api")
        root.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "OpenRune-Server"
            include("content", "engine", "server", "or-cache")
            """.trimIndent(),
        )
        root.resolve("gradlew").writeText("#!/bin/sh")
        root.resolve("gradlew.bat").writeText("@echo off")
        root.resolve("or-cache").createDirectories()
        root.resolve("or-cache/build.gradle.kts").writeText("plugins {}")
        root.resolve("content").createDirectories()
        root.resolve("engine").createDirectories()
        root.resolve("server").createDirectories()
        return root
    }

    private fun projectId(body: String): String =
        Regex("\\\"projectId\\\":\\\"([^\\\"]+)\\\"")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: error("projectId missing")

    private fun io.ktor.client.request.HttpRequestBuilder.auth() {
        header(HttpHeaders.Host, "localhost")
        header(StudioServiceSecurity.TOKEN_HEADER, TEST_TOKEN)
    }

    private fun pathBody(path: java.nio.file.Path): String {
        val jsonPath = path.toString().replace("\\", "\\\\")
        return "{\"path\":\"$jsonPath\"}"
    }

    private companion object {
        const val TEST_TOKEN = "test-token-with-at-least-32-characters"
        val TEST_SECURITY = StudioServiceSecurity(TEST_TOKEN)
    }
}
