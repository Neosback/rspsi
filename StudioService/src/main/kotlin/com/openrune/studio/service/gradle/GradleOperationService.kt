package com.openrune.studio.service.gradle

import com.openrune.studio.service.ApiErrorCode
import com.openrune.studio.service.ApiException
import com.openrune.studio.service.project.ProjectSession
import io.ktor.http.HttpStatusCode
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

enum class GradleOperationState {
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
}

data class GradleOperationDescriptor(
    val id: String,
    val task: String,
    val timeoutMillis: Long,
)

data class GradleOperationResult(
    val operationId: String,
    val projectId: String,
    val operation: String,
    val task: String,
    val state: GradleOperationState,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val durationMillis: Long,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val stdoutTruncated: Boolean,
    val stderrTruncated: Boolean,
)

interface GradleOperationService {
    fun catalog(): List<GradleOperationDescriptor>

    suspend fun execute(
        project: ProjectSession,
        operationId: String,
    ): GradleOperationResult

    fun requireResult(
        project: ProjectSession,
        operationId: String,
    ): GradleOperationResult
}

class DefaultGradleOperationService(
    private val runner: GradleCommandRunner = GradleProcessRunner(),
    private val maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
) : GradleOperationService {
    private val activeProjects = ConcurrentHashMap.newKeySet<String>()
    private val results = ConcurrentHashMap<String, GradleOperationResult>()
    private val resultOrder = ConcurrentLinkedDeque<String>()

    init {
        require(maxOutputBytes > 0) { "maxOutputBytes must be positive" }
    }

    override fun catalog(): List<GradleOperationDescriptor> =
        SupportedOperation.entries.map { operation ->
            GradleOperationDescriptor(
                id = operation.id,
                task = operation.task,
                timeoutMillis = operation.timeout.toMillis(),
            )
        }

    override suspend fun execute(
        project: ProjectSession,
        operationId: String,
    ): GradleOperationResult {
        val operation =
            SupportedOperation.fromId(operationId)
                ?: throw ApiException(
                    code = ApiErrorCode.GRADLE_OPERATION_INVALID,
                    status = HttpStatusCode.BadRequest,
                    message = "Unsupported Gradle operation.",
                    details = mapOf("operation" to operationId),
                )

        if (!activeProjects.add(project.id)) {
            throw ApiException(
                code = ApiErrorCode.GRADLE_OPERATION_BUSY,
                status = HttpStatusCode.Conflict,
                message = "A Gradle operation is already active for this project.",
            )
        }

        val resultId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()

        try {
            val process =
                try {
                    runner.execute(
                        project = project,
                        arguments =
                            listOf(
                                operation.task,
                                "--console=plain",
                                "--no-daemon",
                            ),
                        timeout = operation.timeout,
                        maxOutputBytes = maxOutputBytes,
                    )
                } catch (failure: GradleProcessException) {
                    if (failure.reason == GradleProcessFailure.TIMEOUT) {
                        return store(
                            GradleOperationResult(
                                operationId = resultId,
                                projectId = project.id,
                                operation = operation.id,
                                task = operation.task,
                                state = GradleOperationState.TIMED_OUT,
                                startedAtEpochMillis = startedAt,
                                completedAtEpochMillis = System.currentTimeMillis(),
                                durationMillis =
                                    (System.currentTimeMillis() - startedAt).coerceAtLeast(0),
                                exitCode = null,
                                stdout = "",
                                stderr = "",
                                stdoutTruncated = false,
                                stderrTruncated = false,
                            ),
                        )
                    }
                    throw failure.toOperationApiException()
                }

            val completedAt = System.currentTimeMillis()
            return store(
                GradleOperationResult(
                    operationId = resultId,
                    projectId = project.id,
                    operation = operation.id,
                    task = operation.task,
                    state =
                        if (process.exitCode == 0) {
                            GradleOperationState.SUCCEEDED
                        } else {
                            GradleOperationState.FAILED
                        },
                    startedAtEpochMillis = startedAt,
                    completedAtEpochMillis = completedAt,
                    durationMillis = process.durationMillis,
                    exitCode = process.exitCode,
                    stdout = process.stdout,
                    stderr = process.stderr,
                    stdoutTruncated = process.stdoutTruncated,
                    stderrTruncated = process.stderrTruncated,
                ),
            )
        } finally {
            activeProjects.remove(project.id)
        }
    }

    override fun requireResult(
        project: ProjectSession,
        operationId: String,
    ): GradleOperationResult {
        val result = results[operationId]
        if (result == null || result.projectId != project.id) {
            throw ApiException(
                code = ApiErrorCode.GRADLE_OPERATION_NOT_FOUND,
                status = HttpStatusCode.NotFound,
                message = "Gradle operation was not found for this project.",
            )
        }
        return result
    }

    private fun store(result: GradleOperationResult): GradleOperationResult {
        results[result.operationId] = result
        resultOrder.addLast(result.operationId)

        while (resultOrder.size > MAX_RETAINED_RESULTS) {
            resultOrder.pollFirst()?.let(results::remove)
        }

        return result
    }

    private fun GradleProcessException.toOperationApiException(): ApiException =
        when (reason) {
            GradleProcessFailure.WRAPPER_UNAVAILABLE ->
                ApiException(
                    code = ApiErrorCode.GRADLE_UNAVAILABLE,
                    status = HttpStatusCode.Conflict,
                    message = message,
                    details = details,
                    cause = this,
                )
            GradleProcessFailure.START_FAILED ->
                ApiException(
                    code = ApiErrorCode.GRADLE_EXECUTION_FAILED,
                    status = HttpStatusCode.UnprocessableEntity,
                    message = "Gradle operation could not be started.",
                    details = details,
                    cause = this,
                )
            GradleProcessFailure.INTERRUPTED,
            GradleProcessFailure.OUTPUT_FAILED,
            ->
                ApiException(
                    code = ApiErrorCode.GRADLE_EXECUTION_FAILED,
                    status = HttpStatusCode.ServiceUnavailable,
                    message = "Gradle operation could not complete.",
                    details = details,
                    cause = this,
                )
            GradleProcessFailure.TIMEOUT ->
                error("Timeout is converted to a terminal operation result before this mapping.")
        }

    private enum class SupportedOperation(
        val id: String,
        val task: String,
        val timeout: Duration,
    ) {
        ASSEMBLE(
            id = "assemble",
            task = "assemble",
            timeout = Duration.ofMinutes(15),
        ),
        TEST(
            id = "test",
            task = "test",
            timeout = Duration.ofMinutes(15),
        ),
        CACHE_BUILD(
            id = "cache-build",
            task = ":or-cache:buildCache",
            timeout = Duration.ofMinutes(20),
        ),
        ;

        companion object {
            fun fromId(id: String): SupportedOperation? =
                entries.firstOrNull { it.id == id.trim().lowercase() }
        }
    }

    private companion object {
        const val DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024
        const val MAX_RETAINED_RESULTS = 100
    }
}
