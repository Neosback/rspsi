package com.openrune.studio.service.openrune

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenRuneContentResolverTest {
    @Test
    fun resolvesGameValToHandlerAndPluginSource() {
        val root = Files.createTempDirectory("openrune-content-resolver")
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

            val result = OpenRuneContentResolver().resolve(root, "content.rock")

            assertTrue(result.found)
            assertEquals(1, result.gameVals.size)
            assertEquals(1, result.modules.size)
            assertEquals("skills/mining", result.modules.single().path)
            assertEquals(1, result.handlers.size)
            assertEquals("onOpContentLoc1", result.handlers.single().name)
            assertTrue(result.references.any { it.name == "content.rock" })
            assertEquals(1, result.pluginScripts.size)
            assertEquals("Mining", result.pluginScripts.single().name)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
