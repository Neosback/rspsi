package com.rspsi.studio

import com.rspsi.cache.workspace.OsrsCacheHealth
import com.rspsi.editor.integration.IntegrationCapability
import com.rspsi.editor.integration.IntegrationOptions
import com.rspsi.editor.integration.ServerIntegrationService
import com.rspsi.project.ProjectIntegrationCapability
import com.rspsi.project.StudioProjectDescriptor
import com.rspsi.project.StudioProjectKind
import com.rspsi.project.StudioProjectService
import com.rspsi.server.ServerConnection
import com.rspsi.server.ServerPathKey
import com.rspsi.server.ServerProjectInspection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import java.nio.file.Path
import java.util.EnumSet
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

/**
 * Structured-concurrency boundary for opening a Studio project.
 *
 * Project open is intentionally lightweight: validate the descriptor, establish the
 * project-owned OpenRune session when applicable, resolve cache roles, and prove that
 * FileStore can open LIVE. Definition/content decoding belongs to the workspace that
 * actually needs it.
 */
class ProjectOpenCoordinator(
    private val projects: StudioProjectService,
    private val integrations: ServerIntegrationService,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun openAsync(
        project: StudioProjectDescriptor,
        progress: Consumer<ProjectLoadStatus>,
    ): CompletableFuture<ProjectOpenSnapshot> =
        scope.future {
            progress.accept(
                ProjectLoadStatus(
                    ProjectLoadStatus.Phase.VALIDATE_PROJECT,
                    0.10,
                    "Validating project...",
                    project.sourcePathValue().toString(),
                    null,
                ),
            )
            projects.validateSource(project)

            val cachePath: Path
            val inspection: ServerProjectInspection?

            if (project.kind() == StudioProjectKind.OPENRUNE_SERVER) {
                val root = project.sourcePathValue()
                progress.accept(
                    ProjectLoadStatus(
                        ProjectLoadStatus.Phase.INSPECT_INTEGRATION,
                        0.18,
                        "Finding OpenRune project files...",
                        root.toString(),
                        null,
                    ),
                )

                val options = IntegrationOptions.defaults(root, startupCapabilities(project))
                val session = integrations.connect(ServerConnection.forRoot(root), options)
                inspection =
                    session.projectInspection().orElseThrow {
                        IllegalStateException("OpenRune project inspection is unavailable")
                    }

                progress.accept(
                    ProjectLoadStatus(
                        ProjectLoadStatus.Phase.RESOLVE_CACHE_ROLES,
                        0.42,
                        "Resolving LIVE and SERVER cache roles...",
                        "Revision ${inspection.revision()}",
                        null,
                    ),
                )
                cachePath =
                    inspection.path(ServerPathKey.LIVE_CACHE).orElseThrow {
                        IllegalStateException(
                            "OpenRune LIVE cache was not found. Build or repair the project cache first.",
                        )
                    }
            } else {
                inspection = null
                cachePath = project.sourcePathValue()
            }

            progress.accept(
                ProjectLoadStatus(
                    ProjectLoadStatus.Phase.VERIFY_CACHE,
                    0.68,
                    "Checking FileStore and cache identity...",
                    cachePath.toString(),
                    null,
                ),
            )
            val health = OsrsCacheHealth.inspect(cachePath)

            progress.accept(
                ProjectLoadStatus(
                    ProjectLoadStatus.Phase.BIND_REQUIRED_PROJECT_SERVICES,
                    0.94,
                    "Preparing Content Studio...",
                    "Definition and content decoding remains deferred until a workspace requests it",
                    null,
                ),
            )

            ProjectOpenSnapshot(project, cachePath, health, inspection)
        }

    override fun close() {
        scope.cancel("Project open coordinator closed")
    }

    companion object {
        /**
         * Startup may bind build authorization, but never content/source providers.
         * RSCM/GameVals, content manifests, PSI, semantic graphs and spawn indexes are lazy.
         */
        @JvmStatic
        fun startupCapabilities(project: StudioProjectDescriptor): Set<IntegrationCapability> {
            val capabilities = EnumSet.noneOf(IntegrationCapability::class.java)
            if (project.capabilities().contains(ProjectIntegrationCapability.CACHE_BUILD)) {
                capabilities.add(IntegrationCapability.CACHE_BUILD)
            }
            return capabilities.toSet()
        }
    }
}

data class ProjectOpenSnapshot(
    val project: StudioProjectDescriptor,
    val cachePath: Path,
    val cacheHealth: OsrsCacheHealth,
    val inspection: ServerProjectInspection?,
)
