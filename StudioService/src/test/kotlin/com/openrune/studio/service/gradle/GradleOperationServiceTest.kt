package com.openrune.studio.service.gradle

import com.openrune.studio.service.ApiErrorCode
import com.openrune.studio.service.ApiException
import com.openrune.studio.service.openrune.OpenRuneProjectInspection
import com.openrune.studio.service.openrune.ProjectLocation
import com.openrune.studio.service.project.ProjectSession
import java.nio.file.Files
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class GradleOperationServiceTest {
    @Test
    fun catalogContainsOnlySupportedOperations() {
        val service = DefaultGradleOperationService(runner = RecordingRunner())
        val catalog = service.catalog()

        assertEquals(listOf("assemble", "test", "cache-build"), catalog.map { it.id })
        assertEquals(
            listOf("assemble", "test", ":or-cache:buildCache"),
            catalog.map { it.task },
        )
        assertTrue(catalog.all { it.timeoutMillis > 0 })
    }

    @Test
    fun operationIdsMapToFixedGradleArguments() = runBlocking {
        val runner = RecordingRunner()
        val service = DefaultGradleOperationService(runner = runner)
        val project = projectSession("project-one")

        service.execute(project, "assemble")
        service.execute(project, "test")
        val cache = service.execute(project, " CACHE-BUILD ")

        assertEquals(
            listOf(
                listOf("assemble", "--console=plain", "--no-daemon"),
                listOf("test", "--console=plain", "--no-daemon"),
                listOf(":or-cache:buildCache", "--console=plain", "--no-daemon"),
            ),
            runner.arguments,
        )
        assertEquals("cache-build", cache.operation)
        assertEquals(":or-cache:buildCache", cache.task)
        assertEquals(GradleOperationState.SUCCEEDED, cache.state)
    }

    @Test
    fun arbitraryGradleTaskIsRejectedBeforeRunnerIsCalled() = runBlocking {
        val runner = RecordingRunner()
        val service = DefaultGradleOperationService(runner = runner)
        val project = projectSession("project-one")

        val failure =
            try {
                service.execute(project, ":server:app:run")
                fail("expected invalid operation")
            } catch (failure: ApiException) {
                failure
            }

        assertEquals(ApiErrorCode.GRADLE_OPERATION_INVALID, failure.code)
        assertTrue(runner.arguments.isEmpty())
    }

    @Test
    fun nonZeroExitIsStoredAsFailedOperation() = runBlocking {
        val runner =
            RecordingRunner(
                result =
                    GradleProcessResult(
                        exitCode = 7,
                        stdout = "build output",
                        stderr = "compile failed",
                        stdoutTruncated = true,
                        stderrTruncated = false,
                        durationMillis = 42,
                    ),
            )
        val service = DefaultGradleOperationService(runner = runner)
        val project = projectSession("project-one")

        val result = service.execute(project, "assemble")
        val stored = service.requireResult(project, result.operationId)

        assertEquals(GradleOperationState.FAILED, result.state)
        assertEquals(7, result.exitCode)
        assertEquals("compile failed", result.stderr)
        assertTrue(result.stdoutTruncated)
        assertEquals(result, stored)
    }

    @Test
    fun timeoutBecomesTerminalOperationResult() = runBlocking {
        val runner =
            RecordingRunner(
                failure =
                    GradleProcessException(
                        reason = GradleProcessFailure.TIMEOUT,
                        message = "timed out",
                    ),
            )
        val service = DefaultGradleOperationService(runner = runner)
        val project = projectSession("project-one")

        val result = service.execute(project, "test")

        assertEquals(GradleOperationState.TIMED_OUT, result.state)
        assertEquals(null, result.exitCode)
        assertEquals(result, service.requireResult(project, result.operationId))
    }

    @Test
    fun operationResultsAreScopedToOwningProject() = runBlocking {
        val service = DefaultGradleOperationService(runner = RecordingRunner())
        val first = projectSession("project-one")
        val second = projectSession("project-two")
        val result = service.execute(first, "assemble")

        val failure =
            try {
                service.requireResult(second, result.operationId)
                fail("expected result lookup to be project scoped")
            } catch (failure: ApiException) {
                failure
            }

        assertEquals(ApiErrorCode.GRADLE_OPERATION_NOT_FOUND, failure.code)
    }

    @Test
    fun rejectsConcurrentOperationsForSameCheckoutAcrossSessions() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val runner =
            object : GradleCommandRunner {
                override suspend fun execute(
                    project: ProjectSession,
                    arguments: List<String>,
                    timeout: Duration,
                    maxOutputBytes: Int,
                ): GradleProcessResult {
                    entered.complete(Unit)
                    release.await()
                    return successResult()
                }
            }

        val service = DefaultGradleOperationService(runner = runner)
        val project = projectSession("project-one")
        val secondSession = project.copy(id = "project-two")
        val first = async { service.execute(project, "assemble") }
        entered.await()

        val failure =
            try {
                service.execute(secondSession, "test")
                fail("expected project to reject concurrent Gradle operation")
            } catch (failure: ApiException) {
                failure
            }

        assertEquals(ApiErrorCode.GRADLE_OPERATION_BUSY, failure.code)
        release.complete(Unit)
        assertEquals(GradleOperationState.SUCCEEDED, first.await().state)
    }

    private class RecordingRunner(
        private val result: GradleProcessResult = successResult(),
        private val failure: GradleProcessException? = null,
    ) : GradleCommandRunner {
        val arguments = mutableListOf<List<String>>()

        override suspend fun execute(
            project: ProjectSession,
            arguments: List<String>,
            timeout: Duration,
            maxOutputBytes: Int,
        ): GradleProcessResult {
            this.arguments += arguments
            failure?.let { throw it }
            return result
        }
    }

    private fun projectSession(id: String): ProjectSession {
        val root = Files.createTempDirectory("openrune-operation-test").toRealPath()
        val wrapperName =
            if (System.getProperty("os.name").orEmpty().lowercase().contains("win")) {
                "gradlew.bat"
            } else {
                "gradlew"
            }
        val wrapper = root.resolve(wrapperName)
        Files.writeString(wrapper, if (wrapperName.endsWith(".bat")) "@echo off" else "#!/bin/sh")
        if (!wrapperName.endsWith(".bat")) {
            assertTrue(wrapper.toFile().setExecutable(true))
        }

        return ProjectSession(
            id = id,
            root = root,
            capabilities = listOf("gradle.tasks", "gradle.operations"),
            inspection =
                OpenRuneProjectInspection(
                    root = root.toString(),
                    matched = true,
                    confidence = 100,
                    evidence = emptyList(),
                    capabilities = listOf("gradle-project"),
                    locations =
                        mapOf(
                            "gradleWrapper" to
                                ProjectLocation(
                                    path = wrapper.toString(),
                                    exists = true,
                                ),
                        ),
                ),
        )
    }

    private companion object {
        fun successResult(): GradleProcessResult =
            GradleProcessResult(
                exitCode = 0,
                stdout = "ok",
                stderr = "",
                stdoutTruncated = false,
                stderrTruncated = false,
                durationMillis = 5,
            )
    }
}
