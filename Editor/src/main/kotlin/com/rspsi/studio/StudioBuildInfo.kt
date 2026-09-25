package com.rspsi.studio

/** Lightweight build metadata safe to use before any project/cache is opened. */
object StudioBuildInfo {
    private const val VERSION_PROPERTY = "openrune.studio.version"
    private const val DEVELOPMENT_VERSION = "dev"

    @JvmStatic
    fun version(): String {
        System.getProperty(VERSION_PROPERTY)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return StudioBuildInfo::class.java.package
            ?.implementationVersion
            ?.takeIf { it.isNotBlank() }
            ?: DEVELOPMENT_VERSION
    }

    @JvmStatic
    fun displayVersion(): String = "OpenRune Content Studio  v${version()}"
}
