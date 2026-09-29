package com.openrune.studio.companion.project

import com.openrune.studio.companion.ApiErrorCode
import com.openrune.studio.companion.ApiException
import com.openrune.studio.companion.openrune.OpenRuneContentIndex
import com.openrune.studio.companion.openrune.OpenRuneContentIndexer
import com.openrune.studio.companion.openrune.OpenRuneContentResolver
import com.openrune.studio.companion.openrune.OpenRuneKotlinSourceIndexer
import com.openrune.studio.companion.openrune.OpenRuneSourceIndex
import com.openrune.studio.companion.openrune.ResolvedContentSymbol
import io.ktor.http.HttpStatusCode
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class ProjectIndexSnapshot(
    val projectId: String,
    val generation: Long,
    val fingerprint: String,
    val builtAtEpochMillis: Long,
    val content: OpenRuneContentIndex,
    val source: OpenRuneSourceIndex,
)

data class ProjectIndexRefresh(
    val projectId: String,
    val generation: Long,
    val fingerprint: String,
    val builtAtEpochMillis: Long,
    val contentModuleCount: Int,
    val gameValCount: Int,
    val sourceFileCount: Int,
    val sourceFactCount: Int,
)

class ProjectIndexService(
    private val contentIndex: (Path) -> OpenRuneContentIndex = OpenRuneContentIndexer()::index,
    private val sourceIndex: (Path) -> OpenRuneSourceIndex = OpenRuneKotlinSourceIndexer()::index,
    private val resolver: OpenRuneContentResolver = OpenRuneContentResolver(),
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "openrune-studio-index").apply { isDaemon = true }
        },
) : AutoCloseable {
    private data class Cached(
        val snapshot: ProjectIndexSnapshot,
    )

    private val cache = HashMap<String, Cached>()

    suspend fun snapshot(project: ProjectSession, force: Boolean = false): ProjectIndexSnapshot =
        onIndexThread {
            val fingerprint = fingerprint(project.root)
            val existing = cache[project.id]?.snapshot
            if (!force && existing != null && existing.fingerprint == fingerprint) {
                return@onIndexThread existing
            }

            try {
                val content = contentIndex(project.root)
                val source = sourceIndex(project.root)
                val snapshot =
                    ProjectIndexSnapshot(
                        projectId = project.id,
                        generation = (existing?.generation ?: 0L) + 1L,
                        fingerprint = fingerprint,
                        builtAtEpochMillis = System.currentTimeMillis(),
                        content = content,
                        source = source,
                    )
                cache[project.id] = Cached(snapshot)
                snapshot
            } catch (failure: ApiException) {
                throw failure
            } catch (failure: Exception) {
                throw ApiException(
                    code = ApiErrorCode.INDEX_FAILED,
                    status = HttpStatusCode.UnprocessableEntity,
                    message = "Project content/source indexing failed.",
                    cause = failure,
                )
            }
        }

    suspend fun content(project: ProjectSession): OpenRuneContentIndex = snapshot(project).content

    suspend fun source(project: ProjectSession): OpenRuneSourceIndex = snapshot(project).source

    suspend fun resolve(project: ProjectSession, symbol: String): ResolvedContentSymbol {
        val snapshot = snapshot(project)
        return resolver.resolve(snapshot.content, snapshot.source, symbol)
    }

    suspend fun refresh(project: ProjectSession): ProjectIndexRefresh {
        val snapshot = snapshot(project, force = true)
        return snapshot.summary()
    }

    private fun ProjectIndexSnapshot.summary(): ProjectIndexRefresh =
        ProjectIndexRefresh(
            projectId = projectId,
            generation = generation,
            fingerprint = fingerprint,
            builtAtEpochMillis = builtAtEpochMillis,
            contentModuleCount = content.moduleCount,
            gameValCount = content.gameValCount,
            sourceFileCount = source.fileCount,
            sourceFactCount = source.factCount,
        )

    private fun fingerprint(root: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val files = mutableListOf<Path>()

        val content = root.resolve("content")
        if (Files.isDirectory(content)) {
            Files.walk(content).use { paths ->
                paths
                    .filter { path ->
                        Files.isRegularFile(path) &&
                            (
                                path.fileName.toString().endsWith(".kt") ||
                                    path.fileName.toString() == "gamevals.toml" ||
                                    path.fileName.toString() == "build.gradle.kts"
                            )
                    }
                    .forEach(files::add)
            }
        }

        val gamevals = root.resolve(".data/gamevals")
        if (Files.isDirectory(gamevals)) {
            Files.list(gamevals).use { paths ->
                paths
                    .filter { path ->
                        Files.isRegularFile(path) && path.fileName.toString().endsWith(".rscm")
                    }
                    .forEach(files::add)
            }
        }

        for (file in files.sortedBy { root.relativize(it).toString() }) {
            val relative = root.relativize(file).toString().replace('\\', '/')
            val attributes = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes::class.java)
            digest.update(relative.toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(attributes.size().toString().toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(attributes.lastModifiedTime().toMillis().toString().toByteArray(Charsets.UTF_8))
            digest.update(0)
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun <T> onIndexThread(block: () -> T): T =
        suspendCoroutine { continuation ->
            executor.execute {
                try {
                    continuation.resume(block())
                } catch (failure: Throwable) {
                    continuation.resumeWithException(failure)
                }
            }
        }

    override fun close() {
        executor.shutdownNow()
    }
}
