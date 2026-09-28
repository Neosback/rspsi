package com.openrune.studio.companion.openrune

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenRuneKotlinSourceIndexerTest {
    @Test
    fun indexesPluginHandlersSymbolsAndSourceSpans() {
        val root = Files.createTempDirectory("openrune-source-index")
        try {
            val sourceRoot = root.resolve("content/skills/mining/src/main/kotlin/org/example")
            sourceRoot.createDirectories()
            sourceRoot.resolve("Mining.kt").writeText(
                """
                package org.example

                class Mining : PluginScript() {
                    fun ScriptContext.startup() {
                        onOpContentLoc1("content.rock") { mine() }
                    }

                    private fun mine() {
                        val skill = "stat.mining"
                    }
                }
                """.trimIndent(),
            )

            val result = OpenRuneKotlinSourceIndexer().index(root)

            assertEquals(1, result.fileCount)
            assertTrue(result.diagnostics.isEmpty())
            assertTrue(result.facts.any {
                it.kind == SourceFactKind.PLUGIN_SCRIPT &&
                    it.name == "Mining" &&
                    it.modulePath == "skills/mining"
            })

            val handler =
                result.facts.single {
                    it.kind == SourceFactKind.SCRIPT_HANDLER &&
                        it.name == "onOpContentLoc1"
                }
            assertEquals(listOf("content.rock"), handler.arguments)
            assertEquals("content/skills/mining/src/main/kotlin/org/example/Mining.kt", handler.source.path)
            assertTrue(handler.source.startLine >= 4)

            assertTrue(result.facts.any {
                it.kind == SourceFactKind.SYMBOL_REFERENCE && it.name == "content.rock"
            })
            assertTrue(result.facts.any {
                it.kind == SourceFactKind.SYMBOL_REFERENCE && it.name == "stat.mining"
            })
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
