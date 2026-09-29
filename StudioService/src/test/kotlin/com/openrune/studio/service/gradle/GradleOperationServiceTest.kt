package com.openrune.studio.service.gradle

import com.openrune.studio.service.ApiErrorCode
import com.openrune.studio.service.ApiException
import com.openrune.studio.service.openrune.OpenRuneProjectInspection
import com.openrune.studio.service.openrune.ProjectLocation
import com.openrune.studio.service.project.ProjectSession
import java.nio.file.Files
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class GradleOperationServiceTest {
    @Test
    fun catalogContainsOnlySupportedOperations() {
        val service = DefaultGradleOperationService(runner = ImmediateRunner())
        val catalog = service.catalog()

        assertEquals(listOf("assemble", "test", "cache-build"), catalog.map { it.id })
        assertEquals(
            listOf("assemble", "test", ":or-cache:buildCache"),
            catalog.map { it.task },
        )
        assertTrue(catalog.all { it.timeoutMillis > 0 })
    }

    @Test
    fun startReturnsRunningAndOperationIdsMapToFixedGradleArguments() = runBlocking {
        val runner = GateRunner()
        val service = DefaultGradleOperationService(runner = runner)
        val project = projectSession("project-one")

        val assemble = service.start(project, "assemble")
        assertEquals(GradleOperationState.RUNNING, assemble.state)
        runner.awaitStart()
        runner.release()
        service.snapshots(project, assemble.operationId).first { it.state.terminal }

        val test = service.start(project, "test")
        runner.awaitStart()
        runner.release()
        service.snapshots(project, test.operationId).first { it.state.terminal }

        val cache = service.start(project, " CACHE-BUILD ")
        runner.awaitStart()
        runner.release()
        val cacheDone = service.snapshots(project, cache.operationId).first { it.state.terminal }

        assertEquals(
            listOf(
                listOf("assemble", "--console=plain", "--no-daemon"),
                listOf("test", "--console=plain", "--no-daemon"),
                listOf(":or-cache:buildCache", "--console=plain", "--no-daemon"),
            ),
            runner.arguments,
        )
        assertEquals("cache-build", cacheDone.operation)
        assertEquals(":or-cache:buildCache", cacheDone.task)
        assertEquals(GradleOperationState.SUCCEEDED, cacheDone.state)
    }

    @Test
    fun arbitraryGradleTaskIsRejectedBeforeRunnerIsCalled() {
        val runner = ImmediateRunner()
        val service = DefaultGradleOperationService(runner = runner)
        val project = projectSession("project-one")

        val failure =
            try {
                service.start(project, ":server:app:run")
                fail("expected invalid operation")
            } catch (failure: ApiException) {
                failure
            }

        assertEquals(ApiErrorCode.GRADLE_OPERATION_INVALID, failure.code)
        assertEquals(0, runner.calls)
    }

    @Test
    fun nonZeroExitBecomesFailedTerminalSnapshot() = runBlocking {
        val runner =
            ImmediateRunner(
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

        val started = service.start(project, "assemble")
        val result =
            service.snapshots(project, started.operationId)
                .first { it.state.terminal }

        assertEquals(GradleOperationState.FAILED, result.state)
        assertEquals(7, result.exitCode)
        assertEquals("compile failed", result.stderrTail)
        assertTrue(result.stdoutTruncated)
        assertEquals(ApiErrorCode.GRADLE_EXECUTION_FAILED.name, result.errorCode)
        assertEquals(result, service.requireSnapshot(project, result.operationId))
    }

    @Test
    fun timeoutBecomesTerminalSnapshot() = runBlocking {
        val runner =
            ImmediateRunner(
                failure =
                    GradleProcessException(
                        reason = GradleProcessFailure.TIMEOUT,
                        message = "timed out",
                    ),
            )
        val service = DefaultGradleOperationService(runner = runner)
        val project = projectSession("project-one")

        val started = service.start(project, "test")
        val result =
            service.snapshots(project, started.operationId)
                .first { it.state.terminal }

        assertEquals(GradleOperationState.TIMED_OUT, result.state)
        assertEquals(null, result.exitCode)
        assertEquals(ApiErrorCode.GRADLE_TIMEOUT.name, result.errorCode)
    }

    @Test
    fun cancellationStreamsLogsAndEndsCancelled() = runBlocking {
        val runner = CancellableRunner()
        val service = DefaultGradleOperationService(runner = runner, liveLogChars = 64)
        val project = projectSession("project-one")

        val started = service.start(project, "assemble")
        val withLog =
            service.snapshots(project, started.operationId)
                .first { "starting" in it.stdoutTail }

        assertEquals(GradleOperationState.RUNNING, withLog.state)

        val cancelling = service.cancel(project, started.operationId)
        assertTrue(cancelling.cancelRequested)

        val cancelled =
            service.snapshots(project, started.operationId)
                .first { it.state == GradleOperationState.CANCELLED }

        assertTrue(cancelled.cancelRequested)
        assertEquals("starting\n", cancelled.stdoutTail)
    }

    @Test
    fun liveLogTailIsBounded() = runBlocking {
        val runner =
            object : GradleCommandRunner {
                override suspend fun execute(
                    project: ProjectSession,
                    arguments: List<String>,
                    timeout: Duration,
                    maxOutputBytes: Int,
                ): GradleProcessResult = successResult()

                override suspend fun executeControlled(
                    project: ProjectSession,
                    arguments: List<String>,
                    timeout: Duration,
                    maxOutputBytes: Int,
                    control: GradleExecutionControl,
                ): GradleProcessResult {
                    control.onOutput(GradleOutputStream.STDOUT, "abcdef")
                    return successResult(stdout = "abcdef")
                }
            }
        val service = DefaultGradleOperationService(runner = runner, liveLogChars = 4)
        val project = projectSession("project-one")

        val started = service.start(project, "assemble")
        val result =
            service.snapshots(project, started.operationId)
                .first { it.state.terminal }

        assertEquals("cdef", result.stdoutTail)
        assertTrue(result.stdoutTruncated)
    }

    @Test
    fun operationSnapshotsAreScopedToOwningProject() = runBlocking {
        val service = DefaultGradleOperationService(runner = ImmediateRunner())
        val first = projectSession("project-one")
        val second = projectSession("project-two")
        val started = service.start(first, "assemble")
        service.snapshots(first, started.operationId).first { it.state.terminal }

        val failure =
            try {
                service.requireSnapshot(second, started.operationId)
                fail("expected result lookup to be project scoped")
            } catch (failure: ApiException) {
                failure
            }

        assertEquals(ApiErrorCode.GRADLE_OPERATION_NOT_FOUND, failure.code)
    }

    @Test
    fun rejectsConcurrentOperationsForSameCheckoutAcrossSessions() = runBlocking {
        val runner = GateRunner()
        val service = DefaultGradleOperationService(runner = runner)
        val project = projectSession("project-one")
        val secondSession = project.copy(id = "project-two")

        val first = service.start(project, "assemble")
        runner.awaitStart()

        val failure =
            try {
                service.start(secondSession, "test")
                fail("expected checkout to reject concurrent Gradle operation")
            } catch (failure: ApiException) {
                failure
            }

        assertEquals(ApiErrorCode.GRADLE_OPERATION_BUSY, failure.code)
        runner.release()
        service.snapshots(project, first.operationId).first { it.state.terminal }
    }

    private class ImmediateRunner(
        private val result: GradleProcessResult = successResult(),
        private val failure: GradleProcessException? = null,
    ) : GradleCommandRunner {
        var calls = 0

        override suspend fun execute(
            project: ProjectSession,
            arguments: List<String>,
            timeout: Duration,
            maxOutputBytes: Int,
        ): GradleProcessResult {
            calls += 1
            failure?.let { throw it }
            return result
        }
    }

    private class GateRunner : GradleCommandRunner {
        private var entered = CompletableDeferred<Unit>()
        private var release = CompletableDeferred<Unit>()
        val arguments = mutableListOf<List<String>>()

        override suspend fun execute(
            project: ProjectSession,
            arguments: List<String>,
            timeout: Duration,
            maxOutputBytes: Int,
        ): GradleProcessResult {
            this.arguments += arguments
            entered.complete(Unit)
            release.await()
            return successResult()
        }

        suspend fun awaitStart() {
            entered.await()
        }

        fun release() {
            release.complete(Unit)
            entered = CompletableDeferred()
            release = CompletableDeferred()
        }
    }

    private class CancellableRunner : GradleCommandRunner {
        override suspend fun execute(
            project: ProjectSession,
            arguments: List<String>,
            timeout: Duration,
            maxOutputBytes: Int,
        ): GradleProcessResult = error("controlled execution expected")

        override suspend fun executeControlled(
            project: ProjectSession,
            arguments: List<String>,
            timeout: Duration,
            maxOutputBytes: Int,
            control: GradleExecutionControl,
        ): GradleProcessResult {
            control.onOutput(GradleOutputStream.STDOUT, "starting\n")
            while (!control.isCancellationRequested()) {
                delay(10)
            }
            throw GradleProcessException(
                reason = GradleProcessFailure.CANCELLED,
                message = "cancelled",
            )
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
        fun successResult(stdout: String = "ok"): GradleProcessResult =
            GradleProcessResult(
                exitCode = 0,
                stdout = stdout,
                stderr = "",
                stdoutTruncated = false,
                stderrTruncated = false,
                durationMillis = 5,
            )
    }
}
