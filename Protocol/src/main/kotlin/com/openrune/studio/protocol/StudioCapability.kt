package com.openrune.studio.protocol

data class StudioCapability(
    val id: String,
    val version: Int = 1,
) {
    init {
        require(id.matches(CAPABILITY_ID)) {
            "capability id must be lowercase dot-separated tokens: $id"
        }
        require(version > 0) { "capability version must be positive" }
    }

    companion object {
        private val CAPABILITY_ID = Regex("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+")
    }
}

object StudioCapabilities {
    val ProjectOpen = StudioCapability("project.open")
    val ProjectInspection = StudioCapability("project.inspect")
    val CacheRead = StudioCapability("cache.read")
    val ContentIndex = StudioCapability("content.index")
    val ContentResolve = StudioCapability("content.resolve")
    val SourceIndex = StudioCapability("source.index")
    val GradleTasks = StudioCapability("gradle.tasks")

    val RuntimeIdentity = StudioCapability("runtime.identity")
    val RuntimeLifecycle = StudioCapability("runtime.lifecycle")
    val RuntimePlugins = StudioCapability("runtime.plugins")
    val RuntimeScripts = StudioCapability("runtime.scripts")
    val RuntimeEvents = StudioCapability("runtime.events")
    val RuntimeCache = StudioCapability("runtime.cache")

    val all: List<StudioCapability> =
        listOf(
            ProjectOpen,
            ProjectInspection,
            CacheRead,
            ContentIndex,
            ContentResolve,
            SourceIndex,
            GradleTasks,
            RuntimeIdentity,
            RuntimeLifecycle,
            RuntimePlugins,
            RuntimeScripts,
            RuntimeEvents,
            RuntimeCache,
        )
}
