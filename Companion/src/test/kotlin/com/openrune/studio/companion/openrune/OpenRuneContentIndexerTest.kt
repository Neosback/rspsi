package com.openrune.studio.companion.openrune

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenRuneContentIndexerTest {
    @Test
    fun indexesSkillModulesPluginGameValsAndGeneratedRscm() {
        val root = Files.createTempDirectory("openrune-content-index")
        try {
            val mining = root.resolve("content/skills/mining")
            mining.createDirectories()
            mining.resolve("build.gradle.kts").writeText("plugins {}")

            val resources = mining.resolve("src/main/resources")
            resources.createDirectories()
            resources.resolve("gamevals.toml").writeText(
                """
                [gamevals.content]
                rock = 52
                mining_pickaxe = 53

                [gamevals.dbtable]
                mining_rocks = 55520
                """.trimIndent(),
            )

            val generated = root.resolve(".data/gamevals")
            generated.createDirectories()
            generated.resolve("content.rscm").writeText(
                """
                tree=32
                rock=52
                """.trimIndent(),
            )

            val result = OpenRuneContentIndexer().index(root)

            assertEquals(1, result.moduleCount)
            assertEquals(1, result.skillModuleCount)
            assertEquals(5, result.gameValCount)
            assertTrue(result.diagnostics.isEmpty())

            val module = result.modules.single()
            assertEquals("skills/mining", module.path)
            assertEquals("skills", module.category)
            assertEquals("mining", module.name)
            assertEquals(3, module.gameValCount)

            assertTrue(result.gameVals.any {
                it.qualifiedName == "content.rock" &&
                    it.id == 52L &&
                    it.sourceType == "plugin-toml" &&
                    it.modulePath == "skills/mining"
            })
            assertTrue(result.gameVals.any {
                it.qualifiedName == "content.tree" &&
                    it.id == 32L &&
                    it.sourceType == "generated-rscm"
            })
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
