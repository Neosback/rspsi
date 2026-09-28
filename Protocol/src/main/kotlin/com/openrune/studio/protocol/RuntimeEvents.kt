package com.openrune.studio.protocol

enum class RuntimeEventType {
    LIFECYCLE_CHANGED,
    PLUGIN_CHANGED,
    SCRIPT_CHANGED,
    EVENT_REGISTRATION_CHANGED,
    CACHE_CHANGED,
    DIAGNOSTIC_RAISED,
}

data class RuntimeEventEnvelope(
    val schemaVersion: Int = 1,
    val sequence: Long,
    val timestampEpochMillis: Long,
    val runtimeId: String,
    val type: RuntimeEventType,
    val subjectId: String? = null,
    val attributes: Map<String, String> = emptyMap(),
) {
    init {
        require(schemaVersion > 0) { "schemaVersion must be positive" }
        require(sequence >= 0) { "sequence must not be negative" }
        require(runtimeId.isNotBlank()) { "runtimeId is required" }
    }
}
