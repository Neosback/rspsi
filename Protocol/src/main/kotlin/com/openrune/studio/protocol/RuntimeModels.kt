package com.openrune.studio.protocol

enum class RuntimeLifecyclePhase {
    BOOTSTRAPPING,
    INJECTOR_READY,
    CACHE_READY,
    MAP_READY,
    SCRIPTS_LOADING,
    CONTENT_READY,
    RUNNING,
    SHUTTING_DOWN,
    STOPPED,
    FAILED,
}

data class RuntimeLifecycle(
    val phase: RuntimeLifecyclePhase,
    val sinceEpochMillis: Long,
    val detail: String? = null,
)

data class RuntimeIdentity(
    val protocolVersion: Int,
    val runtimeId: String,
    val serverImplementation: String,
    val serverRevision: Int? = null,
    val javaVersion: String,
    val javaVendor: String,
    val kotlinVersion: String? = null,
    val capabilities: List<StudioCapability> = emptyList(),
) {
    init {
        require(protocolVersion > 0) { "protocolVersion must be positive" }
        require(runtimeId.isNotBlank()) { "runtimeId is required" }
        require(serverImplementation.isNotBlank()) { "serverImplementation is required" }
        require(javaVersion.isNotBlank()) { "javaVersion is required" }
        require(javaVendor.isNotBlank()) { "javaVendor is required" }
    }
}

enum class PluginSourceKind {
    BUILT_IN,
    EXTERNAL_JAR,
    EXTERNAL_DIRECTORY,
    UNKNOWN,
}

enum class PluginState {
    DISCOVERED,
    DISABLED,
    LOADING,
    LOADED,
    UNLOADING,
    UNLOADED,
    FAILED,
}

data class PluginManifest(
    val name: String,
    val description: String,
    val revision: String,
    val author: String,
)

data class PluginIdentity(
    val id: String,
    val source: PluginSourceKind,
    val state: PluginState,
    val enabled: Boolean? = null,
    val manifest: PluginManifest? = null,
    val classLoaderId: String? = null,
    val moduleClasses: List<String> = emptyList(),
    val scriptClasses: List<String> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "plugin id is required" }
    }
}

enum class ScriptState {
    DISCOVERED,
    INSTANTIATED,
    STARTING,
    STARTED,
    STOPPING,
    STOPPED,
    FAILED,
}

data class ScriptIdentity(
    val className: String,
    val state: ScriptState,
    val pluginId: String? = null,
    val classLoaderId: String? = null,
) {
    init {
        require(className.isNotBlank()) { "script className is required" }
    }
}

enum class RuntimeEventKind {
    UNBOUND,
    KEYED,
    SUSPEND,
    UNKNOWN,
}

data class EventRegistration(
    val eventType: String,
    val kind: RuntimeEventKind,
    val key: Long? = null,
    val handlerClassName: String? = null,
    val ownerPluginId: String? = null,
    val classLoaderId: String? = null,
) {
    init {
        require(eventType.isNotBlank()) { "eventType is required" }
    }
}

enum class CacheRole {
    LIVE,
    SERVER,
    OTHER,
}

data class RuntimeCacheState(
    val role: CacheRole,
    val loaded: Boolean,
    val path: String? = null,
    val revision: Int? = null,
    val fingerprint: String? = null,
    val definitionCounts: Map<String, Int> = emptyMap(),
)

enum class DiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
}

data class RuntimeDiagnostic(
    val code: String,
    val severity: DiagnosticSeverity,
    val message: String,
    val capability: StudioCapability? = null,
    val details: Map<String, String> = emptyMap(),
) {
    init {
        require(code.isNotBlank()) { "diagnostic code is required" }
        require(message.isNotBlank()) { "diagnostic message is required" }
    }
}

data class RuntimeSnapshot(
    val schemaVersion: Int = 1,
    val generatedAtEpochMillis: Long,
    val identity: RuntimeIdentity,
    val lifecycle: RuntimeLifecycle,
    val plugins: List<PluginIdentity> = emptyList(),
    val scripts: List<ScriptIdentity> = emptyList(),
    val eventRegistrations: List<EventRegistration> = emptyList(),
    val caches: List<RuntimeCacheState> = emptyList(),
    val diagnostics: List<RuntimeDiagnostic> = emptyList(),
) {
    init {
        require(schemaVersion > 0) { "schemaVersion must be positive" }
    }
}
