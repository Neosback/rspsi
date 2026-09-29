package com.openrune.studio.service.cache

import dev.openrune.filesystem.Cache
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

data class CacheIndexInspection(
    val id: Int,
    val archiveCount: Int,
)

data class OpenRuneCacheInspection(
    val path: String,
    val backend: String,
    val writeMode: String,
    val indexCount: Int,
    val archiveCount: Int,
    val detectedRevision: Int?,
    val indices: List<CacheIndexInspection>,
)

/**
 * Read-only FileStore adapter for StudioService.
 *
 * This deliberately exposes archive structure and optional revision metadata only.
 * The service exposes bounded cache metadata without taking ownership of generated cache content.
 */
class OpenRuneCacheReader(
    private val backendVersion: String = "3.0.3",
) {
    fun inspect(requestedPath: Path): OpenRuneCacheInspection {
        val path = requestedPath.toAbsolutePath().normalize()
        require(Files.isDirectory(path)) { "cache path is not a directory: $path" }

        val cache = try {
            Cache.Companion.load(path)
        } catch (failure: RuntimeException) {
            throw IllegalArgumentException("unable to open cache with OpenRune FileStore", failure)
        }

        try {
            val indices =
                cache.indices()
                    .sorted()
                    .map { indexId ->
                        CacheIndexInspection(
                            id = indexId,
                            archiveCount = cache.archives(indexId).size,
                        )
                    }

            return OpenRuneCacheInspection(
                path = path.toString(),
                backend = "OpenRune FileStore $backendVersion",
                writeMode = "read-only",
                indexCount = indices.size,
                archiveCount = indices.sumOf { it.archiveCount },
                detectedRevision = detectRevision(cache),
                indices = indices,
            )
        } finally {
            cache.close()
        }
    }

    private fun detectRevision(cache: Cache): Int? {
        val versionArchive = runCatching { cache.archiveId(VERSION_INDEX, VERSION_ARCHIVE) }.getOrDefault(-1)
        if (versionArchive < 0) {
            return null
        }

        val data =
            runCatching { cache.data(VERSION_INDEX, versionArchive, 0, null) }
                .getOrNull()
                ?: return null

        if (data.size < VERSION_PAYLOAD_BYTES) {
            return null
        }

        val buffer = ByteBuffer.wrap(data)
        buffer.short
        return buffer.int.takeIf { it > 0 }
    }

    private companion object {
        const val VERSION_INDEX = 12
        const val VERSION_ARCHIVE = "version.dat"
        const val VERSION_PAYLOAD_BYTES = 6
    }
}
