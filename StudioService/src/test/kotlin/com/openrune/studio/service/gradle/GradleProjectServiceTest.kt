package com.openrune.studio.service.gradle

import com.openrune.studio.service.ApiErrorCode
import com.openrune.studio.service.ApiException
import com.openrune.studio.service.openrune.OpenRuneProjectInspection
import com.openrune.studio.service.openrune.ProjectLocation
import com.openrune.studio.service.project.ProjectSession
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradleProjectServiceTest {
    @Test
    fun parsesGroupedGradleTaskOutputIntoStableTaskPaths() {
        val output =
            """
            > Task :tasks

            Build tasks
            -----------
            assemble - Assembles the outputs of this project.
            build - Assembles and tests this project.
            or-cache:buildCache - Builds OpenRune caches.

            Verification tasks
            ------------------
            check - Runs all checks.
            content:mining:test - Runs mining tests.

            Other tasks
            -----------
            server:run
            """.trimIndent()

        val tasks = parseGradleTasks(output)

        assertEquals(
            listOf(
                ":assemble",
                ":build",
                ":check",
                ":content:mining:test",
                ":or-cache:buildCache",
                ":server:run",
            ),
            tasks.map { it.path },
        )
        assertEquals("build", tasks.first { it.path == ":or-cache:buildCache" }.group)
        assertEquals(
            "Builds OpenRune caches.",
            tasks.first { it.path == ":or-cache:buildCache" }.description,
        )
        assertEquals("verification", tasks.first { it.path == ":check" }.group)
        assertNull(tasks.first { it.path == ":server:run" }.description)
    }

    @Test
    fun ignoresGradleNoiseOutsideTaskGroups() {
        val output =
            """
            Welcome to Gradle 8.14.3!

            Tasks runnable from root project 'OpenRune-Server'

            Help tasks
            ----------
            help - Displays a help message.
            projects - Displays the sub-projects.

            To see all tasks and more detail, run gradlew tasks --all
            BUILD SUCCESSFUL in 2s
            """.trimIndent()

        val tasks = parseGradleTasks(output)

        assertEquals(listOf(":help", ":projects"), tasks.map { it.path })
    }

    @Test
    fun executesFixedWrapperCommandFromProjectRootWithSpecialCharactersInPath() {
        if (isWindows()) return

        val root = Files.createTempDirectory("openrune gradle & project ")
        try {
            val wrapper =
                createWrapper(
                    root,
                    """
                    #!/bin/sh
                    printf '%s\n' \
                      'Build tasks' \
                      '-----------' \
                      'or-cache:buildCache - Builds OpenRune caches.' \
                      '' \
                      'Application tasks' \
                      '-----------------' \
                      'server:run - Runs the server.'
                    """.trimIndent(),
                )
            val project = projectSession(root, wrapper)

            val result =
                runBlocking {
                    GradleProjectService(timeout = Duration.ofSeconds(5)).discoverTasks(project)
                }

            assertEquals("gradlew", result.wrapper)
            assertEquals(2, result.taskCount)
            assertEquals(
                listOf(":or-cache:buildCache", ":server:run"),
                result.tasks.map { it.path },
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun reportsGradleFailureWithStableErrorCode() {
        if (isWindows()) return

        val root = Files.createTempDirectory("openrune-gradle-failure")
        try {
            val wrapper =
                createWrapper(
                    root,
                    """
                    #!/bin/sh
                    echo 'configuration failed' >&2
                    exit 7
                    """.trimIndent(),
                )
            val project = projectSession(root, wrapper)

            val failure =
                assertFailsWith<ApiException> {
                    runBlocking {
                        GradleProjectService(timeout = Duration.ofSeconds(5)).discoverTasks(project)
                    }
                }

            assertEquals(ApiErrorCode.GRADLE_DISCOVERY_FAILED, failure.code)
            assertEquals("7", failure.details["exitCode"])
            assertTrue(failure.details["stderr"].orEmpty().contains("configuration failed"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun cancellationTerminatesRunningWrapperAndChildProcess() {
        if (isWindows()) return

        val root = Files.createTempDirectory("openrune-gradle-cancel")
        try {
            val wrapper =
                createWrapper(
                    root,
                    """
                    #!/bin/sh
                    echo 'started'
                    sleep 10
                    echo 'finished'
                    """.trimIndent(),
                )
            val project = projectSession(root, wrapper)
            val cancel = AtomicBoolean(false)
            val outputObserved = CompletableDeferred<Unit>()

            val failure =
                runBlocking {
                    val execution =
                        async {
                            runCatching {
                                GradleProcessRunner().executeControlled(
                                    project = project,
                                    arguments =
                                        listOf(
                                            "assemble",
                                            "--console=plain",
                                            "--no-daemon",
                                        ),
                                    timeout = Duration.ofSeconds(5),
                                    maxOutputBytes = 4096,
                                    control =
                                        GradleExecutionControl(
                                            isCancellationRequested = cancel::get,
                                            onOutput = { stream, text ->
                                                if (
                                                    stream == GradleOutputStream.STDOUT &&
                                                        "started" in text
                                                ) {
                                                    outputObserved.complete(Unit)
                                                }
                                            },
                                        ),
                                )
                            }.exceptionOrNull()
                        }

                    outputObserved.await()
                    cancel.set(true)
                    execution.await()
                }

            val processFailure = failure as? GradleProcessException
            assertEquals(GradleProcessFailure.CANCELLED, processFailure?.reason)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun timesOutAndTerminatesLongRunningWrapper() {
        if (isWindows()) return

        val root = Files.createTempDirectory("openrune-gradle-timeout")
        try {
            val wrapper =
                createWrapper(
                    root,
                    """
                    #!/bin/sh
                    sleep 10
                    """.trimIndent(),
                )
            val project = projectSession(root, wrapper)

            val failure =
                assertFailsWith<ApiException> {
                    runBlocking {
                        GradleProjectService(timeout = Duration.ofMillis(150)).discoverTasks(project)
                    }
                }

            assertEquals(ApiErrorCode.GRADLE_TIMEOUT, failure.code)
            assertEquals("150", failure.details["timeoutMillis"])
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun createWrapper(root: Path, script: String): Path {
        val wrapper = root.resolve("gradlew")
        wrapper.writeText(script + "\n")
        assertTrue(wrapper.toFile().setExecutable(true), "test wrapper must be executable")
        return wrapper
    }

    private fun projectSession(root: Path, wrapper: Path): ProjectSession {
        val realRoot = root.toRealPath()
        return ProjectSession(
            id = "project-id",
            root = realRoot,
            capabilities = listOf("gradle.tasks"),
            inspection =
                OpenRuneProjectInspection(
                    root = realRoot.toString(),
                    matched = true,
                    confidence = 100,
                    evidence = emptyList(),
                    capabilities = listOf("gradle-project"),
                    locations =
                        mapOf(
                            "gradleWrapper" to
                                ProjectLocation(
                                    path = wrapper.toRealPath().toString(),
                                    exists = true,
                                ),
                        ),
                ),
        )
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")
}
