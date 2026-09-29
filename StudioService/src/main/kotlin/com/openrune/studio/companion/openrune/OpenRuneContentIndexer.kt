package com.openrune.studio.companion.openrune

import java.nio.file.Files
import java.nio.file.Path
import org.tomlj.Toml

data class ContentModuleEntry(
    val path: String,
    val category: String,
    val name: String,
    val gameValCount: Int,
)

data class GameValEntry(
    val namespace: String,
    val name: String,
    val id: Long,
    val qualifiedName: String,
    val sourceType: String,
    val sourcePath: String,
    val modulePath: String?,
)

data class OpenRuneContentIndex(
    val root: String,
    val moduleCount: Int,
    val skillModuleCount: Int,
    val gameValCount: Int,
    val modules: List<ContentModuleEntry>,
    val gameVals: List<GameValEntry>,
    val diagnostics: List<String>,
)

class OpenRuneContentIndexer {
    fun index(requestedRoot: Path): OpenRuneContentIndex {
        val root = requestedRoot.toAbsolutePath().normalize()
        require(Files.isDirectory(root)) { "OpenRune project root is not a directory: $root" }

        val contentRoot = root.resolve("content")
        require(Files.isDirectory(contentRoot)) { "OpenRune content root was not found: $contentRoot" }

        val diagnostics = mutableListOf<String>()
        val sourceGameVals = scanSourceGameVals(root, contentRoot, diagnostics)
        val generatedGameVals = scanGeneratedGameVals(root, diagnostics)
        val allGameVals =
            (sourceGameVals + generatedGameVals)
                .sortedWith(compareBy(GameValEntry::namespace, GameValEntry::name, GameValEntry::sourceType))

        val countsByModule =
            sourceGameVals
                .mapNotNull { entry -> entry.modulePath?.let { it to 1 } }
                .groupingBy { it.first }
                .eachCount()
                .mapValues { (_, count) -> count }

        val modules =
            scanModules(contentRoot)
                .map { module ->
                    ContentModuleEntry(
                        path = module,
                        category = module.substringBefore('/'),
                        name = module.substringAfterLast('/'),
                        gameValCount = countsByModule[module] ?: 0,
                    )
                }

        return OpenRuneContentIndex(
            root = root.toString(),
            moduleCount = modules.size,
            skillModuleCount = modules.count { it.category == "skills" },
            gameValCount = allGameVals.size,
            modules = modules,
            gameVals = allGameVals,
            diagnostics = diagnostics,
        )
    }

    private fun scanModules(contentRoot: Path): List<String> =
        Files.walk(contentRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.fileName.toString() == "build.gradle.kts" }
                .map { path -> normalizeRelative(contentRoot, path.parent) }
                .sorted()
                .toList()
        }

    private fun scanSourceGameVals(
        root: Path,
        contentRoot: Path,
        diagnostics: MutableList<String>,
    ): List<GameValEntry> =
        Files.walk(contentRoot).use { paths ->
            paths
                .filter { path ->
                    Files.isRegularFile(path) &&
                        path.fileName.toString() == "gamevals.toml" &&
                        path.toString().replace('\\', '/').contains("/src/main/resources/")
                }
                .sorted()
                .flatMap { file ->
                    val relative = normalizeRelative(root, file)
                    val modulePath =
                        relative.substringAfter("content/")
                            .substringBefore("/src/main/resources/")
                    parseTomlGameVals(file, relative, modulePath, diagnostics).stream()
                }
                .toList()
        }

    private fun parseTomlGameVals(
        file: Path,
        relativePath: String,
        modulePath: String,
        diagnostics: MutableList<String>,
    ): List<GameValEntry> {
        val result = Toml.parse(file)
        if (result.hasErrors()) {
            diagnostics += "$relativePath: ${result.errors().joinToString()}"
            return emptyList()
        }

        val gamevals = result.getTable("gamevals") ?: return emptyList()
        val entries = mutableListOf<GameValEntry>()
        for (namespace in gamevals.keySet().sorted()) {
            val namespaceTable = gamevals.getTable(namespace) ?: continue
            for (name in namespaceTable.keySet().sorted()) {
                val id = namespaceTable.getLong(name)
                if (id == null) {
                    diagnostics += "$relativePath: gamevals.$namespace.$name is not an integer"
                    continue
                }
                entries +=
                    GameValEntry(
                        namespace = namespace,
                        name = name,
                        id = id,
                        qualifiedName = "$namespace.$name",
                        sourceType = "plugin-toml",
                        sourcePath = relativePath,
                        modulePath = modulePath,
                    )
            }
        }
        return entries
    }

    private fun scanGeneratedGameVals(
        root: Path,
        diagnostics: MutableList<String>,
    ): List<GameValEntry> {
        val generatedRoot = root.resolve(".data/gamevals")
        if (!Files.isDirectory(generatedRoot)) {
            return emptyList()
        }

        return Files.list(generatedRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".rscm") }
                .sorted()
                .flatMap { file ->
                    val namespace = file.fileName.toString().removeSuffix(".rscm")
                    val relative = normalizeRelative(root, file)
                    parseRscm(file, relative, namespace, diagnostics).stream()
                }
                .toList()
        }
    }

    private fun parseRscm(
        file: Path,
        relativePath: String,
        namespace: String,
        diagnostics: MutableList<String>,
    ): List<GameValEntry> {
        val entries = mutableListOf<GameValEntry>()
        Files.readAllLines(file).forEachIndexed { index, rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) {
                return@forEachIndexed
            }
            val separator = line.indexOf('=')
            if (separator <= 0 || separator == line.lastIndex) {
                diagnostics += "$relativePath:${index + 1}: invalid RSCM entry"
                return@forEachIndexed
            }

            val name = line.substring(0, separator).trim()
            val id = line.substring(separator + 1).trim().toLongOrNull()
            if (name.isEmpty() || id == null) {
                diagnostics += "$relativePath:${index + 1}: invalid RSCM entry"
                return@forEachIndexed
            }

            entries +=
                GameValEntry(
                    namespace = namespace,
                    name = name,
                    id = id,
                    qualifiedName = "$namespace.$name",
                    sourceType = "generated-rscm",
                    sourcePath = relativePath,
                    modulePath = null,
                )
        }
        return entries
    }

    private fun normalizeRelative(root: Path, path: Path): String =
        root.relativize(path).toString().replace('\\', '/')
}
