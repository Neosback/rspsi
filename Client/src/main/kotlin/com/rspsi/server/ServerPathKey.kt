package com.rspsi.server

import java.util.Locale

/** Well-known OpenRune project paths that may be overridden per connection. */
enum class ServerPathKey(
    private val configNameValue: String,
) {
    LIVE_CACHE("live_cache"),
    SERVER_CACHE("server_cache"),
    RAW_CACHE("raw_cache"),
    GAMEVALS("gamevals"),
    GAMEVALS_BINARY("gamevals_binary"),
    CONTENT("content"),
    PACKS("packs"),
    RUNTIME_PLUGINS("runtime_plugins"),
    CS2("cs2"),
    ;

    /** Stable serialized name used by saved connection configuration. */
    fun configName(): String = configNameValue

    companion object {
        /** Resolves a serialized path key while preserving the previous Java normalization rules. */
        @JvmStatic
        fun fromConfigName(value: String): ServerPathKey {
            val normalized = value.trim().lowercase(Locale.getDefault())
            return entries.firstOrNull { it.configNameValue == normalized }
                ?: throw IllegalArgumentException("Unknown server path key: $value")
        }
    }
}
