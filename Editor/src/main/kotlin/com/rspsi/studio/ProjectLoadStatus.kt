package com.rspsi.studio

/** Immutable status snapshot rendered by the pre-dashboard project loading gate. */
data class ProjectLoadStatus private constructor(
    private val phaseValue: Phase,
    private val progressValue: Double,
    private val messageValue: String,
    private val detailValue: String,
    private val failureValue: Throwable?,
) {
    enum class Phase {
        READ_DESCRIPTOR,
        VALIDATE_PROJECT,
        INSPECT_INTEGRATION,
        RESOLVE_CACHE_ROLES,
        VERIFY_CACHE,
        OPEN_CACHE_FILESYSTEM,
        PREPARE_DEFINITIONS,
        BIND_REQUIRED_PROJECT_SERVICES,
        READY,
        FAILED,
    }

    constructor(
        phase: Phase?,
        progress: Double,
        message: String?,
        detail: String?,
        failure: Throwable?,
    ) : this(
        phaseValue = phase ?: Phase.READ_DESCRIPTOR,
        progressValue = progress.coerceIn(0.0, 1.0),
        messageValue = message.orEmpty(),
        detailValue = detail.orEmpty(),
        failureValue = failure,
    )

    fun phase(): Phase = phaseValue

    fun progress(): Double = progressValue

    fun message(): String = messageValue

    fun detail(): String = detailValue

    fun failure(): Throwable? = failureValue

    fun failed(): Boolean = phaseValue == Phase.FAILED

    companion object {
        @JvmStatic
        fun initial(): ProjectLoadStatus =
            ProjectLoadStatus(
                Phase.READ_DESCRIPTOR,
                0.05,
                "Reading project descriptor...",
                "",
                null,
            )
    }
}
