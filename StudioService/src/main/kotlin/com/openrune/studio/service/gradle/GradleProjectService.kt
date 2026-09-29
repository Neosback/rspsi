package com.openrune.studio.service.gradle

import com.openrune.studio.service.ApiErrorCode
import com.openrune.studio.service.ApiException
import com.openrune.studio.service.project.ProjectSession
import io.ktor.http.HttpStatusCode
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/**
 * Discovers Gradle tasks only after an explicit API request.
 *
 * This service never accepts arbitrary Gradle arguments. It invokes the detected project wrapper
 * with one fixed discovery command and bounds execution time and captured output.
 */
class GradleProjectService(
    private val timeout: Duration = Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS),
    private val maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
) : GradleTaskDiscoveryService {
    init {
        require(!timeout.isNegative && !timeout.isZero) { "timeout must be positive" }
        require(maxOutputBytes > 0) { "maxOutputBytes must be positive" }
    }

    override suspend fun discoverTasks(project: ProjectSession): GradleTaskDiscovery =
        withContext(Dispatchers.IO) {
            val wrapper = project.location("gradleWrapper")
            val windows = isWindows()
            val expectedName = if (windows) "gradlew.bat" else "gradlew"

            if (
                !Files.isRegularFile(wrapper) ||
                    wrapper.parent != project.root ||
                    wrapper.fileName.toString() != expectedName
            ) {
                throw ApiException(
                    code = ApiErrorCode.GRADLE_UNAVAILABLE,
                    status = HttpStatusCode.Conflict,
                    message = "The opened project does not have a usable platform Gradle wrapper.",
                )
            }

            if (!windows && !Files.isExecutable(wrapper)) {
                throw ApiException(
                    code = ApiErrorCode.GRADLE_UNAVAILABLE,
                    status = HttpStatusCode.Conflict,
                    message = "The project's Gradle wrapper is not executable.",
                )
            }

            val command = wrapperCommand(expectedName, windows)
            val process =
                try {
                    ProcessBuilder(command)
                        .directory(project.root.toFile())
                        .redirectInput(ProcessBuilder.Redirect.PIPE)
                        .start()
                } catch (failure: Exception) {
                    throw ApiException(
                        code = ApiErrorCode.GRADLE_DISCOVERY_FAILED,
                        status = HttpStatusCode.UnprocessableEntity,
                        message = "Gradle task discovery could not be started.",
                        cause = failure,
                    )
                }

            process.outputStream.close()
            val readers = Executors.newFixedThreadPool(2)
            try {
                val stdoutFuture = readers.submit<CapturedOutput> {
                    capture(process.inputStream, maxOutputBytes)
                }
                val stderrFuture = readers.submit<CapturedOutput> {
                    capture(process.errorStream, maxOutputBytes)
                }

                val completed =
                    try {
                        process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        terminate(process)
                        throw ApiException(
                            code = ApiErrorCode.GRADLE_DISCOVERY_FAILED,
                            status = HttpStatusCode.ServiceUnavailable,
                            message = "Gradle task discovery was interrupted.",
                            cause = interrupted,
                        )
                    }

                if (!completed) {
                    terminate(process)
                    runCatching { awaitReader(stdoutFuture) }
                    runCatching { awaitReader(stderrFuture) }
                    throw ApiException(
                        code = ApiErrorCode.GRADLE_TIMEOUT,
                        status = HttpStatusCode.GatewayTimeout,
                        message = "Gradle task discovery exceeded the execution timeout.",
                        details = mapOf("timeoutMillis" to timeout.toMillis().toString()),
                    )
                }

                val stdout = awaitReader(stdoutFuture)
                val stderr = awaitReader(stderrFuture)
                if (stdout.truncated || stderr.truncated) {
                    throw ApiException(
                        code = ApiErrorCode.GRADLE_DISCOVERY_FAILED,
                        status = HttpStatusCode.UnprocessableEntity,
                        message = "Gradle task discovery exceeded the output limit.",
                        details = mapOf("maxOutputBytes" to maxOutputBytes.toString()),
                    )
                }

                if (process.exitValue() != 0) {
                    throw ApiException(
                        code = ApiErrorCode.GRADLE_DISCOVERY_FAILED,
                        status = HttpStatusCode.UnprocessableEntity,
                        message = "Gradle task discovery failed.",
                        details =
                            buildMap {
                                put("exitCode", process.exitValue().toString())
                                stderr.text.takeIf { it.isNotBlank() }?.let {
                                    put("stderr", it.take(MAX_ERROR_DETAIL_CHARS))
                                }
                            },
                    )
                }

                val tasks = parseGradleTasks(stdout.text)
                GradleTaskDiscovery(
                    wrapper = expectedName,
                    taskCount = tasks.size,
                    tasks = tasks,
                )
            } finally {
                readers.shutdownNow()
                if (process.isAlive) {
                    terminate(process)
                }
            }
        }

    private fun wrapperCommand(wrapperName: String, windows: Boolean): List<String> {
        val fixedArguments = listOf("tasks", "--all", "--console=plain", "--no-daemon")
        return if (windows) {
            listOf("cmd.exe", "/d", "/c", wrapperName) + fixedArguments
        } else {
            listOf("./$wrapperName") + fixedArguments
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private fun terminate(process: Process) {
        val descendants = process.toHandle().descendants().toList().asReversed()
        process.destroy()
        descendants.forEach { it.destroy() }

        try {
            if (!process.waitFor(TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                descendants.filter { it.isAlive }.forEach { it.destroyForcibly() }
                process.destroyForcibly()
                process.waitFor(TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            descendants.filter { it.isAlive }.forEach { it.destroyForcibly() }
            process.destroyForcibly()
        }
    }

    private fun awaitReader(future: java.util.concurrent.Future<CapturedOutput>): CapturedOutput =
        try {
            future.get(READER_JOIN_SECONDS, TimeUnit.SECONDS)
        } catch (failure: Exception) {
            throw ApiException(
                code = ApiErrorCode.GRADLE_DISCOVERY_FAILED,
                status = HttpStatusCode.ServiceUnavailable,
                message = "Gradle task discovery output could not be collected.",
                cause = failure,
            )
        }

    private data class CapturedOutput(
        val text: String,
        val truncated: Boolean,
    )

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 45L
        const val DEFAULT_MAX_OUTPUT_BYTES = 2 * 1024 * 1024
        const val MAX_ERROR_DETAIL_CHARS = 4000
        const val TERMINATION_GRACE_MILLIS = 500L
        const val READER_JOIN_SECONDS = 5L

        fun capture(stream: InputStream, limit: Int): CapturedOutput {
            val buffer = ByteArray(8192)
            val kept = java.io.ByteArrayOutputStream(minOf(limit, 64 * 1024))
            var total = 0L
            var truncated = false

            stream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    val remaining = (limit - kept.size()).coerceAtLeast(0)
                    if (remaining > 0) {
                        kept.write(buffer, 0, minOf(read, remaining))
                    }
                    total += read
                    if (total > limit) truncated = true
                }
            }

            return CapturedOutput(
                text = kept.toString(StandardCharsets.UTF_8),
                truncated = truncated,
            )
        }
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
