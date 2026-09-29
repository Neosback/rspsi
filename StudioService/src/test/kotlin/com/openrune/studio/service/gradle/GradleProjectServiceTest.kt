package com.openrune.studio.service.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
