package com.openrune.studio.service

import com.fasterxml.jackson.databind.SerializationFeature
import com.openrune.studio.service.cache.OpenRuneCacheReader
import com.openrune.studio.service.gradle.DefaultGradleOperationService
import com.openrune.studio.service.gradle.GradleOperationService
import com.openrune.studio.service.gradle.GradleProjectService
import com.openrune.studio.service.gradle.GradleTaskDiscoveryService
import com.openrune.studio.service.project.ProjectIndexService
import com.openrune.studio.service.project.ProjectSessionManager
import com.openrune.studio.protocol.StudioCapabilities
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

private const val API_VERSION = 1

data class StudioServiceStatus(
    val name: String,
    val apiVersion: Int,
    val status: String,
    val capabilities: List<String>,
)

data class ProjectOpenRequest(val path: String = "")
data class ContentResolveRequest(val symbol: String = "")
data class GradleOperationRequest(val operation: String = "")

fun Application.studioServiceModule(
    security: StudioServiceSecurity = StudioServiceSecurity.create(),
    projectSessions: ProjectSessionManager = ProjectSessionManager(),
    openRuneCacheReader: OpenRuneCacheReader = OpenRuneCacheReader(),
    projectIndexes: ProjectIndexService = ProjectIndexService(),
    gradleProjects: GradleTaskDiscoveryService = GradleProjectService(),
    gradleOperations: GradleOperationService = DefaultGradleOperationService(),
) {
    installApiErrors()
    installStudioServiceSecurity(security)

    install(ContentNegotiation) {
        jackson {
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        get("/api/v1/status") {
            call.respond(
                HttpStatusCode.OK,
                StudioServiceStatus(
                    name = "OpenRune Server Studio",
                    apiVersion = API_VERSION,
                    status = "ready",
                    capabilities = listOf(StudioCapabilities.ProjectOpen.id),
                ),
            )
        }

        post("/api/v1/project/open") {
            val request = call.receive<ProjectOpenRequest>()
            if (request.path.isBlank()) {
                throw ApiException(
                    code = ApiErrorCode.INVALID_REQUEST,
                    status = HttpStatusCode.BadRequest,
                    message = "Project path is required.",
                )
            }
            call.respond(HttpStatusCode.OK, projectSessions.open(request.path))
        }

        get("/api/v1/project/{projectId}") {
            val project = projectSessions.require(call.parameters["projectId"])
            call.respond(HttpStatusCode.OK, project.view())
        }

        get("/api/v1/project/{projectId}/gradle/tasks") {
            val project = projectSessions.require(call.parameters["projectId"])
            requireCapability(project.capabilities, StudioCapabilities.GradleTasks.id)
            call.respond(HttpStatusCode.OK, gradleProjects.discoverTasks(project))
        }

        get("/api/v1/project/{projectId}/gradle/operations") {
            val project = projectSessions.require(call.parameters["projectId"])
            requireCapability(project.capabilities, StudioCapabilities.GradleOperations.id)
            call.respond(HttpStatusCode.OK, gradleOperations.catalog())
        }

        post("/api/v1/project/{projectId}/gradle/operations") {
            val project = projectSessions.require(call.parameters["projectId"])
            requireCapability(project.capabilities, StudioCapabilities.GradleOperations.id)
            val request = call.receive<GradleOperationRequest>()
            call.respond(
                HttpStatusCode.OK,
                gradleOperations.execute(project, request.operation),
            )
        }

        get("/api/v1/project/{projectId}/gradle/operations/{operationId}") {
            val project = projectSessions.require(call.parameters["projectId"])
            requireCapability(project.capabilities, StudioCapabilities.GradleOperations.id)
            call.respond(
                HttpStatusCode.OK,
                gradleOperations.requireResult(
                    project,
                    call.parameters["operationId"].orEmpty(),
                ),
            )
        }

        post("/api/v1/project/{projectId}/content/index") {
            val project = projectSessions.require(call.parameters["projectId"])
            requireCapability(project.capabilities, StudioCapabilities.ContentIndex.id)
            call.respond(HttpStatusCode.OK, projectIndexes.content(project))
        }

        post("/api/v1/project/{projectId}/content/resolve") {
            val project = projectSessions.require(call.parameters["projectId"])
            requireCapability(project.capabilities, StudioCapabilities.ContentResolve.id)
            val request = call.receive<ContentResolveRequest>()
            if (request.symbol.isBlank()) {
                throw ApiException(
                    code = ApiErrorCode.INVALID_REQUEST,
                    status = HttpStatusCode.BadRequest,
                    message = "Content symbol is required.",
                )
            }
            call.respond(
                HttpStatusCode.OK,
                projectIndexes.resolve(project, request.symbol),
            )
        }

        post("/api/v1/project/{projectId}/source/index") {
            val project = projectSessions.require(call.parameters["projectId"])
            requireCapability(project.capabilities, StudioCapabilities.SourceIndex.id)
            call.respond(HttpStatusCode.OK, projectIndexes.source(project))
        }

        post("/api/v1/project/{projectId}/index/refresh") {
            val project = projectSessions.require(call.parameters["projectId"])
            requireCapability(project.capabilities, StudioCapabilities.ContentIndex.id)
            requireCapability(project.capabilities, StudioCapabilities.SourceIndex.id)
            call.respond(HttpStatusCode.OK, projectIndexes.refresh(project))
        }

        get("/api/v1/project/{projectId}/cache/{role}/inspect") {
            val project = projectSessions.require(call.parameters["projectId"])
            requireCapability(project.capabilities, StudioCapabilities.CacheRead.id)
            val locationKey =
                when (call.parameters["role"]?.lowercase()) {
                    "live" -> "liveCache"
                    "server" -> "serverCache"
                    else ->
                        throw ApiException(
                            code = ApiErrorCode.CACHE_UNSUPPORTED,
                            status = HttpStatusCode.BadRequest,
                            message = "Cache role must be 'live' or 'server'.",
                        )
                }
            call.respond(
                HttpStatusCode.OK,
                openRuneCacheReader.inspect(project.location(locationKey)),
            )
        }
    }
}

private fun requireCapability(capabilities: List<String>, capability: String) {
    if (capability !in capabilities) {
        throw ApiException(
            code = ApiErrorCode.CAPABILITY_UNAVAILABLE,
            status = HttpStatusCode.Conflict,
            message = "Project capability is unavailable: $capability",
        )
    }
}
