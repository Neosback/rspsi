package com.openrune.studio.service.gradle

import com.openrune.studio.service.project.ProjectSession
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class GradleProcessFailure {
    WRAPPER_UNAVAILABLE,
    START_FAILED,
    INTERRUPTED,
    TIMEOUT,
    CANCELLED,
    OUTPUT_FAILED,
}

enum class GradleOutputStream {
    STDOUT,
    STDERR,
}

data class GradleExecutionControl(
    val isCancellationRequested: () -> Boolean = { false },
    val onOutput: (GradleOutputStream, String) -> Unit = { _, _ -> },
)

class GradleProcessException(
    val reason: GradleProcessFailure,
    override val message: String,
    val details: Map<String, String> = emptyMap(),
    cause: Throwable? = null,
) : RuntimeException(message, cause)

data class GradleProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val stdoutTruncated: Boolean,
    val stderrTruncated: Boolean,
    val durationMillis: Long,
)

interface GradleCommandRunner {
    suspend fun execute(
        project: ProjectSession,
        arguments: List<String>,
        timeout: Duration?,
        maxOutputBytes: Int,
    ): GradleProcessResult

    suspend fun executeControlled(
        project: ProjectSession,
        arguments: List<String>,
        timeout: Duration?,
        maxOutputBytes: Int,
        control: GradleExecutionControl,
    ): GradleProcessResult =
        execute(
            project = project,
            arguments = arguments,
            timeout = timeout,
            maxOutputBytes = maxOutputBytes,
        )
}

class GradleProcessRunner : GradleCommandRunner {
    override suspend fun execute(
        project: ProjectSession,
        arguments: List<String>,
        timeout: Duration?,
        maxOutputBytes: Int,
    ): GradleProcessResult =
        executeControlled(
            project = project,
            arguments = arguments,
            timeout = timeout,
            maxOutputBytes = maxOutputBytes,
            control = GradleExecutionControl(),
        )

    override suspend fun executeControlled(
        project: ProjectSession,
        arguments: List<String>,
        timeout: Duration?,
        maxOutputBytes: Int,
        control: GradleExecutionControl,
    ): GradleProcessResult =
        withContext(Dispatchers.IO) {
            require(arguments.isNotEmpty()) { "Gradle arguments must not be empty" }
            timeout?.let { require(!it.isNegative && !it.isZero) { "timeout must be positive" } }
            require(maxOutputBytes > 0) { "maxOutputBytes must be positive" }

            if (control.isCancellationRequested()) {
                throw GradleProcessException(
                    reason = GradleProcessFailure.CANCELLED,
                    message = "Gradle execution was cancelled before process start.",
                )
            }

            val wrapper = project.location("gradleWrapper")
            val windows = isWindows()
            val expectedName = if (windows) "gradlew.bat" else "gradlew"

            if (
                !Files.isRegularFile(wrapper) ||
                    wrapper.parent != project.root ||
                    wrapper.fileName.toString() != expectedName
            ) {
                throw GradleProcessException(
                    reason = GradleProcessFailure.WRAPPER_UNAVAILABLE,
                    message = "The opened project does not have a usable platform Gradle wrapper.",
                )
            }

            if (!windows && !Files.isExecutable(wrapper)) {
                throw GradleProcessException(
                    reason = GradleProcessFailure.WRAPPER_UNAVAILABLE,
                    message = "The project's Gradle wrapper is not executable.",
                )
            }

            val process =
                try {
                    ProcessBuilder(wrapperCommand(expectedName, windows, arguments))
                        .directory(project.root.toFile())
                        .redirectInput(ProcessBuilder.Redirect.PIPE)
                        .start()
                } catch (failure: Exception) {
                    throw GradleProcessException(
                        reason = GradleProcessFailure.START_FAILED,
                        message = "Gradle could not be started.",
                        cause = failure,
                    )
                }

            val startedAt = System.nanoTime()
            val deadline = timeout?.let { startedAt + it.toNanos() }
            process.outputStream.close()
            val readers = Executors.newFixedThreadPool(2)

            try {
                val stdoutFuture = readers.submit<CapturedOutput> {
                    captureTail(
                        stream = process.inputStream,
                        limit = maxOutputBytes,
                        outputStream = GradleOutputStream.STDOUT,
                        onOutput = control.onOutput,
                    )
                }
                val stderrFuture = readers.submit<CapturedOutput> {
                    captureTail(
                        stream = process.errorStream,
                        limit = maxOutputBytes,
                        outputStream = GradleOutputStream.STDERR,
                        onOutput = control.onOutput,
                    )
                }

                while (true) {
                    if (control.isCancellationRequested()) {
                        terminate(process)
                        runCatching { awaitReader(stdoutFuture) }
                        runCatching { awaitReader(stderrFuture) }
                        throw GradleProcessException(
                            reason = GradleProcessFailure.CANCELLED,
                            message = "Gradle execution was cancelled.",
                        )
                    }

                    val remainingNanos = deadline?.minus(System.nanoTime())
                    if (remainingNanos != null && remainingNanos <= 0) {
                        terminate(process)
                        runCatching { awaitReader(stdoutFuture) }
                        runCatching { awaitReader(stderrFuture) }
                        throw GradleProcessException(
                            reason = GradleProcessFailure.TIMEOUT,
                            message = "Gradle execution exceeded the configured timeout.",
                            details =
                                mapOf(
                                    "timeoutMillis" to
                                        checkNotNull(timeout).toMillis().toString(),
                                ),
                        )
                    }

                    val waitMillis =
                        if (remainingNanos == null) {
                            POLL_INTERVAL_MILLIS
                        } else {
                            minOf(
                                POLL_INTERVAL_MILLIS,
                                TimeUnit.NANOSECONDS
                                    .toMillis(remainingNanos)
                                    .coerceAtLeast(1),
                            )
                        }

                    val completed =
                        try {
                            process.waitFor(waitMillis, TimeUnit.MILLISECONDS)
                        } catch (interrupted: InterruptedException) {
                            Thread.currentThread().interrupt()
                            terminate(process)
                            throw GradleProcessException(
                                reason = GradleProcessFailure.INTERRUPTED,
                                message = "Gradle execution was interrupted.",
                                cause = interrupted,
                            )
                        }

                    if (completed) {
                        break
                    }
                }

                val stdout = awaitReader(stdoutFuture)
                val stderr = awaitReader(stderrFuture)

                GradleProcessResult(
                    exitCode = process.exitValue(),
                    stdout = stdout.text,
                    stderr = stderr.text,
                    stdoutTruncated = stdout.truncated,
                    stderrTruncated = stderr.truncated,
                    durationMillis =
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                )
            } finally {
                readers.shutdownNow()
                if (process.isAlive) {
                    terminate(process)
                }
            }
        }

