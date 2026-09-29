package com.openrune.studio.service.gradle

import com.openrune.studio.service.ApiErrorCode
import com.openrune.studio.service.ApiException
import com.openrune.studio.service.project.ProjectSession
import io.ktor.http.HttpStatusCode
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class GradleOperationState {
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELLED,
    ;

    val terminal: Boolean
        get() = this != RUNNING
}

data class GradleOperationDescriptor(
    val id: String,
    val task: String,
    val timeoutMillis: Long,
)

data class GradleOperationSnapshot(
    val operationId: String,
    val projectId: String,
    val operation: String,
    val task: String,
    val state: GradleOperationState,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val durationMillis: Long,
    val exitCode: Int?,
    val stdoutTail: String,
    val stderrTail: String,
    val stdoutTruncated: Boolean,
    val stderrTruncated: Boolean,
    val cancelRequested: Boolean,
    val sequence: Long,
    val errorCode: String?,
    val errorMessage: String?,
)

interface GradleOperationService {
    fun catalog(): List<GradleOperationDescriptor>

    fun start(
        project: ProjectSession,
        operationId: String,
    ): GradleOperationSnapshot

    fun requireSnapshot(
        project: ProjectSession,
        operationId: String,
    ): GradleOperationSnapshot

    fun cancel(
        project: ProjectSession,
        operationId: String,
    ): GradleOperationSnapshot

    fun snapshots(
        project: ProjectSession,
        operationId: String,
    ): StateFlow<GradleOperationSnapshot>
}

