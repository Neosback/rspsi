package com.openrune.studio.service.gradle

import com.openrune.studio.service.ApiErrorCode
import com.openrune.studio.service.ApiException
import com.openrune.studio.service.project.ProjectSession
import io.ktor.http.HttpStatusCode
import java.time.Duration

data class GradleTaskInfo(
    val path: String,
    val group: String?,
    val description: String?,
)

data class GradleTaskDiscovery(
    val wrapper: String,
    val taskCount: Int,
    val tasks: List<GradleTaskInfo>,
)

interface GradleTaskDiscoveryService {
    suspend fun discoverTasks(project: ProjectSession): GradleTaskDiscovery
}

class GradleProjectService(
    private val runner: GradleCommandRunner = GradleProcessRunner(),
    private val timeout: Duration = Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS),
    private val maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
) : GradleTaskDiscoveryService {
    override suspend fun discoverTasks(project: ProjectSession): GradleTaskDiscovery {
        val result =
            try {
                runner.execute(
                    project = project,
                    arguments = DISCOVERY_ARGUMENTS,
                    timeout = timeout,
                    maxOutputBytes = maxOutputBytes,
                )
            } catch (failure: GradleProcessException) {
                throw failure.toDiscoveryApiException()
            }

        if (result.stdoutTruncated || result.stderrTruncated) {
            throw ApiException(
                code = ApiErrorCode.GRADLE_DISCOVERY_FAILED,
                status = HttpStatusCode.UnprocessableEntity,
                message = "Gradle task discovery exceeded the output limit.",
                details = mapOf("maxOutputBytes" to maxOutputBytes.toString()),
            )
        }

        if (result.exitCode != 0) {
            throw ApiException(
                code = ApiErrorCode.GRADLE_DISCOVERY_FAILED,
                status = HttpStatusCode.UnprocessableEntity,
                message = "Gradle task discovery failed.",
                details =
                    buildMap {
                        put("exitCode", result.exitCode.toString())
                        result.stderr.takeIf { it.isNotBlank() }?.let {
                            put("stderr", it.take(MAX_ERROR_DETAIL_CHARS))
                        }
                    },
            )
        }

        val tasks = parseGradleTasks(result.stdout)
        return GradleTaskDiscovery(
            wrapper = if (isWindows()) "gradlew.bat" else "gradlew",
            taskCount = tasks.size,
            tasks = tasks,
        )
    }

    private fun GradleProcessException.toDiscoveryApiException(): ApiException =
        when (reason) {
            GradleProcessFailure.WRAPPER_UNAVAILABLE ->
                ApiException(
                    code = ApiErrorCode.GRADLE_UNAVAILABLE,
                    status = HttpStatusCode.Conflict,
                    message = message,
                    details = details,
                    cause = this,
                )
            GradleProcessFailure.TIMEOUT ->
                ApiException(
                    code = ApiErrorCode.GRADLE_TIMEOUT,
                    status = HttpStatusCode.GatewayTimeout,
                    message = "Gradle task discovery exceeded the execution timeout.",
                    details = details,
                    cause = this,
                )
            GradleProcessFailure.INTERRUPTED,
            GradleProcessFailure.OUTPUT_FAILED,
            ->
                ApiException(
                    code = ApiErrorCode.GRADLE_DISCOVERY_FAILED,
                    status = HttpStatusCode.ServiceUnavailable,
                    message = "Gradle task discovery could not complete.",
                    details = details,
                    cause = this,
                )
            GradleProcessFailure.START_FAILED ->
                ApiException(
                    code = ApiErrorCode.GRADLE_DISCOVERY_FAILED,
                    status = HttpStatusCode.UnprocessableEntity,
                    message = "Gradle task discovery could not be started.",
                    details = details,
                    cause = this,
                )
        }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 45L
        const val DEFAULT_MAX_OUTPUT_BYTES = 2 * 1024 * 1024
        const val MAX_ERROR_DETAIL_CHARS = 4000
        val DISCOVERY_ARGUMENTS =
            listOf("tasks", "--all", "--console=plain", "--no-daemon")
    }
}

internal fun parseGradleTasks(output: String): List<GradleTaskInfo> {
    val lines = output.lineSequence().toList()
    val tasks = linkedMapOf<String, GradleTaskInfo>()
    var group: String? = null
    var inTaskGroup = false

    var index = 0
    while (index < lines.size) {
        val trimmed = lines[index].trim()
        val next = lines.getOrNull(index + 1)?.trim().orEmpty()

        if (trimmed.isNotEmpty() && next.length >= 3 && next.all { it == '-' }) {
            group = normalizeGroup(trimmed)
            inTaskGroup = trimmed.endsWith(" tasks", ignoreCase = true)
            index += 2
            continue
        }

        if (inTaskGroup) {
            if (trimmed.isEmpty()) {
                group = null
                inTaskGroup = false
            } else {
                parseTaskLine(trimmed, group)?.let { task ->
                    tasks.putIfAbsent(task.path, task)
                }
            }
        }

        index++
    }

    return tasks.values.sortedBy { it.path }
}

private fun parseTaskLine(line: String, group: String?): GradleTaskInfo? {
    val parts = line.split(" - ", limit = 2)
    val rawPath = parts[0].trim()
    if (!rawPath.matches(TASK_PATH)) return null

    val path = if (rawPath.startsWith(':')) rawPath else ":$rawPath"
    val description = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    return GradleTaskInfo(path = path, group = group, description = description)
}

private fun normalizeGroup(header: String): String =
    header
        .replace(Regex("(?i)\\s+tasks$"), "")
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

private val TASK_PATH = Regex(":?[A-Za-z0-9_.-]+(?::[A-Za-z0-9_.-]+)*")