    private fun wrapperCommand(
        wrapperName: String,
        windows: Boolean,
        arguments: List<String>,
    ): List<String> =
        if (windows) {
            listOf("cmd.exe", "/d", "/c", wrapperName) + arguments
        } else {
            listOf("./$wrapperName") + arguments
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
            throw GradleProcessException(
                reason = GradleProcessFailure.OUTPUT_FAILED,
                message = "Gradle output could not be collected.",
                cause = failure,
            )
        }

    private fun captureTail(
        stream: InputStream,
        limit: Int,
        outputStream: GradleOutputStream,
        onOutput: (GradleOutputStream, String) -> Unit,
    ): CapturedOutput {
        val buffer = ByteArray(8192)
        val tail = TailBuffer(limit)

        stream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                tail.append(buffer, read)
                onOutput(
                    outputStream,
                    String(buffer, 0, read, StandardCharsets.UTF_8),
                )
            }
        }

        return CapturedOutput(
            text = tail.text(),
            truncated = tail.truncated,
        )
    }

    private data class CapturedOutput(
        val text: String,
        val truncated: Boolean,
    )

    private class TailBuffer(
        private val limit: Int,
    ) {
        private val bytes = ByteArray(limit)
        private var start = 0
        private var size = 0
        private var totalBytes = 0L

        val truncated: Boolean
            get() = totalBytes > limit

        fun append(source: ByteArray, length: Int) {
            if (length <= 0) return
            totalBytes += length

            if (length >= limit) {
                source.copyInto(
                    destination = bytes,
                    destinationOffset = 0,
                    startIndex = length - limit,
                    endIndex = length,
                )
                start = 0
                size = limit
                return
            }

            val overflow = (size + length - limit).coerceAtLeast(0)
            if (overflow > 0) {
                start = (start + overflow) % limit
                size -= overflow
            }

            var sourceOffset = 0
            var remaining = length
            while (remaining > 0) {
                val destination = (start + size) % limit
                val chunk = minOf(remaining, limit - destination)
                source.copyInto(
                    destination = bytes,
                    destinationOffset = destination,
                    startIndex = sourceOffset,
                    endIndex = sourceOffset + chunk,
                )
                size += chunk
                sourceOffset += chunk
                remaining -= chunk
            }
        }

        fun text(): String {
            if (size == 0) return ""
            val ordered = ByteArray(size)
            val first = minOf(size, limit - start)
            bytes.copyInto(ordered, 0, start, start + first)
            if (first < size) {
                bytes.copyInto(ordered, first, 0, size - first)
            }
            return String(ordered, StandardCharsets.UTF_8)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 100L
        const val TERMINATION_GRACE_MILLIS = 500L
        const val READER_JOIN_SECONDS = 5L
    }
}