class DefaultGradleOperationService(
    private val runner: GradleCommandRunner = GradleProcessRunner(),
    private val maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    private val liveLogChars: Int = DEFAULT_LIVE_LOG_CHARS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : GradleOperationService {
    private val activeProjectRoots = ConcurrentHashMap.newKeySet<Path>()
    private val records = ConcurrentHashMap<String, OperationRecord>()
    private val recordOrder = ConcurrentLinkedDeque<String>()

    init {
        require(maxOutputBytes > 0) { "maxOutputBytes must be positive" }
        require(liveLogChars > 0) { "liveLogChars must be positive" }
    }

    override fun catalog(): List<GradleOperationDescriptor> =
        SupportedOperation.entries.map { operation ->
            GradleOperationDescriptor(
                id = operation.id,
                task = operation.task,
                timeoutMillis = operation.timeout.toMillis(),
            )
        }

    override fun start(
        project: ProjectSession,
        operationId: String,
    ): GradleOperationSnapshot {
        val operation =
            SupportedOperation.fromId(operationId)
                ?: throw ApiException(
                    code = ApiErrorCode.GRADLE_OPERATION_INVALID,
                    status = HttpStatusCode.BadRequest,
                    message = "Unsupported Gradle operation.",
                    details = mapOf("operation" to operationId),
                )

        if (!activeProjectRoots.add(project.root)) {
            throw ApiException(
                code = ApiErrorCode.GRADLE_OPERATION_BUSY,
                status = HttpStatusCode.Conflict,
                message = "A Gradle operation is already active for this project checkout.",
            )
        }

        val record =
            OperationRecord(
                operationId = UUID.randomUUID().toString(),
                projectId = project.id,
                operation = operation.id,
                task = operation.task,
                startedAtEpochMillis = System.currentTimeMillis(),
                logLimit = liveLogChars,
            )
        records[record.operationId] = record
        recordOrder.addLast(record.operationId)

        scope.launch {
            try {
                val result =
                    runner.executeControlled(
                        project = project,
                        arguments =
                            listOf(
                                operation.task,
                                "--console=plain",
                                "--no-daemon",
                            ),
                        timeout = operation.timeout,
                        maxOutputBytes = maxOutputBytes,
                        control =
                            GradleExecutionControl(
                                isCancellationRequested = record::isCancellationRequested,
                                onOutput = record::appendOutput,
                            ),
                    )
                record.completeFromProcess(result)
            } catch (failure: GradleProcessException) {
                record.completeFromFailure(failure)
            } catch (failure: Throwable) {
                record.complete(
                    state = GradleOperationState.FAILED,
                    exitCode = null,
                    errorCode = ApiErrorCode.INTERNAL_ERROR.name,
                    errorMessage = "Unexpected Gradle operation failure.",
                )
            } finally {
                activeProjectRoots.remove(project.root)
                trimRecords()
            }
        }

        return record.snapshot()
    }

    override fun requireSnapshot(
        project: ProjectSession,
        operationId: String,
    ): GradleOperationSnapshot =
        requireRecord(project, operationId).snapshot()

    override fun cancel(
        project: ProjectSession,
        operationId: String,
    ): GradleOperationSnapshot {
        val record = requireRecord(project, operationId)
        record.requestCancellation()
        return record.snapshot()
    }

    override fun snapshots(
        project: ProjectSession,
        operationId: String,
    ): StateFlow<GradleOperationSnapshot> =
        requireRecord(project, operationId).flow()

    private fun requireRecord(
        project: ProjectSession,
        operationId: String,
    ): OperationRecord {
        val record = records[operationId]
        if (record == null || record.projectId != project.id) {
            throw ApiException(
                code = ApiErrorCode.GRADLE_OPERATION_NOT_FOUND,
                status = HttpStatusCode.NotFound,
                message = "Gradle operation was not found for this project.",
            )
        }
        return record
    }

    private fun trimRecords() {
        while (recordOrder.size > MAX_RETAINED_RESULTS) {
            val oldestId = recordOrder.peekFirst() ?: return
            val oldest = records[oldestId]
            if (oldest != null && !oldest.snapshot().state.terminal) {
                return
            }
            recordOrder.pollFirst()
            records.remove(oldestId)
        }
    }

    private class OperationRecord(
        val operationId: String,
        val projectId: String,
        private val operation: String,
        private val task: String,
        private val startedAtEpochMillis: Long,
        logLimit: Int,
    ) {
        private val cancellationRequested = AtomicBoolean(false)
        private val stdout = TextTailBuffer(logLimit)
        private val stderr = TextTailBuffer(logLimit)

        private var state = GradleOperationState.RUNNING
        private var completedAtEpochMillis: Long? = null
        private var exitCode: Int? = null
        private var sequence = 0L
        private var errorCode: String? = null
        private var errorMessage: String? = null

        private val stateFlow =
            kotlinx.coroutines.flow.MutableStateFlow(snapshotLocked())

        @Synchronized
        fun snapshot(): GradleOperationSnapshot = snapshotLocked()

        fun flow(): StateFlow<GradleOperationSnapshot> = stateFlow.asStateFlow()

        fun isCancellationRequested(): Boolean = cancellationRequested.get()

        @Synchronized
        fun requestCancellation() {
            if (state.terminal || cancellationRequested.getAndSet(true)) {
                return
            }
            publishLocked()
        }

        @Synchronized
        fun appendOutput(
            outputStream: GradleOutputStream,
            text: String,
        ) {
            if (text.isEmpty() || state.terminal) {
                return
            }
            when (outputStream) {
                GradleOutputStream.STDOUT -> stdout.append(text)
                GradleOutputStream.STDERR -> stderr.append(text)
            }
            publishLocked()
        }

        @Synchronized
        fun completeFromProcess(result: GradleProcessResult) {
            if (stdout.isEmpty() && result.stdout.isNotEmpty()) {
                stdout.append(result.stdout)
            }
            if (stderr.isEmpty() && result.stderr.isNotEmpty()) {
                stderr.append(result.stderr)
            }
            stdout.markTruncated(result.stdoutTruncated)
            stderr.markTruncated(result.stderrTruncated)

            completeLocked(
                state =
                    if (result.exitCode == 0) {
                        GradleOperationState.SUCCEEDED
                    } else {
                        GradleOperationState.FAILED
                    },
                exitCode = result.exitCode,
                errorCode =
                    if (result.exitCode == 0) {
                        null
                    } else {
                        ApiErrorCode.GRADLE_EXECUTION_FAILED.name
                    },
                errorMessage =
                    if (result.exitCode == 0) {
                        null
                    } else {
                        "Gradle operation exited with a non-zero status."
                    },
            )
        }

        @Synchronized
        fun completeFromFailure(failure: GradleProcessException) {
            when (failure.reason) {
                GradleProcessFailure.TIMEOUT ->
                    completeLocked(
                        state = GradleOperationState.TIMED_OUT,
                        exitCode = null,
                        errorCode = ApiErrorCode.GRADLE_TIMEOUT.name,
                        errorMessage = "Gradle operation exceeded its timeout.",
                    )
                GradleProcessFailure.CANCELLED ->
                    completeLocked(
                        state = GradleOperationState.CANCELLED,
                        exitCode = null,
                        errorCode = null,
                        errorMessage = null,
                    )
                GradleProcessFailure.WRAPPER_UNAVAILABLE ->
                    completeLocked(
                        state = GradleOperationState.FAILED,
                        exitCode = null,
                        errorCode = ApiErrorCode.GRADLE_UNAVAILABLE.name,
                        errorMessage = failure.message,
                    )
                GradleProcessFailure.START_FAILED,
                GradleProcessFailure.INTERRUPTED,
                GradleProcessFailure.OUTPUT_FAILED,
                ->
                    completeLocked(
                        state = GradleOperationState.FAILED,
                        exitCode = null,
                        errorCode = ApiErrorCode.GRADLE_EXECUTION_FAILED.name,
                        errorMessage = failure.message,
                    )
            }
        }

        @Synchronized
        fun complete(
            state: GradleOperationState,
            exitCode: Int?,
            errorCode: String?,
            errorMessage: String?,
        ) {
            completeLocked(state, exitCode, errorCode, errorMessage)
        }

        private fun completeLocked(
            state: GradleOperationState,
            exitCode: Int?,
            errorCode: String?,
            errorMessage: String?,
        ) {
            if (this.state.terminal) {
                return
            }
            this.state = state
            this.exitCode = exitCode
            this.errorCode = errorCode
            this.errorMessage = errorMessage
            this.completedAtEpochMillis = System.currentTimeMillis()
            publishLocked()
        }

        private fun publishLocked() {
            sequence += 1
            stateFlow.value = snapshotLocked()
        }

        private fun snapshotLocked(): GradleOperationSnapshot {
            val now = completedAtEpochMillis ?: System.currentTimeMillis()
            return GradleOperationSnapshot(
                operationId = operationId,
                projectId = projectId,
                operation = operation,
                task = task,
                state = state,
                startedAtEpochMillis = startedAtEpochMillis,
                completedAtEpochMillis = completedAtEpochMillis,
                durationMillis = (now - startedAtEpochMillis).coerceAtLeast(0),
                exitCode = exitCode,
                stdoutTail = stdout.text(),
                stderrTail = stderr.text(),
                stdoutTruncated = stdout.truncated,
                stderrTruncated = stderr.truncated,
                cancelRequested = cancellationRequested.get(),
                sequence = sequence,
                errorCode = errorCode,
                errorMessage = errorMessage,
            )
        }
    }

    private class TextTailBuffer(
        private val limit: Int,
    ) {
        private val text = StringBuilder()
        private var totalChars = 0L
        private var forcedTruncated = false

        val truncated: Boolean
            get() = forcedTruncated || totalChars > limit

        fun append(value: String) {
            if (value.isEmpty()) return
            totalChars += value.length

            if (value.length >= limit) {
                text.setLength(0)
                text.append(value.takeLast(limit))
                return
            }

            text.append(value)
            val overflow = text.length - limit
            if (overflow > 0) {
                text.delete(0, overflow)
            }
        }

        fun markTruncated(value: Boolean) {
            forcedTruncated = forcedTruncated || value
        }

        fun text(): String = text.toString()

        fun isEmpty(): Boolean = text.isEmpty()
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
        const val DEFAULT_LIVE_LOG_CHARS = 256 * 1024
        const val MAX_RETAINED_RESULTS = 100
    }
}
