package com.openrune.studio.service.openrune

import java.nio.file.Files
import java.nio.file.Path

data class ProjectLocation(
    val path: String,
    val exists: Boolean,
)

data class OpenRuneProjectInspection(
    val root: String,
    val matched: Boolean,
    val confidence: Int,
    val evidence: List<String>,
    val capabilities: List<String>,
    val locations: Map<String, ProjectLocation>,
)

/**
 * Performs passive OpenRune project discovery without loading server code, running Gradle,
 * opening caches, or mutating the checkout.
 */
class OpenRuneProjectInspector {
    fun inspect(requestedRoot: Path): OpenRuneProjectInspection {
        val root = requestedRoot.toAbsolutePath().normalize()
        val settings = firstExisting(root.resolve("settings.gradle.kts"), root.resolve("settings.gradle"))
            ?: root.resolve("settings.gradle.kts")
        val wrapper = root.resolve(nativeWrapperName())
        val cacheModule = root.resolve("or-cache/build.gradle.kts")
        val content = root.resolve("content")
        val engine = root.resolve("engine")
        val server = root.resolve("server")
        val gamevals = root.resolve(".data/gamevals")
        val liveCache = root.resolve(".data/cache/LIVE")
        val serverCache = root.resolve(".data/cache/SERVER")

        val locations = linkedMapOf(
            "settings" to location(settings),
            "gradleWrapper" to location(wrapper),
            "cacheModule" to location(cacheModule),
            "content" to location(content),
            "engine" to location(engine),
            "server" to location(server),
            "gamevals" to location(gamevals),
            "liveCache" to location(liveCache),
            "serverCache" to location(serverCache),
        )

        if (!Files.isDirectory(root)) {
            return OpenRuneProjectInspection(
                root = root.toString(),
                matched = false,
                confidence = 0,
                evidence = listOf("project root does not exist"),
                capabilities = emptyList(),
                locations = locations,
            )
        }

        var confidence = 0
        val evidence = mutableListOf<String>()
        val capabilities = linkedSetOf<String>()

        if (Files.isRegularFile(settings)) {
            confidence += 10
            evidence += "Gradle settings detected"
            val text = runCatching { Files.readString(settings) }.getOrDefault("")
            if (text.contains("OpenRune-Server") || text.contains("\"or-cache\"") || text.contains("':or-cache'")) {
                confidence += 20
                evidence += "OpenRune/or-cache settings detected"
            }
        }

        if (Files.isRegularFile(wrapper)) {
            confidence += 10
            evidence += "Platform Gradle wrapper detected"
        }

        if (Files.isRegularFile(cacheModule)) {
            confidence += 25
            evidence += "or-cache module detected"
            capabilities += "cache-build-module"
        }

        if (Files.isDirectory(content)) {
            confidence += 15
            evidence += "content root detected"
            capabilities += "content-source"
        }

        if (Files.isDirectory(engine)) {
            confidence += 5
            evidence += "engine root detected"
        }

        if (Files.isDirectory(server)) {
            confidence += 5
            evidence += "server root detected"
        }

        if (Files.isDirectory(gamevals)) {
            confidence += 5
            evidence += "generated GameVals detected"
            capabilities += "gamevals"
        }

        if (Files.isDirectory(liveCache)) {
            evidence += "LIVE cache detected"
            capabilities += "live-cache"
        }

        if (Files.isDirectory(serverCache)) {
            evidence += "SERVER cache detected"
            capabilities += "server-cache"
        }

        if (Files.isRegularFile(wrapper) && Files.isRegularFile(settings)) {
            capabilities += "gradle-project"
        }

        return OpenRuneProjectInspection(
            root = root.toString(),
            matched = confidence >= MATCH_THRESHOLD,
            confidence = confidence.coerceAtMost(100),
            evidence = evidence,
            capabilities = capabilities.toList(),
            locations = locations,
        )
    }

    private fun firstExisting(vararg paths: Path): Path? = paths.firstOrNull { Files.exists(it) }

    private fun location(path: Path): ProjectLocation =
        ProjectLocation(path = path.toString(), exists = Files.exists(path))

    private fun nativeWrapperName(): String =
        if (System.getProperty("os.name").orEmpty().lowercase().contains("win")) {
            "gradlew.bat"
        } else {
            "gradlew"
        }

    private companion object {
        const val MATCH_THRESHOLD = 50
    }
}
