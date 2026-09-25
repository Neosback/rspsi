package com.rspsi.studio

/** Immutable status snapshot rendered by the pre-dashboard project loading gate. */
class ProjectLoadStatus(
    phase: Phase?,
    progress: Double,
    message: String?,
    detail: String?,
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

    private val phaseValue: Phase = phase ?: Phase.READ_DESCRIPTOR
    private val progressValue: Double = progress.coerceIn(0.0, 1.0)
    private val messageValue: String = message.orEmpty()
    private val detailValue: String = detail.orEmpty()

    fun phase(): Phase = phaseValue

    fun progress(): Double = progressValue

    fun message(): String = messageValue

    fun detail(): String = detailValue

    fun failure(): Throwable? = failureValue

    fun failed(): Boolean = phaseValue == Phase.FAILED

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProjectLoadStatus) return false
        return phaseValue == other.phaseValue &&
            java.lang.Double.compare(progressValue, other.progressValue) == 0 &&
            messageValue == other.messageValue &&
            detailValue == other.detailValue &&
            failureValue == other.failureValue
    }

    override fun hashCode(): Int {
        var result = phaseValue.hashCode()
        result = 31 * result + progressValue.hashCode()
        result = 31 * result + messageValue.hashCode()
        result = 31 * result + detailValue.hashCode()
        result = 31 * result + (failureValue?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "ProjectLoadStatus[phase=$phaseValue, progress=$progressValue, " +
            "message=$messageValue, detail=$detailValue, failure=$failureValue]"

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
