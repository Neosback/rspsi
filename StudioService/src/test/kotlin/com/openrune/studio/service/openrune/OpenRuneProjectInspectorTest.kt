package com.openrune.studio.service.openrune

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenRuneProjectInspectorTest {
    private val inspector = OpenRuneProjectInspector()

    @Test
    fun detectsAnOpenRuneCheckoutWithoutGeneratedCaches() {
        val root = Files.createTempDirectory("openrune-project")
        try {
            root.resolve("settings.gradle.kts").writeText(
                """
                rootProject.name = "OpenRune-Server"
                include("api", "content", "engine", "server", "or-cache")
                """.trimIndent(),
            )
            root.resolve("gradlew").writeText("#!/bin/sh")
            root.resolve("or-cache").createDirectories()
            root.resolve("or-cache/build.gradle.kts").writeText("plugins { id(\"base-conventions\") }")
            root.resolve("content").createDirectories()
            root.resolve("engine").createDirectories()
            root.resolve("server").createDirectories()

            val result = inspector.inspect(root)

            assertTrue(result.matched)
            assertTrue(result.confidence >= 50)
            assertTrue("cache-build-module" in result.capabilities)
            assertTrue("content-source" in result.capabilities)
            assertTrue("gradle-project" in result.capabilities)
            assertFalse(result.locations.getValue("liveCache").exists)
            assertFalse(result.locations.getValue("gamevals").exists)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun reportsMissingDirectoryWithoutGuessingThatItIsOpenRune() {
        val missing = Files.createTempDirectory("openrune-missing").resolve("not-there")
        val result = inspector.inspect(missing)

        assertFalse(result.matched)
        assertEquals(0, result.confidence)
        assertTrue(result.capabilities.isEmpty())
    }
}
