package com.openrune.studio.companion.project

import com.openrune.studio.companion.ApiErrorCode
import com.openrune.studio.companion.ApiException
import com.openrune.studio.companion.openrune.OpenRuneProjectInspection
import com.openrune.studio.companion.openrune.OpenRuneProjectInspector
import com.openrune.studio.protocol.StudioCapabilities
import io.ktor.http.HttpStatusCode
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ProjectSessionView(
    val projectId: String,
    val root: String,
    val capabilities: List<String>,
    val inspection: OpenRuneProjectInspection,
)

data class ProjectSession(
    val id: String,
    val root: Path,
    val capabilities: List<String>,
    val inspection: OpenRuneProjectInspection,
) {
    fun view(): ProjectSessionView =
        ProjectSessionView(
            projectId = id,
            root = root.toString(),
            capabilities = capabilities,
            inspection = inspection,
        )

    fun location(key: String): Path {
        val raw =
            inspection.locations[key]?.path
                ?: throw ApiException(
                    code = ApiErrorCode.CAPABILITY_UNAVAILABLE,
                    status = HttpStatusCode.Conflict,
                    message = "Project location is unavailable: $key",
                )
        return requireWithinProject(Path.of(raw))
    }

    fun requireWithinProject(candidate: Path): Path {
        val normalized = candidate.toAbsolutePath().normalize()
        val checked =
            if (Files.exists(normalized)) {
                runCatching { normalized.toRealPath() }.getOrElse {
                    throw ApiException(
                        code = ApiErrorCode.PROJECT_PATH_INVALID,
                        status = HttpStatusCode.UnprocessableEntity,
                        message = "Project path could not be resolved.",
                        cause = it,
                    )
                }
            } else {
                normalized
            }

        if (!checked.startsWith(root)) {
            throw ApiException(
                code = ApiErrorCode.PATH_OUTSIDE_PROJECT,
                status = HttpStatusCode.Forbidden,
                message = "Resolved path is outside the opened project.",
            )
        }
        return checked
    }
}

class ProjectSessionManager(
    private val inspector: OpenRuneProjectInspector = OpenRuneProjectInspector(),
) {
    private val sessions = ConcurrentHashMap<String, ProjectSession>()

    fun open(rawPath: String): ProjectSessionView {
        val requested =
            try {
                Path.of(rawPath)
            } catch (failure: InvalidPathException) {
                throw ApiException(
                    code = ApiErrorCode.PROJECT_PATH_INVALID,
                    status = HttpStatusCode.BadRequest,
                    message = "Project path is invalid.",
                    cause = failure,
                )
            }

        val root =
            try {
                requested.toRealPath()
            } catch (failure: Exception) {
                throw ApiException(
                    code = ApiErrorCode.PROJECT_PATH_INVALID,
                    status = HttpStatusCode.UnprocessableEntity,
                    message = "Project root does not exist or cannot be resolved.",
                    cause = failure,
                )
            }

        if (!Files.isDirectory(root)) {
            throw ApiException(
                code = ApiErrorCode.PROJECT_PATH_INVALID,
                status = HttpStatusCode.UnprocessableEntity,
                message = "Project root is not a directory.",
            )
        }

        val inspection = inspector.inspect(root)
        if (!inspection.matched) {
            throw ApiException(
                code = ApiErrorCode.PROJECT_UNSUPPORTED,
                status = HttpStatusCode.UnprocessableEntity,
                message = "The selected directory is not a recognized OpenRune Server project.",
                details = mapOf("confidence" to inspection.confidence.toString()),
            )
        }

        validateKnownLocations(root, inspection)

        val capabilities =
            buildList {
                add(StudioCapabilities.ProjectInspection.id)
                if (inspection.locations["content"]?.exists == true) {
                    add(StudioCapabilities.ContentIndex.id)
                    add(StudioCapabilities.ContentResolve.id)
                    add(StudioCapabilities.SourceIndex.id)
                }
                if (
                    inspection.locations["liveCache"]?.exists == true ||
                        inspection.locations["serverCache"]?.exists == true
                ) {
                    add(StudioCapabilities.CacheRead.id)
                }
            }

        val id = UUID.randomUUID().toString()
        sessions[id] =
            ProjectSession(
                id = id,
                root = root,
                capabilities = capabilities,
                inspection = inspection,
            )
        return sessions.getValue(id).view()
    }

    fun require(projectId: String?): ProjectSession {
        if (projectId.isNullOrBlank()) {
            throw ApiException(
                code = ApiErrorCode.PROJECT_NOT_OPEN,
                status = HttpStatusCode.NotFound,
                message = "Project session was not specified.",
            )
        }
        return sessions[projectId]
            ?: throw ApiException(
                code = ApiErrorCode.PROJECT_NOT_OPEN,
                status = HttpStatusCode.NotFound,
                message = "Project session is not open.",
            )
    }

    private fun validateKnownLocations(root: Path, inspection: OpenRuneProjectInspection) {
        for ((name, location) in inspection.locations) {
            if (!location.exists) {
                continue
            }
            val resolved =
                runCatching { Path.of(location.path).toRealPath() }.getOrElse {
                    throw ApiException(
                        code = ApiErrorCode.PROJECT_PATH_INVALID,
                        status = HttpStatusCode.UnprocessableEntity,
                        message = "Project location could not be resolved: $name",
                        cause = it,
                    )
                }
            if (!resolved.startsWith(root)) {
                throw ApiException(
                    code = ApiErrorCode.PATH_OUTSIDE_PROJECT,
                    status = HttpStatusCode.Forbidden,
                    message = "Project location escapes the selected root: $name",
                )
            }
        }
    }
}
